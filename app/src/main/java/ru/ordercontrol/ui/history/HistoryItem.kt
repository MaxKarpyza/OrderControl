package ru.ordercontrol.ui.history

import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.db.SharedHistoryEntry

/**
 * Строка вкладки «История»: своя обработанная запись или запись другого
 * оператора из общей истории.
 *
 * Свои и чужие показываются в одном списке, потому что сортировать их надо
 * вместе по времени обработки.
 */
sealed interface HistoryItem {

    /** Ключ строки в списке: id своих и чужих могут совпасть. */
    val key: String

    val processedAt: Long

    /** Адрес доставки: по нему идёт поиск. */
    val address: String

    val phone: String?

    /** Имя контакта для поиска; у чужих записей его нет. */
    val searchName: String?

    data class Mine(val record: CallRecord) : HistoryItem {
        override val key: String = "mine:${record.id}"
        override val processedAt: Long = record.processedAt ?: record.incomingAt
        override val address: String = record.deliveryAddress
        override val phone: String? = record.phoneNormalized
        override val searchName: String? = record.displayName
    }

    data class Other(val entry: SharedHistoryEntry) : HistoryItem {
        override val key: String = "other:${entry.remoteId}"
        override val processedAt: Long = entry.processedAt
        override val address: String = entry.address
        override val phone: String? = entry.phone
        override val searchName: String? = null
    }
}
