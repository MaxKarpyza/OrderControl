package ru.ordercontrol.domain

/**
 * Нормализация телефонных номеров РФ/СНГ.
 *
 * `+7XXXXXXXXXX`, `8XXXXXXXXXX`, `7XXXXXXXXXX` и `9XXXXXXXXX` приводятся
 * к одному виду `+7XXXXXXXXXX`. Скрытый/неизвестный номер даёт `null`.
 */
object PhoneNormalizer {

    private val HIDDEN_VALUES = setOf("-1", "-2", "-3", "0")

    private val HIDDEN_MARKERS = listOf(
        "private",
        "unknown",
        "restricted",
        "скрыт",
        "аноним",
        "неизвест",
    )

    fun normalize(raw: String?): String? {
        if (raw == null) return null
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed in HIDDEN_VALUES) return null
        val lower = trimmed.lowercase()
        if (HIDDEN_MARKERS.any { lower.contains(it) }) return null

        val digits = digits(raw)
        if (digits.isEmpty()) return null
        if (digits.length < 10) return digits

        return when {
            digits.length == 10 -> "+7$digits"
            digits.length == 11 && (digits[0] == '8' || digits[0] == '7') ->
                "+7${digits.substring(1)}"

            else -> "+$digits"
        }
    }

    fun digits(raw: String?): String = raw.orEmpty().filter { it.isDigit() }
}
