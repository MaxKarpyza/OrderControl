package ru.ordercontrol.domain

/** Ссылка на необработанную запись, у которой есть системный `callLogId`. */
data class OpenCallRef(
    val recordId: Long,
    val callLogId: Long,
)

/**
 * Сверка с системным журналом: если звонка в журнале больше нет
 * (оператор удалил его из истории), запись закрывается.
 */
object CallLogReconciler {

    fun idsToClose(
        open: List<OpenCallRef>,
        existingCallLogIds: Set<Long>,
    ): List<Long> = open
        .filter { it.callLogId !in existingCallLogIds }
        .map { it.recordId }
}
