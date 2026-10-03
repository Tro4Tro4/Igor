package com.igor.fridge.ui

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.ui.shopping.ShoppingUiState
import com.igor.fridge.ui.shopping.groupedByCategory
import com.igor.fridge.ui.shopping.shareText
import com.igor.fridge.ui.shopping.totalsOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShoppingListModelTest {

    private val latte = ShoppingItem(
        uuid = "1",
        name = "Latte",
        quantity = 2.0,
        unit = QuantityUnit.L,
        category = FoodCategory.LATTICINI,
        brand = "Granarolo",
        unitPriceCents = 129,
        store = "Esselunga",
    )
    private val mele = ShoppingItem(
        uuid = "2",
        name = "Mele",
        quantity = 1.5,
        unit = QuantityUnit.KG,
        category = FoodCategory.FRUTTA,
        unitPriceCents = 199,
        store = "mercato",
    )
    private val pane = ShoppingItem(uuid = "3", name = "Pane", category = FoodCategory.PANE)

    @Test
    fun `le corsie seguono l'ordine scelto`() {
        val order = listOf(FoodCategory.LATTICINI, FoodCategory.PANE, FoodCategory.FRUTTA)

        val sections = listOf(mele, pane, latte).groupedByCategory(order)

        assertEquals(order, sections.map { it.category })
    }

    @Test
    fun `una categoria assente dall'ordine finisce in fondo invece di sparire`() {
        val sections = listOf(mele, latte).groupedByCategory(listOf(FoodCategory.LATTICINI))

        assertEquals(listOf(FoodCategory.LATTICINI, FoodCategory.FRUTTA), sections.map { it.category })
    }

    @Test
    fun `il filtro per negozio tiene anche le voci senza negozio`() {
        val state = ShoppingUiState(items = listOf(latte, mele, pane), storeFilter = "ESSELUNGA")

        assertEquals(listOf("Esselunga", "mercato"), state.stores)
        assertEquals("Esselunga", state.activeStore)
        assertEquals(listOf("Latte", "Pane"), state.visibleItems.map { it.name })
    }

    @Test
    fun `un filtro per un negozio sparito non nasconde nulla`() {
        val state = ShoppingUiState(items = listOf(latte, pane), storeFilter = "Lidl")

        assertNull(state.activeStore)
        assertEquals(2, state.visibleItems.size)
    }

    @Test
    fun `il totale stima la lista e conta la spesa vera nel carrello`() {
        val inCart = mele.copy(isChecked = true, purchasedQuantity = 1.0)

        val totals = totalsOf(listOf(latte, inCart, pane))

        // 2 l x 1,29 + 1,5 kg x 1,99 = 2,58 + 2,985 -> 258 + 299 (arrotondato per voce)
        assertEquals(557, totals.estimatedCents)
        assertEquals(199, totals.inCartCents)
        assertEquals(1, totals.unpricedCount)
    }

    @Test
    fun `il testo da condividere ha corsie, quantita' e dettagli`() {
        val state = ShoppingUiState(items = listOf(latte, pane))

        val text = shareText(state.toBuy, state.totals)

        assertEquals(
            "Lista della spesa\n\n" +
                "🍞 Pane e forno\n• Pane — 1 pz\n\n" +
                "🥛 Latticini e uova\n• Latte — 2 l (Granarolo, da Esselunga)\n\n" +
                "Totale stimato: 2,58 €",
            text,
        )
    }
}
