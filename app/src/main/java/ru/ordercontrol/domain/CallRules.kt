package ru.ordercontrol.domain

/** Что сделать с входящим звонком. */
sealed interface RegistrationPlan {

    /** Запись не создаётся и не обновляется. */
    data object Ignore : RegistrationPlan

    /** Обновить уже открытую запись этого номера. */
    data object Merge : RegistrationPlan

    /** Создать новую запись. */
    data object Create : RegistrationPlan
}

/**
 * Правила, по которым входящий звонок превращается в запись.
 *
 * - Отвеченный звонок учитывается всегда, в том числе вне рабочего времени:
 *   оператор уже поговорил с клиентом, и заказ нельзя терять из счётчика.
 * - Пропущенный/сброшенный звонок учитывается только в рабочее время.
 * - Номер в чёрном списке не создаёт записей.
 * - Скрытый номер не склеивается: у него нет идентификатора.
 */
object CallRules {

    fun shouldCreateRecord(answered: Boolean, working: Boolean): Boolean =
        answered || working

    fun plan(
        answered: Boolean,
        working: Boolean,
        blacklisted: Boolean,
        hasOpenRecord: Boolean,
        hiddenNumber: Boolean,
    ): RegistrationPlan {
        if (blacklisted) return RegistrationPlan.Ignore
        if (!shouldCreateRecord(answered = answered, working = working)) {
            return RegistrationPlan.Ignore
        }
        if (!hiddenNumber && hasOpenRecord) return RegistrationPlan.Merge
        return RegistrationPlan.Create
    }
}
