package ru.ordercontrol.domain

/** Контакт телефонной книги: имя и номер, как они записаны в книге. */
data class ContactEntry(
    val name: String?,
    val number: String?,
)

/**
 * Сопоставление номера с телефонной книгой.
 *
 * Номер в книге и номер из журнала звонков могут быть записаны по-разному
 * (`8…`, `+7…`, `9…`), поэтому сравнение идёт по нормализованной форме.
 * Отдельная функция, а не запрос к провайдеру, чтобы правило «не создавать
 * дубликат» проверялось тестами без Android.
 */
object ContactMatcher {

    /** Имя контакта с этим номером или `null`, если такого контакта нет. */
    fun findName(number: String?, contacts: Sequence<ContactEntry>): String? {
        val target = PhoneNormalizer.normalize(number) ?: return null
        return contacts
            .firstOrNull { PhoneNormalizer.normalize(it.number) == target }
            ?.name
            ?.takeIf { it.isNotBlank() }
    }

    /**
     * Нужно ли создавать контакт: имя введено, а контакта с таким номером
     * ещё нет.
     */
    fun shouldCreate(name: String?, existingName: String?): Boolean =
        !name.isNullOrBlank() && existingName.isNullOrBlank()
}
