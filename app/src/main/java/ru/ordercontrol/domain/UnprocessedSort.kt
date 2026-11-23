package ru.ordercontrol.domain

/** Порядок записей на вкладке «Незаписанные». */
enum class UnprocessedSort {
    /** Сначала те, чей звонок принят раньше. */
    TIME_EARLIER,

    /** Сначала те, чей звонок принят позже. */
    TIME_LATER,

    /** По имени клиента, адресу или номеру. */
    ALPHABET,

    /** По району доставки, внутри района — ранние сверху. */
    DISTRICT,
    ;

    companion object {
        val DEFAULT = TIME_EARLIER

        fun fromKey(key: String?): UnprocessedSort =
            entries.firstOrNull { it.name == key } ?: DEFAULT
    }
}
