package ru.ordercontrol.ui.base

import android.app.Application
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
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
)

/**
 * База адресов: сборка при первом запуске и обновление дальше.
 *
 * Первый разбор всех вкладок — операция блокирующая: пока базы нет,
 * вводить заказ нечего. Дальше база обновляется сама раз в сутки, а при
 * ошибке — на следующей попытке.
 */
class AddressBaseViewModel(application: Application) : AndroidViewModel(application) {

    var state by mutableStateOf(AddressBaseUiState())
        private set

    private var started = false

    /** Пока база не собрана, ввод заказа недоступен. */
    val blocking: Boolean
        get() = state.empty &&
            (state.phase == AddressBasePhase.Building || state.phase == AddressBasePhase.Failed)

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
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
                refreshInBackground()
            }
        }
    }

    /** Повтор после ошибки: разбор запускается заново. */
    fun retry() {
        viewModelScope.launch { build() }
    }

    /** Обновление по кнопке в настройках — без ожидания суток. */
    fun refreshNow() {
        viewModelScope.launch {
            state = state.copy(phase = AddressBasePhase.Building, done = 0, total = 0, error = null)
            apply(AppGraph.addressBase.refresh())
        }
    }

    /** Первый разбор: со всеми вкладками, с прогрессом. */
    private suspend fun build() {
        state = state.copy(phase = AddressBasePhase.Building, done = 0, total = 0, error = null)
        val result = AppGraph.addressBase.refresh { done, total ->
            state = state.copy(done = done, total = total)
        }
        apply(result)
    }

    /**
     * Фоновое обновление.
     *
     * Не чаще раза в сутки; при ошибке отметка о разборе не ставится, и
     * следующая попытка будет через [CHECK_INTERVAL_MS].
     */
    private suspend fun refreshInBackground() {
        if (!AppGraph.settings.isAddressBaseRefreshDue()) return
        apply(AppGraph.addressBase.refresh())
    }

    private suspend fun apply(result: AddressBaseResult) {
        when (result) {
            is AddressBaseResult.Success -> {
                AppGraph.settings.markAddressBaseRefreshed()
                state = state.copy(
                    phase = AddressBasePhase.Ready,
                    addresses = result.addresses,
                    empty = result.addresses == 0,
                    error = null,
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
    }
}
