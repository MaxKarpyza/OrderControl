package ru.ordercontrol.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * Какой день (и, значит, какая вкладка таблицы) соответствует заказу.
 *
 * День доставки хранится относительно дня звонка, поэтому конкретная дата
 * вычисляется только здесь и только в момент записи.
 */
object SheetTarget {

    fun date(
        incomingAt: Long,
        deliveryDay: DeliveryDay?,
        zone: ZoneId = ZoneId.systemDefault(),
        today: LocalDate = LocalDate.now(zone),
    ): LocalDate {
        val callDate = Instant.ofEpochMilli(incomingAt).atZone(zone).toLocalDate()
        val target = when (deliveryDay) {
            DeliveryDay.TOMORROW -> callDate.plusDays(1)
            DeliveryDay.DAY_AFTER_TOMORROW -> callDate.plusDays(2)

            // Звонок в понедельник с пометкой «понедельник» — это следующая
            // неделя: ближайший понедельник уже сегодня.
            DeliveryDay.MONDAY -> callDate
                .with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY))
                .let { if (it == callDate) callDate.plusWeeks(1) else it }

            // «Другой день» и неотмеченный заказ идут в текущую вкладку:
            // конкретную дату оператор не называл.
            DeliveryDay.OTHER_DAY, null -> today
        }
        // По воскресеньям доставки нет, поэтому всё, что попало на воскресенье,
        // уезжает на понедельник.
        return if (target.dayOfWeek == DayOfWeek.SUNDAY) target.plusDays(1) else target
    }
}
