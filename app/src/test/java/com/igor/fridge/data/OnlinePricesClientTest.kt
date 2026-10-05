package com.igor.fridge.data

import com.igor.fridge.data.onlineprices.*
import com.igor.fridge.data.openprices.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OnlinePricesClientTest {
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
