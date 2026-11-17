package ru.ordercontrol.data

import java.time.LocalDate
import java.time.ZoneId
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.db.CallRecordDao
import ru.ordercontrol.domain.AddressNormalizer
import ru.ordercontrol.domain.CloseReason
import ru.ordercontrol.domain.DeliveryDay
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
 * Район для ячейки таблицы.
 *
 * Сначала район из самой записи: у ручных заказов его подставляет база
 * адресов или вводит оператор. У заказов по звонку района в записи нет —
 * тогда он ищется в базе адресов: по адресу доставки, затем по адресу из
 * поля ввода и по контакту. Названия у одного адреса бывают разными, поэтому
 * одного написания мало. Если не нашлось ничего, ячейка остаётся пустой.
 *
 * @param lookup поиск района по адресу.
 */
internal suspend fun resolveDistrict(
    record: CallRecord,
    lookup: suspend (String) -> String?,
): String {
    record.district?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    val candidates = listOf(
        record.deliveryAddress,
        record.addressText,
        record.displayName.orEmpty(),
    )
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .distinct()
    for (address in candidates) {
        lookup(address)?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
    }
    return ""
}

/**
 * Заказ для таблицы: адрес, бутыли, комментарий и район.
 *
 * Район берётся из записи, а если его там нет — из базы адресов. Заказ,
 * принятый по телефону, попадает в таблицу с тем же набором полей, что и
 * ручной: адрес, количество и район.
 *
 * @param districtLookup поиск района по адресу, если в записи его нет.
 */
internal suspend fun CallRecord.toSheetOrder(
    districtLookup: suspend (String) -> String?,
): SheetWriteOrder = SheetWriteOrder(
    address = deliveryAddress.trim(),
    bottles = bottlesText.trim(),
    comment = buildSheetComment(this),
    district = resolveDistrict(this, districtLookup),
    otherDay = deliveryDay == DeliveryDay.OTHER_DAY,
)

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
    private val addressBase: AddressBaseRepository,
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
                    record.toSheetOrder { address -> addressBase.find(address)?.district }
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
        var fetched = false
        for ((tab, records) in writtenByTab) {
            val rows = sheets.fetchTabByName(tab)?.rows
            if (rows == null) {
                outcome.errors.add("не удалось перечитать вкладку $tab")
                outcome.notConfirmed.addAll(records.map { it.deliveryAddress })
                continue
            }
            fetched = true

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

        // Таблицу скачали: счётчик растёт, а на десятое скачивание
        // обновляется база адресов.
        if (fetched) {
            addressBase.onTableFetched()
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
