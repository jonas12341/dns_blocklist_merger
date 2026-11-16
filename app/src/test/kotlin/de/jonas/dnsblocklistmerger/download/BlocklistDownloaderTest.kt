package de.jonas.dnsblocklistmerger.download

import de.jonas.dnsblocklistmerger.core.ListFormat
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.concurrent.TimeUnit

class BlocklistDownloaderTest {
    private lateinit var server: MockWebServer
    private val client = OkHttpClient.Builder().readTimeout(500, TimeUnit.MILLISECONDS).build()
    private val downloader = BlocklistDownloader(client)

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private fun url() = server.url("/list.txt").toString()

    @Test
    fun ok200ParsesAndKeepsValidators() = runBlocking {
        server.enqueue(
            MockResponse().setBody("! comment\n||a.example^\n||b.example^\n")
                .setHeader("ETag", "\"v1\"").setHeader("Last-Modified", "Mon, 16 Nov 2026 05:00:00 GMT"),
        )
        val r = downloader.download(url(), null, null) as DownloadResult.Updated
        assertEquals(setOf("a.example", "b.example"), r.parsed.domains)
        assertEquals(ListFormat.ADBLOCK, r.parsed.format)
        assertEquals("\"v1\"", r.etag)
        assertEquals("Mon, 16 Nov 2026 05:00:00 GMT", r.lastModified)
        val sent = server.takeRequest()
        assertNull(sent.getHeader("If-None-Match"))
    }

    @Test
    fun notModified304SendsConditionalHeaders() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(304))
        val r = downloader.download(url(), "\"v1\"", "Mon, 16 Nov 2026 05:00:00 GMT")
        assertEquals(DownloadResult.NotModified, r)
        val sent = server.takeRequest()
        assertEquals("\"v1\"", sent.getHeader("If-None-Match"))
        assertEquals("Mon, 16 Nov 2026 05:00:00 GMT", sent.getHeader("If-Modified-Since"))
    }

    @Test
    fun serverError503IsAFailureWithMessage() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503))
        val r = downloader.download(url(), null, null) as DownloadResult.Failed
        assertEquals("Serverfehler: HTTP 503", r.message)
    }

    @Test
    fun timeoutIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setBody("||a.example^\n").setBodyDelay(3, TimeUnit.SECONDS))
        val r = downloader.download(url(), null, null) as DownloadResult.Failed
        assertEquals("Zeitüberschreitung beim Laden", r.message)
    }

    @Test
    fun emptyBodyIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setBody(""))
        val r = downloader.download(url(), null, null) as DownloadResult.Failed
        assertEquals("Leere Antwort vom Server", r.message)
    }

    @Test
    fun bodyWithoutDomainsIsAFailure() = runBlocking {
        server.enqueue(MockResponse().setBody("<html><body>Not found</body></html>"))
        val r = downloader.download(url(), null, null) as DownloadResult.Failed
        assertTrue(r.message, r.message.startsWith("Keine Domains"))
    }

    @Test
    fun unreachableServerIsAFailure() = runBlocking {
        val dead = server.url("/x").toString()
        server.shutdown()
        val r = downloader.download(dead, null, null)
        assertTrue(r is DownloadResult.Failed)
    }
}
