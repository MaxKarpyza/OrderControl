package ru.ordercontrol.domain

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId

/**
 * День доставки, выбранный оператором в мини-форме.
 *
 * Значение хранится как есть, а не как конкретная дата: подписи «завтра» и
 * «понедельник» относятся к дню звонка и остаются понятными в истории.
 */
enum class DeliveryDay {

    /** Завтра. */
    TOMORROW,

    /** Послезавтра. */
    DAY_AFTER_TOMORROW,

    /** Ближайший понедельник (вариант пятницы, субботы и воскресенья). */
    MONDAY,

    /** Любой другой день, который оператор уточнит сам. */
    OTHER_DAY,
    ;

    companion object {

        /**
         * Варианты переключателя для дня недели, когда принят звонок:
         * - пн–чт: завтра, послезавтра, другой день;
         * - пт: завтра, понедельник, другой день;
         * - сб–вс: понедельник, другой день.
         */
        fun options(day: DayOfWeek): List<DeliveryDay> = when (day) {
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            -> listOf(TOMORROW, DAY_AFTER_TOMORROW, OTHER_DAY)

            DayOfWeek.FRIDAY -> listOf(TOMORROW, MONDAY, OTHER_DAY)

            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
            -> listOf(MONDAY, OTHER_DAY)
        }

        /** Варианты для момента звонка в системном часовом поясе. */
        fun optionsAt(
            epochMillis: Long,
            zone: ZoneId = ZoneId.systemDefault(),
        ): List<DeliveryDay> = options(Instant.ofEpochMilli(epochMillis).atZone(zone).dayOfWeek)
    }
}
