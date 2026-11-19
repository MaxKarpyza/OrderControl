package ru.ordercontrol.data

import java.time.LocalDate
import java.time.ZoneId
import ru.ordercontrol.data.db.CallRecord
import ru.ordercontrol.data.db.CallRecordDao
import ru.ordercontrol.domain.AddressNormalizer

/**
 * Правило «адрес уже записывался в таблицу»: записи с отметкой о записи,
 * начиная с полуночи вчера.
 *
 * Его читают список необработанных, отправка в таблицу и снятие отметки —
 * граница у всех должна быть одна.
 */

/** Записи, которые приложение писало в таблицу вчера и сегодня. */
internal suspend fun CallRecordDao.recordsWrittenSinceYesterday(): List<CallRecord> {
    val zone = ZoneId.systemDefault()
    val from = LocalDate.now(zone).minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return writtenSince(from)
}

/**
 * Нормализованные адреса записей, которые приложение писало в таблицу
 * вчера и сегодня. Повтор по тому же адресу — почти наверняка дубль.
 */
internal suspend fun CallRecordDao.writtenAddressesSinceYesterday(): Set<String> =
    recordsWrittenSinceYesterday()
        .map { AddressNormalizer.normalize(it.deliveryAddress) }
        .filter { it.isNotEmpty() }
        .toSet()
