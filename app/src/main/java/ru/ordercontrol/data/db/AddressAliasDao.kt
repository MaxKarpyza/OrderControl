package ru.ordercontrol.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface AddressAliasDao {

    @Query("SELECT * FROM address_aliases")
    suspend fun all(): List<AddressAlias>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun put(alias: AddressAlias)

    @Query("SELECT * FROM address_aliases WHERE aliasNormalized = :alias LIMIT 1")
    suspend fun find(alias: String): AddressAlias?

    @Query("DELETE FROM address_aliases")
    suspend fun clear()
}
