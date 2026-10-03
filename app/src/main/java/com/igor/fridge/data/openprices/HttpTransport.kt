package com.igor.fridge.data.openprices

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI

data class HttpRequest(
    val method: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: ByteArray? = null,
) {
    // ByteArray non ha un equals per contenuto: i test confrontano le richieste.
    override fun equals(other: Any?): Boolean = other is HttpRequest && method == other.method &&
        url == other.url && headers == other.headers && body.contentEqualsNullable(other.body)

    override fun hashCode(): Int = listOf(method, url, headers, body?.contentHashCode()).hashCode()

    val bodyText: String? get() = body?.toString(Charsets.UTF_8)
}

private fun ByteArray?.contentEqualsNullable(other: ByteArray?): Boolean =
    if (this == null) other == null else other != null && contentEquals(other)

data class HttpResponse(val code: Int, val body: String) {
    val isSuccessful: Boolean get() = code in 200..299
}

/** Il canale verso la rete: un'interfaccia, perche' i test parlino con un server finto. */
interface HttpTransport {
    /** @throws IOException se la rete non risponde. */
    suspend fun execute(request: HttpRequest): HttpResponse
}

/** HTTP con le classi della piattaforma: per poche richieste non serve una libreria. */
class UrlConnectionTransport(
    private val timeoutMillis: Int = 15_000,
) : HttpTransport {

    /**
     * La lettura di [HttpURLConnection] e' bloccante e non si accorge che la coroutine e'
     * stata annullata: chi lascia la schermata la terrebbe aperta fino al timeout. Quando
     * l'attesa viene annullata si chiude la connessione, e la lettura bloccata finisce.
     */
    override suspend fun execute(request: HttpRequest): HttpResponse = coroutineScope {
        val connection = URI(request.url).toURL().openConnection() as HttpURLConnection
        val call = async(Dispatchers.IO) { exchange(connection, request) }
        try {
            call.await()
        } catch (e: CancellationException) {
            connection.disconnect()
            throw e
        }
    }

    private fun exchange(connection: HttpURLConnection, request: HttpRequest): HttpResponse {
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            request.headers.forEach { (name, value) -> connection.setRequestProperty(name, value) }
            val body = request.body
            if (body != null) {
                connection.doOutput = true
                connection.setFixedLengthStreamingMode(body.size)
                connection.outputStream.use { it.write(body) }
            }
            val code = connection.responseCode
            val stream = if (code < 400) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(code, text)
        } finally {
            connection.disconnect()
        }
    }
}
