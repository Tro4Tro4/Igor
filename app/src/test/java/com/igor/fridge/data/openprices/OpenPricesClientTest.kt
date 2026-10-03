package com.igor.fridge.data.openprices

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.time.LocalDate

/**
 * Le risposte di esempio seguono i serializer del server Open Prices (PriceFullSerializer,
 * LocationSerializer, login): pagina con items/total, decimali come numeri.
 */
class OpenPricesClientTest {

    private val transport = FakeTransport()
    private val client = OpenPricesClient(transport, boundary = { "XYZ" })

    private val esselunga = CommunityLocation(osmId = 123, osmType = "NODE", name = "Esselunga", city = "Milano")

    private val pricesPage = """
        {"items":[
          {"id":1,"type":"PRODUCT","product_code":"8001234567890","price":1.29,"currency":"EUR",
           "price_is_discounted":false,"date":"2026-09-30","location_osm_id":123,"location_osm_type":"NODE",
           "product":{"code":"8001234567890","product_name":"Latte PS"},
           "location":{"id":7,"type":"OSM","osm_id":123,"osm_type":"NODE","osm_name":"Esselunga",
                       "osm_brand":"Esselunga","osm_address_city":"Milano","osm_address_country_code":"IT","price_count":40}},
          {"id":2,"type":"PRODUCT","product_code":"8001234567890","price":1.19,"currency":"EUR",
           "price_is_discounted":true,"date":"2026-09-01","location":null},
          {"id":3,"price":null,"date":"2026-09-01"}
        ],"page":1,"pages":1,"size":100,"total":3}
    """.trimIndent()

    @Test
    fun `i prezzi di un prodotto si leggono senza account`() = runTest {
        transport.respond("GET", "/prices", HttpResponse(200, pricesPage))

        val prices = client.productPrices("8001234567890")

        val request = transport.requests.single()
        assertEquals("GET", request.method)
        assertEquals(
            "https://prices.openfoodfacts.org/api/v1/prices?product_code=8001234567890&order_by=-date&size=100",
            request.url,
        )
        assertEquals("Igor/0.1.0 (Android)", request.headers["User-Agent"])
        assertNull(request.headers["Authorization"])

        assertEquals(2, prices.size)
        val first = prices.first()
        assertEquals(129L, first.priceCents)
        assertEquals(LocalDate.of(2026, 9, 30), first.date)
        assertEquals("Latte PS", first.productName)
        assertEquals("Esselunga", first.location?.name)
        assertEquals("IT", first.location?.countryCode)
        assertEquals(40, first.location?.priceCount)
        assertTrue(prices[1].isDiscounted)
        assertNull(prices[1].location)
    }

    @Test
    fun `l'accesso manda utente e password come modulo e restituisce il token`() = runTest {
        transport.respond(
            "POST",
            "/auth",
            HttpResponse(200, """{"user_id":"mario","is_moderator":false,"access_token":"abc","token_type":"bearer"}"""),
        )

        val session = client.login(" mario ", "p&ss word")

        assertEquals(OpenPricesSession("mario", "abc"), session)
        val request = transport.requests.single()
        assertEquals("application/x-www-form-urlencoded", request.headers["Content-Type"])
        assertEquals("username=mario&password=p%26ss+word", request.bodyText)
    }

    @Test
    fun `credenziali sbagliate danno un messaggio chiaro`() = runTest {
        transport.respond("POST", "/auth", HttpResponse(401, """{"detail":"Invalid authentication credentials"}"""))

        try {
            client.login("mario", "x")
            fail("doveva fallire")
        } catch (e: OpenPricesException) {
            assertEquals("Utente o password non corretti", e.message)
            assertEquals(401, e.code)
        }
    }

    @Test
    fun `la ricerca dei negozi filtra per nome e citta'`() = runTest {
        transport.respond(
            "GET",
            "/locations",
            HttpResponse(
                200,
                """{"items":[{"id":7,"type":"OSM","osm_id":123,"osm_type":"NODE","osm_name":"Esselunga","osm_address_city":"Milano","price_count":40},
                   {"id":8,"type":"ONLINE","osm_id":null,"website_url":"https://x.it"}],"total":2}""",
            ),
        )

        val found = client.searchLocations("Esselunga", "Milano")

        assertEquals(
            "https://prices.openfoodfacts.org/api/v1/locations?type=OSM&osm_name__like=Esselunga" +
                "&osm_address_city__like=Milano&order_by=-price_count&size=20",
            transport.requests.single().url,
        )
        assertEquals(listOf("Esselunga · Milano"), found.map { it.label })
    }

    @Test
    fun `la prova si carica in multipart con negozio, data e valuta`() = runTest {
        transport.respond("POST", "/proofs/upload", HttpResponse(201, """{"id":55,"type":"RECEIPT"}"""))

        val id = client.uploadReceipt("tok", byteArrayOf(9, 9), esselunga, LocalDate.of(2026, 10, 1), 4, null)

        assertEquals(55L, id)
        val request = transport.requests.single()
        assertEquals("https://prices.openfoodfacts.org/api/v1/proofs/upload?app_name=Igor&app_platform=android", request.url)
        assertEquals("Bearer tok", request.headers["Authorization"])
        assertEquals("multipart/form-data; boundary=XYZ", request.headers["Content-Type"])
        val body = request.body!!.toString(Charsets.ISO_8859_1)
        listOf(
            "name=\"type\"\r\n\r\nRECEIPT\r\n",
            "name=\"location_osm_id\"\r\n\r\n123\r\n",
            "name=\"location_osm_type\"\r\n\r\nNODE\r\n",
            "name=\"date\"\r\n\r\n2026-10-01\r\n",
            "name=\"currency\"\r\n\r\nEUR\r\n",
            "name=\"receipt_price_count\"\r\n\r\n4\r\n",
            "name=\"file\"; filename=\"scontrino.jpg\"\r\nContent-Type: image/jpeg\r\n\r\n\u0009\u0009\r\n--XYZ--\r\n",
        ).forEach { assertTrue(it, body.contains(it)) }
        assertFalse(body.contains("receipt_price_total"))
    }

    @Test
    fun `un prezzo si invia in JSON riferito alla prova`() = runTest {
        transport.respond("POST", "/prices", HttpResponse(201, """{"id":900}"""))

        client.addPrice("tok", 55, esselunga, LocalDate.of(2026, 10, 1), "8001234567890", 129, 2)

        val request = transport.requests.single()
        assertEquals("application/json", request.headers["Content-Type"])
        assertEquals(
            """{"product_code":"8001234567890","price":1.29,"currency":"EUR","date":"2026-10-01",""" +
                """"location_osm_id":123,"location_osm_type":"NODE","proof_id":55,"receipt_quantity":2}""",
            request.bodyText,
        )
    }

    @Test
    fun `gli errori del server e la rete assente diventano messaggi`() = runTest {
        transport.respond("POST", "/prices", HttpResponse(400, """{"proof":["Proof date (2026-10-01) does not match the price date"]}"""))
        try {
            client.addPrice("tok", 55, esselunga, LocalDate.of(2026, 10, 2), "8001234567890", 129, 1)
            fail("doveva fallire")
        } catch (e: OpenPricesException) {
            assertEquals("proof: Proof date (2026-10-01) does not match the price date", e.message)
        }

        transport.offline = true
        try {
            client.productPrices("8001234567890")
            fail("doveva fallire")
        } catch (e: OpenPricesException) {
            assertEquals("Open Prices non raggiungibile: controlla la connessione", e.message)
        }
    }

    @Test
    fun `gli importi vanno col punto e due decimali`() {
        assertEquals("1.29", OpenPricesClient.amount(129))
        assertEquals("0.05", OpenPricesClient.amount(5))
        assertEquals("12.00", OpenPricesClient.amount(1200))
    }

    @Test
    fun `una risposta che non e' JSON diventa un errore leggibile, non un crash`() = runTest {
        transport.respond("GET", "/prices", HttpResponse(200, "<html>Accedi al Wi-Fi dell'hotel</html>"))
        transport.respond("POST", "/auth", HttpResponse(200, "{\"user_id\":"))

        listOf(
            suspend { client.productPrices("8001234567890") },
            suspend { client.login("mario", "x") },
        ).forEach { call ->
            try {
                call()
                fail("doveva fallire")
            } catch (e: OpenPricesException) {
                assertEquals("Risposta non valida da Open Prices", e.message)
            }
        }
    }

    @Test
    fun `un prodotto si legge con formato e categorie, e se manca e' null`() = runTest {
        transport.respond(
            "GET",
            "/products/code/8001234567890",
            HttpResponse(
                200,
                """{"id":5,"code":"8001234567890","product_name":"Latte PS","brands":"Granarolo",
                   "product_quantity":1000,"product_quantity_unit":"ml",
                   "categories_tags":["en:dairies","en:milks","en:semi-skimmed-milks"],"price_count":12}""",
            ),
        )

        val product = client.product("8001234567890")!!

        assertEquals("Latte PS", product.name)
        assertEquals("Granarolo", product.brands)
        assertEquals(1000.0, product.quantity!!, 0.001)
        assertEquals(com.igor.fridge.data.local.QuantityUnit.ML, product.quantityUnit)
        assertEquals("en:semi-skimmed-milks", product.categories.last())
        assertNull(client.product("0000000000000"))
    }

    @Test
    fun `la ricerca di simili chiede prodotti prezzati e prezzi attorno all'Italia`() = runTest {
        transport.respond("GET", "/products", HttpResponse(200, """{"items":[{"code":"1","product_name":"Latte"}]}"""))
        transport.respond("GET", "/prices", HttpResponse(200, """{"items":[]}"""))

        assertEquals(listOf("1"), client.productsNamed("latte").map { it.code })
        client.pricesOf(listOf("1", "2"))
        client.pricesInCategory("en:milks")
        assertTrue(client.pricesOf(emptyList()).isEmpty())

        val urls = transport.requests.map { it.url.substringAfter("/api/v1") }
        assertEquals(
            listOf(
                "/products?product_name__like=latte&price_count__gte=1&order_by=-price_count&size=50",
                "/prices?product_code__in=1%2C2&currency=EUR&lat=42.5&lon=12.5&radius_km=800&order_by=-date&size=100",
                "/prices?product__categories_tags__contains=en%3Amilks&currency=EUR&lat=42.5&lon=12.5&radius_km=800&order_by=-date&size=100",
            ),
            urls,
        )
    }

    @Test
    fun `il formato in chili o centilitri diventa grammi o millilitri`() = runTest {
        transport.respond(
            "GET",
            "/products",
            HttpResponse(
                200,
                """{"items":[
                  {"code":"1","product_quantity":1,"product_quantity_unit":"kg"},
                  {"code":"2","product_quantity":75,"product_quantity_unit":"cl"},
                  {"code":"3","product_quantity":6,"product_quantity_unit":"pz"},
                  {"code":"4","product_quantity":0}
                ]}""",
            ),
        )

        val products = client.productsNamed("x")

        assertEquals(listOf(1000.0, 750.0, null, null), products.map { it.quantity })
        assertEquals(
            listOf(com.igor.fridge.data.local.QuantityUnit.G, com.igor.fridge.data.local.QuantityUnit.ML, null, null),
            products.map { it.quantityUnit },
        )
    }
}
