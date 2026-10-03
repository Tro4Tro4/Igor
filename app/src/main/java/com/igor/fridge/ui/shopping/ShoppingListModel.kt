package com.igor.fridge.ui.shopping

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.ui.formatEuro
import com.igor.fridge.ui.formatQuantity
import com.igor.fridge.ui.icon
import com.igor.fridge.ui.label
import java.util.Locale
import kotlin.math.roundToLong

/** Le voci ancora da comprare di una categoria, cioe' di una corsia del negozio. */
data class ShoppingSection(
    val category: FoodCategory,
    val items: List<ShoppingItem>,
)

/**
 * Quanto costa la spesa, per le voci che hanno un prezzo.
 *
 * [estimatedCents] e' il costo di tutta la lista alle quantita' da comprare; [inCartCents]
 * quello di cio' che e' gia' nel carrello, alla quantita' presa se indicata: la spesa vera.
 * [unpricedCount] dice quante voci mancano al conto, perche' una stima parziale non sembri
 * completa.
 */
data class ShoppingTotals(
    val estimatedCents: Long = 0,
    val inCartCents: Long = 0,
    val unpricedCount: Int = 0,
) {
    val hasPrices: Boolean get() = estimatedCents > 0 || inCartCents > 0
}

data class ShoppingUiState(
    val items: List<ShoppingItem> = emptyList(),
    val isLoading: Boolean = true,
    val message: String? = null,
    /** Cambia a ogni messaggio: due messaggi uguali di seguito si mostrano entrambi. */
    val messageId: Long = 0,
    val canUndo: Boolean = false,
    val categoryOrder: List<FoodCategory> = FoodCategory.entries,
    /** Negozio scelto nel filtro; null mostra tutto. */
    val storeFilter: String? = null,
) {
    /** I negozi indicati nelle voci, senza doppioni (maiuscole a parte), in ordine alfabetico. */
    val stores: List<String> = items.mapNotNull { it.store?.trim()?.takeIf(String::isNotEmpty) }
        .distinctBy { it.lowercase(Locale.ITALIAN) }
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    /** Il filtro vale solo se quel negozio e' ancora nella lista. */
    val activeStore: String? = stores.firstOrNull { it.equals(storeFilter, ignoreCase = true) }

    /**
     * Le voci da mostrare: con un negozio scelto, quelle di quel negozio e quelle senza
     * negozio, che si possono comprare dovunque.
     */
    val visibleItems: List<ShoppingItem> = if (activeStore == null) {
        items
    } else {
        items.filter { it.store.isNullOrBlank() || it.store.trim().equals(activeStore, ignoreCase = true) }
    }

    /** "Metti in frigo" sposta tutte le voci spuntate, anche quelle nascoste dal filtro. */
    val checkedCount: Int get() = items.count { it.isChecked }

    /** Da comprare, raggruppate per categoria nell'ordine delle corsie. */
    val toBuy: List<ShoppingSection> =
        visibleItems.filterNot { it.isChecked }.groupedByCategory(categoryOrder)

    /** Gia' nel carrello, nell'ordine della lista. */
    val inCart: List<ShoppingItem> = visibleItems.filter { it.isChecked }

    val totals: ShoppingTotals = totalsOf(visibleItems)
}

/**
 * Raggruppa per categoria nell'ordine di [order] (le corsie); dentro ogni gruppo resta
 * l'ordine della lista. Le categorie vuote non compaiono, e una categoria assente da
 * [order] finisce in fondo invece di sparire.
 */
fun List<ShoppingItem>.groupedByCategory(
    order: List<FoodCategory> = FoodCategory.entries,
): List<ShoppingSection> {
    val byCategory = groupBy { it.category }
    val fullOrder = order.distinct() + FoodCategory.entries.filterNot { it in order }
    return fullOrder.mapNotNull { category ->
        byCategory[category]?.let { ShoppingSection(category, it) }
    }
}

fun totalsOf(items: List<ShoppingItem>): ShoppingTotals {
    var estimated = 0L
    var inCart = 0L
    var unpriced = 0
    for (item in items) {
        val price = item.unitPriceCents
        if (price == null) {
            unpriced++
            continue
        }
        estimated += (price * item.quantity).roundToLong()
        if (item.isChecked) inCart += (price * (item.purchasedQuantity ?: item.quantity)).roundToLong()
    }
    return ShoppingTotals(estimated, inCart, unpriced)
}

/**
 * La lista da mandare a qualcuno (WhatsApp, SMS): solo cio' che resta da comprare, per
 * corsia, con quanto, marca, negozio e note. Testo semplice, leggibile ovunque.
 */
fun shareText(sections: List<ShoppingSection>, totals: ShoppingTotals? = null): String = buildString {
    append("Lista della spesa")
    for (section in sections) {
        append("\n\n").append(section.category.icon()).append(' ').append(section.category.label())
        for (item in section.items) {
            append("\n• ").append(item.name).append(" — ").append(formatQuantity(item.quantity, item.unit))
            val extra = listOfNotNull(item.brand, item.store?.let { "da $it" }, item.notes)
            if (extra.isNotEmpty()) append(" (").append(extra.joinToString(", ")).append(')')
        }
    }
    if (totals != null && totals.estimatedCents > 0) {
        append("\n\nTotale stimato: ").append(formatEuro(totals.estimatedCents))
    }
}
