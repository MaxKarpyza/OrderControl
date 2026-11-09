package ru.ordercontrol.update

import android.content.Context
import java.io.File
import ru.ordercontrol.data.SettingsRepository

/**
 * Обновления приложения: проверка последнего релиза GitHub и скачивание APK.
 * Отметка о проверке хранится в настройках, чтобы не дёргать API при каждом
 * запуске.
 */
class UpdateRepository(
    context: Context,
    private val settings: SettingsRepository,
) {

    private val client = UpdateClient(context)

    suspend fun check(): UpdateCheckResult {
        val result = client.check()
        if (result != UpdateCheckResult.NoNetwork) {
            settings.markUpdateChecked()
        }
        return result
    }

    suspend fun download(info: UpdateInfo, onProgress: (Int) -> Unit): File =
        client.download(info, onProgress)
}
