package ru.ordercontrol.domain

/** Почему запись закрыта. */
enum class CloseReason {
    DONE,
    DELETED_FROM_CALLLOG,
    SHEET,
}
