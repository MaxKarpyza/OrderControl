package ru.ordercontrol.domain

/** Пометка «не клиент»: навсегда или только до следующего звонка. */
enum class BlacklistKind {
    PERMANENT,
    ONCE,
}

object BlacklistPolicy {

    /** Номер в чёрном списке не создаёт новых записей. */
    fun suppressesCall(kind: BlacklistKind?): Boolean = kind != null

    /** Временная пометка снимается после следующего звонка. */
    fun consumesAfterCall(kind: BlacklistKind?): Boolean = kind == BlacklistKind.ONCE
}
