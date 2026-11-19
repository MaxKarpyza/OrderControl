package ru.ordercontrol.domain

/** Почему запись закрыта. */
enum class CloseReason {
    DONE,
    DELETED_FROM_CALLLOG,
    SHEET;

    companion object {
        /** Записи с этими причинами закрыты оператором, а не системой:
         *  они попадают во вкладку истории и в общую историю операторов. */
        val HISTORY_REASONS = listOf(DONE, SHEET)
    }
}
