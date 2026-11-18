package ru.ordercontrol.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Одна строка, которую надо записать в таблицу. */
data class SheetWriteOrder(
    val address: String,
    val bottles: String,
    val comment: String,
    /** Район доставки: пишется в колонку `M` блока заказов. */
    val district: String = "",
    /** Заказ на «другой день»: строку помечаем цветом, дата неизвестна. */
    val otherDay: Boolean = false,
)

/** Ответ скрипта на успешную запись. */
data class SheetWrittenRow(
    val address: String,
    val row: Int,
)

sealed interface SheetWriteResult {
    data class Success(val tab: String, val written: List<SheetWrittenRow>) : SheetWriteResult

    /** Вкладки с таким именем нет: люди создают её сами. */
    data class TabMissing(val tab: String) : SheetWriteResult

    data class Failure(val reason: String) : SheetWriteResult

    data object NoNetwork : SheetWriteResult
}

/**
 * Отправка заказов в Google Таблицу через Apps Script Web App.
 *
 * Скрипт живёт отдельно от приложения, поэтому здесь только HTTP: JSON на
 * входе, JSON на выходе, без авторизации. URL — константа сборки.
 */
class SheetWriteClient(private val endpointUrl: String = DEFAULT_ENDPOINT) {

    suspend fun write(tab: String, orders: List<SheetWriteOrder>): SheetWriteResult =
        withContext(Dispatchers.IO) {
            if (orders.isEmpty()) return@withContext SheetWriteResult.Success(tab, emptyList())

            val body = buildWriteBody(tab, orders)

            val response = try {
                post(body)
            } catch (_: IOException) {
                return@withContext SheetWriteResult.NoNetwork
            } ?: return@withContext SheetWriteResult.Failure("HTTP")

            parse(response, tab)
        }

    /** POST и чтение тела ответа; `null`, если сервер ответил не 200. */
    private fun post(body: String): String? = WebAppPost.post(endpointUrl, body)

    private fun parse(response: String, tab: String): SheetWriteResult {
        val json = try {
            JSONObject(response)
        } catch (_: Exception) {
            return SheetWriteResult.Failure("ответ не JSON")
        }

        if (!json.optBoolean("ok", false)) {
            return when (json.optString("error")) {
                "tab_not_found" -> SheetWriteResult.TabMissing(json.optString("tab", tab))
                else -> SheetWriteResult.Failure(json.optString("error", "ошибка скрипта"))
            }
        }

        val written = json.optJSONArray("written")?.let { array ->
            buildList {
                for (index in 0 until array.length()) {
                    val row = array.optJSONObject(index) ?: continue
                    add(
                        SheetWrittenRow(
                            address = row.optString("address"),
                            row = row.optInt("row", 0),
                        ),
                    )
                }
            }
        }.orEmpty()

        return SheetWriteResult.Success(tab = json.optString("tab", tab), written = written)
    }

    companion object {
        /**
         * Веб-приложение Apps Script проекта «OrderControl таблица»
         * (см. doc/table-write.md). Меняется только новой сборкой.
         */
        const val DEFAULT_ENDPOINT =
            "https://script.google.com/macros/s/AKfycbx--hHP_xxnAL-_Hth_RPK8Rt7n1COT6SlpdBIi8qolJVcuWbhKzWKt1fUp_YOMwkXp-A/exec"
    }
}

/**
 * Тело POST-запроса: вкладка и список заказов.
 *
 * Отдельно от клиента, чтобы состав запроса можно было проверить без сети.
 */
internal fun buildWriteBody(tab: String, orders: List<SheetWriteOrder>): String =
    JSONObject()
        .put("tab", tab)
        .put(
            "orders",
            JSONArray().apply {
                orders.forEach { order ->
                    val json = JSONObject()
                    orderFields(order).forEach { (key, value) -> json.put(key, value) }
                    put(json)
                }
            },
        )
        .toString()

/**
 * Поля заказа, которые уходят скрипту.
 *
 * Отдельно от JSON: в JVM-тестах `org.json` — заглушка Android, которая
 * ничего не делает, поэтому состав запроса проверяется по этой карте.
 */
internal fun orderFields(order: SheetWriteOrder): Map<String, Any> = linkedMapOf(
    "address" to order.address,
    "bottles" to order.bottles,
    "comment" to order.comment,
    "district" to order.district,
    "otherDay" to order.otherDay,
)
