package com.igor.fridge.domain.prices

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GtinTest {

    @Test
    fun `accetta codici validi delle lunghezze ammesse`() {
        assertEquals("4006381333931", normalizeGtin("4006381333931"))
        assertEquals("4006381333931", normalizeGtin(" 4006-3813 33931 "))
        assertEquals("96385074", normalizeGtin("96385074"))
        assertEquals("036000291452", normalizeGtin("036000291452"))
    }

    @Test
    fun `rifiuta cifra di controllo sbagliata, lettere e lunghezze strane`() {
        assertNull(normalizeGtin("4006381333932"))
        assertNull(normalizeGtin("40063813339A1"))
        assertNull(normalizeGtin("12345"))
        assertNull(normalizeGtin(""))
    }

    @Test
    fun `solo cifre ASCII`() {
        // La prima e' uno zero arabo: Char.isDigit lo accetterebbe, e la somma di controllo tornerebbe.
        assertNull(normalizeGtin("\u0660006381333931"))
    }
}
