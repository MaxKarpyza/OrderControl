package ru.ordercontrol.phone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CallSessionStoreTest {

    /** Простое хранилище в памяти вместо SharedPreferences. */
    private class MemoryStorage : SessionStorage {
        val map = mutableMapOf<String, String>()

        override fun load(): Map<String, String> = map.toMap()

        override fun apply(changed: Map<String, String?>) {
            changed.forEach { (key, value) ->
                if (value == null) map.remove(key) else map[key] = value
            }
        }
    }

    private val storage = MemoryStorage()
    private var nowMs = 1_000_000L
    private val store = CallSessionStore(storage, now = { nowMs })

    @Test
    fun `RINGING без subscription и OFFHOOK с номером SIM — звонок отвечен`() {
        // Прошивка прислала `subscription` только в OFFHOOK: раньше такой
        // звонок считался исходящим и потом попадал в «пропущенные».
        store.start(subscriptionId = -1, number = "+79990001111", at = nowMs)

        assertTrue(store.markAnswered(subscriptionId = 1))

        val session = store.active().single()
        assertTrue(session.answered)
        assertEquals("+79990001111", session.number)
    }

    @Test
    fun `finish по чужой SIM финализирует только сессию с неизвестной SIM`() {
        // Гудки на одной SIM, IDLE пришёл с номером другой SIM: живой звонок
        // под известной SIM финализировать нельзя.
        store.start(subscriptionId = -1, number = null, at = nowMs)
        store.start(subscriptionId = 1, number = "+79990002222", at = nowMs)

        val finished = store.finish(subscriptionId = 2)

        assertEquals(-1, finished.single().subscriptionId)
        val alive = store.active().single()
        assertEquals(1, alive.subscriptionId)
        assertEquals("+79990002222", alive.number)
    }

    @Test
    fun `устаревшая сессия игнорируется и удаляется`() {
        store.start(subscriptionId = 1, number = "+79990003333", at = nowMs)
        nowMs += CallSessionStore.MAX_SESSION_MS + 1

        assertTrue(store.active().isEmpty())
        // Хранилище реально очищено, а не только чтение отфильтровано.
        assertTrue(storage.map.isEmpty())
        // Новый звонок на той же SIM не наследует фантом.
        store.start(subscriptionId = 1, number = "+79990004444", at = nowMs)
        assertEquals(nowMs, store.active().single().startedAt)
    }

    @Test
    fun `второй звонок на той же SIM не затирает отвеченный разговор`() {
        store.start(subscriptionId = 1, number = "+79990005555", at = nowMs)
        store.markAnswered(subscriptionId = 1)
        nowMs += 5_000

        store.start(subscriptionId = 1, number = "+79990006666", at = nowMs)

        val session = store.active().single()
        assertTrue(session.answered)
        assertEquals("+79990005555", session.number)
    }

    @Test
    fun `без сессий OFFHOOK считается исходящим`() {
        assertFalse(store.markAnswered(subscriptionId = 1))
    }

    @Test
    fun `finish без номера SIM забирает все сессии`() {
        store.start(subscriptionId = 1, number = null, at = nowMs)
        store.start(subscriptionId = 2, number = null, at = nowMs)

        val finished = store.finish(subscriptionId = -1)

        assertEquals(listOf(1, 2), finished.map { it.subscriptionId }.sorted())
        assertTrue(store.active().isEmpty())
    }
}
