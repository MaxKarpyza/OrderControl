package ru.ordercontrol.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Адрес в базе адресов: справочник, собранный из вкладок Google Таблицы.
 *
 * @property key нормализованный адрес — ключ записи.
 * @property address адрес как в таблице (последнее написание).
 * @property district район доставки, если он был указан в таблице.
 * @property bottles количество бутылей из последнего заказа по адресу.
 */
@Entity(tableName = "address_base")
data class AddressBaseEntry(
    @PrimaryKey val key: String,
    val address: String,
    val district: String? = null,
    val bottles: Int? = null,
    val updatedAt: Long = 0L,
)
