package ru.ordercontrol.data

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
