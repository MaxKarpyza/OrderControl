package ru.ordercontrol.ui.history

import ru.ordercontrol.domain.AddressNormalizer

/**
 * Поиск по истории: один запрос проверяется сразу по имени контакта,
 * адресу доставки и номеру телефона.
 *
 * Номер ищется по цифрам, поэтому `+7 (999) 123-45-67` находится и по
 * `999123`, и по `123-45-67`.
 *
 * «ё» пишут по-разному: «пр. Артёма 114» находится по запросу «артема».
 * Имя сравнивается с заменой ё→е, адрес — нормализованно, как в базе адресов.
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

        // Быстрый путь: точное вхождение без учёта регистра.
        if (displayName?.contains(text, ignoreCase = true) == true) return true
        if (address.contains(text, ignoreCase = true)) return true

        if (containsFolded(displayName, text)) return true

        // Пустой запрос из слов-«шум» («кв») не должен подходить всему.
        val normalizedQuery = AddressNormalizer.normalize(text)
        if (normalizedQuery.isNotEmpty() &&
            AddressNormalizer.normalize(address).contains(normalizedQuery)
        ) return true

        val digits = text.filter { it.isDigit() }
        return digits.length >= 3 && phone?.filter { it.isDigit() }?.contains(digits) == true
    }

    /** Имя контакта: сравнение с заменой ё→е в обеих строках. */
    private fun containsFolded(name: String?, query: String): Boolean =
        name?.replace('ё', 'е')?.contains(query.replace('ё', 'е'), ignoreCase = true) == true
}
