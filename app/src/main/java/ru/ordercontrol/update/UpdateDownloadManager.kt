package ru.ordercontrol.update

import android.content.Context
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import ru.ordercontrol.BuildConfig
import ru.ordercontrol.data.SettingsRepository

/** Состояние скачивания обновления, общее для всех экранов. */
sealed interface UpdateDownloadState {

    /** Ничего не скачивается и скачанного файла нет. */
    data object Idle : UpdateDownloadState

    data class Downloading(
        val info: UpdateInfo,
        val percent: Int,
        val status: String?,
    ) : UpdateDownloadState

    /** APK скачан и проверен, можно запускать установщик. */
    data class Ready(
        val info: UpdateInfo,
        val apk: File,
    ) : UpdateDownloadState

    /** Скачать не удалось; `.part` остался и будет докачан позже. */
    data class Failed(val info: UpdateInfo) : UpdateDownloadState

    /** Обновление, с которым связано состояние; `null` — ничего нет. */
    val updateInfo: UpdateInfo?
        get() = when (this) {
            UpdateDownloadState.Idle -> null
            is Downloading -> info
            is Ready -> info
            is Failed -> info
        }
}

/**
 * Держит скачивание обновления на уровне приложения, а не экрана.
 *
 * Уход с экрана настроек или плашки больше не отменяет загрузку. Сведения о
 * начатом обновлении сохраняются, поэтому после перезапуска приложения
 * недокачанный файл продолжается сам, без нажатия кнопки. Обрыв связи
 * лечится внутри [UpdateClient] повтором с места обрыва.
 */
class UpdateDownloadManager(
    context: Context,
    private val updates: UpdateRepository,
    private val settings: SettingsRepository,
) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _state = MutableStateFlow<UpdateDownloadState>(UpdateDownloadState.Idle)
    val state: StateFlow<UpdateDownloadState> = _state.asStateFlow()

    private var job: Job? = null
    private var currentVersionCode = 0

    /**
     * Продолжает прерванное обновление после запуска приложения.
     *
     * Если начатого файла уже нет или установлена та же или более новая
     * версия, запись о нём выбрасывается.
     */
    suspend fun resumePending() {
        val info = readPending() ?: return
        if (info.versionCode <= BuildConfig.VERSION_CODE) {
            clearPending()
            return
        }
        // Пользователь скрыл плашку этой версии — не качаем без спроса.
        if (settings.isUpdateDismissed(info.versionCode)) return
        if (updates.partialBytes(info) <= 0 && updates.completedApk(info) == null) {
            clearPending()
            return
        }
        start(info)
    }

    /** Начинает скачивание; уже скачанный APK сразу отдаётся в [UpdateDownloadState.Ready]. */
    fun start(info: UpdateInfo) {
        if (job?.isActive == true && currentVersionCode == info.versionCode) return
        job?.cancel()
        currentVersionCode = info.versionCode

        updates.completedApk(info)?.let { apk ->
            clearPending()
            _state.value = UpdateDownloadState.Ready(info, apk)
            return
        }

        savePending(info)
        job = scope.launch {
            _state.value = UpdateDownloadState.Downloading(
                info = info,
                percent = DownloadSupport.percentOf(updates.partialBytes(info), info.sizeBytes),
                status = null,
            )
            try {
                val apk = updates.download(
                    info = info,
                    onProgress = { percent ->
                        val status = (_state.value as? UpdateDownloadState.Downloading)?.status
                        _state.value = UpdateDownloadState.Downloading(info, percent, status)
                    },
                    onStatus = { status ->
                        val percent =
                            (_state.value as? UpdateDownloadState.Downloading)?.percent ?: 0
                        _state.value = UpdateDownloadState.Downloading(info, percent, status)
                    },
                )
                clearPending()
                _state.value = UpdateDownloadState.Ready(info, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // `.part` и запись о нём остаются: при следующем запуске
                // докачаем с того же места.
                _state.value = UpdateDownloadState.Failed(info)
            }
        }
    }

    /**
     * Возобновляет уже начатое обновление: недокачанный `.part` или
     * полностью скачанный APK. Первый запуск с нуля так не начинается.
     */
    fun resumeIfStarted(info: UpdateInfo) {
        if (job?.isActive == true || _state.value is UpdateDownloadState.Ready) return
        if (updates.partialBytes(info) <= 0 && updates.completedApk(info) == null) return
        start(info)
    }

    private fun savePending(info: UpdateInfo) {
        val json = JSONObject().apply {
            put("versionName", info.versionName)
            put("versionCode", info.versionCode)
            put("notes", info.notes)
            put("apkName", info.apkName)
            put("apkUrl", info.apkUrl)
            put("sizeBytes", info.sizeBytes)
        }
        prefs.edit().putString(KEY_PENDING, json.toString()).apply()
    }

    private fun readPending(): UpdateInfo? {
        val raw = prefs.getString(KEY_PENDING, null) ?: return null
        return try {
            val json = JSONObject(raw)
            UpdateInfo(
                versionName = json.getString("versionName"),
                versionCode = json.getInt("versionCode"),
                notes = json.optString("notes"),
                apkName = json.getString("apkName"),
                apkUrl = json.getString("apkUrl"),
                sizeBytes = json.optLong("sizeBytes"),
            )
        } catch (_: Exception) {
            clearPending()
            null
        }
    }

    private fun clearPending() {
        prefs.edit().remove(KEY_PENDING).apply()
    }

    private companion object {
        const val PREFS_NAME = "update_download"
        const val KEY_PENDING = "pending"
    }
}
