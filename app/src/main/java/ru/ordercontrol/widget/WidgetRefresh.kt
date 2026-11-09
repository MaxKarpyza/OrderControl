package ru.ordercontrol.widget

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.phone.CallSync

/**
 * Push-обновление обоих виджетов: после звонка, сохранения формы и
 * нажатия «Заказы обработаны». Перед обновлением записи сверяются
 * с системным журналом.
 */
object WidgetRefresh {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun updateAll(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            try {
                CallSync.sync(appContext)
                val summary = AppGraph.calls.unreadSummary()
                OrdersWidgetProvider.render(appContext)
                UnprocessedWidgetProvider.render(appContext, summary)
            } catch (_: Exception) {
                // Виджет не должен ломать приложение при сбое данных.
            }
        }
    }
}
