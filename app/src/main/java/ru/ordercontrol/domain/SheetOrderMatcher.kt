package ru.ordercontrol.domain

/**
 * Минимальные данные записи `CallRecord`, нужные для сопоставления с таблицей.
 */
data class MatchRecord(
    val id: Long,
    val address: String,
    val bottlesText: String,
    val incomingAt: Long,
)

/**
 * Надёжное совпадение записи со строкой таблицы.
 *
 * @property bottlesDiffer количество в таблице известно и не совпало с количеством из формы.
 */
data class ExactMatch(
    val recordId: Long,
    val row: SheetOrderRow,
    val bottlesDiffer: Boolean,
)

/**
 * Похожее, но не точное совпадение: кандидат для подтверждения оператором.
 *
 * @property similarity сходство компактных адресов, 0..1; для пары
 *   «один адрес — префикс другого» это меньше [SheetOrderMatcher.SIMILARITY_THRESHOLD].
 * @property partial `true`, если пара распознана как неполное совпадение
 *   (один адрес — префикс другого, у короткого есть номер дома).
 */
data class AmbiguousMatch(
    val recordId: Long,
    val recordAddress: String,
    val recordBottles: Int?,
    val row: SheetOrderRow,
    val similarity: Double,
    val partial: Boolean = false,
)

/** Итог сопоставления: что закрыто наверняка и что требует решения. */
data class SheetMatchResult(
    val exact: List<ExactMatch>,
    val ambiguous: List<AmbiguousMatch>,
)

/**
 * Сопоставление необработанных записей звонков со строками Google Таблицы.
 *
 * Записи обрабатываются от свежих к старым, строки таблицы — по порядку номеров.
 * Каждая запись и каждая строка участвуют максимум в одной паре. Сначала
 * точные совпадения нормализованных адресов (с учётом синонимов оператора)
 * ищутся для **всех** записей, и только потом оставшиеся записи получают
 * лучшего кандидата по сходству не ниже [SIMILARITY_THRESHOLD] или по
 * неполному совпадению ([isPartialCandidate]). Поэтому похожий адрес одной
 * записи не отнимает строку таблицы у точного совпадения другой записи.
 * Кандидат ничего не закрывает. Функция чистая: входы не мутируются.
 */
object SheetOrderMatcher {

    const val SIMILARITY_THRESHOLD: Double = 0.80

    /** Сколько слов должно быть в коротком адресе, чтобы предлагать пару. */
    private const val MIN_PARTIAL_TOKENS = 2

    /** Минимальная длина недописанного слова, которое считается тем же словом. */
    private const val MIN_PREFIX = 3

    private const val PAIR_SEPARATOR = "\u001F"

    fun match(
        records: List<MatchRecord>,
        rows: List<SheetOrderRow>,
        aliases: Map<String, String>,
        usedRowKeys: Set<String>,
        tabName: String,
        rejectedPairs: Set<String> = emptySet(),
    ): SheetMatchResult {
        val availableRows = rows
            .filter { rowKey(tabName, it) !in usedRowKeys }
            .sortedBy { it.rowIndex }
        val rowStates = availableRows.map { RowState(row = it) }

        val exact = mutableListOf<ExactMatch>()
        val ambiguous = mutableListOf<AmbiguousMatch>()

        val ordered = records
            .filter { it.address.isNotBlank() }
            .sortedByDescending { it.incomingAt }

        // Первый проход — только точные совпадения для всех записей. Иначе
        // похожий адрес более свежей записи занимал строку, и точное
        // совпадение следующей записи уже не находилось.
        val unmatched = mutableListOf<PendingRecord>()
        for (record in ordered) {
            val recordAddress = AddressNormalizer.normalize(record.address)
            val canonical = aliases[recordAddress] ?: recordAddress
            val recordBottles = AddressNormalizer.bottlesFromText(record.bottlesText)

            val exactState = rowStates.firstOrNull { state ->
                !state.used && (
                    state.normalizedAddress == canonical ||
                        // Синоним подтверждён, но в таблице может встретиться
                        // и исходное написание адреса — это тот же адрес.
                        state.normalizedAddress == recordAddress
                    )
            }
            if (exactState != null) {
                exactState.used = true
                val rowBottles = exactState.row.bottles
                exact.add(
                    ExactMatch(
                        recordId = record.id,
                        row = exactState.row,
                        bottlesDiffer = rowBottles != null &&
                            recordBottles != null &&
                            rowBottles != recordBottles,
                    ),
                )
            } else {
                unmatched.add(
                    PendingRecord(
                        record = record,
                        canonical = canonical,
                        bottles = recordBottles,
                    ),
                )
            }
        }

        // Второй проход — похожие адреса только среди оставшихся записей
        // и свободных строк.
        for (pending in unmatched) {
            val record = pending.record
            val canonical = pending.canonical
            var best: RowState? = null
            var bestScore = 0.0
            for (state in rowStates) {
                if (state.used) continue
                // Оператор уже сказал, что это разные адреса — не предлагаем.
                if (pairKey(canonical, state.normalizedAddress) in rejectedPairs) continue
                val score = similarity(canonical, state.normalizedAddress)
                val candidate = score >= SIMILARITY_THRESHOLD ||
                    isPartialCandidate(canonical, state.normalizedAddress)
                // Лучший — по similarity; при равенстве остаётся более ранняя строка.
                if (candidate && (best == null || score > bestScore)) {
                    bestScore = score
                    best = state
                }
            }

            if (best != null) {
                best.used = true
                ambiguous.add(
                    AmbiguousMatch(
                        recordId = record.id,
                        recordAddress = record.address,
                        recordBottles = pending.bottles,
                        row = best.row,
                        similarity = bestScore,
                        partial = isPartialCandidate(canonical, best.normalizedAddress),
                    ),
                )
            }
        }

        return SheetMatchResult(exact = exact, ambiguous = ambiguous)
    }

    /**
     * Ключ адреса в таблице: `"$tabName:$rowIndex:$column"`.
     *
     * Колонка входит в ключ, потому что в одной строке листа лежит до трёх
     * адресов (B, I, Q). Иначе закрытый заказ из колонки B блокировал бы
     * адрес из колонки Q в той же строке.
     */
    fun rowKey(tabName: String, row: SheetOrderRow): String =
        "$tabName:${row.rowIndex}:${row.column}"

    /** Ключ пары «адрес записи → адрес таблицы» для запомненных решений. */
    fun pairKey(recordAddress: String, rowAddress: String): String =
        AddressNormalizer.normalize(recordAddress) + PAIR_SEPARATOR +
            AddressNormalizer.normalize(rowAddress)

    /**
     * Сходство адресов по компактным формам:
     * `1.0 - расстояние Левенштейна / max(длина)`.
     */
    fun similarity(a: String, b: String): Double {
        val left = AddressNormalizer.compact(a)
        val right = AddressNormalizer.compact(b)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        if (left == right) return 1.0
        val distance = levenshtein(left, right)
        return 1.0 - distance.toDouble() / maxOf(left.length, right.length)
    }

    /**
     * Неполное совпадение: адрес из приложения — часть адреса из таблицы
     * (или наоборот), записанная словами подряд.
     *
     * В таблице клиента часто пишут полнее: «Континент город женский»,
     * а в телефоне — «Город женский». Сравниваются слова, поэтому
     * «Кирова 5» и «Кирова 53» остаются разными домами. Слишком общий
     * адрес из одного слова («Мира») кандидатом не считается.
     *
     * Недописанное слово считается тем же словом: «Куйб налог 407» и
     * «Куйб налоговая 407» — одна пара, которую подтверждает оператор.
     */
    fun isPartialCandidate(a: String, b: String): Boolean {
        val left = AddressNormalizer.tokens(a)
        val right = AddressNormalizer.tokens(b)
        if (left.isEmpty() || right.isEmpty() || left == right) return false
        val (shorter, longer) = if (left.size <= right.size) left to right else right to left
        if (shorter.size < MIN_PARTIAL_TOKENS) return false
        return containsRun(longer, shorter)
    }

    /** Есть ли [run] в [tokens] подряд, без разрывов. */
    private fun containsRun(tokens: List<String>, run: List<String>): Boolean {
        if (run.size > tokens.size) return false
        for (start in 0..(tokens.size - run.size)) {
            var matches = true
            for (offset in run.indices) {
                if (!sameWord(tokens[start + offset], run[offset])) {
                    matches = false
                    break
                }
            }
            if (matches) return true
        }
        return false
    }

    /**
     * Одно и то же слово или его недописанная форма: «налог» и «налоговая»,
     * «водоканал» и «водок». Клиент в телефоне и в таблице сокращает и
     * дописывает названия по-разному, поэтому сравнивается начало слова.
     *
     * Недописанное слово должно быть не короче [MIN_PREFIX]. Короткие слова
     * сравниваются целиком: иначе «5» и «53» стали бы одним домом.
     */
    private fun sameWord(a: String, b: String): Boolean {
        if (a == b) return true
        val shorter = minOf(a.length, b.length)
        if (shorter < MIN_PREFIX) return false
        return a.startsWith(b) || b.startsWith(a)
    }

    private fun levenshtein(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    previous[j] + 1,
                    current[j - 1] + 1,
                    previous[j - 1] + cost,
                )
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }

    private class RowState(val row: SheetOrderRow) {
        val normalizedAddress: String = AddressNormalizer.normalize(row.address)
        var used: Boolean = false
    }

    /** Запись без точного совпадения: ждёт похожего адреса во втором проходе. */
    private class PendingRecord(
        val record: MatchRecord,
        val canonical: String,
        val bottles: Int?,
    )
}
