package ru.ordercontrol.update

import android.content.Context
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.ordercontrol.BuildConfig
import ru.ordercontrol.R

/** Сведения о релизе, который новее установленного. */
data class UpdateInfo(
    val versionName: String,
    val versionCode: Int,
    val notes: String,
    val apkName: String,
    val apkUrl: String,
    val sizeBytes: Long,
)

sealed interface UpdateCheckResult {

    data class Available(val info: UpdateInfo) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult

    data object NoNetwork : UpdateCheckResult

    data class Failure(val reason: String) : UpdateCheckResult
}

/**
 * Проверка обновлений по последнему релизу GitHub и скачивание APK.
 *
 * Версия берётся из тега релиза (`v0.0.128`): последнее число — это
 * `versionCode` (см. `doc/versions.md`). Сравнивается с `versionCode`
 * установленного приложения, а не с `versionName`.
 *
 * Скачивание умеет докачивать: недокачанный файл лежит в `filesDir/updates`
 * под именем `<apk>.part` и при следующей попытке запрос продолжается с
 * нужного места (`Range`/`If-Range`). Обрыв связи не отменяет загрузку:
 * запрос повторяется с паузой, каждый раз продолжая с места обрыва, пока
 * попытки не начнут приносить новые байты. Файл `.part` остаётся на диске
 * и после неудачи — следующий запуск докачает с него.
 */
class UpdateClient(
    private val context: Context,
    private val repo: String = BuildConfig.UPDATE_REPO,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) {

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val primary = try {
            get(latestReleaseUrl())?.let { parse(it) } ?: UpdateCheckResult.Failure("HTTP")
        } catch (_: IOException) {
            UpdateCheckResult.NoNetwork
        }
        // API GitHub без токена отдаёт 60 запросов в час на IP и на остальные
        // отвечает 403. Страница релиза лимита не имеет, поэтому при любой
        // неудаче API версию берём из её редиректа.
        when (primary) {
            is UpdateCheckResult.Available, UpdateCheckResult.UpToDate,
            UpdateCheckResult.NoNetwork -> primary
            is UpdateCheckResult.Failure -> checkViaRedirect() ?: primary
        }
    }

    /**
     * Скачивает APK релиза и проверяет, что это действительно наше
     * приложение и версия не ниже заявленной.
     *
     * @param onProgress доля скачанного в процентах.
     * @param onStatus сообщение о ходе: повтор запроса или докачка.
     */
    suspend fun download(
        info: UpdateInfo,
        onProgress: (Int) -> Unit,
        onStatus: (String) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        // filesDir, а не cacheDir: систему просят «освободить место» и чистить
        // кэш прямо до подтверждения установки — APK не должен исчезнуть.
        val dir = updatesDir()
        val apkName = apkFileName(info)
        val target = File(dir, apkName)
        val part = File(dir, "$apkName.part")
        val etag = File(dir, "$apkName.etag")
        cleanUp(dir, keep = setOf(apkName, part.name, etag.name))
        if (target.exists()) target.delete()

        // Повторяем, пока попытки приносят новые байты. Сдаёмся, только если
        // несколько попыток подряд ничего не докачали: значит, сеть или
        // сервер сейчас недоступны, и ждать дальше бессмысленно.
        var stalled = 0
        while (true) {
            val before = part.length()
            if (before > 0) {
                status(
                    onStatus,
                    context.getString(
                        R.string.update_resume,
                        DownloadSupport.percentOf(before, info.sizeBytes),
                    ),
                )
            }
            try {
                downloadOnce(info, part, etag, onProgress)
                break
            } catch (e: FatalDownloadException) {
                throw e
            } catch (e: IOException) {
                val progressed = part.length() > before
                stalled = if (progressed) 0 else stalled + 1
                if (stalled > MAX_STALLED_ATTEMPTS) {
                    throw IOException(context.getString(R.string.update_download_failed), e)
                }
                status(onStatus, context.getString(R.string.update_retrying, stalled))
                delay(if (progressed) RESUMED_PAUSE_MS else backoffMs(stalled))
            }
        }

        if (!part.renameTo(target)) {
            part.copyTo(target, overwrite = true)
            part.delete()
        }
        etag.delete()
        verify(target, info)
        target
    }

    /** Сколько байт обновления уже лежит в недокачанном `.part`. */
    fun partialBytes(info: UpdateInfo): Long = partFile(info).length()

    /**
     * Уже скачанный и проверенный APK этого релиза; `null` — файла нет.
     * Битый или чужой файл удаляется.
     */
    fun completedApk(info: UpdateInfo): File? {
        val target = File(updatesDir(), apkFileName(info))
        if (!target.isFile) return null
        return try {
            verify(target, info)
            target
        } catch (_: IOException) {
            target.delete()
            null
        }
    }

    private fun updatesDir(): File =
        File(context.filesDir, UPDATES_DIR).apply { mkdirs() }

    private fun apkFileName(info: UpdateInfo): String =
        info.apkName.substringAfterLast('/').substringAfterLast('\\')

    private fun partFile(info: UpdateInfo): File =
        File(updatesDir(), "${apkFileName(info)}.part")

    /**
     * Одна попытка скачивания. Продолжает файл `.part` с места обрыва,
     * если сервер умеет отдавать куски.
     */
    private suspend fun downloadOnce(
        info: UpdateInfo,
        part: File,
        etagFile: File,
        onProgress: (Int) -> Unit,
    ) {
        val offset = part.length()
        val connection = open(info.apkUrl, offset, etagFile)
        try {
            val code = connection.responseCode
            if (code == HTTP_RANGE_NOT_SATISFIABLE) {
                // Сервер считает, что отдавать с этого места нечего: либо файл
                // уже скачан целиком, либо `.part` больше самого файла.
                // Пусть решает проверка APK после скачивания.
                if (part.length() > 0) return
                throw IOException("сервер не смог продолжить загрузку")
            }
            if (code != HttpURLConnection.HTTP_OK &&
                code != HttpURLConnection.HTTP_PARTIAL
            ) {
                val message = "HTTP $code"
                throw if (DownloadSupport.isRetryableStatus(code)) {
                    IOException(message)
                } else {
                    FatalDownloadException(message)
                }
            }

            saveEtag(connection, etagFile)

            val range = if (code == HttpURLConnection.HTTP_PARTIAL) {
                DownloadSupport.parseContentRange(connection.getHeaderField("Content-Range"))
            } else {
                null
            }
            if (range != null && range.start != offset) {
                // Сервер начал не с того места, которое мы просили, — куски
                // не сойдутся. Начинаем скачивание заново.
                part.delete()
                throw IOException("сервер начал загрузку с ${range.start}")
            }

            val resumeFrom = if (code == HttpURLConnection.HTTP_PARTIAL) offset else 0L
            if (offset > 0 && resumeFrom == 0L) {
                // Докачка не поддержана — пишем файл с нуля.
                part.delete()
            }

            val total = range?.total?.takeIf { it > 0 }
                ?: info.sizeBytes.takeIf { it > 0 }
                ?: connection.contentLengthLong.takeIf { it > 0 }?.let { it + resumeFrom }
                ?: -1L

            val received = writeStream(connection, part, resumeFrom, total, onProgress)
            if (total > 0 && resumeFrom + received < total) {
                throw IOException("соединение прервалось")
            }
        } finally {
            connection.disconnect()
        }
    }

    /** Скачивает поток в `.part`, начиная с `resumeFrom`. Возвращает число байт. */
    private suspend fun writeStream(
        connection: HttpURLConnection,
        part: File,
        resumeFrom: Long,
        total: Long,
        onProgress: (Int) -> Unit,
    ): Long {
        var received = 0L
        var lastPercent = -1
        RandomAccessFile(part, "rw").use { file ->
            file.seek(resumeFrom)
            connection.inputStream.use { input ->
                val buffer = ByteArray(BUFFER_SIZE)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    file.write(buffer, 0, count)
                    received += count
                    val percent = DownloadSupport.percentOf(resumeFrom + received, total)
                    if (percent != lastPercent) {
                        lastPercent = percent
                        withContext(Dispatchers.Main) { onProgress(percent) }
                    }
                }
            }
        }
        return received
    }

    /**
     * Открывает соединение с нужными заголовками. Редиректы разворачиваем
     * сами, чтобы заголовок `Range` не потерялся по дороге.
     */
    private fun open(url: String, offset: Long, etagFile: File): HttpURLConnection {
        var current = url
        var redirects = 0
        while (true) {
            val connection = URL(current).openConnection() as HttpURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = READ_TIMEOUT_MS
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Accept", "*/*")
            if (offset > 0) {
                connection.setRequestProperty("Range", "bytes=$offset-")
                val etag = etagFile.takeIf { it.isFile }?.readText()?.trim()
                if (!etag.isNullOrBlank()) connection.setRequestProperty("If-Range", etag)
            }

            val code = connection.responseCode
            if (code in 300..399) {
                val location = connection.getHeaderField("Location")
                connection.disconnect()
                if (location.isNullOrBlank()) throw FatalDownloadException("HTTP $code")
                if (redirects++ >= MAX_REDIRECTS) {
                    throw IOException("слишком много перенаправлений")
                }
                current = URL(URL(current), location).toString()
                continue
            }
            return connection
        }
    }

    /** Сохраняет ETag, чтобы докачка билась ровно с тем же файлом. */
    private fun saveEtag(connection: HttpURLConnection, etagFile: File) {
        val etag = connection.getHeaderField("ETag")
        if (etag.isNullOrBlank()) {
            etagFile.delete()
        } else {
            etagFile.writeText(etag)
        }
    }

    /** Убирает файлы прошлых загрузок, кроме текущего. */
    private fun cleanUp(dir: File, keep: Set<String>) {
        dir.listFiles()?.forEach { file ->
            if (file.isFile && file.name !in keep) file.delete()
        }
    }

    private fun parse(body: String): UpdateCheckResult {
        // Ответ может быть не JSON: например, captive-портал отдаёт HTML
        // с кодом 200. Это не повод ронять приложение.
        val json = try {
            JSONObject(body)
        } catch (_: Exception) {
            return UpdateCheckResult.Failure("ответ не JSON")
        }
        val tag = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        val versionCode = tag.substringAfterLast('.').toIntOrNull()
            ?: return UpdateCheckResult.Failure("непонятный тег: $tag")
        if (versionCode <= currentVersionCode) return UpdateCheckResult.UpToDate

        val assets = json.optJSONArray("assets")
            ?: return UpdateCheckResult.Failure("в релизе нет файлов")
        val expected = "$APK_PREFIX$tag.apk"
        var fallback: UpdateInfo? = null
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
            val info = UpdateInfo(
                versionName = tag,
                versionCode = versionCode,
                notes = json.optString("body").trim(),
                apkName = name,
                apkUrl = url,
                sizeBytes = asset.optLong("size"),
            )
            if (name.equals(expected, ignoreCase = true)) {
                return UpdateCheckResult.Available(info)
            }
            if (fallback == null) fallback = info
        }
        return fallback?.let { UpdateCheckResult.Available(it) }
            ?: UpdateCheckResult.Failure("в релизе нет APK")
    }

    /**
     * Версия из редиректа `github.com/<repo>/releases/latest` — без API и без
     * лимита запросов. Файл собирается по тому же имени, что публикует
     * `release.yml`: `OrderControl-<версия>.apk`.
     */
    private fun checkViaRedirect(): UpdateCheckResult? {
        return try {
            val connection =
                URL("https://github.com/$repo/releases/latest").openConnection() as HttpURLConnection
            val location = try {
                connection.instanceFollowRedirects = false
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.setRequestProperty("User-Agent", USER_AGENT)
                if (connection.responseCode !in 300..399) {
                    null
                } else {
                    connection.getHeaderField("Location")
                }
            } finally {
                connection.disconnect()
            }
            val tag = location
                ?.takeIf { it.contains("/tag/") }
                ?.substringAfterLast("/tag/")
                ?.trim()
                ?.removePrefix("v")
                ?.removePrefix("V")
                ?.takeIf { it.isNotBlank() }
                ?: return null
            val versionCode = tag.substringAfterLast('.').toIntOrNull() ?: return null
            if (versionCode <= currentVersionCode) return UpdateCheckResult.UpToDate
            UpdateCheckResult.Available(
                UpdateInfo(
                    versionName = tag,
                    versionCode = versionCode,
                    notes = "",
                    apkName = "$APK_PREFIX$tag.apk",
                    apkUrl = "https://github.com/$repo/releases/download/v$tag/$APK_PREFIX$tag.apk",
                    sizeBytes = 0L,
                ),
            )
        } catch (_: IOException) {
            null
        }
    }

    private fun verify(file: File, info: UpdateInfo) {
        if (!file.exists() || file.length() == 0L) throw FatalDownloadException("пустой файл")
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw FatalDownloadException("APK не читается")
        if (archive.packageName != context.packageName) throw FatalDownloadException("чужой APK")
        if (archive.longVersionCode < info.versionCode) throw FatalDownloadException("старый APK")
    }

    /** GET-запрос. `null` — сервер ответил не 200. */
    private fun get(requestUrl: String): String? {
        val connection = URL(requestUrl).openConnection() as HttpURLConnection
        try {
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    internal fun latestReleaseUrl(): String = "https://api.github.com/repos/$repo/releases/latest"

    private suspend fun status(onStatus: (String) -> Unit, text: String) {
        withContext(Dispatchers.Main) { onStatus(text) }
    }

    private fun backoffMs(attempt: Int): Long =
        (BASE_BACKOFF_MS shl (attempt - 1).coerceIn(0, 6)).coerceAtMost(MAX_BACKOFF_MS)

    private companion object {
        const val UPDATES_DIR = "updates"
        const val APK_PREFIX = "OrderControl-"
        const val TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 30_000
        const val BUFFER_SIZE = 64 * 1024
        const val USER_AGENT = "OrderControl (Android)"
        // Пока попытки приносят байты, счётчик обнуляется: медленную, но
        // живую загрузку не обрываем. Это предел именно «пустых» попыток.
        const val MAX_STALLED_ATTEMPTS = 8
        const val MAX_REDIRECTS = 5
        const val BASE_BACKOFF_MS = 1_000L
        const val MAX_BACKOFF_MS = 30_000L
        const val RESUMED_PAUSE_MS = 500L
        const val HTTP_RANGE_NOT_SATISFIABLE = 416
    }
}

/** Ошибка, повторением которую не вылечить. */
private class FatalDownloadException(
    message: String,
    cause: Throwable? = null,
) : IOException(message, cause)
