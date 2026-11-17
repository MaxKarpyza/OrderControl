package ru.ordercontrol.ui.order

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import ru.ordercontrol.R
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.domain.SheetTarget

/**
 * Что будет считаться днём доставки.
 *
 * Неотмеченный день — это сегодня, а по воскресеньям доставки нет:
 * заказ уезжает в понедельник.
 *
 * @param day отмеченный день доставки.
 * @param at момент заказа: от него считаются варианты дней.
 */
@Composable
fun OrderDeliveryHint(day: DeliveryDay?, at: Long) {
    val date = SheetTarget.date(at, day)
    val weekday = stringResource(dayOfWeekRes(date.dayOfWeek))
    Text(
        text = stringResource(
            R.string.manual_delivery_hint,
            "${date.format(DATE_FORMAT)} ($weekday)",
        ),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private fun dayOfWeekRes(day: DayOfWeek): Int = when (day) {
    DayOfWeek.MONDAY -> R.string.day_monday_short
    DayOfWeek.TUESDAY -> R.string.day_tuesday_short
    DayOfWeek.WEDNESDAY -> R.string.day_wednesday_short
    DayOfWeek.THURSDAY -> R.string.day_thursday_short
    DayOfWeek.FRIDAY -> R.string.day_friday_short
    DayOfWeek.SATURDAY -> R.string.day_saturday_short
    DayOfWeek.SUNDAY -> R.string.day_sunday_short
}

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")
