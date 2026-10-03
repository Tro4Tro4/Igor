package com.igor.fridge.ui

import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.openprices.CommunityLocation
import com.igor.fridge.data.openprices.CommunityPrice
import com.igor.fridge.data.openprices.CommunityProduct
import com.igor.fridge.data.openprices.SimilarProductSearch
import com.igor.fridge.ui.compare.communitySimilarOffers
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class SimilarOffersTest {

    private val today = LocalDate.of(2026, 10, 3)

    private fun price(
        code: String,
        name: String,
        cents: Long,
        brands: String? = null,
        quantity: Double? = null,
        unit: QuantityUnit? = null,
        country: String = "IT",
    ) = CommunityPrice(
        priceCents = cents,
        currency = "EUR",
        date = today,
        isDiscounted = false,
        location = CommunityLocation(1, "NODE", "Esselunga", brand = "Esselunga", countryCode = country),
        productName = name,
        product = CommunityProduct(code, name, brands, quantity, unit),
    )

    @Test
    fun `i prezzi della comunita' diventano offerte al litro, senza il prodotto stesso`() {
        val result = SimilarProductSearch.Result(
            listOf(
                price("800", "Latte PS Granarolo", 179, quantity = 1000.0, unit = QuantityUnit.ML),
                price("801", "Latte PS", 89, brands = "Esselunga", quantity = 500.0, unit = QuantityUnit.ML),
                price("802", "Latte PS Parmalat", 165, quantity = 1000.0, unit = QuantityUnit.ML),
                price("803", "Latte senza formato", 99),
                price("804", "Latte francese", 50, quantity = 1000.0, unit = QuantityUnit.ML, country = "FR"),
            ),
            byCategory = true,
        )

        val offers = communitySimilarOffers("Latte PS Granarolo", "800", result, today)

        assertEquals(listOf("Latte PS Parmalat", "Latte PS", "Latte senza formato"), offers.map { it.productName })
        val storeBrand = offers[1]
        assertEquals(178L, storeBrand.unitPriceCents)
        assertEquals(QuantityUnit.L, storeBrand.referenceUnit)
        assertEquals("Esselunga", storeBrand.privateLabel)
        assertEquals(QuantityUnit.PZ, offers[2].referenceUnit)
    }

    @Test
    fun `trovati per nome devono somigliare, trovati per categoria no`() {
        val prices = listOf(price("900", "Bevanda di avena", 199, quantity = 1000.0, unit = QuantityUnit.ML))

        assertEquals(
            1,
            communitySimilarOffers("Latte PS", null, SimilarProductSearch.Result(prices, byCategory = true), today).size,
        )
        assertEquals(
            0,
            communitySimilarOffers("Latte PS", null, SimilarProductSearch.Result(prices, byCategory = false), today).size,
        )
    }
}
