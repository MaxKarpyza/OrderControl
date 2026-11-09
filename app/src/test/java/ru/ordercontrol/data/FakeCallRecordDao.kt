package ru.ordercontrol.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.db.CallRecordDao
import ru.ordercontrol.domain.CloseReason

/**
 * In-memory реализация [CallRecordDao] для JVM-тестов.
 *
 * Повторяет поведение SQL-запросов, которые использует сверка с таблицей.
 */
class FakeCallRecordDao : CallRecordDao {

    private val records = mutableListOf<CallRecord>()
    private val changes = MutableStateFlow(0)
    private var nextId = 1L

    fun add(record: CallRecord): Long {
        val id = if (record.id == 0L) nextId++ else record.id
        records += record.copy(id = id)
        notifyChanged()
        return id
    }

    fun all(): List<CallRecord> = records.toList()

    fun byId(id: Long): CallRecord? = records.firstOrNull { it.id == id }

    override fun observeOpen(): Flow<List<CallRecord>> = changes.map {
        records.filter { !it.isProcessed }.sortedByDescending { it.incomingAt }
    }

    override suspend fun openRecords(): List<CallRecord> =
        records.filter { !it.isProcessed }.sortedByDescending { it.incomingAt }

    override suspend fun latestOpen(): CallRecord? = openRecords().firstOrNull()

    override suspend fun findById(id: Long): CallRecord? = byId(id)

    override fun observeHistory(reasons: List<CloseReason>): Flow<List<CallRecord>> =
        changes.map {
            records
                .filter { it.isProcessed && it.closeReason in reasons }
                .sortedByDescending { it.processedAt ?: 0L }
        }

    override suspend fun findOpenByPhone(phone: String): CallRecord? =
        openRecords().firstOrNull { it.phoneNormalized == phone }

    override suspend fun openWithCallLogId(): List<CallRecord> =
        openRecords().filter { it.callLogId != null }

    override suspend fun previousOpenBefore(from: Long, before: Long): CallRecord? =
        openRecords().firstOrNull { it.incomingAt >= from && it.incomingAt < before }

    override suspend fun unreadCount(): Long =
        records.count { !it.isProcessed && !it.isSilent }.toLong()

    override suspend fun countByCallLogId(callLogId: Long): Long =
        records.count { it.callLogId == callLogId }.toLong()

    override suspend fun previousOrder(phone: String, excludeId: Long): CallRecord? =
        records
            .filter { it.phoneNormalized == phone && it.id != excludeId && it.bottlesText != "" }
            .maxByOrNull { it.incomingAt }

    override suspend fun openWithoutName(): List<CallRecord> =
        openRecords().filter { it.phoneNormalized != null && it.displayName.isNullOrEmpty() }

    override suspend fun insert(record: CallRecord): Long = add(record)

    override suspend fun update(record: CallRecord) {
        val index = records.indexOfFirst { it.id == record.id }
        if (index < 0) return
        records[index] = record
        notifyChanged()
    }

    override suspend fun updateName(id: Long, name: String) {
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        records[index] = records[index].copy(displayName = name)
        notifyChanged()
    }

    override suspend fun closeAll(at: Long, reason: CloseReason) {
        records.replaceAll { record ->
            if (record.isProcessed) {
                record
            } else {
                record.copy(isProcessed = true, processedAt = at, closeReason = reason)
            }
        }
        notifyChanged()
    }

    override suspend fun closeIds(ids: List<Long>, at: Long, reason: CloseReason) {
        val target = ids.toSet()
        records.replaceAll { record ->
            if (record.id in target) {
                record.copy(isProcessed = true, processedAt = at, closeReason = reason)
            } else {
                record
            }
        }
        notifyChanged()
    }

    override suspend fun reopen(id: Long) {
        val index = records.indexOfFirst { it.id == id }
        if (index < 0) return
        records[index] = records[index].copy(
            isProcessed = false,
            processedAt = null,
            closeReason = null,
            sheetMatchKey = null,
        )
        notifyChanged()
    }

    override suspend fun openWithAddress(): List<CallRecord> =
        openRecords().filter {
            it.addressText.isNotBlank() || !it.displayName.isNullOrBlank()
        }

    override suspend fun openWithoutCallLogId(): List<CallRecord> =
        openRecords().filter { it.callLogId == null && it.phoneNormalized != null }

    override suspend fun usedCallLogIds(): List<Long> =
        records.mapNotNull { it.callLogId }

    override suspend fun openWithoutNumber(): List<CallRecord> =
        openRecords().filter { it.phoneNormalized == null }

    override suspend fun countNear(phone: String, from: Long, to: Long): Long =
        records.count {
            it.phoneNormalized == phone && it.incomingAt in from..to
        }.toLong()

    override suspend fun setNumber(
        id: Long,
        phone: String,
        name: String?,
        callLogId: Long?,
    ): Int {
        val index = records.indexOfFirst { it.id == id && it.phoneNormalized == null }
        if (index < 0) return 0
        records[index] = records[index].copy(
            phoneNormalized = phone,
            displayName = name,
            callLogId = callLogId,
        )
        notifyChanged()
        return 1
    }

    override suspend fun usedSheetKeys(): List<String> =
        records.mapNotNull { it.sheetMatchKey }

    override suspend fun setCallLogId(id: Long, callLogId: Long): Int {
        val index = records.indexOfFirst { it.id == id && it.callLogId == null }
        if (index < 0) return 0
        records[index] = records[index].copy(callLogId = callLogId)
        notifyChanged()
        return 1
    }

    override suspend fun closeBySheet(
        id: Long,
        at: Long,
        reason: CloseReason,
        sheetKey: String,
    ): Int {
        val index = records.indexOfFirst { it.id == id && !it.isProcessed }
        if (index < 0) return 0
        records[index] = records[index].copy(
            isProcessed = true,
            processedAt = at,
            closeReason = reason,
            sheetMatchKey = sheetKey,
        )
        notifyChanged()
        return 1
    }

    private fun notifyChanged() {
        changes.value = changes.value + 1
    }
}
