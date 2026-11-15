package ru.ordercontrol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface SheetTabDao {

    /** Имена уже разобранных вкладок. */
    @Query("SELECT name FROM sheet_tabs")
    suspend fun parsedNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markParsed(tabs: List<SheetTabEntity>)
}
