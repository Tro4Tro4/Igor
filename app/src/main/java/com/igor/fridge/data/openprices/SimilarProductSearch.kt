package com.igor.fridge.data.openprices

import com.igor.fridge.domain.prices.descriptionWords
import com.igor.fridge.domain.prices.nameSimilarity

/**
 * Cerca su Open Prices i prezzi di prodotti simili a uno dato.
 *
 * Con il codice a barre si parte dalla categoria di Open Food Facts del prodotto, la piu'
 * specifica ("latte parzialmente scremato", non "bevande"): e' la somiglianza piu'
 * affidabile. Senza codice, o se la categoria non ha altri prodotti, si cercano i prodotti
 * il cui nome comincia come il suo.
 */
class SimilarProductSearch(private val client: OpenPricesClient) {

    /** I prezzi trovati e se vengono dalla categoria: allora non serve che il nome somigli. */
    data class Result(val prices: List<CommunityPrice>, val byCategory: Boolean)

    suspend fun find(name: String, barcode: String?): Result {
        if (barcode != null) {
            val categories = client.product(barcode)?.categories.orEmpty()
                .filter { it.startsWith("en:") }
            for (category in categories.asReversed().take(CATEGORY_LEVELS)) {
                val prices = client.pricesInCategory(category)
                val others = prices.mapNotNull { it.product?.code }.filter { it != barcode }.distinct()
                if (others.size >= MIN_OTHER_PRODUCTS) return Result(prices, byCategory = true)
            }
        }
        val wanted = descriptionWords(name)
        val head = wanted.firstOrNull() ?: return Result(emptyList(), byCategory = false)
        val codes = client.productsNamed(head)
            .filter { it.code != barcode }
            .map { it to nameSimilarity(wanted, descriptionWords(it.name.orEmpty())) }
            .filter { (_, similarity) -> similarity > 0.0 }
            .sortedByDescending { (_, similarity) -> similarity }
            .take(MAX_CANDIDATES)
            .map { (product, _) -> product.code }
        return Result(client.pricesOf(codes), byCategory = false)
    }

    private companion object {
        /** La categoria piu' specifica e, se e' quasi vuota, quella che la contiene. */
        const val CATEGORY_LEVELS = 2
        const val MIN_OTHER_PRODUCTS = 2

        /** I codici viaggiano nell'indirizzo: abbastanza per scegliere, non troppi. */
        const val MAX_CANDIDATES = 20
    }
}
