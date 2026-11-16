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
import ru.ordercontrol.AppGraph
import ru.ordercontrol.widget.WidgetRefresh

/**
 * Ловит `PHONE_STATE`: RINGING → OFFHOOK → IDLE.
 *
 * После `IDLE` запись создаётся сразу. Если прошивка не отдала номер,
 * он добирается из журнала — и всё ожидание происходит **до**
 * `pendingResult.finish()`: после завершения работы ресивера система может
 * убить процесс, и отложенная задача уже не выполнится.
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

        val store = CallSessionStore(context)
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
                if (sessions.isEmpty()) {
                    // Повторный IDLE: звонок уже обработан.
                    WidgetRefresh.updateAll(context.applicationContext)
                } else {
                    finalizeAsync(context.applicationContext, sessions)
                }
                if (outgoing) {
                    // Исходящий звонок закончился: он мог закрыть пропущенный
                    // входящий звонок, на который клиент так и не ответил.
                    checkOutgoingAsync(context.applicationContext)
                }
            }
        }
    }

    private fun finalizeAsync(context: Context, sessions: List<CallSession>) {
        val pendingResult = goAsync()

        receiverScope.launch {
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
            } finally {
                pendingResult.finish()
            }

            // Сразу после звонка журнал часто ещё не обновлён. Первое
            // обновление виджетов заодно пробует добрать номер, а повторное
            // через несколько секунд — когда журнал уже догнал звонок.
            WidgetRefresh.updateAll(context)
            backgroundScope.launch {
                delay(CALL_LOG_CATCH_UP_MS)
                WidgetRefresh.updateAll(context)
            }
        }
    }

    /**
     * После исходящего звонка журнал обновляется с задержкой: сразу после
     * `IDLE` исходящего звонка в нём ещё нет. Через несколько секунд
     * сверяем журнал — и снимаем «пропущен» у тех, кого оператор перезвонил.
     */
    private fun checkOutgoingAsync(context: Context) {
        backgroundScope.launch {
            delay(OUTGOING_CATCH_UP_MS)
            WidgetRefresh.updateAll(context.applicationContext)
        }
    }

    private companion object {
        const val TAG = "PhoneStateReceiver"
        val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        const val EXTRA_SUBSCRIPTION = "subscription"

        /** Через сколько ещё раз проверить журнал после звонка. */
        const val CALL_LOG_CATCH_UP_MS = 6_000L

        /** Через сколько после исходящего звонка искать его в журнале. */
        const val OUTGOING_CATCH_UP_MS = 10_000L
    }
}
