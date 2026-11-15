package ru.ordercontrol.domain

/**
 * Строка адреса из вкладки таблицы.
 *
 * @property address адрес как в таблице, без ведущих и замыкающих пробелов.
 * @property bottles количество бутылей из соседней ячейки; `null`, если ячейка
 *   пуста или в ней не число.
 * @property candidates значения колонок, где может лежать район:
 *   индекс колонки → значение.
 */
data class SheetAddressRow(
    val address: String,
    val bottles: Int?,
    val candidates: Map<Int, String>,
)

/** Адреса одного блока колонок вкладки. */
data class SheetAddressBlock(
    val index: Int,
    val rows: List<SheetAddressRow>,
)

/**
 * Строка базы адресов: адрес, количество бутылей и район.
 *
 * @property district район доставки; `null`, если в таблице он не указан.
 */
data class SheetAddressEntry(
    val address: String,
    val bottles: Int?,
    val district: String?,
)

/**
 * Разбор вкладки Google Таблицы: адреса, количества бутылей и колонки,
 * где может лежать район.
 *
 * Адреса лежат в трёх блоках колонок: B, I и Q. Рядом с адресом — количество
 * бутылей и объём, а дальше — комментарий и район. Их порядок менялся: в
 * старых вкладках район стоял перед комментарием, в новых — после. Поэтому
 * район ищется по всей вкладке, а не по фиксированной колонке.
 */
object SheetAddressBase {

    /** Блок колонок: адрес, бутыли и колонки, где может лежать район. */
    data class Block(
        val address: Int,
        val bottles: Int,
        val districtColumns: IntRange,
    )

    val BLOCKS: List<Block> = listOf(
        Block(address = 1, bottles = 2, districtColumns = 4..6),
        Block(address = 8, bottles = 9, districtColumns = 11..14),
        Block(address = 16, bottles = 17, districtColumns = 19..24),
    )

    /** Разбор вкладки: адреса по блокам колонок. */
    fun parse(csv: List<List<String>>): List<SheetAddressBlock> =
        BLOCKS.mapIndexed { index, block ->
            SheetAddressBlock(index = index, rows = block.rows(csv))
        }

    private fun Block.rows(csv: List<List<String>>): List<SheetAddressRow> {
        val result = mutableListOf<SheetAddressRow>()
        for (cells in csv) {
            val address = cells.getOrNull(address)?.trim().orEmpty()
            if (address.isEmpty()) continue
            val candidates = districtColumns
                .mapNotNull { column ->
                    val value = cells.getOrNull(column)?.trim().orEmpty()
                    if (value.isEmpty()) null else column to value
                }
                .toMap()
            result.add(
                SheetAddressRow(
                    address = address,
                    bottles = bottles(cells.getOrNull(bottles)),
                    candidates = candidates,
                ),
            )
        }
        return result
    }

    /** Количество бутылей: ноль в таблице означает «не заказано». */
    private fun bottles(cell: String?): Int? =
        cell?.trim()?.toIntOrNull()?.takeIf { it > 0 }
}
