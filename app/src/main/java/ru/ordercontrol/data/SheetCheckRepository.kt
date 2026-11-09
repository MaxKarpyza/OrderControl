package ru.ordercontrol.data

import ru.ordercontrol.data.db.AddressAlias
import ru.ordercontrol.data.db.AddressAliasDao
import ru.ordercontrol.data.db.CallRecordDao
import ru.ordercontrol.domain.AddressNormalizer
import ru.ordercontrol.domain.AliasKind
import ru.ordercontrol.domain.AmbiguousMatch
import ru.ordercontrol.domain.CloseReason
import ru.ordercontrol.domain.ExactMatch
import ru.ordercontrol.domain.MatchRecord
import ru.ordercontrol.domain.SheetOrderMatcher

/**
 * Итог сверки необработанных записей с таблицей.
 *
 * @property closedCount сколько записей закрыто как `SHEET`.
 * @property exactWithBottleMismatch точные совпадения с расхождением количества —
 *   подсказка оператору, записи при этом закрыты.
 * @property ambiguous похожие совпадения: ничего не закрыто, ждут подтверждения.
 * @property tabsChecked имена проверенных вкладок.
 * @property rowsRead сколько адресов прочитано из таблицы.
 * @property recordsWithAddress сколько необработанных записей вообще имеют адрес.
 */
data class SheetCheckOutcome(
    val closedCount: Int,
    val exactWithBottleMismatch: List<ExactMatch>,
    val ambiguous: List<AmbiguousMatch>,
    val tabsChecked: List<String>,
    val rowsRead: Int,
    val recordsWithAddress: Int,
)

/**
 * Сверка необработанных записей с Google Таблицей.
 *
 * Точное совпадение адреса закрывает запись с `closeReason = SHEET` и
 * запоминает ключ строки, поэтому одна строка таблицы закрывает не более
 * одной записи. Неполное совпадение ничего не закрывает: оператор
 * подтверждает его через [confirmAlias], и со следующего раза адрес
 * считается точным.
 */
class SheetCheckRepository(
    private val callDao: CallRecordDao,
    private val aliasDao: AddressAliasDao,
    private val sheets: SheetsClient,
) {

    /**
     * Скачивает вкладки за сегодня и завтра. Ничего не меняет: применение —
     * отдельный шаг [apply], чтобы вызывающий получил итог сверки.
     */
    suspend fun check(): SheetFetchResult = sheets.fetchTodayAndTomorrow()

    suspend fun apply(tabs: List<SheetTab>): SheetCheckOutcome {
        val records = callDao.openWithAddress().map { record ->
            MatchRecord(
                id = record.id,
                address = record.deliveryAddress,
                bottlesText = record.bottlesText,
                incomingAt = record.incomingAt,
            )
        }
        val usedRowKeys = callDao.usedSheetKeys().toMutableSet()
        val decisions = aliasDao.all()
        val aliases = decisions
            .filter { it.kind == AliasKind.MATCH }
            .associate { it.aliasNormalized to it.canonicalNormalized }
        val rejectedPairs = decisions
            .filter { it.kind == AliasKind.REJECT }
            .map { SheetOrderMatcher.pairKey(it.aliasNormalized, it.canonicalNormalized) }
            .toSet()

        val now = System.currentTimeMillis()
        val mismatches = mutableListOf<ExactMatch>()
        val ambiguous = mutableListOf<AmbiguousMatch>()
        val closedIds = mutableSetOf<Long>()
        var closedCount = 0

        for (tab in tabs) {
            // Запись, закрытая по вчерашней/сегодняшней вкладке, не должна
            // «съесть» строку в следующей: иначе соседняя запись с тем же
            // адресом останется незакрытой.
            val pending = records.filter { it.id !in closedIds }
            if (pending.isEmpty()) break

            val result = SheetOrderMatcher.match(
                records = pending,
                rows = tab.rows,
                aliases = aliases,
                usedRowKeys = usedRowKeys,
                tabName = tab.name,
                rejectedPairs = rejectedPairs,
            )

            for (match in result.exact) {
                val key = SheetOrderMatcher.rowKey(tab.name, match.row)
                val updated = callDao.closeBySheet(
                    id = match.recordId,
                    at = now,
                    reason = CloseReason.SHEET,
                    sheetKey = key,
                )
                if (updated == 0) continue
                usedRowKeys.add(key)
                closedIds.add(match.recordId)
                closedCount++
                if (match.bottlesDiffer) mismatches.add(match)
            }
            ambiguous.addAll(result.ambiguous)
        }

        return SheetCheckOutcome(
            closedCount = closedCount,
            exactWithBottleMismatch = mismatches,
            ambiguous = ambiguous,
            tabsChecked = tabs.map { it.name },
            rowsRead = tabs.sumOf { it.rows.size },
            recordsWithAddress = records.size,
        )
    }

    /** Запоминает подтверждённую оператором пару адресов как синоним. */
    suspend fun confirmAlias(aliasAddress: String, canonicalAddress: String) {
        remember(aliasAddress, canonicalAddress, AliasKind.MATCH)
    }

    /** Запоминает, что это разные адреса: пару больше не предлагать. */
    suspend fun rejectAlias(aliasAddress: String, canonicalAddress: String) {
        remember(aliasAddress, canonicalAddress, AliasKind.REJECT)
    }

    private suspend fun remember(aliasAddress: String, canonicalAddress: String, kind: AliasKind) {
        aliasDao.put(
            AddressAlias(
                aliasNormalized = AddressNormalizer.normalize(aliasAddress),
                canonicalNormalized = AddressNormalizer.normalize(canonicalAddress),
                createdAt = System.currentTimeMillis(),
                kind = kind,
            ),
        )
    }
}
