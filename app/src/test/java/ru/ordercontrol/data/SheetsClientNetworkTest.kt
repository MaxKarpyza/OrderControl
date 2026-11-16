package ru.ordercontrol.data

import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Сеть отвалилась: клиент не должен бросать исключение наружу.
 *
 * Раньше обрыв связи на списке вкладок ронял приложение: исключение
 * доходило до корутины экрана и убивало процесс.
 */
class SheetsClientNetworkTest {

    private val client = OfflineSheetsClient()

    @Test
    fun `tab names are empty without network`() = runTest {
        assertEquals(emptyList<String>(), client.fetchAllTabNames())
    }

    @Test
    fun `tab csv is null without network`() = runTest {
        assertNull(client.fetchTabCsv("27.10.26"))
    }

    @Test
    fun `sheet index reports no network`() = runTest {
        val result = client.fetchTodayAndTomorrow(LocalDate.of(2026, 10, 27))

        assertTrue(result is SheetFetchResult.NoNetwork)
    }
}

/** Клиент, у которого сеть не работает. */
private class OfflineSheetsClient : SheetsClient() {
    override fun download(requestUrl: String): String? = throw IOException("нет сети")
}
