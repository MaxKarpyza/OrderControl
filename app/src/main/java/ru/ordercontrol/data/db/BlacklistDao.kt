package ru.ordercontrol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface BlacklistDao {

    @Query("SELECT * FROM blacklist WHERE phoneNormalized = :phone LIMIT 1")
    suspend fun find(phone: String): BlacklistEntry?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(entry: BlacklistEntry)

    @Query("DELETE FROM blacklist WHERE phoneNormalized = :phone")
    suspend fun remove(phone: String)
}
