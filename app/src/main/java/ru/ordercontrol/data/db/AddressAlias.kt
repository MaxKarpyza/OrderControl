package ru.ordercontrol.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import ru.ordercontrol.domain.AliasKind

/**
 * Решение оператора по паре похожих адресов. Хранится локально,
 * в таблицу ничего не пишется.
 *
 * @property aliasNormalized как адрес записан в приложении, после
 *   `AddressNormalizer.normalize`.
 * @property canonicalNormalized как тот же адрес записан в таблице, после
 *   `AddressNormalizer.normalize`.
 * @property kind подтверждённый синоним или отказ: пары с отказом больше
 *   не предлагаются.
 */
@Entity(tableName = "address_aliases")
data class AddressAlias(
    @PrimaryKey val aliasNormalized: String,
    val canonicalNormalized: String,
    val createdAt: Long,
    @ColumnInfo(defaultValue = "MATCH") val kind: AliasKind = AliasKind.MATCH,
)
