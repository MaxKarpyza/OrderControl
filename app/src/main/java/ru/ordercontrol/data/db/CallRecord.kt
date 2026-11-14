package ru.ordercontrol.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import ru.ordercontrol.domain.CloseReason
import ru.ordercontrol.domain.DeliveryDay

/**
 * Одна запись = один входящий звонок.
 *
 * Пока запись не обработана, повторный звонок того же номера обновляет
 * эту же строку, поэтому `bottlesText` и `addressText` переживают повтор.
 *
 * @property sheetMatchKey ключ строки Google Таблицы (`вкладка:номер строки`),
 *   если запись закрыта проверкой таблицы; `null` — во всех остальных случаях.
 */
@Entity(
    tableName = "call_records",
    indices = [
        Index("phoneNormalized"),
        Index("isProcessed"),
        Index("callLogId"),
        Index("sheetMatchKey"),
    ],
)
data class CallRecord(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val phoneNormalized: String?,
    val displayName: String? = null,
    val callLogId: Long? = null,
    val incomingAt: Long,
    val bottlesText: String = "",
    val addressText: String = "",
    val isProcessed: Boolean = false,
    val processedAt: Long? = null,
    val isSkipped: Boolean = false,
    val isSilent: Boolean = false,
    val closeReason: CloseReason? = null,
    val sheetMatchKey: String? = null,
    /** Заказ на следующий день: доставка не сегодня. Оставлено для старых баз. */
    val isNextDay: Boolean = false,
    /** Выбранный день доставки; `null` — оператор день не отмечал. */
    val deliveryDay: DeliveryDay? = null,
    /**
     * Звонок был принят оператором (`true`) или пропущен (`false`).
     * `null` — у старых записей состояние неизвестно.
     */
    val isAnswered: Boolean? = null,
    /** Комментарий оператора: пишется в таблицу рядом с количеством бутылей. */
    @ColumnInfo(defaultValue = "")
    val comment: String = "",
    /** Дописать телефон в комментарий при записи в таблицу. */
    @ColumnInfo(defaultValue = "0")
    val withPhoneInComment: Boolean = false,
    /**
     * Когда приложение само записало заказ в Google Таблицу и проверка это
     * подтвердила. По этой отметке ищутся повторы, а не по тексту листа.
     */
    val sheetWrittenAt: Long? = null,
) {
    /**
     * Адрес доставки. Контакт и есть адрес: если оператор не ввёл в поле
     * адреса ничего своего, адресом считается имя контакта.
     */
    val deliveryAddress: String
        get() = addressText.ifBlank { displayName?.takeIf { it.isNotBlank() }.orEmpty() }
}
