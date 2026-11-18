package ru.ordercontrol.data

import java.net.HttpURLConnection
import java.net.URL

/**
 * POST в веб-приложение Apps Script и чтение тела ответа.
 *
 * Скрипты в таблице отвечают JSON, но на некоторых ошибках Google отдаёт
 * HTML — такой ответ превращается в `null`, и вызывающий код видит обычную
 * ошибку, а не мусор в разборе.
 *
 * Перенаправление включено: после POST Apps Script отвечает 302 на
 * `script.googleusercontent.com`, и без этого ответ теряется.
 */
internal object WebAppPost {

    private const val USER_AGENT = "OrderControl/1.0 (Android)"

    /** @return тело ответа или `null`, если сервер ответил не 200. */
    fun post(endpointUrl: String, body: String, timeoutMs: Int = DEFAULT_TIMEOUT_MS): String? {
        val connection = URL(endpointUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.doOutput = true
            connection.connectTimeout = timeoutMs
            connection.readTimeout = timeoutMs
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                // Тело ошибки тоже может быть JSON с причиной.
                val error = connection.errorStream?.use {
                    it.readBytes().toString(Charsets.UTF_8)
                }
                return error?.takeIf { it.startsWith("{") }
            }
            return connection.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
        } finally {
            connection.disconnect()
        }
    }

    const val DEFAULT_TIMEOUT_MS = 20_000
}
