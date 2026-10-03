package com.igor.fridge.data.openprices

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.IOException
import java.math.BigDecimal
import java.net.URLEncoder
import java.time.LocalDate
import java.util.Locale
import java.util.UUID

/**
 * Client dell'API di Open Prices (https://prices.openfoodfacts.org/api/v1), database aperto
 * di prezzi raccolti dalla comunita' di Open Food Facts, con licenza ODbL.
 *
 * Leggere non richiede un account. Contribuire si'; il server chiede inoltre per ogni
 * prezzo una prova (la foto dello scontrino) e un negozio identificato come oggetto
 * OpenStreetMap. Le richieste che scrivono portano `app_name`, come chiede il progetto per
 * attribuire la fonte dei contributi; ogni richiesta porta uno User-Agent con il nome
 * dell'app, come chiede la politica d'uso delle API di Open Food Facts.
 */
class OpenPricesClient(
    private val transport: HttpTransport,
    private val baseUrl: String = "https://prices.openfoodfacts.org/api/v1",
    private val userAgent: String = "Igor/0.1.0 (Android)",
    private val boundary: () -> String = { "igor-${UUID.randomUUID()}" },
) {

    /** Gli ultimi prezzi registrati per un codice a barre, dal piu' recente. */
    suspend fun productPrices(barcode: String, size: Int = 100): List<CommunityPrice> {
        val url = "$baseUrl/prices?product_code=${encode(barcode)}&order_by=-date&size=$size"
        return OpenPricesJson.parsePrices(send(HttpRequest("GET", url, baseHeaders())).body)
    }

    /** Accesso con utente (non l'email) e password di Open Food Facts. */
    suspend fun login(username: String, password: String): OpenPricesSession {
        val form = "username=${encode(username.trim())}&password=${encode(password)}"
        val response = send(
            HttpRequest(
                method = "POST",
                url = "$baseUrl/auth",
                headers = baseHeaders() + ("Content-Type" to "application/x-www-form-urlencoded"),
                body = form.toByteArray(Charsets.UTF_8),
            ),
            failure = { code -> if (code == 401) "Utente o password non corretti" else null },
        )
        return OpenPricesJson.parseSession(response.body)
    }

    /**
     * I negozi gia' noti a Open Prices il cui nome contiene [name] (e la citta' [city], se
     * indicata), dai piu' usati.
     */
    suspend fun searchLocations(name: String, city: String?): List<CommunityLocation> {
        val query = buildString {
            append("type=OSM&osm_name__like=").append(encode(name.trim()))
            city?.trim()?.takeIf { it.isNotEmpty() }?.let { append("&osm_address_city__like=").append(encode(it)) }
            append("&order_by=-price_count&size=20")
        }
        return OpenPricesJson.parseLocations(send(HttpRequest("GET", "$baseUrl/locations?$query", baseHeaders())).body)
    }

    /**
     * Carica la foto dello scontrino come prova.
     * @return l'identificativo della prova, da indicare in ogni prezzo.
     */
    suspend fun uploadReceipt(
        token: String,
        jpeg: ByteArray,
        location: CommunityLocation,
        date: LocalDate,
        priceCount: Int,
        totalCents: Long?,
    ): Long {
        val separator = boundary()
        val fields = buildList {
            add("type" to "RECEIPT")
            add("location_osm_id" to location.osmId.toString())
            add("location_osm_type" to location.osmType)
            add("date" to date.toString())
            add("currency" to CURRENCY)
            add("receipt_price_count" to priceCount.toString())
            totalCents?.let { add("receipt_price_total" to amount(it)) }
        }
        val response = send(
            HttpRequest(
                method = "POST",
                url = "$baseUrl/proofs/upload?$APP_PARAMS",
                headers = authHeaders(token) + ("Content-Type" to "multipart/form-data; boundary=$separator"),
                body = multipart(separator, fields, fileField = "file", fileName = "scontrino.jpg", file = jpeg),
            ),
        )
        return OpenPricesJson.parseId(response.body)
    }

    /**
     * Aggiunge il prezzo di un prodotto comprato, riferito alla prova dello scontrino.
     * [priceCents] e' il prezzo di un pezzo; [quantity] quanti pezzi.
     */
    suspend fun addPrice(
        token: String,
        proofId: Long,
        location: CommunityLocation,
        date: LocalDate,
        barcode: String,
        priceCents: Long,
        quantity: Int,
    ): Long {
        // buildJsonObject fa l'escape di ogni valore: nessun testo puo' alterare il corpo.
        val body = buildJsonObject {
            put("product_code", barcode)
            put("price", BigDecimal(amount(priceCents)))
            put("currency", CURRENCY)
            put("date", date.toString())
            put("location_osm_id", location.osmId)
            put("location_osm_type", location.osmType)
            put("proof_id", proofId)
            put("receipt_quantity", quantity)
        }.toString()
        val response = send(
            HttpRequest(
                method = "POST",
                url = "$baseUrl/prices?$APP_PARAMS",
                headers = authHeaders(token) + ("Content-Type" to "application/json"),
                body = body.toByteArray(Charsets.UTF_8),
            ),
        )
        return OpenPricesJson.parseId(response.body)
    }

    private suspend fun send(
        request: HttpRequest,
        failure: (Int) -> String? = { null },
    ): HttpResponse {
        val response = try {
            transport.execute(request)
        } catch (e: IOException) {
            throw OpenPricesException("Open Prices non raggiungibile: controlla la connessione")
        }
        if (!response.isSuccessful) {
            val message = failure(response.code)
                ?: OpenPricesJson.errorMessage(response.body)
                ?: "Errore di Open Prices (${response.code})"
            throw OpenPricesException(message, response.code)
        }
        return response
    }

    private fun baseHeaders(): Map<String, String> =
        mapOf("User-Agent" to userAgent, "Accept" to "application/json")

    private fun authHeaders(token: String): Map<String, String> =
        baseHeaders() + ("Authorization" to "Bearer $token")

    companion object {
        private const val CURRENCY = "EUR"
        private const val APP_PARAMS = "app_name=Igor&app_platform=android"

        private fun encode(value: String): String = URLEncoder.encode(value, "UTF-8")

        /** 129 centesimi diventano "1.29": il server vuole il punto decimale. */
        internal fun amount(cents: Long): String = String.format(Locale.ROOT, "%d.%02d", cents / 100, cents % 100)

        internal fun multipart(
            boundary: String,
            fields: List<Pair<String, String>>,
            fileField: String,
            fileName: String,
            file: ByteArray,
        ): ByteArray {
            val crlf = "\r\n"
            val head = buildString {
                fields.forEach { (name, value) ->
                    append("--").append(boundary).append(crlf)
                    append("Content-Disposition: form-data; name=\"").append(name).append('"').append(crlf)
                    append(crlf).append(value).append(crlf)
                }
                append("--").append(boundary).append(crlf)
                append("Content-Disposition: form-data; name=\"").append(fileField)
                append("\"; filename=\"").append(fileName).append('"').append(crlf)
                append("Content-Type: image/jpeg").append(crlf).append(crlf)
            }
            val tail = "$crlf--$boundary--$crlf"
            return head.toByteArray(Charsets.UTF_8) + file + tail.toByteArray(Charsets.UTF_8)
        }
    }
}
