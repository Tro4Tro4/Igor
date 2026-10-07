package com.igor.fridge.data.openfoodfacts

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.openprices.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OpenFoodFactsClientTest {
    private val code = "3017620422003"
    private val requests = mutableListOf<HttpRequest>()
    private var response = HttpResponse(200, """{"product":{"code":"3017620422003","product_name_it":"Latte","product_name":"Milk","brands":"Marca"}}""")
    private var now = 0L
    private val client = OpenFoodFactsClient(object : HttpTransport {
        override suspend fun execute(request: HttpRequest): HttpResponse {
            requests += request
            return response
        }
    }, "Igor/test (Android)", clock = { now })

    @Test fun `preferisce italiano e limita i campi senza inviare dati personali`() = runTest {
        val result = client.product(code)!!
        assertEquals("Latte", result.name)
        assertEquals("Marca", result.brand)
        assertEquals(FoodCategory.LATTE_PANNA, result.category)
        assertTrue(requests.single().url.contains("fields=code,product_name_it,product_name,brands"))
        assertEquals("Igor/test (Android)", requests.single().headers["User-Agent"])
        assertNull(requests.single().body)
    }
    @Test fun `codici non validi non contattano la rete`() = runTest {
        assertNull(client.product("ABC123")); assertNull(client.product("3017620422004"))
        assertTrue(requests.isEmpty())
    }
    @Test fun `assenza e risposta incompleta sono distinte`() = runTest {
        response = HttpResponse(404, "")
        assertNull(client.product(code))
        response = HttpResponse(200, """{"product":{"code":"3017620422003","brands":"Marca"}}""")
        assertEquals("", client.product(code)!!.name)
    }
    @Test fun `nome generico usa categoria nota senza forzare tassonomie sconosciute`() = runTest {
        response = HttpResponse(200, """{"product":{"code":"3017620422003","product_name":"Original","categories_tags":["en:milks"]}}""")
        assertEquals(FoodCategory.LATTE_PANNA, client.product(code)!!.category)
        response = HttpResponse(200, """{"product":{"code":"3017620422003","product_name":"Original","categories_tags":["en:unknown"]}}""")
        assertEquals(FoodCategory.ALTRO, client.product(code)!!.category)
    }
    @Test fun `codice diverso e json malformato sono errori`() = runTest {
        for (body in listOf("{}", "non json", """{"product":{"code":"123","product_name":"Altro"}}""")) {
            response = HttpResponse(200, body)
            try { client.product(code); fail("Atteso errore") } catch (_: Exception) { }
        }
    }
    @Test fun `limite locale e remoto non diventano prodotto assente`() = runTest {
        repeat(15) { client.product(code) }
        try { client.product(code); fail("Atteso limite") }
        catch (e: ProductLookupException) { assertTrue(e.rateLimited) }
        assertEquals(15, requests.size)
        now = 60_000
        assertNotNull(client.product(code))
        response = HttpResponse(429, "")
        try { client.product(code); fail("Atteso limite") }
        catch (e: ProductLookupException) { assertTrue(e.rateLimited) }
    }
}
