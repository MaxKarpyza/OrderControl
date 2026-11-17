package ru.ordercontrol.data

/**
 * Правило обновления базы адресов.
 *
 * Таблицу скачивают при отправке адресов и при проверке. На десятое
 * скачивание база обновляется: за это время в таблице могли поправить
 * адреса. И не реже раза в сутки — даже если скачиваний было мало.
 */

/** Сколько скачиваний таблицы ждём до обновления базы. */
internal const val SHEET_FETCH_LIMIT = 10

/** Обновление базы адресов — не реже раза в сутки. */
internal const val ADDRESS_BASE_REFRESH_INTERVAL_MS = 24 * 60 * 60 * 1000L

/**
 * Пора ли обновлять базу адресов.
 *
 * @param fetchCount сколько раз таблицу скачивали с последнего обновления.
 * @param lastRefreshAt когда базу обновляли в последний раз.
 * @param now текущее время.
 */
internal fun isRefreshDue(fetchCount: Int, lastRefreshAt: Long, now: Long): Boolean =
    fetchCount >= SHEET_FETCH_LIMIT ||
        now - lastRefreshAt >= ADDRESS_BASE_REFRESH_INTERVAL_MS
