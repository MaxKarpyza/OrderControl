package ru.ordercontrol.domain

/** Исходящий звонок: номер, время начала и длительность разговора в секундах. */
data class CallbackCall(
    val phone: String,
    val date: Long,
    val durationSec: Int,
)

/**
 * Правила обратной связи: снимает ли перезвон отметку «пропущен».
 *
 * Входящий звонок, на который не ответили, остаётся пропущенным навсегда,
 * пока оператор не перезвонит клиенту. Отметка снимается, только если после
 * пропущенного звонка состоялся разговор: звонок без ответа ничего не
 * меняет — клиент всё ещё не обслужен.
 */
object CallbackRules {

    /**
     * @param phone нормализованный номер клиента.
     * @param missedAt время пропущенного звонка.
     * @param callbacks исходящие звонки из журнала.
     */
    fun answeredByCallback(
        phone: String,
        missedAt: Long,
        callbacks: List<CallbackCall>,
    ): Boolean = callbacks.any { call ->
        call.phone == phone &&
            call.date >= missedAt &&
            call.durationSec > 0
    }
}
