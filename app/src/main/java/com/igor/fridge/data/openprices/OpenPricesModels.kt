package com.igor.fridge.data.openprices

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
)

data class OpenPricesSession(val userId: String, val token: String)

/** Un errore di Open Prices, con un messaggio da mostrare. */
class OpenPricesException(message: String, val code: Int? = null) : Exception(message)
