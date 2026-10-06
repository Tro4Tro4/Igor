package com.igor.fridge.data

import com.igor.fridge.data.onlineprices.*
import com.igor.fridge.data.openprices.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OnlinePricesClientTest {
    @Test fun `chiave privata inviata solo nell header e errore senza segreti`() = runTest {
        val token="synthetic-test-private-token-00000000"
        val requests=mutableListOf<HttpRequest>()
        val client=OnlinePricesClient(object:HttpTransport {
            override suspend fun execute(request:HttpRequest):HttpResponse { requests.add(request); return HttpResponse(401,"") }
        },"https://prices.example.org",accessToken=token)
        val failure=runCatching { client.sources() }.exceptionOrNull()!!
        assertEquals("Bearer $token",requests.single().headers["Authorization"])
        assertFalse(requests.single().url.contains(token))
        assertFalse(failure.message!!.contains(token))
    }
    @Test fun `servizio non configurato non esegue richieste`() = runTest {
        val client=OnlinePricesClient(object:HttpTransport {
            override suspend fun execute(request:HttpRequest):HttpResponse = error("Rete inattesa")
        },"")
        assertFalse(client.configured)
        assertTrue(runCatching { client.sources() }.exceptionOrNull() is OnlinePricesException)
    }
    @Test fun `errore http distinto da catalogo vuoto e query codificata`() = runTest {
        val requests=mutableListOf<HttpRequest>()
        val client=OnlinePricesClient(object:HttpTransport {
            override suspend fun execute(request:HttpRequest):HttpResponse { requests.add(request); return HttpResponse(503,"") }
        },"https://prices.example.org")
        assertTrue(runCatching { client.search("l'acqua & latte",null,"20125") }.exceptionOrNull() is OnlinePricesException)
        assertTrue(requests.single().url.contains("q=l%27acqua+%26+latte"))
    }
}
