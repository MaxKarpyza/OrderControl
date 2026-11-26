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

/** Какая часть истории показана: всё, только свои или только чужие записи. */
enum class HistoryFilter {
    ALL,
    MINE,
    OTHERS,
}

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    var loading by mutableStateOf(true)
        private set

    /** Строка поиска по имени контакта, адресу и номеру телефона. */
    var query by mutableStateOf("")
        private set

    /** Какая часть истории показана; по умолчанию — всё. */
    var filter by mutableStateOf(HistoryFilter.ALL)
        private set

    /** Идёт обмен общей историей. */
    var syncing by mutableStateOf(false)
        private set

    /** Идёт выгрузка своих адресов в общую историю. */
    var exporting by mutableStateOf(false)
        private set

    /** Сообщение для снекбара после возврата записи или обновления. */
    var message by mutableStateOf<String?>(null)
        private set

    private var mine: List<CallRecord> = emptyList()

    private var others: List<SharedHistoryEntry> = emptyList()

    /** Есть ли что-то в общей истории, кроме своих записей. */
    val hasOthers: Boolean
        get() = others.isNotEmpty()

    /** Все строки с учётом фильтра «Все / Мои / Не мои». */
    var items by mutableStateOf<List<HistoryItem>>(emptyList())
        private set

    /** Записи, подходящие под текущий запрос. */
    var visibleItems by mutableStateOf<List<HistoryItem>>(emptyList())
        private set

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
                        rebuild()
                    }
            } catch (_: Exception) {
                loading = false
            }
        }
    }

    /** Пересобирает список и фильтр: вызывается при смене данных, запроса или переключателя. */
    private fun rebuild() {
        items = buildList {
            if (filter != HistoryFilter.OTHERS) {
                mine.forEach { add(HistoryItem.Mine(it)) }
            }
            if (filter != HistoryFilter.MINE) {
                others.forEach { add(HistoryItem.Other(it)) }
            }
        }.sortedByDescending { it.processedAt }
        visibleItems = items.filter { item ->
            HistorySearch.matches(
                displayName = item.searchName,
                address = item.address,
                phone = item.phone,
                query = query,
            )
        }
    }

    fun updateQuery(value: String) {
        query = value
        rebuild()
    }

    fun clearQuery() {
        query = ""
        rebuild()
    }

    fun applyFilter(value: HistoryFilter) {
        filter = value
        rebuild()
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

    /**
     * Выгрузка своих адресов в общую историю: кнопка на вкладке.
     *
     * Отправляются только записи, которых там ещё нет; чужие не забираются —
     * для этого есть кнопка обновления.
     */
    fun export() {
        if (exporting) return
        exporting = true
        viewModelScope.launch {
            val context = getApplication<Application>()
            try {
                val outcome = AppGraph.historySync.export()
                message = when {
                    outcome.outdatedScript -> context.getString(R.string.history_sync_outdated)
                    outcome.error != null ->
                        context.getString(R.string.history_export_error, outcome.error)
                    outcome.pushed == 0 -> context.getString(R.string.history_export_empty)
                    else -> context.getString(R.string.history_export_done, outcome.pushed)
                }
            } catch (_: Exception) {
                message = context.getString(R.string.main_error)
            } finally {
                exporting = false
            }
        }
    }

    fun consumeMessage() {
        message = null
    }
}
