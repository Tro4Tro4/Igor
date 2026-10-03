package com.igor.fridge.domain.receipt

import org.junit.Assert.assertEquals
import org.junit.Test

class ReceiptRowsTest {

    @Test
    fun `nome e prezzo alla stessa altezza tornano una riga`() {
        val fragments = listOf(
            TextFragment("1,29", left = 400, top = 102, right = 450, bottom = 122),
            TextFragment("LATTE PS 1L", left = 10, top = 100, right = 200, bottom = 120),
            TextFragment("YOGURT", left = 10, top = 130, right = 120, bottom = 150),
            TextFragment("1,78", left = 400, top = 131, right = 450, bottom = 151),
        )

        assertEquals(listOf("LATTE PS 1L  1,29", "YOGURT  1,78"), groupIntoRows(fragments))
    }

    @Test
    fun `una riga leggermente storta resta una riga`() {
        val fragments = listOf(
            TextFragment("PANE", left = 10, top = 100, right = 100, bottom = 120),
            TextFragment("1,20", left = 400, top = 107, right = 450, bottom = 127),
        )

        assertEquals(listOf("PANE  1,20"), groupIntoRows(fragments))
    }

    @Test
    fun `i frammenti vuoti si scartano`() {
        val fragments = listOf(
            TextFragment("  ", left = 10, top = 100, right = 100, bottom = 120),
            TextFragment("PANE", left = 10, top = 200, right = 100, bottom = 220),
        )

        assertEquals(listOf("PANE"), groupIntoRows(fragments))
    }

    @Test
    fun `un prezzo spostato di mezza riga va col nome sopra`() {
        val fragments = listOf(
            TextFragment("LATTE", left = 10, top = 100, right = 200, bottom = 130),
            TextFragment("1,29", left = 400, top = 116, right = 450, bottom = 146),
            TextFragment("YOGURT", left = 10, top = 130, right = 200, bottom = 160),
            TextFragment("1,78", left = 400, top = 146, right = 450, bottom = 176),
        )

        assertEquals(listOf("LATTE  1,29", "YOGURT  1,78"), groupIntoRows(fragments))
        assertEquals(listOf("LATTE  1,29"), groupIntoRows(fragments.take(2)))
    }

    @Test
    fun `righe vicine non si fondono a catena`() {
        val fragments = listOf(
            TextFragment("PANE", left = 10, top = 100, right = 200, bottom = 130),
            TextFragment("1,20", left = 400, top = 114, right = 450, bottom = 144),
            TextFragment("UOVA", left = 10, top = 128, right = 200, bottom = 158),
            TextFragment("2,10", left = 400, top = 142, right = 450, bottom = 172),
        )

        assertEquals(listOf("PANE  1,20", "UOVA  2,10"), groupIntoRows(fragments))
    }

    @Test
    fun `due frammenti nella stessa colonna non stanno sulla stessa riga`() {
        val fragments = listOf(
            TextFragment("PANE", left = 10, top = 100, right = 200, bottom = 130),
            TextFragment("UOVA", left = 10, top = 112, right = 200, bottom = 142),
        )

        assertEquals(listOf("PANE", "UOVA"), groupIntoRows(fragments))
    }
}
