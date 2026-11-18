package ru.ordercontrol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Сколько адресов в базе приходится на район. */
data class DistrictCount(val district: String?, val count: Int)

@Dao
interface AddressBaseDao {

    @Query("SELECT COUNT(*) FROM address_base")
    suspend fun count(): Int

    @Query("SELECT * FROM address_base")
    suspend fun all(): List<AddressBaseEntry>

    /** Точное совпадение по нормализованному адресу. */
    @Query("SELECT * FROM address_base WHERE `key` = :key LIMIT 1")
    suspend fun find(key: String): AddressBaseEntry?

    /**
     * Поиск по подстроке нормализованного адреса.
     *
     * Ключи лежат в нижнем регистре, поэтому и запрос приводится к нижнему
     * регистру: `LIKE` в SQLite чувствителен к регистру кириллицы.
     */
    @Query(
        "SELECT * FROM address_base WHERE `key` LIKE '%' || :key || '%' " +
            "ORDER BY address LIMIT :limit",
    )
    suspend fun search(key: String, limit: Int): List<AddressBaseEntry>

    /** Число адресов по районам: порядок районов в выпадающем списке. */
    @Query(
        "SELECT district, COUNT(*) AS count FROM address_base " +
            "WHERE district IS NOT NULL AND TRIM(district) != '' " +
            "GROUP BY district",
    )
    suspend fun districtCounts(): List<DistrictCount>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entries: List<AddressBaseEntry>)

    @Query("DELETE FROM address_base")
    suspend fun clear()
}
