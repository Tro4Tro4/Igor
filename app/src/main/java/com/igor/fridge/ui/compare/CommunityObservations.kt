package com.igor.fridge.ui.compare

import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.openprices.CommunityPrice
import com.igor.fridge.domain.prices.PriceSource
import com.igor.fridge.domain.prices.StoreObservation
import com.igor.fridge.domain.prices.storeLabel
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
