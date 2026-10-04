package com.igor.fridge.domain

import com.igor.fridge.data.local.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGuessTest {

    @Test fun `conserve di pesce non scavalcano il prodotto principale`() {
        assertEquals(FoodCategory.VERDURA, guessCategory("insalata con tonno in scatola"))
        assertEquals(FoodCategory.SUGHI_PASSATE, guessCategory("sugo al tonno sottolio"))
        assertEquals(FoodCategory.CONSERVE_PESCE_CARNE, guessCategory("tonno in scatola"))
        assertEquals(FoodCategory.CONSERVE_PESCE_CARNE, guessCategory("sgombro sottolio"))
        assertEquals(FoodCategory.SURGELATI, guessCategory("insalata con tonno in scatola surgelata"))
    }

    @Test fun `distingue prodotti dettagliati e conserva le ambiguita'`() {
        val cases = mapOf(
            "prosciutto cotto" to FoodCategory.SALUMI,
            "MOZZ. FIOR DI LATTE" to FoodCategory.FORMAGGI_FRESCHI,
            "PARMIG REGG" to FoodCategory.FORMAGGI_STAGIONATI,
            "biscotti al latte" to FoodCategory.BISCOTTI,
            "merendine" to FoodCategory.MERENDINE,
            "tonno in scatola" to FoodCategory.CONSERVE_PESCE_CARNE,
            "tonno fresco" to FoodCategory.PESCE,
            "gelato al latte" to FoodCategory.GELATI,
            "latte di soia" to FoodCategory.ALTERNATIVE_VEGETALI,
            "detersivo lavatrice" to FoodCategory.BUCATO,
            "olio motore" to FoodCategory.CASA,
            "acqua ossigenata" to FoodCategory.IGIENE,
            "regalo per Anna" to FoodCategory.ALTRO,
            "SPINACI SURG." to FoodCategory.SURGELATI,
        )
        cases.forEach { (name, expected) -> assertEquals(name, expected, guessCategory(name)) }
    }

    @Test
    fun `riconosce i prodotti piu' comuni`() {
        assertEquals(FoodCategory.LATTE_PANNA, guessCategory("Latte"))
        assertEquals(FoodCategory.FRUTTA, guessCategory("mele"))
        assertEquals(FoodCategory.PANE, guessCategory("Pane"))
        assertEquals(FoodCategory.PASTA_SECCA, guessCategory("Pasta"))
        assertEquals(FoodCategory.BUCATO, guessCategory("Detersivo lavatrice"))
        assertEquals(FoodCategory.IGIENE, guessCategory("Dentifricio"))
    }

    @Test
    fun `ignora maiuscole, accenti e punteggiatura`() {
        assertEquals(FoodCategory.CAFFE_INFUSI, guessCategory("  CAFFÈ!  "))
        assertEquals(FoodCategory.YOGURT, guessCategory("Yogurt, bianco"))
    }

    @Test
    fun `vince la prima parola riconosciuta`() {
        assertEquals(FoodCategory.GELATI, guessCategory("gelato al latte"))
        assertEquals(FoodCategory.SUCCHI_BIBITE, guessCategory("succo di mela"))
        assertEquals(FoodCategory.CARNE, guessCategory("petto di pollo"))
    }

    @Test
    fun `le espressioni di due parole precedono le singole parole`() {
        assertEquals(FoodCategory.IGIENE, guessCategory("Carta igienica"))
        assertEquals(FoodCategory.FARINE_DOLCI, guessCategory("pane grattugiato"))
    }

    @Test
    fun `surgelato cambia la corsia qualunque sia il prodotto`() {
        assertEquals(FoodCategory.SURGELATI, guessCategory("piselli surgelati"))
    }

    @Test
    fun `cio' che non riconosce e' Altro`() {
        assertEquals(FoodCategory.ALTRO, guessCategory("regalo per Anna"))
        assertEquals(FoodCategory.ALTRO, guessCategory("   "))
    }

    @Test
    fun `le abbreviazioni degli scontrini valgono se portano a una sola categoria`() {
        assertEquals(FoodCategory.FORMAGGI_FRESCHI, guessCategory("MOZZ. FIOR DI LATTE"))
        assertEquals(FoodCategory.FORMAGGI_STAGIONATI, guessCategory("PARMIG REGG"))
        assertEquals(FoodCategory.SALUMI, guessCategory("PROSC. COTTO"))
    }

    @Test
    fun `una parola intera vince sempre su un'abbreviazione`() {
        assertEquals(FoodCategory.LATTE_PANNA, guessCategory("Pomod latte"))
    }

    @Test
    fun `le parole di collegamento non separano le espressioni`() {
        assertEquals(FoodCategory.PESCE, guessCategory("fette di salmone"))
        assertEquals(FoodCategory.FORMAGGI_FRESCHI, guessCategory("fiocchi di latte"))
        assertEquals(FoodCategory.CONFETTURE_CREME, guessCategory("burro di arachidi"))
        assertEquals(FoodCategory.CASA, guessCategory("sapone per piatti"))
        assertEquals(FoodCategory.CASA, guessCategory("sapone piatti"))
    }

    @Test
    fun `espressioni che cambiano il senso della prima parola`() {
        assertEquals(FoodCategory.SUCCHI_BIBITE, guessCategory("te freddo"))
        assertEquals(FoodCategory.SUCCHI_BIBITE, guessCategory("Tè freddo al limone"))
        assertEquals(FoodCategory.CASA, guessCategory("olio motore"))
        assertEquals(FoodCategory.IGIENE, guessCategory("acqua ossigenata"))
        assertEquals(FoodCategory.PANE, guessCategory("fette biscottate"))
    }

    @Test
    fun `parole aggiunte e surgelati abbreviati`() {
        assertEquals(FoodCategory.SURGELATI, guessCategory("pizza margherita"))
        assertEquals(FoodCategory.DOLCI, guessCategory("caramelle"))
        assertEquals(FoodCategory.CONFETTURE_CREME, guessCategory("crema spalmabile"))
        assertEquals(FoodCategory.SURGELATI, guessCategory("SPINACI SURG."))
        assertEquals(FoodCategory.SURGELATI, guessCategory("PISELLI SURGEL"))
    }

    @Test
    fun `le parole comuni non valgono come abbreviazioni`() {
        assertEquals(FoodCategory.CEREALI_COLAZIONE, guessCategory("GRAN CEREALE"))
        assertEquals(FoodCategory.ALTRO, guessCategory("MINI"))
    }
}
