package ru.ordercontrol.domain

/**
 * Словарь районов: значения, которые в таблице действительно означают
 * район.
 *
 * Район в вкладках пишут одинаково, а в колонку района иногда попадает
 * комментарий («Вечер», «до обеда»). Словарь нужен, чтобы отличить одно от
 * другого: он показывает, в какой колонке лежит район.
 */
class AddressBaseVocabulary(known: Set<String> = emptySet()) {

    /** Районы, найденные ранее. */
    private val known = known.toMutableSet()

    /** Пуст ли словарь: без него колонку района не определить. */
    val isEmpty: Boolean
        get() = known.isEmpty()

    /**
     * Опора: по самым свежим вкладкам определяется, где лежит район.
     *
     * В свежих вкладках район пишут одинаково, поэтому колонка с наибольшим
     * числом значений и есть колонка районов. Значения, встречающиеся в
     * нескольких вкладках, попадают в словарь.
     */
    fun bootstrap(tabs: List<List<SheetAddressBlock>>) {
        SheetAddressBase.BLOCKS.forEachIndexed { index, _ ->
            val counts = HashMap<Int, MutableMap<String, Int>>()
            for (tab in tabs) {
                val parsed = tab.getOrNull(index) ?: continue
                for (row in parsed.rows) {
                    for ((column, value) in row.candidates) {
                        if (value.any { it.isDigit() }) continue
                        counts.getOrPut(column) { mutableMapOf() }
                            .merge(value, 1, Int::plus)
                    }
                }
            }
            if (counts.isEmpty()) return@forEachIndexed
            // Больше всего значений — вероятно, это район; при равенстве —
            // колонка, где значения повторяются реже.
            val best = counts.maxWithOrNull(
                compareBy<Map.Entry<Int, MutableMap<String, Int>>> { it.value.values.sum() }
                    .thenBy { -it.value.size },
            ) ?: return@forEachIndexed

            // Значение — район, если оно встретилось во многих вкладках опоры.
            val perTab = mutableMapOf<String, Int>()
            for (tab in tabs) {
                val parsed = tab.getOrNull(index) ?: continue
                val values = parsed.rows.mapNotNull { it.candidates[best.key] }.toSet()
                for (value in values) {
                    perTab[value] = (perTab[value] ?: 0) + 1
                }
            }
            perTab.filterValues { it >= MIN_TABS }.keys.forEach { known += it }
        }
    }

    /**
     * Колонка, где в этом блоке лежит район.
     *
     * Это та колонка, где больше всего уже известных районов: комментарии
     * районами не назвать, а в колонке районов стоят именно они.
     *
     * @return индекс колонки или `null`, если подходящей колонки нет.
     */
    fun districtColumn(block: SheetAddressBlock, columns: IntRange): Int? {
        var best: Int? = null
        var bestCount = 0
        for (column in columns) {
            val count = block.rows.count { row ->
                row.candidates[column]?.let { it in known } == true
            }
            if (count > bestCount) {
                bestCount = count
                best = column
            }
        }
        return best?.takeIf { bestCount > 0 }
    }

    /** Районы, которые стоит запомнить на будущее. */
    fun districts(): Set<String> = known

    companion object {

        /** Сколько вкладок должны встретить значение, чтобы считать его районом. */
        const val MIN_TABS = 3
    }
}
