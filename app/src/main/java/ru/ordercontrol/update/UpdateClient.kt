package ru.ordercontrol.update

import android.content.Context
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import ru.ordercontrol.BuildConfig

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
 */
class UpdateClient(
    private val context: Context,
    private val repo: String = BuildConfig.UPDATE_REPO,
    private val currentVersionCode: Int = BuildConfig.VERSION_CODE,
) {

    suspend fun check(): UpdateCheckResult = withContext(Dispatchers.IO) {
        val body = try {
            get(latestReleaseUrl())
        } catch (_: IOException) {
            return@withContext UpdateCheckResult.NoNetwork
        } ?: return@withContext UpdateCheckResult.Failure("HTTP")
        parse(body)
    }

    /**
     * Скачивает APK релиза во временный каталог и проверяет, что это
     * действительно наше приложение и версия не ниже заявленной.
     */
    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File =
        withContext(Dispatchers.IO) {
            val dir = File(context.cacheDir, UPDATES_DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { if (it.isFile) it.delete() }
            val target = File(dir, info.apkName)

            val connection = URL(info.apkUrl).openConnection() as HttpURLConnection
            try {
                connection.connectTimeout = TIMEOUT_MS
                connection.readTimeout = TIMEOUT_MS
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("User-Agent", USER_AGENT)
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IOException("HTTP ${connection.responseCode}")
                }

                val total = connection.contentLengthLong
                var read = 0L
                var lastPercent = -1
                connection.inputStream.use { input ->
                    target.outputStream().use { out ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            out.write(buffer, 0, count)
                            read += count
                            val percent = if (total > 0) ((read * 100) / total).toInt() else 0
                            if (percent != lastPercent) {
                                lastPercent = percent
                                withContext(Dispatchers.Main) { onProgress(percent) }
                            }
                        }
                    }
                }
                verify(target, info)
                target
            } finally {
                connection.disconnect()
            }
        }

    private fun parse(body: String): UpdateCheckResult {
        val json = JSONObject(body)
        val tag = json.optString("tag_name").trim().removePrefix("v").removePrefix("V")
        val versionCode = tag.substringAfterLast('.').toIntOrNull()
            ?: return UpdateCheckResult.Failure("непонятный тег: $tag")
        if (versionCode <= currentVersionCode) return UpdateCheckResult.UpToDate

        val assets = json.optJSONArray("assets")
            ?: return UpdateCheckResult.Failure("в релизе нет файлов")
        for (i in 0 until assets.length()) {
            val asset = assets.optJSONObject(i) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url")
            if (!name.endsWith(".apk", ignoreCase = true) || url.isBlank()) continue
            return UpdateCheckResult.Available(
                UpdateInfo(
                    versionName = tag,
                    versionCode = versionCode,
                    notes = json.optString("body").trim(),
                    apkName = name,
                    apkUrl = url,
                    sizeBytes = asset.optLong("size"),
                ),
            )
        }
        return UpdateCheckResult.Failure("в релизе нет APK")
    }

    private fun verify(file: File, info: UpdateInfo) {
        if (!file.exists() || file.length() == 0L) throw IOException("пустой файл")
        val archive = context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)
            ?: throw IOException("APK не читается")
        if (archive.packageName != context.packageName) throw IOException("чужой APK")
        if (archive.longVersionCode < info.versionCode) throw IOException("старый APK")
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

    private companion object {
        const val UPDATES_DIR = "updates"
        const val TIMEOUT_MS = 15_000
        const val BUFFER_SIZE = 64 * 1024
        const val USER_AGENT = "OrderControl (Android)"
    }
}
