package ru.ordercontrol.data

import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.ordercontrol.data.db.BlacklistDao
import ru.ordercontrol.data.db.BlacklistEntry
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.db.CallRecordDao
import ru.ordercontrol.domain.BlacklistKind
import ru.ordercontrol.domain.BlacklistPolicy
import ru.ordercontrol.domain.CallLogReconciler
import ru.ordercontrol.domain.CallRules
import ru.ordercontrol.domain.CloseReason
import ru.ordercontrol.domain.DeliveryDay
import ru.ordercontrol.domain.OpenCallRef
import ru.ordercontrol.domain.PhoneNormalizer
import ru.ordercontrol.domain.RegistrationPlan
import ru.ordercontrol.domain.SkipUpdate

/** Запись для экрана-списка: сама запись и прошлый заказ этого номера. */
data class OpenCallItem(
    val record: CallRecord,
    val previousOrder: CallRecord?,
)

/** Необработанные записи в разбивке по состоянию звонка. */
data class UnreadSummary(
    val total: Int,
    val answered: Int,
    val missed: Int,
)

class CallRepository(
    private val callDao: CallRecordDao,
    private val blacklistDao: BlacklistDao,
    private val settings: ScheduleProvider,
    private val contacts: ContactLookup,
) {

    /** Необработанные записи с прошлым заказом номера, реактивно. */
    fun observeOpenItems(): Flow<List<OpenCallItem>> =
        callDao.observeOpen().map { records ->
            records.map { record ->
                val previous = record.phoneNormalized
                    ?.let { phone -> callDao.previousOrder(phone, record.id) }
                OpenCallItem(record = record, previousOrder = previous)
            }
        }

    suspend fun openItems(): List<OpenCallItem> =
        callDao.openRecords().map { record ->
            val previous = record.phoneNormalized
                ?.let { phone -> callDao.previousOrder(phone, record.id) }
            OpenCallItem(record = record, previousOrder = previous)
        }

    suspend fun latestOpen(): CallRecord? = callDao.latestOpen()

    /** Обработанные оператором записи, новые сверху: вкладка истории. */
    fun observeHistory(): Flow<List<CallRecord>> =
        callDao.observeHistory(reasons = HISTORY_REASONS)

    suspend fun recordById(id: Long): CallRecord? = callDao.findById(id)

    suspend fun previousOpenToday(current: CallRecord): CallRecord? {
        val zone = ZoneId.systemDefault()
        val startOfToday = LocalDate.now(zone)
            .atStartOfDay(zone)
            .toInstant()
            .toEpochMilli()
        if (current.incomingAt < startOfToday) return null
        return callDao.previousOpenBefore(from = startOfToday, before = current.incomingAt)
    }

    suspend fun skipKindFor(record: CallRecord): BlacklistKind? =
        record.phoneNormalized?.let { blacklistDao.find(it)?.kind }

    suspend fun isCallLogIdKnown(callLogId: Long): Boolean =
        callDao.countByCallLogId(callLogId) > 0L

    /** Привязывает запись к системному журналу, когда журнал обновился. */
    suspend fun attachCallLogId(recordId: Long, callLogId: Long) {
        callDao.setCallLogId(recordId, callLogId)
    }

    /** Записи, у которых ещё нет привязки к системному журналу. */
    suspend fun openWithoutCallLogId(): List<CallRecord> = callDao.openWithoutCallLogId()

    /** Идентификаторы журнала, уже занятые записями. */
    suspend fun usedCallLogIds(): Set<Long> = callDao.usedCallLogIds().toSet()

    /** Необработанные записи без номера — номер ещё можно добрать из журнала. */
    suspend fun openWithoutNumber(): List<CallRecord> = callDao.openWithoutNumber()

    /** Дозаполняет номер записи, у которой его не было. */
    suspend fun fillNumber(recordId: Long, phone: String, callLogId: Long?) {
        val name = contacts.findName(phone)
        callDao.setNumber(id = recordId, phone = phone, name = name, callLogId = callLogId)
    }

    /**
     * Есть ли уже запись об этом звонке. Нужно восстановлению из журнала:
     * запись могла быть создана без `callLogId` и уже обработана.
     */
    suspend fun hasRecordNear(phone: String, at: Long, windowMs: Long): Boolean =
        callDao.countNear(phone, at - windowMs, at + windowMs) > 0L

    /**
     * Добавляет вторую карточку для того же звонка: клиент заказывает на два
     * разных адреса. Данные текущей карточки не копируются — оператор вводит
     * второй адрес заново. `callLogId` новой карточке не достаётся, иначе
     * сверка с журналом закрыла бы обе записи сразу.
     *
     * @return id новой карточки.
     */
    suspend fun addAnotherOrder(recordId: Long): Long? {
        val record = callDao.findById(recordId) ?: return null
        return callDao.insert(
            record.copy(
                id = 0,
                callLogId = null,
                bottlesText = "",
                addressText = "",
                isProcessed = false,
                processedAt = null,
                closeReason = null,
                sheetMatchKey = null,
                isNextDay = false,
                deliveryDay = null,
            ),
        )
    }

    /**
     * Регистрирует входящий звонок по правилам MVP.
     * @return id созданной или обновлённой записи, либо null, если запись
     *         создавать не нужно (пропущенный вне рабочего времени или номер
     *         в чёрном списке).
     */
    suspend fun registerIncoming(
        numberRaw: String?,
        answered: Boolean,
        incomingAt: Long,
        callLogId: Long?,
    ): Long? {
        val phone = PhoneNormalizer.normalize(numberRaw)

        // Временная пометка снимается ровно на этом звонке, и он же в заказ
        // не превращается: «не клиент» — это не заказ.
        val blacklistKind = phone?.let { blacklistDao.find(it)?.kind }
        if (BlacklistPolicy.suppressesCall(blacklistKind)) {
            if (BlacklistPolicy.consumesAfterCall(blacklistKind)) {
                blacklistDao.remove(phone!!)
            }
            return null
        }

        val schedule = settings.currentSchedule()
        val working = schedule.isWorkingAt(incomingAt)
        val open = if (phone != null) callDao.findOpenByPhone(phone) else null

        val plan = CallRules.plan(
            answered = answered,
            working = working,
            blacklisted = false,
            hasOpenRecord = open != null,
            hiddenNumber = phone == null,
        )
        if (plan is RegistrationPlan.Ignore) return null

        val contactName = phone?.let { contacts.findName(it) }

        if (open != null && plan is RegistrationPlan.Merge) {
            callDao.update(
                open.copy(
                    displayName = open.displayName ?: contactName,
                    callLogId = callLogId ?: open.callLogId,
                    incomingAt = incomingAt,
                    // Повторный звонок мог быть принят, даже если первый
                    // пропущен: запись считается принятой, если принят хотя бы
                    // один из звонков этого номера.
                    isAnswered = answered || open.isAnswered == true,
                ),
            )
            return open.id
        }

        return callDao.insert(
            CallRecord(
                phoneNormalized = phone,
                displayName = contactName,
                callLogId = callLogId,
                incomingAt = incomingAt,
                isAnswered = answered,
            ),
        )
    }

    /**
     * Сохраняет данные мини-формы. Имя используется только для неизвестного
     * номера: при заполненном имени создаётся контакт, если его ещё нет.
     *
     * @return `true`, если имя было введено, но контакт создать не удалось.
     */
    suspend fun saveOrder(
        recordId: Long,
        bottles: String,
        address: String,
        nameInput: String?,
        skip: SkipUpdate,
        deliveryDay: DeliveryDay? = null,
    ): Boolean {
        val record = callDao.findById(recordId) ?: return false
        val phone = record.phoneNormalized
        var displayName = record.displayName
        var contactFailed = false

        val name = nameInput?.trim().orEmpty()
        if (phone != null && name.isNotEmpty()) {
            val created = contacts.createContactIfAbsent(name = name, number = phone)
            val found = contacts.findName(phone)
            if (!created && found == null) contactFailed = true
            displayName = found ?: name
        }

        val skipped = when (skip) {
            SkipUpdate.Unchanged -> record.isSkipped
            SkipUpdate.Clear -> false
            is SkipUpdate.Set -> true
        }

        callDao.update(
            record.copy(
                bottlesText = bottles.trim(),
                addressText = address.trim(),
                displayName = displayName,
                isSkipped = skipped,
                isNextDay = deliveryDay != null,
                deliveryDay = deliveryDay,
            ),
        )

        if (phone != null) {
            when (skip) {
                SkipUpdate.Unchanged -> Unit
                SkipUpdate.Clear -> blacklistDao.remove(phone)
                is SkipUpdate.Set -> blacklistDao.put(
                    BlacklistEntry(
                        phoneNormalized = phone,
                        kind = skip.kind,
                        createdAt = System.currentTimeMillis(),
                    ),
                )
            }
        }

        return contactFailed
    }

    suspend fun markAllProcessed() {
        callDao.closeAll(
            at = System.currentTimeMillis(),
            reason = CloseReason.DONE,
        )
    }

    /** Отмечает обработанной одну запись, не трогая остальные. */
    suspend fun markProcessed(recordId: Long) {
        callDao.closeIds(
            ids = listOf(recordId),
            at = System.currentTimeMillis(),
            reason = CloseReason.DONE,
        )
    }

    /** Возвращает обработанную запись в список необработанных. */
    suspend fun restoreProcessed(recordId: Long) {
        callDao.reopen(recordId)
    }

    suspend fun unreadCount(): Int = callDao.unreadCount().toInt()

    /**
     * Сколько необработанных записей ждёт оператора и сколько из них
     * относится к принятым и пропущенным звонкам.
     */
    suspend fun unreadSummary(): UnreadSummary {
        val records = callDao.openRecords().filter { !it.isSilent }
        return UnreadSummary(
            total = records.size,
            answered = records.count { it.isAnswered == true },
            missed = records.count { it.isAnswered == false },
        )
    }

    suspend fun openCallRefs(): List<OpenCallRef> = callDao.openWithCallLogId()
        .mapNotNull { record ->
            record.callLogId?.let { OpenCallRef(recordId = record.id, callLogId = it) }
        }

    /** Закрывает записи, чьих звонков больше нет в системном журнале. */
    suspend fun reconcileWithCallLog(existingCallLogIds: Set<Long>) {
        val toClose = CallLogReconciler.idsToClose(
            open = openCallRefs(),
            existingCallLogIds = existingCallLogIds,
        )
        if (toClose.isNotEmpty()) {
            callDao.closeIds(
                ids = toClose,
                at = System.currentTimeMillis(),
                reason = CloseReason.DELETED_FROM_CALLLOG,
            )
        }
    }

    /**
     * Подтягивает имена из телефонной книги для необработанных записей.
     * Запись при этом остаётся необработанной.
     */
    suspend fun refreshNamesFromContacts() {
        for (record in callDao.openWithoutName()) {
            val phone = record.phoneNormalized ?: continue
            val name = contacts.findName(phone) ?: continue
            callDao.updateName(record.id, name)
        }
    }

    private companion object {
        /** Записи, закрытые оператором, а не системой. */
        val HISTORY_REASONS = listOf(CloseReason.DONE, CloseReason.SHEET)
    }
}
