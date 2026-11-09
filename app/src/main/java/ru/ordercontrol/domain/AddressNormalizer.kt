package ru.ordercontrol.domain

/**
 * Приведение адресов и текста о бутылях к сравнимому виду.
 *
 * Адрес пишут по-разному: «Артёма 114, кв. 2-314» и «Артема 114 2 314» —
 * один и тот же адрес. [normalize] превращает текст в набор слов из букв и цифр,
 * [compact] убирает ещё и пробелы с дефисами — для нечёткого сравнения.
 */
object AddressNormalizer {

    // \p{L} в java.util.regex не покрывает кириллицу — буквы заданы диапазонами явно.
    private val NON_WORD = Regex("[^a-zа-я0-9]+")
    private val NUMBER = Regex("\\d+")

    /**
     * Слова-«шум»: оператор пишет «кв.», в таблице — только номер квартиры.
     * Без этого один и тот же адрес не совпал бы точно.
     */
    private val NOISE_WORDS = setOf(
        "кв",
        "квартира",
        "оф",
        "офис",
        "пом",
        "помещение",
    )

    /** Максимальное значение, которое ещё считается количеством бутылей. */
    private const val MAX_BOTTLES = 100

    fun normalize(raw: String): String =
        raw.lowercase()
            .replace('ё', 'е')
            .replace(NON_WORD, " ")
            .split(' ')
            .filter { it.isNotEmpty() && it !in NOISE_WORDS }
            .joinToString(" ")

    /** Слова адреса в нормализованном виде — для сравнения по префиксу. */
    fun tokens(raw: String): List<String> =
        normalize(raw).split(' ').filter { it.isNotEmpty() }

    fun compact(raw: String): String = tokens(raw).joinToString("")

    /**
     * Первое целое число в тексте, которое может быть количеством бутылей:
     * «2 по 19 л» → 2, «3 бутыли» → 3, «залог 1200» → `null`.
     */
    fun bottlesFromText(text: String): Int? =
        NUMBER.findAll(text)
            .mapNotNull { it.value.toIntOrNull() }
            .firstOrNull { it in 1..MAX_BOTTLES }
}
