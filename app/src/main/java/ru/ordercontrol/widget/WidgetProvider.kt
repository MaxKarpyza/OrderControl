package ru.ordercontrol.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context

/**
 * Общая часть виджетов: системе достаточно сказать, что данные могли
 * измениться, — оба виджета обновляются одинаково.
 */
abstract class WidgetProvider : AppWidgetProvider() {

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
}

/**
 * Менеджер и id-ы экземпляров виджета; `null`, если виджет не добавлен
 * на экран — обновлять нечего.
 */
internal fun widgetTargets(
    context: Context,
    provider: Class<*>,
): Pair<AppWidgetManager, IntArray>? {
    val manager = AppWidgetManager.getInstance(context)
    val ids = manager.getAppWidgetIds(ComponentName(context, provider))
    return ids.takeIf { it.isNotEmpty() }?.let { manager to it }
}
