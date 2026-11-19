package ru.ordercontrol.widget

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import ru.ordercontrol.FormActivity
import ru.ordercontrol.R

/** Виджет 1x1: только иконка трубки, открывает мини-форму последнего звонка. */
class OrdersWidgetProvider : WidgetProvider() {

    companion object {
        fun render(context: Context) {
            val (manager, ids) = widgetTargets(context, OrdersWidgetProvider::class.java)
                ?: return

            val views = RemoteViews(context.packageName, R.layout.widget_orders)
            val intent = Intent(context, FormActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pending = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_orders_root, pending)
            manager.updateAppWidget(ids, views)
        }

        private const val REQUEST_CODE = 100
    }
}
