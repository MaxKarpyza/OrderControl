package ru.ordercontrol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface SharedHistoryDao {

    /** Чужие обработанные заказы, новые сверху. */
    @Query("SELECT * FROM shared_history ORDER BY processedAt DESC, remoteId DESC")
    fun observeAll(): Flow<List<SharedHistoryEntry>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entries: List<SharedHistoryEntry>)

    @Query("DELETE FROM shared_history")
    suspend fun clear()

    /**
     * Полная замена: скрипт отдаёт всё окно хранения целиком, поэтому строки,
     * которых в ответе нет, из вкладки уже удалены.
     */
    @Transaction
    suspend fun replaceAll(entries: List<SharedHistoryEntry>) {
        clear()
        insertAll(entries)
    }
}
