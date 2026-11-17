package ru.ordercontrol.data

import ru.ordercontrol.data.db.AddressBaseEntry
import ru.ordercontrol.domain.WorkSchedule

/** Источник рабочего времени: настройки или подмена в тестах. */
interface ScheduleProvider {
    suspend fun currentSchedule(): WorkSchedule
}

/** Телефонная книга: поиск имени и создание контакта. */
interface ContactLookup {
    suspend fun findName(number: String): String?

    suspend fun createContactIfAbsent(name: String, number: String): Boolean
}

/**
 * База адресов: точный адрес и всё, что о нём известно.
 *
 * Нужна заказу по звонку: его адрес — это контакт клиента, а из базы
 * адресов берутся последнее количество бутылей и район.
 */
interface AddressLookup {
    suspend fun find(address: String): AddressBaseEntry?
}
