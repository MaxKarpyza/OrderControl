package ru.ordercontrol.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ordercontrol.domain.SheetAddresses
import ru.ordercontrol.domain.SheetCsv
import ru.ordercontrol.domain.SheetOrderRow

/** Вкладка таблицы со строками заказов. */
data class SheetTab(
    val name: String,
    val rows: List<SheetOrderRow>,
)

/** Итог загрузки вкладок таблицы. */
sealed interface SheetFetchResult {

    /** Что удалось скачать; пустой список вкладок сюда не попадает. */
    data class Success(val tabs: List<SheetTab>) : SheetFetchResult

    /** Сеть недоступна (нет соединения, таймаут, обрыв). */
    data object NoNetwork : SheetFetchResult

    /** Сервер ответил ошибкой или разбор не дал ни одной вкладки. */
    data class Failure(val reason: String) : SheetFetchResult
}

/**
 * Клиент Google Таблицы: скачивает CSV-вкладки за сегодня и завтра.
 *
 * Вкладки читаются без авторизации. Имя вкладки — дата `дд.ММ.гг`
 * по системному часовому поясу.
 *
 * Имя нельзя передавать в `gviz` напрямую: на несуществующее имя сервер
 * отвечает 200 и данными **первого** листа, то есть чужим днём. Поэтому
 * сначала берётся список вкладок с их `gid` из `htmlview`, а CSV качается
 * уже по `gid`. Содержимое таблицы не логируется: там адреса и телефоны.
 */
class SheetsClient(private val baseUrl: String = DEFAULT_BASE_URL) {

    /**
     * Скачивает вкладки за [today] и за следующий день.
     *
     * Отсутствие вкладки (например, завтрашней ещё нет) не считается ошибкой:
     * такая вкладка пропускается. Если не скачалась ни одна — возвращается
     * [SheetFetchResult.Failure] или [SheetFetchResult.NoNetwork].
     */
    suspend fun fetchTodayAndTomorrow(today: LocalDate = LocalDate.now()): SheetFetchResult {
        val index = when (val result = fetchSheetIndex()) {
            is IndexResult.Ok -> result.sheets
            IndexResult.NoNetwork -> return SheetFetchResult.NoNetwork
            is IndexResult.HttpError -> return SheetFetchResult.Failure(result.reason)
        }

        val names = listOf(tabName(today), tabName(today.plusDays(1)))
        val tabs = mutableListOf<SheetTab>()
        var lastFailure: String? = null
        var networkFailed = false

        for (name in names) {
            val gid = index[name] ?: continue
            when (val result = fetchTab(name, gid)) {
                is TabResult.Ok -> tabs.add(result.tab)
                is TabResult.HttpError -> lastFailure = result.reason
                TabResult.NoNetwork -> networkFailed = true
            }
        }

        return when {
            tabs.isNotEmpty() -> SheetFetchResult.Success(tabs)
            networkFailed -> SheetFetchResult.NoNetwork
            else -> SheetFetchResult.Failure(lastFailure ?: "нет вкладок")
        }
    }

    /**
     * Список вкладок документа: имя → `gid`.
     *
     * `htmlview` лёгкий (десятки килобайт), но перечисляет не все вкладки —
     * только недавние. Если нужной даты там нет, список берётся со страницы
     * редактирования, где есть все вкладки, но она весит около мегабайта.
     */
    private suspend fun fetchSheetIndex(): IndexResult = withContext(Dispatchers.IO) {
        val light = try {
            download(htmlViewUrl())
        } catch (_: IOException) {
            return@withContext IndexResult.NoNetwork
        }
        if (light != null) {
            val sheets = parseSheetIndex(light)
            if (sheets.isNotEmpty()) return@withContext IndexResult.Ok(sheets)
        }

        val full = try {
            download(editUrl())
        } catch (_: IOException) {
            return@withContext IndexResult.NoNetwork
        } ?: return@withContext IndexResult.HttpError("HTTP")

        val sheets = parseEditIndex(full)
        if (sheets.isEmpty()) {
            IndexResult.HttpError("нет списка вкладок")
        } else {
            IndexResult.Ok(sheets)
        }
    }

    private suspend fun fetchTab(name: String, gid: String): TabResult = withContext(Dispatchers.IO) {
        val text = try {
            download(csvUrl(gid))
        } catch (_: IOException) {
            return@withContext TabResult.NoNetwork
        } ?: return@withContext TabResult.HttpError("HTTP")

        TabResult.Ok(SheetTab(name = name, rows = SheetAddresses.rows(SheetCsv.parse(text))))
    }

    /**
     * GET-запрос.
     * @return тело ответа или `null`, если сервер ответил не 200.
     */
    private fun download(requestUrl: String): String? {
        val connection = URL(requestUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.setRequestProperty("Accept", "text/csv,text/html")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    private sealed interface TabResult {
        data class Ok(val tab: SheetTab) : TabResult

        data class HttpError(val reason: String) : TabResult

        data object NoNetwork : TabResult
    }

    private sealed interface IndexResult {
        data class Ok(val sheets: Map<String, String>) : IndexResult

        data class HttpError(val reason: String) : IndexResult

        data object NoNetwork : IndexResult
    }

    internal companion object {
        const val DEFAULT_BASE_URL =
            "https://docs.google.com/spreadsheets/d/1HDK5_dbN52XIE3XScBhVJtvtJvgbEMwH8U6Trozjmqg"

        private const val TIMEOUT_MS = 10_000
        private const val USER_AGENT = "OrderControl/1.0 (Android)"

        private val TAB_DATE: DateTimeFormatter =
            DateTimeFormatter.ofPattern("dd.MM.yy", Locale.ROOT)

        /** В `htmlview` вкладки перечислены как `{name: "27.10.26", …, gid: "178090042"}`. */
        private val SHEET_ENTRY: Regex =
            Regex("""name:\s*"([^"]+)"[^}]*?gid:\s*"(\d+)"""")

        /**
         * На странице редактирования вкладки лежат как
         * `[311,0,"178090042",[{"1":[[0,0,"27.10.26"]…`, с экранированием
         * кавычек — сначала `gid`, затем имя.
         */
        private val EDIT_SHEET_ENTRY: Regex =
            Regex("""\[\d+,0,\\"(\d+)\\",\[\{\\"1\\":\[\[0,0,\\"([^"\\]+)\\"""")

        /** Имя вкладки по дате: `27.10.26`. */
        internal fun tabName(date: LocalDate): String = date.format(TAB_DATE)

        /** URL списка вкладок документа. */
        internal fun htmlViewUrl(baseUrl: String = DEFAULT_BASE_URL): String = "$baseUrl/htmlview"

        /** URL страницы редактирования — источник полного списка вкладок. */
        internal fun editUrl(baseUrl: String = DEFAULT_BASE_URL): String = "$baseUrl/edit"

        /** URL CSV-экспорта вкладки по её `gid`. */
        internal fun csvUrl(gid: String, baseUrl: String = DEFAULT_BASE_URL): String =
            "$baseUrl/gviz/tq?tqx=out:csv&gid=$gid"

        /** Пары «имя вкладки → gid» из HTML списка вкладок. */
        internal fun parseSheetIndex(html: String): Map<String, String> =
            SHEET_ENTRY.findAll(html).associate { it.groupValues[1] to it.groupValues[2] }

        /** Пары «имя вкладки → gid» из страницы редактирования. */
        internal fun parseEditIndex(html: String): Map<String, String> =
            EDIT_SHEET_ENTRY.findAll(html).associate { it.groupValues[2] to it.groupValues[1] }
    }

    /** URL списка вкладок для этого клиента. */
    internal fun htmlViewUrl(): String = htmlViewUrl(baseUrl)

    /** URL страницы редактирования для этого клиента. */
    internal fun editUrl(): String = editUrl(baseUrl)

    /** URL CSV-экспорта вкладки с `gid` для этого клиента. */
    internal fun csvUrl(gid: String): String = csvUrl(gid, baseUrl)
}
