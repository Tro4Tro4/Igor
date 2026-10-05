package com.igor.fridge.data.onlineprices

import com.igor.fridge.data.local.OnlineOffer
import com.igor.fridge.data.local.OnlineSource
import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.net.URI
import java.time.Instant
import java.time.LocalDate

object OnlinePricesJson {
    private val domains = mapOf("carrefour" to "www.carrefour.it", "conad" to "spesaonline.conad.it", "esselunga" to "spesaonline.esselunga.it", "tigros" to "www.tigros.it", "lidl" to "www.lidl.it", "eurospin" to "www.eurospin.it")
    private fun envelope(body: String): JsonObject = Json.parseToJsonElement(body).jsonObject.also {
        require(it["schema_version"]?.jsonPrimitive?.int == 1)
        require(it["catalog_version"]!!.jsonPrimitive.long >= 0)
        Instant.parse(it.string("generated_at"))
    }
    fun parseOffers(body: String, requestedPostcode: String): OnlinePage {
        require(requestedPostcode.matches(Regex("[0-9]{5}")))
        val root = envelope(body)
        require(root.string("postcode") == requestedPostcode)
        val items = root["items"]!!.jsonArray.map { element ->
            val o = element.jsonObject
            val source = o.string("source")
            val uri = URI(o.string("source_url"))
            require(uri.scheme == "https" && uri.host == domains[source] && uri.userInfo == null && uri.port in listOf(-1,443))
            val pack = o["pack"]?.takeUnless { it == JsonNull }?.jsonObject
            val amount = pack?.string("amount")?.let(::BigDecimal)
            val unit = pack?.string("unit")
            val count = pack?.get("pack_count")?.jsonPrimitive?.int ?: 1
            require(amount == null || amount > BigDecimal.ZERO)
            require(pack == null || unit in listOf("KG","L","PZ"))
            require(count >= 1)
            val cents = o["pack_price_cents"]!!.jsonPrimitive.long
            require(cents > 0 && o.string("currency") == "EUR")
            val scope = o.string("scope")
            val postcode = o.optional("postcode")
            require(scope in listOf("generic","postcode"))
            require(if (scope == "generic") postcode == null else postcode == requestedPostcode)
            val gtin = o.optional("gtin")
            val verified = o["gtin_verified"]!!.jsonPrimitive.boolean
            val sku = o.string("source_sku")
            val productId = o.string("product_id")
            require(sku.isNotBlank())
            require(!verified || gtin != null && normalizedGtin(gtin) != null)
            require(productId == if (verified) "gtin:${normalizedGtin(gtin!!)}" else "$source:$sku")
            val availability = o.string("availability")
            val condition = o.string("condition")
            require(availability in listOf("available","unavailable","unknown"))
            require(condition in listOf("ordinary","loyalty","coupon","multi_buy","unknown"))
            OnlineOffer(o.string("offer_id"),requestedPostcode,productId,source,sku,o.string("name"),o.optional("brand"),gtin,verified,amount,unit,count,cents,Instant.parse(o.string("observed_at")),uri.toString(),scope,postcode,availability,condition,o.optional("valid_until")?.let(LocalDate::parse)).also {
                require(it.offerId.isNotBlank() && it.name.isNotBlank())
            }
        }
        val next = root["next_offset"]?.takeUnless { it == JsonNull }?.jsonPrimitive?.int
        require(next == null || next >= 0)
        return OnlinePage(items,next,root["catalog_version"]!!.jsonPrimitive.long)
    }
    fun parseSources(body: String): List<OnlineSource> = envelope(body)["items"]!!.jsonArray.map {
        val s = it.jsonObject
        val id = s.string("source")
        require(id in domains && s.string("status") in listOf("candidate","active","suspended","unavailable"))
        OnlineSource(id,s.string("status"),s["priority"]!!.jsonPrimitive.int,s.optional("last_successful_at")?.let(Instant::parse),s.optional("limitation").orEmpty())
    }
    fun normalizedGtin(text: String): String? {
        if (!text.matches(Regex("[0-9]{8}|[0-9]{12,14}"))) return null
        val sum = text.dropLast(1).reversed().mapIndexed { i,c -> c.digitToInt() * if (i % 2 == 0) 3 else 1 }.sum()
        return text.trimStart('0').ifEmpty { "0" }.takeIf { (sum + text.last().digitToInt()) % 10 == 0 }
    }
    private fun JsonObject.string(key: String): String = get(key)!!.jsonPrimitive.content.also { require(get(key) != JsonNull) }
    private fun JsonObject.optional(key: String): String? = get(key)?.takeUnless { it == JsonNull }?.jsonPrimitive?.content
}
