package ru.ordercontrol.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Обработанный заказ другого оператора — копия строки из вкладки `history`
 * Google Таблицы.
 *
 * Своя история живёт в [CallRecord], а здесь только чужие записи: они
 * показываются на вкладке «История» и нужны, чтобы видеть, что адрес уже
 * кто-то закрыл. Строки этого телефона сюда не попадают — по ним и так есть
 * своя история.
 *
 * @property remoteId идентификатор строки, который выдаёт приложение автора
 *   (`опер_NN-КОД:запись:время`). По нему запись обновляется вместо
 *   дублирования.
 * @property author подпись автора: ФИО из настроек или `опер_NN-КОД`.
 * @property phone телефон, с которого приняли заказ: ради него история и
 *   нужна — по нему оператор связывается с клиентом по просьбе водителя.
 */
@Entity(tableName = "shared_history")
data class SharedHistoryEntry(
    @PrimaryKey val remoteId: String,
    val author: String,
    val phone: String,
    val address: String,
    val district: String? = null,
    val bottles: String = "",
    val comment: String = "",
    val processedAt: Long,
    /** Когда эту строку в последний раз забирали из таблицы. */
    val syncedAt: Long,
)
