package com.igor.fridge.data

import com.igor.fridge.data.onlineprices.OnlinePricesJson
import org.junit.Assert.*
import org.junit.Test

internal fun onlineEnvelope(version: Int = 1, cents: String = "249", pack: String = "0.375", gtin: String = "0000080050865") = """
{"schema_version":$version,"catalog_version":1,"generated_at":"2026-10-05T10:00:00Z","postcode":"20125","items":[{"offer_id":"conad:a","product_id":"gtin:80050865","source":"conad","source_sku":"a","gtin":"$gtin","gtin_verified":true,"name":"Mozzarella","brand":"Conad","pack":{"amount":"$pack","unit":"KG","pack_count":3},"pack_price_cents":$cents,"currency":"EUR","observed_at":"2026-10-05T10:00:00Z","source_url":"https://spesaonline.conad.it/p/a","scope":"generic","postcode":null,"availability":"unknown","condition":"ordinary","valid_until":null}],"next_offset":null}
""".trimIndent()

class OnlinePricesJsonTest {
    @Test fun `gtin e decimal conservati senza attribuire cap alla fonte generica`() {
        val page = OnlinePricesJson.parseOffers(onlineEnvelope(), "20125")
        val offer = page.items.single()
        assertEquals("0000080050865", offer.gtin)
        assertEquals("0.375", offer.packAmount.toString())
        assertEquals("20125", offer.requestedPostcode)
        assertNull(offer.postcode)
    }
    @Test fun `dati invalidi non diventano cache valida`() {
        listOf(onlineEnvelope(version=2), onlineEnvelope(cents="0"), onlineEnvelope(pack="NaN"),onlineEnvelope().replace("10:00:00Z","10:00:00"),onlineEnvelope().replace("https://spesaonline.conad.it","https://localhost"),onlineEnvelope().replace("\"EUR\"","\"USD\"")).forEach {
            assertTrue(runCatching { OnlinePricesJson.parseOffers(it,"20125") }.isFailure)
        }
        assertTrue(runCatching { OnlinePricesJson.parseOffers(onlineEnvelope(),"20126") }.isFailure)
    }
}
