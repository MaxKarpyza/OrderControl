package ru.ordercontrol.data

import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Своя обработанная запись, которую надо положить в общую историю. */
data class HistoryPushItem(
    /** Идентификатор строки: `опер_NN-КОД:запись:время`. */
    val id: String,
    val phone: String,
    val address: String,
    val district: String?,
    val bottles: String,
    val comment: String,
    val processedAt: Long,
)

/** Чужая обработанная запись, пришедшая из вкладки `history`. */
data class SharedHistoryRow(
    val id: String,
    val author: String,
    val phone: String,
    val address: String,
    val district: String?,
    val bottles: String,
    val comment: String,
    val processedAt: Long,
)

sealed interface HistorySyncResult {
    /**
     * @property rows всё окно истории из таблицы.
     * @property accepted идентификаторы своих строк, которые таблица приняла.
     */
    data class Success(
        val rows: List<SharedHistoryRow>,
        val accepted: Set<String>,
    ) : HistorySyncResult

    /** На развёртывании работает скрипт без общей истории — пора обновить. */
    data object Unsupported : HistorySyncResult

    data class Failure(val reason: String) : HistorySyncResult

    data object NoNetwork : HistorySyncResult
}

/** Обмен общей историей: отдельно от HTTP, чтобы подменять в тестах. */
interface HistorySyncer {
    suspend fun sync(operator: String, push: List<HistoryPushItem>): HistorySyncResult
}

/**
 * Обмен общей историей операторов через тот же Apps Script, что пишет заказы.
 *
 * Один запрос делает всё: отправляет свои новые обработанные записи, получает
 * чужие и заодно просит скрипт почистить старые строки.
 */
class HistorySyncClient(
    private val endpointUrl: String = SheetWriteClient.DEFAULT_ENDPOINT,
) : HistorySyncer {

    override suspend fun sync(operator: String, push: List<HistoryPushItem>): HistorySyncResult =
        withContext(Dispatchers.IO) {
            val response = try {
                WebAppPost.post(endpointUrl, buildSyncBody(operator, push))
            } catch (_: IOException) {
                return@withContext HistorySyncResult.NoNetwork
            }
            val body = response.body
                ?: return@withContext HistorySyncResult.Failure("HTTP ${response.code}")

            parse(body)
        }

    internal fun parse(response: String): HistorySyncResult {
        val json = try {
            JSONObject(response)
        } catch (_: Exception) {
            return HistorySyncResult.Failure("ответ не JSON")
        }

        if (!json.optBoolean("ok", false)) {
            val error = json.optString("error")
            // Старый скрипт не знает action=sync и требует поле tab.
            return if (error == "no_tab" || error == "unknown_action") {
                HistorySyncResult.Unsupported
            } else {
                HistorySyncResult.Failure(error.ifEmpty { "ошибка скрипта" })
            }
        }

        // Скрипт без общей истории отвечает ok и без поля history: значит,
        // развёртывание ещё старое.
        val array = json.optJSONArray("history") ?: return HistorySyncResult.Unsupported
        val rows = buildList {
            for (index in 0 until array.length()) {
                val row = array.optJSONObject(index) ?: continue
                val id = row.optString("id").trim()
                val phone = row.optString("phone").trim()
                if (id.isEmpty() || phone.isEmpty()) continue
                add(
                    SharedHistoryRow(
                        id = id,
                        author = row.optString("author").trim(),
                        phone = phone,
                        address = row.optString("address").trim(),
                        district = row.optString("district").trim().takeIf { it.isNotEmpty() },
                        bottles = row.optString("bottles").trim(),
                        comment = row.optString("comment").trim(),
                        processedAt = row.optLong("processedAt"),
                    ),
                )
            }
        }

        val accepted = json.optJSONArray("accepted")?.let { array ->
            buildSet {
                for (index in 0 until array.length()) {
                    array.optString(index).takeIf { it.isNotEmpty() }?.let { add(it) }
                }
            }
        }.orEmpty()

        return HistorySyncResult.Success(rows = rows, accepted = accepted)
    }
}

/**
 * Тело POST-запроса на обмен историей.
 *
 * Отдельно от клиента, чтобы состав запроса можно было проверить без сети.
 */
internal fun buildSyncBody(operator: String, push: List<HistoryPushItem>): String =
    JSONObject()
        .put("action", "sync")
        .put("operator", operator)
        .put(
            "push",
            JSONArray().apply {
                push.forEach { item ->
                    val json = JSONObject()
                    pushFields(item).forEach { (key, value) -> json.put(key, value) }
                    put(json)
                }
            },
        )
        .toString()

/**
 * Поля отправляемой строки.
 *
 * Отдельно от JSON: в JVM-тестах `org.json` — заглушка Android, поэтому
 * состав запроса проверяется по этой карте.
 */
internal fun pushFields(item: HistoryPushItem): Map<String, Any> = linkedMapOf(
    "id" to item.id,
    "phone" to item.phone,
    "address" to item.address,
    "district" to item.district.orEmpty(),
    "bottles" to item.bottles,
    "comment" to item.comment,
    "processedAt" to item.processedAt,
)
