package ru.ordercontrol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.ordercontrol.domain.CloseReason

@Dao
interface CallRecordDao {

    @Query("SELECT * FROM call_records WHERE isProcessed = 0 ORDER BY incomingAt ASC, id ASC")
    fun observeOpen(): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE isProcessed = 0 ORDER BY incomingAt DESC, id DESC")
    suspend fun openRecords(): List<CallRecord>

    @Query("SELECT * FROM call_records WHERE isProcessed = 0 ORDER BY incomingAt DESC, id DESC LIMIT 1")
    suspend fun latestOpen(): CallRecord?

    @Query("SELECT * FROM call_records WHERE id = :id")
    suspend fun findById(id: Long): CallRecord?

    /**
     * Обработанные записи для вкладки истории: сначала те, что отметили
     * последними. Записи, закрытые системой (звонок исчез из журнала),
     * в историю не попадают — они не обработаны оператором.
     */
    @Query(
        "SELECT * FROM call_records WHERE isProcessed = 1 " +
            "AND closeReason IN (:reasons) ORDER BY processedAt DESC, id DESC",
    )
    fun observeHistory(reasons: List<CloseReason>): Flow<List<CallRecord>>

    @Query("SELECT * FROM call_records WHERE phoneNormalized = :phone AND isProcessed = 0 ORDER BY incomingAt DESC, id DESC LIMIT 1")
    suspend fun findOpenByPhone(phone: String): CallRecord?

    @Query("SELECT * FROM call_records WHERE isProcessed = 0 AND callLogId IS NOT NULL")
    suspend fun openWithCallLogId(): List<CallRecord>

    @Query(
        "SELECT * FROM call_records WHERE isProcessed = 0 AND incomingAt >= :from " +
            "AND incomingAt < :before ORDER BY incomingAt DESC, id DESC LIMIT 1",
    )
    suspend fun previousOpenBefore(from: Long, before: Long): CallRecord?

    @Query("SELECT COUNT(*) FROM call_records WHERE isProcessed = 0 AND isSilent = 0")
    suspend fun unreadCount(): Long

    @Query("SELECT COUNT(*) FROM call_records WHERE callLogId = :callLogId")
    suspend fun countByCallLogId(callLogId: Long): Long

    @Query(
        "SELECT * FROM call_records WHERE phoneNormalized = :phone AND id != :excludeId " +
            "AND bottlesText != '' ORDER BY incomingAt DESC, id DESC LIMIT 1",
    )
    suspend fun previousOrder(phone: String, excludeId: Long): CallRecord?

    @Query(
        "SELECT * FROM call_records WHERE isProcessed = 0 AND phoneNormalized IS NOT NULL " +
            "AND (displayName IS NULL OR displayName = '')",
    )
    suspend fun openWithoutName(): List<CallRecord>

    @Insert
    suspend fun insert(record: CallRecord): Long

    @Update
    suspend fun update(record: CallRecord)

    @Query("UPDATE call_records SET displayName = :name WHERE id = :id")
    suspend fun updateName(id: Long, name: String)

    @Query("UPDATE call_records SET isProcessed = 1, processedAt = :at, closeReason = :reason WHERE isProcessed = 0")
    suspend fun closeAll(at: Long, reason: CloseReason)

    @Query("UPDATE call_records SET isProcessed = 1, processedAt = :at, closeReason = :reason WHERE id IN (:ids)")
    suspend fun closeIds(ids: List<Long>, at: Long, reason: CloseReason)

    /** Возвращает обработанную запись в необработанные, снимая отметки закрытия. */
    @Query(
        "UPDATE call_records SET isProcessed = 0, processedAt = NULL, closeReason = NULL, " +
            "sheetMatchKey = NULL WHERE id = :id",
    )
    suspend fun reopen(id: Long)

    /**
     * Необработанные записи с адресом — кандидаты для сверки с таблицей.
     *
     * Адресом считается и имя контакта: если оператор не ввёл свой адрес,
     * доставка идёт по контакту.
     */
    @Query(
        "SELECT * FROM call_records WHERE isProcessed = 0 AND (" +
            "addressText != '' OR trim(displayName) != '') " +
            "ORDER BY incomingAt DESC",
    )
    suspend fun openWithAddress(): List<CallRecord>

    /**
     * Ключи строк таблицы, уже израсходованные прошлыми проверками.
     */
    @Query("SELECT sheetMatchKey FROM call_records WHERE sheetMatchKey IS NOT NULL")
    suspend fun usedSheetKeys(): List<String>

    /**
     * Привязывает запись к системному журналу, когда журнал догнал звонок.
     * Уже привязанную запись не трогаем.
     */
    @Query("UPDATE call_records SET callLogId = :callLogId WHERE id = :id AND callLogId IS NULL")
    suspend fun setCallLogId(id: Long, callLogId: Long): Int

    /** Необработанные записи известных номеров без привязки к журналу. */
    @Query(
        "SELECT * FROM call_records WHERE isProcessed = 0 AND callLogId IS NULL " +
            "AND phoneNormalized IS NOT NULL",
    )
    suspend fun openWithoutCallLogId(): List<CallRecord>

    /** Все идентификаторы журнала, уже привязанные к записям. */
    @Query("SELECT callLogId FROM call_records WHERE callLogId IS NOT NULL")
    suspend fun usedCallLogIds(): List<Long>

    /** Необработанные записи без номера: прошивка не отдала его вовремя. */
    @Query("SELECT * FROM call_records WHERE isProcessed = 0 AND phoneNormalized IS NULL")
    suspend fun openWithoutNumber(): List<CallRecord>

    /** Есть ли уже запись об этом звонке — неважно, обработанная или нет. */
    @Query(
        "SELECT COUNT(*) FROM call_records WHERE phoneNormalized = :phone " +
            "AND incomingAt BETWEEN :from AND :to",
    )
    suspend fun countNear(phone: String, from: Long, to: Long): Long

    /**
     * Дозаполняет номер, когда он нашёлся в журнале позже.
     * Уже известный номер не перезаписываем.
     */
    @Query(
        "UPDATE call_records SET phoneNormalized = :phone, displayName = :name, " +
            "callLogId = :callLogId WHERE id = :id AND phoneNormalized IS NULL",
    )
    suspend fun setNumber(id: Long, phone: String, name: String?, callLogId: Long?): Int

    /**
     * Закрывает запись строкой таблицы.
     *
     * @return число обновлённых строк: 0, если запись уже закрыта параллельно.
     */
    @Query(
        "UPDATE call_records SET isProcessed = 1, processedAt = :at, closeReason = :reason, " +
            "sheetMatchKey = :sheetKey WHERE id = :id AND isProcessed = 0",
    )
    suspend fun closeBySheet(id: Long, at: Long, reason: CloseReason, sheetKey: String): Int

    /**
     * Закрывает запись после того, как приложение само записало её в таблицу:
     * отметка о записи ставится вместе с закрытием, одним запросом.
     *
     * @return число обновлённых строк: 0, если запись уже закрыта параллельно.
     */
    @Query(
        "UPDATE call_records SET isProcessed = 1, processedAt = :at, closeReason = :reason, " +
            "sheetMatchKey = :sheetKey, sheetWrittenAt = :at " +
            "WHERE id = :id AND isProcessed = 0",
    )
    suspend fun closeWrittenBySheet(id: Long, at: Long, reason: CloseReason, sheetKey: String): Int

    /** Записи, которые приложение писало в таблицу начиная с [from]. */
    @Query(
        "SELECT * FROM call_records WHERE sheetWrittenAt IS NOT NULL AND sheetWrittenAt >= :from " +
            "ORDER BY incomingAt DESC",
    )
    suspend fun writtenSince(from: Long): List<CallRecord>

    /** Снимает отметку «писала в таблицу»: адрес снова считается новым. */
    @Query("UPDATE call_records SET sheetWrittenAt = NULL WHERE id IN (:ids)")
    suspend fun clearSheetWritten(ids: List<Long>)
}
