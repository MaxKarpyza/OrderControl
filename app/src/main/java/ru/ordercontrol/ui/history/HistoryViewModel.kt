package ru.ordercontrol.ui.history

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.R
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.widget.WidgetRefresh

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    var items by mutableStateOf<List<CallRecord>>(emptyList())
        private set

    var loading by mutableStateOf(true)
        private set

    /** Строка поиска по имени контакта, адресу и номеру телефона. */
    var query by mutableStateOf("")
        private set

    /** Сообщение для снекбара после возврата записи. */
    var message by mutableStateOf<String?>(null)
        private set

    /** Записи, подходящие под текущий запрос. */
    val visibleItems: List<CallRecord>
        get() = items.filter { record ->
            HistorySearch.matches(
                displayName = record.displayName,
                address = record.deliveryAddress,
                phone = record.phoneNormalized,
                query = query,
            )
        }

    init {
        viewModelScope.launch {
            try {
                AppGraph.calls.observeHistory().collectLatest { records ->
                    items = records
                    loading = false
                }
            } catch (_: Exception) {
                loading = false
            }
        }
    }

    fun updateQuery(value: String) {
        query = value
    }

    fun clearQuery() {
        query = ""
    }

    /** Возвращает обработанную запись в список необработанных. */
    fun restore(recordId: Long) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                AppGraph.calls.restoreProcessed(recordId)
                WidgetRefresh.updateAll(context)
                message = context.getString(R.string.history_restored)
            } catch (_: Exception) {
                message = context.getString(R.string.main_error)
            }
        }
    }

    fun consumeMessage() {
        message = null
    }
}
