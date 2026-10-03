package com.igor.fridge.domain

import com.igor.fridge.data.local.FoodCategory
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryGuessTest {

    @Test
    fun `riconosce i prodotti piu' comuni`() {
        assertEquals(FoodCategory.LATTICINI, guessCategory("Latte"))
        assertEquals(FoodCategory.FRUTTA, guessCategory("mele"))
        assertEquals(FoodCategory.PANE, guessCategory("Pane"))
        assertEquals(FoodCategory.DISPENSA, guessCategory("Pasta"))
        assertEquals(FoodCategory.CASA, guessCategory("Detersivo lavatrice"))
        assertEquals(FoodCategory.IGIENE, guessCategory("Dentifricio"))
    }

    @Test
    fun `ignora maiuscole, accenti e punteggiatura`() {
        assertEquals(FoodCategory.DISPENSA, guessCategory("  CAFFÈ!  "))
        assertEquals(FoodCategory.LATTICINI, guessCategory("Yogurt, bianco"))
    }

    @Test
    fun `vince la prima parola riconosciuta`() {
        assertEquals(FoodCategory.SURGELATI, guessCategory("gelato al latte"))
        assertEquals(FoodCategory.BEVANDE, guessCategory("succo di mela"))
        assertEquals(FoodCategory.CARNE, guessCategory("petto di pollo"))
    }

    @Test
    fun `le espressioni di due parole precedono le singole parole`() {
        assertEquals(FoodCategory.IGIENE, guessCategory("Carta igienica"))
        assertEquals(FoodCategory.DISPENSA, guessCategory("pane grattugiato"))
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
        assertEquals(FoodCategory.LATTICINI, guessCategory("MOZZ. FIOR DI LATTE"))
        assertEquals(FoodCategory.LATTICINI, guessCategory("PARMIG REGG"))
        assertEquals(FoodCategory.CARNE, guessCategory("PROSC. COTTO"))
    }

    @Test
    fun `una parola intera vince sempre su un'abbreviazione`() {
        assertEquals(FoodCategory.LATTICINI, guessCategory("Pomod latte"))
    }

    @Test
    fun `le parole di collegamento non separano le espressioni`() {
        assertEquals(FoodCategory.PESCE, guessCategory("fette di salmone"))
        assertEquals(FoodCategory.LATTICINI, guessCategory("fiocchi di latte"))
        assertEquals(FoodCategory.DISPENSA, guessCategory("burro di arachidi"))
        assertEquals(FoodCategory.CASA, guessCategory("sapone per piatti"))
        assertEquals(FoodCategory.CASA, guessCategory("sapone piatti"))
    }

    @Test
    fun `espressioni che cambiano il senso della prima parola`() {
        assertEquals(FoodCategory.BEVANDE, guessCategory("te freddo"))
        assertEquals(FoodCategory.BEVANDE, guessCategory("Tè freddo al limone"))
        assertEquals(FoodCategory.CASA, guessCategory("olio motore"))
        assertEquals(FoodCategory.IGIENE, guessCategory("acqua ossigenata"))
        assertEquals(FoodCategory.PANE, guessCategory("fette biscottate"))
    }

    @Test
    fun `parole aggiunte e surgelati abbreviati`() {
        assertEquals(FoodCategory.SURGELATI, guessCategory("pizza margherita"))
        assertEquals(FoodCategory.DISPENSA, guessCategory("caramelle"))
        assertEquals(FoodCategory.DISPENSA, guessCategory("crema spalmabile"))
        assertEquals(FoodCategory.SURGELATI, guessCategory("SPINACI SURG."))
        assertEquals(FoodCategory.SURGELATI, guessCategory("PISELLI SURGEL"))
    }

    @Test
    fun `le parole comuni non valgono come abbreviazioni`() {
        assertEquals(FoodCategory.DISPENSA, guessCategory("GRAN CEREALE"))
        assertEquals(FoodCategory.ALTRO, guessCategory("MINI"))
    }
}
