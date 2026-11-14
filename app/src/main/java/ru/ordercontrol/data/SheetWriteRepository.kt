package ru.ordercontrol.data

import java.time.LocalDate
import java.time.ZoneId
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.db.CallRecordDao
import ru.ordercontrol.domain.AddressNormalizer
import ru.ordercontrol.domain.CloseReason
import ru.ordercontrol.domain.SheetTarget

/** Сколько сделано и что делается прямо сейчас. */
data class SheetWriteProgress(
    val done: Int,
    val total: Int,
    val address: String,
)

/** Итог отправки в таблицу: что записано, что подтвердилось, что нет. */
data class SheetWriteOutcome(
    val total: Int,
    val written: Int,
    val confirmed: Int,
    val duplicates: Int,
    val skipped: Int,
    val notConfirmed: List<String>,
    val tabs: List<String>,
    val errors: List<String>,
)

/**
 * Комментарий для ячейки таблицы: текст оператора, а если включён
 * переключатель — телефон в формате `тел +7XXXXXXXXXX` после текста.
 */
internal fun buildSheetComment(record: CallRecord): String {
    val parts = mutableListOf<String>()
    record.comment.trim().takeIf { it.isNotEmpty() }?.let { parts.add(it) }
    val phone = record.phoneNormalized
    if (record.withPhoneInComment && phone != null) {
        parts.add("тел $phone")
    }
    return parts.joinToString(" ")
}

/**
 * Запись необработанных заказов в Google Таблицу.
 *
 * Порядок: отбираем заказы → отбрасываем те, которые приложение уже писало
 * вчера или сегодня → пишем пачками по вкладкам → перечитываем вкладки и
 * закрываем только подтверждённые адреса.
 */
class SheetWriteRepository(
    private val callDao: CallRecordDao,
    private val client: SheetWriteClient,
    private val sheets: SheetsClient,
    private val now: () -> Long = System::currentTimeMillis,
) {

    suspend fun send(onProgress: (SheetWriteProgress) -> Unit): SheetWriteOutcome {
        val records = callDao.openRecords()
        // Пропущенные звонки и «не клиент» в таблицу не идут: там только заказы.
        val eligible = records.filter { record ->
            !record.isSkipped && record.isAnswered != false && record.deliveryAddress.isNotBlank()
        }

        val writtenBefore = previousAddresses()
        val duplicates = eligible.filter {
            AddressNormalizer.normalize(it.deliveryAddress) in writtenBefore
        }
        val pending = eligible.filter { record ->
            duplicates.none { it.id == record.id }
        }

        val outcome = MutableOutcome(
            total = eligible.size,
            duplicates = duplicates.size,
            skipped = records.size - eligible.size,
        )

        if (pending.isNotEmpty()) {
            writeAndVerify(pending, outcome, onProgress)
        }

        return outcome.toResult()
    }

    private suspend fun writeAndVerify(
        pending: List<CallRecord>,
        outcome: MutableOutcome,
        onProgress: (SheetWriteProgress) -> Unit,
    ) {
        val zone = ZoneId.systemDefault()
        val byTab = pending.groupBy { record ->
            SheetsClient.tabName(SheetTarget.date(record.incomingAt, record.deliveryDay, zone))
        }

        var done = 0
        val writtenByTab = linkedMapOf<String, List<CallRecord>>()

        for ((tab, records) in byTab) {
            onProgress(SheetWriteProgress(done = done, total = pending.size, address = records.first().deliveryAddress))
            val result = client.write(
                tab = tab,
                orders = records.map { record ->
                    SheetWriteOrder(
                        address = record.deliveryAddress.trim(),
                        bottles = record.bottlesText.trim(),
                        comment = commentOf(record),
                    )
                },
            )
            done += records.size

            when (result) {
                is SheetWriteResult.Success -> {
                    outcome.tabs.add(tab)
                    outcome.written += result.written.size
                    writtenByTab[tab] = records
                }

                is SheetWriteResult.TabMissing -> {
                    outcome.errors.add("вкладки ${result.tab} нет")
                }

                is SheetWriteResult.Failure -> {
                    outcome.errors.add("ошибка записи: ${result.reason}")
                }

                SheetWriteResult.NoNetwork -> {
                    outcome.errors.add("нет сети")
                    return
                }
            }
        }

        onProgress(SheetWriteProgress(done = pending.size, total = pending.size, address = ""))
        confirmWritten(writtenByTab, outcome)
    }

    /**
     * Перечитывает вкладки и закрывает только те записи, чей адрес реально
     * нашёлся в листе. Ответ скрипта — не доказательство, лист — доказательство.
     */
    private suspend fun confirmWritten(
        writtenByTab: Map<String, List<CallRecord>>,
        outcome: MutableOutcome,
    ) {
        for ((tab, records) in writtenByTab) {
            val rows = sheets.fetchTabByName(tab)?.rows
            if (rows == null) {
                outcome.errors.add("не удалось перечитать вкладку $tab")
                outcome.notConfirmed.addAll(records.map { it.deliveryAddress })
                continue
            }

            val byAddress = rows.associateBy { AddressNormalizer.normalize(it.address) }
            val byCompact = rows.associateBy { AddressNormalizer.compact(it.address) }

            for (record in records) {
                val address = record.deliveryAddress
                val row = byAddress[AddressNormalizer.normalize(address)]
                    ?: byCompact[AddressNormalizer.compact(address)]
                if (row == null) {
                    outcome.notConfirmed.add(address)
                    continue
                }
                val closed = callDao.closeWrittenBySheet(
                    id = record.id,
                    at = now(),
                    reason = CloseReason.SHEET,
                    sheetKey = "$tab:${row.rowIndex}",
                )
                if (closed > 0) {
                    outcome.confirmed += 1
                }
            }
        }
    }

    /**
     * Адреса, которые приложение писало в таблицу вчера и сегодня.
     * Повтор по тому же адресу — почти наверняка дубль.
     */
    private suspend fun previousAddresses(): Set<String> {
        val zone = ZoneId.systemDefault()
        val from = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return callDao.writtenSince(from)
            .map { AddressNormalizer.normalize(it.deliveryAddress) }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun commentOf(record: CallRecord): String = buildSheetComment(record)

    private class MutableOutcome(
        val total: Int,
        val duplicates: Int,
        val skipped: Int,
    ) {
        var written: Int = 0
        var confirmed: Int = 0
        val notConfirmed = mutableListOf<String>()
        val tabs = mutableListOf<String>()
        val errors = mutableListOf<String>()

        fun toResult(): SheetWriteOutcome = SheetWriteOutcome(
            total = total,
            written = written,
            confirmed = confirmed,
            duplicates = duplicates,
            skipped = skipped,
            notConfirmed = notConfirmed.toList(),
            tabs = tabs.toList(),
            errors = errors.toList(),
        )
    }
}
