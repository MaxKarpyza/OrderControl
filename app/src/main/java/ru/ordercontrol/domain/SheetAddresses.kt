package ru.ordercontrol.domain

/**
 * Строка таблицы с адресом и количеством бутылей.
 *
 * @property rowIndex 1-based номер строки CSV — стабильный ключ строки таблицы.
 * @property column 0-based индекс колонки с адресом.
 * @property address адрес как в таблице, без ведущих/замыкающих пробелов.
 * @property bottles количество из соседней справа ячейки; `null`, если пусто,
 *   не число или ноль и меньше — как в [SheetAddressBase], это «не заказано».
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
 * Раскладка берётся из блоков [SheetAddressBase.BLOCKS]: адрес и бутыли —
 * одна таблица, и новый блок в раскладке виден обоим разборам сразу.
 */
object SheetAddresses {

    /** 0-based индексы колонок с адресами: те же блоки, что в базе адресов. */
    val ADDRESS_COLUMNS: List<Int> = SheetAddressBase.BLOCKS.map { it.address }

    fun rows(csv: List<List<String>>): List<SheetOrderRow> {
        val result = mutableListOf<SheetOrderRow>()
        csv.forEachIndexed { rowIdx, cells ->
            for (block in SheetAddressBase.BLOCKS) {
                val address = cells.getOrNull(block.address)?.trim().orEmpty()
                if (address.isEmpty()) continue
                // Ноль и меньше — «не заказано», как в базе адресов.
                val bottles = cells.getOrNull(block.bottles)
                    ?.trim()
                    ?.toIntOrNull()
                    ?.takeIf { it > 0 }
                result.add(
                    SheetOrderRow(
                        rowIndex = rowIdx + 1,
                        address = address,
                        bottles = bottles,
                        column = block.address,
                    ),
                )
            }
        }
        return result
    }
}
