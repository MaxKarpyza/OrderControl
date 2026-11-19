package ru.ordercontrol.ui.base

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ru.ordercontrol.AppGraph
import ru.ordercontrol.data.AddressBaseResult

/** Состояние базы адресов. */
enum class AddressBasePhase { Idle, Building, Ready, Failed }

/** Что происходит с базой адресов. */
data class AddressBaseUiState(
    val phase: AddressBasePhase = AddressBasePhase.Idle,
    /** Разобрано вкладок из [total]. */
    val done: Int = 0,
    val total: Int = 0,
    /** Сколько адресов в базе. */
    val addresses: Int = 0,
    /** База ещё пуста: без неё подсказок нет. */
    val empty: Boolean = true,
    val error: String? = null,
    /** Сколько раз база успешно обновилась: метка времени живёт по нему. */
    val refreshCount: Int = 0,
)

/**
 * База адресов: сборка при первом запуске и обновление дальше.
 *
 * База нужна только для подсказок при ручном вводе адреса, поэтому её
 * сборка не блокирует работу: заказ по звонку базы не требует. Дальше
 * база обновляется на десятое скачивание таблицы и не реже раза в сутки,
 * а при ошибке — на следующей попытке.
 */
class AddressBaseViewModel(application: Application) : AndroidViewModel(application) {

    var state by mutableStateOf(AddressBaseUiState())
        private set

    private var started = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            try {
                val empty = AppGraph.addressBase.isEmpty()
                state = state.copy(empty = empty, addresses = AppGraph.addressBase.count())
                if (empty) {
                    build()
                } else {
                    refreshInBackground()
                }

                // Приложение может остаться открытым надолго: база обновляется
                // раз в сутки, пока оно открыто.
                while (isActive) {
                    delay(CHECK_INTERVAL_MS)
                    try {
                        refreshInBackground()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Одна неудачная попытка не должна выключать
                        // обновление до перезапуска приложения.
                        fail(e)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Повтор после ошибки: разбор запускается заново. */
    fun retry() {
        viewModelScope.launch {
            try {
                build()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Обновление по кнопке в настройках — без ожидания суток. */
    fun refreshNow() {
        viewModelScope.launch {
            try {
                state = state.copy(phase = AddressBasePhase.Building, done = 0, total = 0, error = null)
                apply(AppGraph.addressBase.refresh())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                fail(e)
            }
        }
    }

    /** Первый разбор: со всеми вкладками, с прогрессом. */
    private suspend fun build() {
        state = state.copy(phase = AddressBasePhase.Building, done = 0, total = 0, error = null)
        // Разбор идёт по сети, а сеть может быть очень слабой: ограничиваем
        // время, чтобы оператор не ждал в пустом экране минутами.
        val result = withTimeoutOrNull(BUILD_TIMEOUT_MS) {
            AppGraph.addressBase.refresh { done, total ->
                state = state.copy(done = done, total = total)
            }
        } ?: AddressBaseResult.Failure("сеть не отвечает")
        apply(result)
    }

    /**
     * Фоновое обновление.
     *
     * Не чаще десяти скачиваний таблицы и не реже раза в сутки; при ошибке
     * отметка о разборе не ставится, и следующая попытка будет через
     * [CHECK_INTERVAL_MS].
     */
    private suspend fun refreshInBackground() {
        if (!AppGraph.settings.isAddressBaseRefreshDue()) return
        apply(AppGraph.addressBase.refresh())
    }

    /**
     * Ошибка вместо результата: сеть отвалилась или таблица не читается.
     *
     * Приложение продолжает работать — база адресов нужна только для
     * подсказок при ручном вводе.
     */
    private fun fail(e: Throwable) {
        Log.w(TAG, "База адресов не обновилась", e)
        state = state.copy(
            phase = AddressBasePhase.Failed,
            error = e.message?.takeIf { it.isNotBlank() } ?: "нет связи",
        )
    }

    private suspend fun apply(result: AddressBaseResult) {
        when (result) {
            is AddressBaseResult.Success -> {
                state = state.copy(
                    phase = AddressBasePhase.Ready,
                    addresses = result.addresses,
                    empty = result.addresses == 0,
                    error = null,
                    refreshCount = state.refreshCount + 1,
                )
                if (result.failedTabs > 0) {
                    Log.w(TAG, "Не разобраны вкладки: ${result.failedTabs}")
                }
            }

            is AddressBaseResult.Failure -> {
                Log.w(TAG, "База адресов не обновлена: ${result.reason}")
                state = state.copy(
                    phase = if (state.empty) {
                        AddressBasePhase.Failed
                    } else {
                        state.phase
                    },
                    error = result.reason,
                )
            }
        }
    }

    private companion object {
        const val TAG = "AddressBase"

        /** Как часто проверять, не пора ли обновить базу. */
        const val CHECK_INTERVAL_MS = 15 * 60 * 1000L

        /** Сколько ждёт первый разбор, прежде чем признать сеть мёртвой. */
        const val BUILD_TIMEOUT_MS = 120_000L
    }
}
