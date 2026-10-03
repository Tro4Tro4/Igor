package com.igor.fridge.data.photos

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLayoutTest {

    @Test
    fun `uno scontrino stretto in punti si ingrandisce fino al lato massimo`() {
        // 80 x 300 mm: 226 x 850 punti.
        val layout = stackPages(listOf(PageSize(226, 850)), maxSidePx = 3000)

        assertEquals(3000, layout.height)
        assertEquals(798, layout.width)
        assertEquals(listOf(0), layout.pageTops)
    }

    @Test
    fun `le pagine si impilano con la stessa scala`() {
        // Due A4 alte in tutto 1684 punti: l'altezza comanda la scala.
        val layout = stackPages(listOf(PageSize(595, 842), PageSize(595, 842)), maxSidePx = 3000)

        val pageHeight = layout.pageHeights.first()
        assertEquals(listOf(pageHeight, pageHeight), layout.pageHeights)
        assertEquals(listOf(0, pageHeight), layout.pageTops)
        assertEquals(2 * pageHeight, layout.height)
        assertTrue(layout.height in 2999..3001)
        assertTrue(layout.width in 1060..1061)
    }

    @Test
    fun `una pagina larga e' limitata dalla larghezza`() {
        val layout = stackPages(listOf(PageSize(842, 595)), maxSidePx = 1000)

        assertEquals(1000, layout.width)
        assertEquals(707, layout.height)
    }
}
