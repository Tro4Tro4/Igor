package com.igor.fridge.ui.compare

import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.openprices.CommunityPrice
import com.igor.fridge.data.openprices.SimilarProductSearch
import com.igor.fridge.domain.prices.PriceSource
import com.igor.fridge.domain.prices.SimilarOffer
import com.igor.fridge.domain.prices.StoreObservation
import com.igor.fridge.domain.prices.descriptionWords
import com.igor.fridge.domain.prices.nameSimilarity
import com.igor.fridge.domain.prices.privateLabelChain
import com.igor.fridge.domain.prices.rankOffers
import com.igor.fridge.domain.prices.storeLabel
import com.igor.fridge.domain.prices.unitPriceCents
import java.time.LocalDate

/** Oltre questo tempo un prezzo della comunita' dice poco del prezzo di oggi. */
const val COMMUNITY_MAX_AGE_DAYS = 365L

/**
 * I prezzi della comunita' utili al confronto: in Italia, in euro, non piu' vecchi di un
 * anno, in un negozio riconoscibile. Un prodotto con codice a barre si paga al pezzo, quindi
 * il prezzo e' per pezzo ed e' anche il costo di un acquisto.
 */
fun communityObservations(prices: List<CommunityPrice>, today: LocalDate): List<StoreObservation> =
    prices.mapNotNull { price ->
        val location = price.location ?: return@mapNotNull null
        if (location.countryCode != null && !location.countryCode.equals("IT", ignoreCase = true)) return@mapNotNull null
        if (price.currency != "EUR") return@mapNotNull null
        if (price.date.isBefore(today.minusDays(COMMUNITY_MAX_AGE_DAYS))) return@mapNotNull null
        val store = storeLabel(location.brand) ?: storeLabel(location.name) ?: return@mapNotNull null
        StoreObservation(
            store = store,
            unitPriceCents = price.priceCents,
            referenceUnit = QuantityUnit.PZ,
            date = price.date,
            source = PriceSource.COMMUNITY,
            totalCents = price.priceCents,
            quantity = 1.0,
            unit = QuantityUnit.PZ,
        )
    }

/**
 * Il prezzo al kg o al litro di un prezzo della comunita', se Open Food Facts conosce il
 * formato del prodotto; altrimenti null (si sa solo quanto costa la confezione).
 */
fun communityUnitPrice(price: CommunityPrice): Pair<Long, QuantityUnit>? {
    val product = price.product ?: return null
    val quantity = product.quantity ?: return null
    val reference = when (product.quantityUnit) {
        QuantityUnit.G -> QuantityUnit.KG
        QuantityUnit.ML -> QuantityUnit.L
        else -> return null
    }
    val cents = unitPriceCents(price.priceCents, quantity, product.quantityUnit) ?: return null
    return cents to reference
}

/**
 * I prezzi della comunita' di prodotti simili a [name], escluso il prodotto [barcode]
 * stesso, con le stesse regole di [communityObservations]. Trovati per categoria non
 * devono somigliare nel nome; trovati per nome si'.
 */
fun communitySimilarOffers(
    name: String,
    barcode: String?,
    result: SimilarProductSearch.Result,
    today: LocalDate,
): List<SimilarOffer> {
    val wanted = descriptionWords(name)
    return result.prices.mapNotNull { price ->
        val product = price.product ?: return@mapNotNull null
        if (product.code == barcode) return@mapNotNull null
        val store = communityObservations(listOf(price), today).firstOrNull()?.store ?: return@mapNotNull null
        val productName = product.name ?: price.productName ?: return@mapNotNull null
        val similarity = nameSimilarity(wanted, descriptionWords(productName))
        if (!result.byCategory && similarity <= 0.0) return@mapNotNull null
        val (unitCents, reference) = communityUnitPrice(price) ?: (price.priceCents to QuantityUnit.PZ)
        SimilarOffer(
            productName = productName,
            brands = product.brands,
            privateLabel = privateLabelChain(product.brands)?.name,
            store = store,
            priceCents = price.priceCents,
            unitPriceCents = unitCents,
            referenceUnit = reference,
            date = price.date,
            source = PriceSource.COMMUNITY,
            similarity = similarity,
        )
    }.let(::rankOffers)
}
