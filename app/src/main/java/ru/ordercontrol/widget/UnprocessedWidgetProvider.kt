package ru.ordercontrol.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.content.ContextCompat
import ru.ordercontrol.MainActivity
import ru.ordercontrol.R
import ru.ordercontrol.data.UnreadSummary

/** Виджет 2x2: счётчик необработанных звонков и цветовая индикация. */
class UnprocessedWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        WidgetRefresh.updateAll(context)
    }

    override fun onEnabled(context: Context) {
        WidgetRefresh.updateAll(context)
    }

    companion object {
        fun render(context: Context, summary: UnreadSummary) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(
                ComponentName(context, UnprocessedWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return

            val views = RemoteViews(context.packageName, R.layout.widget_unprocessed)
            val count = summary.total
            val hasUnread = count > 0
            views.setTextViewText(R.id.widget_count, count.toString())
            views.setTextViewText(R.id.widget_label, statusLabel(context, count))
            views.setTextViewText(
                R.id.widget_answered,
                context.getString(R.string.widget_answered, summary.answered),
            )
            views.setTextViewText(
                R.id.widget_missed,
                context.getString(R.string.widget_missed, summary.missed),
            )
            val hasBreakdown = hasUnread && (summary.answered > 0 || summary.missed > 0)
            views.setViewVisibility(
                R.id.widget_breakdown,
                if (hasBreakdown) View.VISIBLE else View.GONE,
            )
            views.setViewVisibility(
                R.id.widget_answered,
                if (summary.answered > 0) View.VISIBLE else View.GONE,
            )
            views.setViewVisibility(
                R.id.widget_missed,
                if (summary.missed > 0) View.VISIBLE else View.GONE,
            )
            views.setImageViewResource(
                R.id.widget_badge_icon,
                when {
                    hasUnread -> R.drawable.ic_widget_phone_alert
                    else -> R.drawable.ic_widget_check
                },
            )
            views.setImageViewResource(
                R.id.widget_chevron,
                if (hasUnread) {
                    R.drawable.ic_widget_chevron_alert
                } else {
                    R.drawable.ic_widget_chevron
                },
            )

            views.setInt(
                R.id.widget_unprocessed_root,
                "setBackgroundResource",
                if (hasUnread) R.drawable.bg_widget_alert else R.drawable.bg_widget_calm,
            )
            val textColor = ContextCompat.getColor(
                context,
                if (hasUnread) R.color.widget_alert_text else R.color.widget_calm_text,
            )
            val mutedColor = ContextCompat.getColor(
                context,
                if (hasUnread) R.color.widget_alert_muted else R.color.widget_calm_muted,
            )
            views.setTextColor(R.id.widget_count, textColor)
            views.setTextColor(R.id.widget_label, textColor)
            views.setTextColor(R.id.widget_title, mutedColor)

            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            val pending = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            views.setOnClickPendingIntent(R.id.widget_unprocessed_root, pending)

            manager.updateAppWidget(ids, views)
        }

        /** Подпись под счётчиком с правильным падежом: 1 звонок, 2 звонка, 5 звонков. */
        private fun statusLabel(context: Context, count: Int): String = when {
            count == 0 -> context.getString(R.string.widget_all_done)
            count % 10 == 1 && count % 100 != 11 ->
                context.getString(R.string.widget_waiting_one)

            count % 10 in 2..4 && count % 100 !in 12..14 ->
                context.getString(R.string.widget_waiting_few)

            else -> context.getString(R.string.widget_waiting_many)
        }

        private const val REQUEST_CODE = 200
    }
}
