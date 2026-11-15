package ru.ordercontrol.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadSupportTest {

    @Test
    fun `парсит Content-Range с общим размером`() {
        val range = DownloadSupport.parseContentRange("bytes 100-999/1000")
        assertEquals(100L, range?.start)
        assertEquals(999L, range?.end)
        assertEquals(1000L, range?.total)
    }

    @Test
    fun `парсит Content-Range без известного размера`() {
        val range = DownloadSupport.parseContentRange("bytes 0-499/*")
        assertEquals(0L, range?.start)
        assertEquals(499L, range?.end)
        assertNull(range?.total)
    }

    @Test
    fun `не понимает мусорный заголовок`() {
        assertNull(DownloadSupport.parseContentRange(null))
        assertNull(DownloadSupport.parseContentRange(""))
        assertNull(DownloadSupport.parseContentRange("bytes abc-def/100"))
        assertNull(DownloadSupport.parseContentRange("items 1-2/3"))
    }

    @Test
    fun `повторяем только временные ошибки`() {
        assertTrue(DownloadSupport.isRetryableStatus(500))
        assertTrue(DownloadSupport.isRetryableStatus(502))
        assertTrue(DownloadSupport.isRetryableStatus(429))
        assertTrue(DownloadSupport.isRetryableStatus(408))
        assertTrue(!DownloadSupport.isRetryableStatus(404))
        assertTrue(!DownloadSupport.isRetryableStatus(403))
    }

    @Test
    fun `процент считается от размера файла`() {
        assertEquals(0, DownloadSupport.percentOf(0, 1000))
        assertEquals(50, DownloadSupport.percentOf(500, 1000))
        assertEquals(100, DownloadSupport.percentOf(1000, 1000))
        assertEquals(0, DownloadSupport.percentOf(500, 0))
        assertEquals(100, DownloadSupport.percentOf(1200, 1000))
    }
}
