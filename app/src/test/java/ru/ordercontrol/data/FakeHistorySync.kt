package ru.ordercontrol.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import ru.ordercontrol.data.db.SharedHistoryDao
import ru.ordercontrol.data.db.SharedHistoryEntry

/** Общая история в памяти: как таблица, только без Room. */
class FakeSharedHistoryDao : SharedHistoryDao {

    private val entries = linkedMapOf<String, SharedHistoryEntry>()
    private val state = MutableStateFlow<List<SharedHistoryEntry>>(emptyList())

    fun all(): List<SharedHistoryEntry> = entries.values.toList()

    override fun observeAll(): Flow<List<SharedHistoryEntry>> = state

    override suspend fun insertAll(entries: List<SharedHistoryEntry>) {
        entries.forEach { this.entries[it.remoteId] = it }
        publish()
    }

    override suspend fun clear() {
        entries.clear()
        publish()
    }

    private fun publish() {
        state.value = entries.values.sortedByDescending { it.processedAt }
    }
}

/** Оператор для тестов: имя фиксированное. */
class FakeOperatorProvider(var operator: Operator) : OperatorProvider {
    override suspend fun operator(): Operator = operator
}

/** Подмена скрипта: запоминает, что ей прислали, и отвечает заданным. */
class FakeHistorySyncer(var result: HistorySyncResult) : HistorySyncer {

    /**
     * Подтверждать всё присланное, как настоящий скрипт. Выключается, когда
     * тесту нужен ответ с частичным подтверждением.
     */
    var acceptAll: Boolean = true

    var lastOperator: String? = null
    var lastPush: List<HistoryPushItem> = emptyList()

    override suspend fun sync(operator: String, push: List<HistoryPushItem>): HistorySyncResult {
        lastOperator = operator
        lastPush = push
        val response = result
        return if (acceptAll && response is HistorySyncResult.Success && response.accepted.isEmpty()) {
            response.copy(accepted = push.map { it.id }.toSet())
        } else {
            response
        }
    }
}
