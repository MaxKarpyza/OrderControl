package ru.ordercontrol

import android.app.Application
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.ordercontrol.phone.CallSync
import ru.ordercontrol.widget.WidgetRefresh

class App : Application() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        AppGraph.init(this)

        // Пока приложение не запущено, система не доставляет PHONE_STATE,
        // поэтому звонки за последние часы восстанавливаются из журнала.
        scope.launch {
            try {
                val restored = CallSync.catchUp(this@App)
                if (restored > 0) {
                    Log.i(TAG, "Восстановлено звонков из журнала: $restored")
                }
            } catch (e: Exception) {
                // Сбой журнала не должен мешать запуску приложения.
                Log.w(TAG, "Не удалось восстановить звонки из журнала", e)
            }
            WidgetRefresh.updateAll(this@App)
        }
    }

    private companion object {
        const val TAG = "App"
    }
}
