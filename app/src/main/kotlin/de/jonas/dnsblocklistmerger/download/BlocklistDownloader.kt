package de.jonas.dnsblocklistmerger.download

import de.jonas.dnsblocklistmerger.core.BlocklistParser
import de.jonas.dnsblocklistmerger.core.ParsedList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

sealed interface DownloadResult {
    data class Updated(val parsed: ParsedList, val etag: String?, val lastModified: String?) : DownloadResult
    data object NotModified : DownloadResult
    data class Failed(val message: String) : DownloadResult
}

/** Loads one list with a conditional GET and parses it while streaming. */
class BlocklistDownloader(private val client: OkHttpClient = defaultClient()) {

    suspend fun download(url: String, etag: String?, lastModified: String?): DownloadResult = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(url).apply {
            if (etag != null) header("If-None-Match", etag)
            if (lastModified != null) header("If-Modified-Since", lastModified)
        }.build()
        try {
            client.newCall(request).execute().use { response ->
                when {
                    response.code == 304 -> DownloadResult.NotModified
                    !response.isSuccessful -> DownloadResult.Failed("Serverfehler: HTTP ${response.code}")
                    else -> {
                        var sawContent = false
                        val parsed = response.body.charStream().buffered().useLines { lines ->
                            BlocklistParser.parse(lines.onEach { if (!sawContent && it.isNotBlank()) sawContent = true })
                        }
                        when {
                            !sawContent -> DownloadResult.Failed("Leere Antwort vom Server")
                            parsed.domains.isEmpty() -> DownloadResult.Failed("Keine Domains in der Antwort erkannt")
                            else -> DownloadResult.Updated(parsed, response.header("ETag"), response.header("Last-Modified"))
                        }
                    }
                }
            }
        } catch (e: IOException) {
            DownloadResult.Failed(messageFor(e))
        } catch (e: IllegalArgumentException) {
            DownloadResult.Failed("Ungültige Adresse")
        }
    }

    companion object {
        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .callTimeout(3, TimeUnit.MINUTES)
            .build()

        fun messageFor(e: IOException): String = when (e) {
            is UnknownHostException -> "Server nicht gefunden oder kein Netz"
            is ConnectException -> "Keine Verbindung zum Server"
            is InterruptedIOException -> "Zeitüberschreitung beim Laden"
            is SSLException -> "Sichere Verbindung fehlgeschlagen"
            else -> "Netzwerkfehler: ${e.message ?: e.javaClass.simpleName}"
        }
    }
}
