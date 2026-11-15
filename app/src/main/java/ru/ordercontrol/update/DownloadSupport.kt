package ru.ordercontrol.update

/** Разобранный заголовок `Content-Range: bytes 100-999/1000`. */
internal data class ContentRange(
    val start: Long,
    val end: Long,
    val total: Long?,
)

/**
 * Вспомогательная логика докачивания: разбор заголовков ответа и решение,
 * стоит ли повторять запрос.
 */
internal object DownloadSupport {

    /**
     * Разбирает `Content-Range`. `null` — заголовка нет или он непонятен.
     */
    fun parseContentRange(header: String?): ContentRange? {
        if (header.isNullOrBlank()) return null
        val value = header.trim()
        if (!value.startsWith("bytes", ignoreCase = true)) return null
        val parts = value.substringAfter(' ').split('/')
        if (parts.size != 2) return null
        val range = parts[0].trim().split('-')
        if (range.size != 2) return null
        val start = range[0].trim().toLongOrNull() ?: return null
        val end = range[1].trim().toLongOrNull() ?: return null
        val total = parts[1].trim().takeIf { it != "*" }?.toLongOrNull()
        return ContentRange(start, end, total)
    }

    /**
     * Стоит ли повторять запрос после такого кода ответа.
     *
     * Повторяем при временных проблемах: сервер перегружен (5xx), таймаут
     * (408) и слишком много запросов (429). Остальное (404, 403 и прочее)
     * повторением не лечится.
     */
    fun isRetryableStatus(code: Int): Boolean = when {
        code == 408 || code == 429 -> true
        code in 500..599 -> true
        else -> false
    }

    /**
     * Процент скачанного по размеру файла. `0`, если размер неизвестен.
     */
    fun percentOf(downloaded: Long, total: Long): Int {
        if (total <= 0) return 0
        return ((downloaded * 100) / total).toInt().coerceIn(0, 100)
    }
}
