package ru.ordercontrol.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Вкладка Google Таблицы, уже разобранная в базу адресов.
 *
 * Имена вкладок хранятся, чтобы обновление разбирало только новые вкладки,
 * а не всю историю заново.
 */
@Entity(tableName = "sheet_tabs")
data class SheetTabEntity(
    @PrimaryKey val name: String,
    val parsedAt: Long = 0L,
)
