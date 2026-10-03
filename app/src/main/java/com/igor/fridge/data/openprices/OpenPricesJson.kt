package com.igor.fridge.data.openprices

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import java.time.LocalDate
import java.time.format.DateTimeParseException
import kotlin.math.roundToLong

/**
 * Lettura delle risposte di Open Prices. I nomi dei campi sono quelli dei serializer del
 * server (api/prices, api/locations, api/auth): pagine con `items` e `total`, decimali come
 * numeri, date "AAAA-MM-GG". Un elemento malformato si salta invece di far fallire la pagina.
 */
internal object OpenPricesJson {

    private val json = Json { ignoreUnknownKeys = true }

    fun parsePrices(body: String): List<CommunityPrice> =
        items(body).mapNotNull { (it as? JsonObject)?.let(::price) }

    fun parseLocations(body: String): List<CommunityLocation> =
        items(body).mapNotNull { (it as? JsonObject)?.let(::location) }

    fun parseSession(body: String): OpenPricesSession {
        val obj = root(body) as? JsonObject
            ?: throw OpenPricesException("Risposta di accesso non valida")
        val user = obj.string("user_id") ?: throw OpenPricesException("Risposta di accesso non valida")
        val token = obj.string("access_token") ?: throw OpenPricesException("Risposta di accesso non valida")
        return OpenPricesSession(user, token)
    }

    /** L'identificativo dell'oggetto creato (prova o prezzo). */
    fun parseId(body: String): Long =
        ((root(body) as? JsonObject)?.get("id") as? JsonPrimitive)?.longOrNull
            ?: throw OpenPricesException("Risposta di Open Prices senza identificativo")

    /** Il messaggio d'errore del server: `detail`, oppure il primo errore di un campo. */
    fun errorMessage(body: String): String? = try {
        when (val element = json.parseToJsonElement(body)) {
            is JsonObject -> element.string("detail") ?: element.entries.firstOrNull()?.let { (field, value) ->
                val text = when (value) {
                    is JsonArray -> value.firstOrNull()?.let { (it as? JsonPrimitive)?.content }
                    is JsonPrimitive -> value.content
                    else -> null
                }
                text?.let { "$field: $it" }
            }
            else -> null
        }
    } catch (e: IllegalArgumentException) {
        null
    }

    /**
     * Il documento JSON, oppure un errore leggibile. Una risposta 200 che non e' JSON (un
     * portale Wi-Fi, un proxy che restituisce HTML, un corpo troncato) altrimenti
     * lancerebbe una SerializationException che nessuno si aspetta e chiuderebbe l'app.
     */
    private fun root(body: String): JsonElement = try {
        json.parseToJsonElement(body)
    } catch (e: IllegalArgumentException) {
        throw OpenPricesException("Risposta non valida da Open Prices")
    }

    private fun items(body: String): List<JsonElement> {
        return when (val root = root(body)) {
            is JsonObject -> (root["items"] as? JsonArray).orEmpty()
            is JsonArray -> root
            else -> emptyList()
        }
    }

    private fun price(obj: JsonObject): CommunityPrice? {
        val value = obj.double("price") ?: return null
        val date = obj.string("date")?.let(::date) ?: return null
        val product = obj["product"] as? JsonObject
        return CommunityPrice(
            priceCents = (value * 100).roundToLong(),
            currency = obj.string("currency") ?: "EUR",
            date = date,
            isDiscounted = (obj["price_is_discounted"] as? JsonPrimitive)?.booleanOrNull ?: false,
            location = (obj["location"] as? JsonObject)?.let(::location),
            productName = product?.string("product_name") ?: obj.string("product_name"),
        )
    }

    private fun location(obj: JsonObject): CommunityLocation? {
        val osmId = obj.long("osm_id") ?: return null
        val osmType = obj.string("osm_type") ?: return null
        return CommunityLocation(
            osmId = osmId,
            osmType = osmType,
            name = obj.string("osm_name") ?: obj.string("osm_brand") ?: return null,
            brand = obj.string("osm_brand"),
            city = obj.string("osm_address_city"),
            countryCode = obj.string("osm_address_country_code"),
            priceCount = obj.long("price_count")?.toInt() ?: 0,
        )
    }

    private fun date(text: String): LocalDate? = try {
        LocalDate.parse(text.take(10))
    } catch (e: DateTimeParseException) {
        null
    }

    private fun JsonObject.string(key: String): String? =
        (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.takeIf { it.isNotBlank() }

    private fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.longOrNull
}
