package com.igor.fridge.data.openprices

import com.igor.fridge.data.local.QuantityUnit
import java.time.LocalDate

/** Un negozio conosciuto da Open Prices, identificato come oggetto OpenStreetMap. */
data class CommunityLocation(
    val osmId: Long,
    val osmType: String,
    val name: String,
    val brand: String? = null,
    val city: String? = null,
    val countryCode: String? = null,
    val priceCount: Int = 0,
) {
    /** "Esselunga · Milano" */
    val label: String get() = listOfNotNull(name, city).joinToString(" · ")
}

/** Un prezzo della comunita' per un prodotto (per pezzo: e' identificato dal codice a barre). */
data class CommunityPrice(
    val priceCents: Long,
    val currency: String,
    val date: LocalDate,
    val isDiscounted: Boolean,
    val location: CommunityLocation?,
    val productName: String?,
    /** Il prodotto come lo descrive Open Food Facts, se la risposta lo include. */
    val product: CommunityProduct? = null,
)

/**
 * Un prodotto di Open Food Facts noto a Open Prices. [quantity] e' il contenuto netto in
 * [quantityUnit] (grammi o millilitri): permette il prezzo al kg o al litro.
 * [categories] sono i tag di categoria ("en:semi-skimmed-milks"), dal generico allo specifico.
 */
data class CommunityProduct(
    val code: String,
    val name: String?,
    val brands: String?,
    val quantity: Double? = null,
    val quantityUnit: QuantityUnit? = null,
    val categories: List<String> = emptyList(),
)

data class OpenPricesSession(val userId: String, val token: String)

/** Un errore di Open Prices, con un messaggio da mostrare. */
class OpenPricesException(message: String, val code: Int? = null) : Exception(message) {
    /** Il server non riconosce piu' l'accesso: token scaduto o revocato. */
    val isUnauthorized: Boolean get() = code == 401 || code == 403
}
