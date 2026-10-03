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
}
