package com.igor.fridge.data.openfoodfacts

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.openprices.HttpRequest
import com.igor.fridge.data.openprices.HttpTransport
import com.igor.fridge.domain.guessCategory
import com.igor.fridge.domain.prices.normalizeGtin
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.*
import java.io.IOException

data class BarcodeProduct(val name: String, val brand: String, val category: FoodCategory)

class ProductLookupException(val rateLimited: Boolean = false) : IOException()

/** Lettura puntuale del catalogo ODbL: nessun dato personale viene inviato. */
class OpenFoodFactsClient(
    private val transport: HttpTransport,
    private val userAgent: String,
    private val baseUrl: String = "https://world.openfoodfacts.org",
    private val clock: () -> Long = { System.nanoTime() / 1_000_000 },
) {
    private val mutex = Mutex()
    private val requests = ArrayDeque<Long>()

    suspend fun product(barcode: String): BarcodeProduct? {
        val code = normalizeGtin(barcode) ?: return null
        // Non accodiamo richieste automatiche: oltre il limite si prosegue a mano.
        mutex.withLock {
            val now = clock()
            while (requests.isNotEmpty() && now - requests.first() >= 60_000) requests.removeFirst()
            if (requests.size >= 15) throw ProductLookupException(rateLimited = true)
            requests.addLast(now)
        }
        val response = transport.execute(HttpRequest(
            "GET", "$baseUrl/api/v3/product/$code?lc=it&fields=code,product_name_it,product_name,brands,categories_tags",
            mapOf("User-Agent" to userAgent, "Accept" to "application/json"),
        ))
        if (response.code == 404) return null
        if (response.code !in 200..299) throw ProductLookupException(response.code == 429)
        val root = Json.parseToJsonElement(response.body).jsonObject
        val product = root["product"] as? JsonObject ?: throw ProductLookupException()
        fun field(key: String) = (product[key] as? JsonPrimitive)?.contentOrNull?.trim().orEmpty()
        // Alcuni UPC sono restituiti come EAN con zero iniziale.
        if (field("code").trimStart('0') != code.trimStart('0')) throw ProductLookupException()
        val name = field("product_name_it").ifBlank { field("product_name") }
        val guessed = guessCategory(name)
        val tags = (product["categories_tags"] as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val category = if (guessed != FoodCategory.ALTRO) guessed else
            tags.asReversed().firstNotNullOfOrNull { CATEGORIES[it] } ?: FoodCategory.ALTRO
        return BarcodeProduct(name, field("brands"), category)
    }

    companion object {
        // Solo equivalenze note: le categorie sconosciute non diventano classificazioni certe.
        private val CATEGORIES = mapOf(
            "en:milks" to FoodCategory.LATTE_PANNA,
            "en:yogurts" to FoodCategory.YOGURT,
            "en:cheeses" to FoodCategory.LATTICINI,
            "en:eggs" to FoodCategory.UOVA,
            "en:breads" to FoodCategory.PANE,
            "en:pastas" to FoodCategory.PASTA_SECCA,
            "en:rices" to FoodCategory.RISO_CEREALI,
            "en:biscuits" to FoodCategory.BISCOTTI,
            "en:ice-creams" to FoodCategory.GELATI,
            "en:frozen-foods" to FoodCategory.SURGELATI,
            "en:waters" to FoodCategory.ACQUA,
            "en:plant-milks" to FoodCategory.ALTERNATIVE_VEGETALI,
        )
    }
}
