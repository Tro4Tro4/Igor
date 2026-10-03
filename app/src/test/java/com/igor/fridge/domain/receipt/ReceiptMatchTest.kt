package com.igor.fridge.domain.receipt

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReceiptMatchTest {

    @Test
    fun `la voce corrisponde alla riga che la contiene`() {
        assertTrue(receiptMatches("Latte", "Latte ps granarolo"))
        assertTrue(receiptMatches("latte di soia", "LATTE SOIA ALPRO"))
        assertTrue(receiptMatches("Caffè", "CAFFE MACINATO"))
    }

    @Test
    fun `le abbreviazioni della cassa valgono`() {
        assertTrue(receiptMatches("Mozzarella", "Mozz. fior di latte"))
        assertTrue(receiptMatches("Parmigiano reggiano", "PARMIG. REGG. 24 MESI"))
    }

    @Test
    fun `serve ogni parola significativa della voce`() {
        assertFalse(receiptMatches("Latte di soia", "Latte ps"))
        assertFalse(receiptMatches("Pane", "Panna da cucina"))
        assertFalse(receiptMatches("di", "Latte di soia"))
    }

    @Test
    fun `una parola corta intera non e' l'abbreviazione di una lunga`() {
        assertFalse(receiptMatches("Panettone", "PANE"))
        assertFalse(receiptMatches("Panettone", "PANE COMUNE"))
        assertTrue(receiptMatches("Panettone", "PANETT. CLASSICO"))
        assertTrue(receiptMatches("Mozzarella", "MOZZAR FIOR DI LATTE"))
    }
}
