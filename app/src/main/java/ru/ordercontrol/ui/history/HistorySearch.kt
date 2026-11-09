package ru.ordercontrol.ui.history

/**
 * Поиск по истории: один запрос проверяется сразу по имени контакта,
 * адресу доставки и номеру телефона.
 *
 * Номер ищется по цифрам, поэтому `+7 (999) 123-45-67` находится и по
 * `999123`, и по `123-45-67`.
 */
object HistorySearch {

    fun matches(
        displayName: String?,
        address: String,
        phone: String?,
        query: String,
    ): Boolean {
        val text = query.trim()
        if (text.isEmpty()) return true

        if (displayName?.contains(text, ignoreCase = true) == true) return true
        if (address.contains(text, ignoreCase = true)) return true

        val digits = text.filter { it.isDigit() }
        return digits.length >= 3 && phone?.filter { it.isDigit() }?.contains(digits) == true
    }
}
