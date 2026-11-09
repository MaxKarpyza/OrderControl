package ru.ordercontrol.domain

/**
 * Строка таблицы с адресом и количеством бутылей.
 *
 * @property rowIndex 1-based номер строки CSV — стабильный ключ строки таблицы.
 * @property column 0-based индекс колонки с адресом (B, I или Q).
 * @property address адрес как в таблице, без ведущих/замыкающих пробелов.
 * @property bottles количество из соседней справа ячейки; `null`, если пусто или не число.
 */
data class SheetOrderRow(
    val rowIndex: Int,
    val address: String,
    val bottles: Int?,
    val column: Int = 0,
)

/**
 * Достаёт пары «адрес — количество» из CSV-экспорта вкладки.
 *
 * Адреса лежат в трёх блоках колонок ([ADDRESS_COLUMNS]), количество — в ячейке
 * справа от адреса. Цены в колонках 3/10/18 не читаются.
 */
object SheetAddresses {

    /** 0-based индексы колонок с адресами: B, I, Q. */
    val ADDRESS_COLUMNS: List<Int> = listOf(1, 8, 16)

    fun rows(csv: List<List<String>>): List<SheetOrderRow> {
        val result = mutableListOf<SheetOrderRow>()
        csv.forEachIndexed { rowIdx, cells ->
            for (column in ADDRESS_COLUMNS) {
                val address = cells.getOrNull(column)?.trim().orEmpty()
                if (address.isEmpty()) continue
                val bottles = cells.getOrNull(column + 1)
                    ?.trim()
                    ?.toIntOrNull()
                result.add(
                    SheetOrderRow(
                        rowIndex = rowIdx + 1,
                        address = address,
                        bottles = bottles,
                        column = column,
                    ),
                )
            }
        }
        return result
    }
}
