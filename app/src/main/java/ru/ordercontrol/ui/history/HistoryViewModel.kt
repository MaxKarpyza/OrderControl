package ru.ordercontrol.ui.history

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import ru.ordercontrol.AppGraph
import ru.ordercontrol.R
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.db.SharedHistoryEntry
import ru.ordercontrol.widget.WidgetRefresh

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    var loading by mutableStateOf(true)
        private set

    /** Строка поиска по имени контакта, адресу и номеру телефона. */
    var query by mutableStateOf("")
        private set

    /**
     * Показывать только свои записи. Чужие включаются переключателем «Все»:
     * по умолчанию они бы мешали в обычной работе.
     */
    var onlyMine by mutableStateOf(true)
        private set

    /** Идёт обмен общей историей. */
    var syncing by mutableStateOf(false)
        private set

    /** Сообщение для снекбара после возврата записи или обновления. */
    var message by mutableStateOf<String?>(null)
        private set

    private var mine: List<CallRecord> = emptyList()

    private var others: List<SharedHistoryEntry> = emptyList()

    /** Есть ли что-то в общей истории, кроме своих записей. */
    val hasOthers: Boolean
        get() = others.isNotEmpty()

    /** Все строки с учётом переключателя «Мои / Все». */
    val items: List<HistoryItem>
        get() = buildList {
            mine.forEach { add(HistoryItem.Mine(it)) }
            if (!onlyMine) others.forEach { add(HistoryItem.Other(it)) }
        }.sortedByDescending { it.processedAt }

    /** Записи, подходящие под текущий запрос. */
    val visibleItems: List<HistoryItem>
        get() = items.filter { item ->
            HistorySearch.matches(
                displayName = item.searchName,
                address = item.address,
                phone = item.phone,
                query = query,
            )
        }

    init {
        viewModelScope.launch {
            try {
                combine(
                    AppGraph.calls.observeHistory(),
                    AppGraph.sharedHistory.observeAll(),
                ) { mine, others -> mine to others }
                    .collectLatest { (mine, others) ->
                        this@HistoryViewModel.mine = mine
                        this@HistoryViewModel.others = others
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

    fun showOnlyMine(value: Boolean) {
        onlyMine = value
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

    /** Ручное обновление общей истории: кнопка на вкладке. */
    fun refresh() {
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                val outcome = AppGraph.historySync.sync()
                message = when {
                    outcome.outdatedScript -> context.getString(R.string.history_sync_outdated)
                    outcome.error != null ->
                        context.getString(R.string.history_sync_error, outcome.error)
                    else -> context.getString(R.string.history_sync_done, outcome.received)
                }
            } catch (_: Exception) {
                message = context.getString(R.string.main_error)
            } finally {
                syncing = false
            }
        }
    }

    fun consumeMessage() {
        message = null
    }
}
