package ru.ordercontrol.phone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.TelephonyManager
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import ru.ordercontrol.AppGraph
import ru.ordercontrol.widget.WidgetRefresh

/**
 * Ловит `PHONE_STATE`: RINGING → OFFHOOK → IDLE.
 *
 * После `IDLE` запись создаётся сразу. Если прошивка не отдала номер,
 * он добирается из журнала — и всё ожидание происходит **до**
 * `pendingResult.finish()`: после завершения работы ресивера система может
 * убить процесс, и отложенная задача уже не выполнится. Обновления виджетов
 * и их отложенные повторы — тоже: виджеты сами не обновляются
 * (`updatePeriodMillis=0`).
 *
 * `goAsync()` можно вызвать **только один раз** на срабатывание:
 * реализация отдаёт и обнуляет внутренний результат, повторный вызов
 * возвращает `null`. Поэтому все отложенные работы одного `IDLE`
 * запускаются под общий `pendingResult` в [holdAsync].
 *
 * Исходящие звонки не создают записей: журнал отдаёт только входящие,
 * пропущенные и сброшенные. Но по исходящим видно, что оператор перезвонил
 * клиенту и разговор состоялся — такая запись перестаёт быть пропущенной.
 */
class PhoneStateReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != TelephonyManager.ACTION_PHONE_STATE_CHANGED) return
        val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE) ?: return
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION, -1)
        val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)

        // Номер в лог не пишем: в нём персональные данные. Только факт наличия —
        // по этому видно, отдаёт ли прошивка номер в PHONE_STATE.
        Log.i(
            TAG,
            "PHONE_STATE $state, sim=$subscriptionId, номер " +
                if (number.isNullOrBlank()) "не передан" else "передан",
        )

        val store = CallSessionStore(PrefsSessionStorage(context))
        when (state) {
            TelephonyManager.EXTRA_STATE_RINGING -> store.start(
                subscriptionId = subscriptionId,
                number = number,
                at = System.currentTimeMillis(),
            )

            TelephonyManager.EXTRA_STATE_OFFHOOK -> {
                // У входящего звонка перед OFFHOOK есть RINGING. Если сеанса
                // нет — звонок исходящий: оператор звонит клиенту.
                val incoming = store.markAnswered(subscriptionId = subscriptionId)
                if (!incoming) store.markOutgoing()
            }

            TelephonyManager.EXTRA_STATE_IDLE -> {
                val outgoing = store.clearOutgoing()
                val sessions = store.finish(subscriptionId)
                holdAsync(context.applicationContext) {
                    if (sessions.isEmpty()) {
                        // Повторный IDLE: звонок уже обработан.
                        WidgetRefresh.updateAll(context)
                    } else {
                        launch { finalizeSessions(context, sessions) }
                    }
                    if (outgoing) {
                        // Исходящий звонок закончился: он мог закрыть пропущенный
                        // входящий звонок, на который клиент так и не ответил.
                        // Параллельно finalize: последовательно 6 с + 9 с
                        // не влезут в десятисекундный лимит ресивера.
                        launch {
                            delay(OUTGOING_CATCH_UP_MS)
                            WidgetRefresh.updateAll(context)
                        }
                    }
                }
            }
        }
    }

    /**
     * Сверяет закончившиеся звонки с журналом и базой, затем дважды
     * обновляет виджеты: сразу и с повтором через несколько секунд —
     * к повтору журнал уже догнал звонок, и добранный по нему номер
     * попадает в запись.
     */
    private suspend fun finalizeSessions(context: Context, sessions: List<CallSession>) {
        try {
            val finalizer = CallFinalizer(
                calls = AppGraph.calls,
                callLog = CallLogReader(context),
            )
            sessions.forEach { finalizer.resolve(it) }
        } catch (e: Exception) {
            // Сбой журнала или базы не должен ронять приложение,
            // но и молча терять звонок нельзя: пишем причину в лог.
            Log.e(TAG, "Не удалось создать запись о звонке", e)
        }

        WidgetRefresh.updateAll(context)
        delay(CALL_LOG_CATCH_UP_MS)
        WidgetRefresh.updateAll(context)
    }

    /**
     * Держит broadcast (`goAsync`), пока не завершится [block] и все
     * запущенные им корутины: после `finish()` процесс могут убить, а
     * отложенные обновления виджетов уже не выполнятся — виджеты сами
     * не обновляются (`updatePeriodMillis=0`). Дочерние корутины не
     * зависят друг от друга: сбой одной не отменяет остальные.
     */
    private fun holdAsync(context: Context, block: suspend CoroutineScope.() -> Unit) {
        val pendingResult = goAsync()

        receiverScope.launch {
            try {
                supervisorScope(block)
            } finally {
                pendingResult.finish()
            }
        }
    }

    private companion object {
        const val TAG = "PhoneStateReceiver"
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val EXTRA_SUBSCRIPTION = "subscription"

        /** Через сколько ещё раз проверить журнал после звонка. */
        const val CALL_LOG_CATCH_UP_MS = 6_000L

        /**
         * Через сколько после исходящего звонка искать его в журнале.
         * Лимит ресивера — 10 секунд, поэтому чуть меньше и всегда
         * параллельно [finalizeSessions], а не после неё.
         */
        const val OUTGOING_CATCH_UP_MS = 9_000L
    }
}
