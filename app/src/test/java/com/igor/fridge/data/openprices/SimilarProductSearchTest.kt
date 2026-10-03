package com.igor.fridge.data.openprices

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SimilarProductSearchTest {

    private val transport = FakeTransport()
    private val search = SimilarProductSearch(OpenPricesClient(transport))

    private val milk = HttpResponse(
        200,
        """{"code":"800","product_name":"Latte PS Granarolo",
           "categories_tags":["en:dairies","en:milks","en:semi-skimmed-milks"]}""",
    )

    private fun pricesOf(vararg codes: String) = HttpResponse(
        200,
        codes.joinToString(",", prefix = """{"items":[""", postfix = "]}") { code ->
            """{"price":1.2,"date":"2026-09-30","product":{"code":"$code","product_name":"Latte $code"}}"""
        },
    )

    private fun urls() = transport.requests.map { it.url.substringAfter("/api/v1").substringBefore("&currency") }

    @Test
    fun `con il codice a barre cerca nella categoria piu' specifica`() = runTest {
        transport.respond("GET", "/products/code/800", milk)
        transport.respond("GET", "/prices", pricesOf("800", "801", "802"))

        val result = search.find("Latte PS Granarolo", "800")

        assertTrue(result.byCategory)
        assertEquals(3, result.prices.size)
        assertEquals(
            listOf("/products/code/800", "/prices?product__categories_tags__contains=en%3Asemi-skimmed-milks"),
            urls(),
        )
    }

    @Test
    fun `se la categoria non ha altri prodotti sale di un livello e poi cerca per nome`() = runTest {
        transport.respond("GET", "/products/code/800", milk)
        transport.respond("GET", "/prices", pricesOf("800"), pricesOf("800", "801"), pricesOf("805"))
        transport.respond(
            "GET",
            "/products",
            HttpResponse(
                200,
                """{"items":[
                  {"code":"806","product_name":"Caffe latte"},
                  {"code":"800","product_name":"Latte PS Granarolo"},
                  {"code":"805","product_name":"Latte parzialmente scremato Esselunga"}
                ]}""",
            ),
        )

        val result = search.find("Latte PS Granarolo", "800")

        assertFalse(result.byCategory)
        assertEquals(listOf("805"), result.prices.mapNotNull { it.product?.code })
        assertEquals(
            listOf(
                "/products/code/800",
                "/prices?product__categories_tags__contains=en%3Asemi-skimmed-milks",
                "/prices?product__categories_tags__contains=en%3Amilks",
                "/products?product_name__like=latte&price_count__gte=1&order_by=-price_count&size=50",
                // Solo i candidati che somigliano, e non il prodotto stesso.
                "/prices?product_code__in=805",
            ),
            urls(),
        )
    }

    @Test
    fun `senza codice a barre cerca per nome`() = runTest {
        transport.respond("GET", "/products", HttpResponse(200, """{"items":[]}"""))

        val result = search.find("Mozzarella", null)

        assertTrue(result.prices.isEmpty())
        // Nessun candidato: nessuna richiesta di prezzi.
        assertEquals(listOf("/products?product_name__like=mozzarella&price_count__gte=1&order_by=-price_count&size=50"), urls())
    }
}
