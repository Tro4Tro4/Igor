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
}
