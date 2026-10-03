package com.igor.fridge.domain.prices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChainsTest {

    @Test
    fun `riconosce la catena nel nome stampato`() {
        assertEquals("Esselunga", chainOf("ESSELUNGA S.P.A. - VIA ROMA")?.name)
        assertEquals("Il Gigante", chainOf("Supermercato Il Gigante")?.name)
        assertEquals("Lidl", chainOf("Lidl Italia S.r.l.")?.name)
        assertEquals("Coop", chainOf("IPERCOOP LOMBARDIA")?.name)
        assertNull(chainOf("Bottega di Mario"))
        assertNull(chainOf(null))
    }

    @Test
    fun `il nome da confrontare e' la catena, oppure il negozio com'e'`() {
        assertEquals("Tigros", storeLabel("TIGROS SPA"))
        assertEquals("Bottega di Mario", storeLabel(" Bottega di Mario "))
        assertNull(storeLabel("  "))
    }

    @Test
    fun `cerca sul sito della catena attraverso il motore di ricerca`() {
        val esselunga = KNOWN_CHAINS.first { it.name == "Esselunga" }

        assertEquals(
            "https://www.google.com/search?q=site%3Aesselunga.it+latte+di+soia",
            searchUrl(esselunga, "latte di soia"),
        )
        assertNull(searchUrl(KNOWN_CHAINS.first { it.name == "Coop" }, "latte"))
    }

    @Test
    fun `le catene chieste hanno tutte un sito su cui cercare`() {
        val wanted = listOf("Tigros", "Esselunga", "Unes", "Eurospin", "Lidl", "Aldi", "Il Gigante")
        wanted.forEach { name -> assertEquals(name, KNOWN_CHAINS.single { it.name == name && it.domain != null }.name) }
    }

    @Test
    fun `Spar, U2 e M D sono catene note`() {
        assertEquals("Despar", chainOf("SPAR - Aspiag Service")?.name)
        assertEquals("Unes", chainOf("U2 Supermercato")?.name)
        assertEquals("MD", chainOf("M.D. S.p.A.")?.name)
        assertEquals("MD", chainOf("MD Discount")?.name)
        assertNull(chainOf("Spartaco alimentari"))
        assertNull(chainOf("Salumeria M. Rossi D. Bianchi"))
    }
}
