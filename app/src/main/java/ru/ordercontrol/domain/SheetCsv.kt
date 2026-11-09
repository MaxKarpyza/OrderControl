package ru.ordercontrol.domain

/**
 * Разбор CSV-экспорта вкладки Google Таблицы (RFC 4180).
 *
 * Разделитель — запятая, поле в двойных кавычках может содержать запятые,
 * переводы строк и экранированные кавычки (`""`). Поддерживаются `\r\n` и `\n`.
 * Строки не выравниваются по количеству колонок: каждая остаётся такой, какая
 * есть в источнике. Незакрытая кавычка не считается ошибкой — возвращается то,
 * что успели разобрать.
 */
object SheetCsv {

    private const val QUOTE = '"'
    private const val COMMA = ','
    private const val CR = '\r'
    private const val LF = '\n'

    fun parse(text: String): List<List<String>> {
        if (text.isEmpty()) return emptyList()

        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val field = StringBuilder()
        var inQuotes = false
        var fieldStarted = false
        var i = 0

        while (i < text.length) {
            val ch = text[i]

            if (inQuotes) {
                when {
                    ch == QUOTE && i + 1 < text.length && text[i + 1] == QUOTE -> {
                        field.append(QUOTE)
                        i++
                    }

                    ch == QUOTE -> inQuotes = false

                    else -> field.append(ch)
                }
            } else {
                when (ch) {
                    QUOTE -> if (!fieldStarted) {
                        // Кавычка в середине неэкранированного поля считается обычным символом.
                        inQuotes = true
                        fieldStarted = true
                    } else {
                        field.append(ch)
                    }

                    COMMA -> {
                        row.add(field.toString())
                        field.setLength(0)
                        fieldStarted = false
                    }

                    CR, LF -> {
                        if (ch == CR && i + 1 < text.length && text[i + 1] == LF) {
                            i++
                        }
                        row.add(field.toString())
                        rows.add(row)
                        field.setLength(0)
                        fieldStarted = false
                        row = mutableListOf()
                    }

                    else -> {
                        field.append(ch)
                        fieldStarted = true
                    }
                }
            }
            i++
        }

        // Последняя строка без завершающего перевода строки.
        if (field.isNotEmpty() || row.isNotEmpty() || fieldStarted) {
            row.add(field.toString())
            rows.add(row)
        }

        return rows
    }
}
