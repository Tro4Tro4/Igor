package com.igor.fridge.domain.receipt

/** Un pezzo di testo riconosciuto, con il suo rettangolo nell'immagine (in pixel). */
data class TextFragment(
    val text: String,
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int,
) {
    val centerY: Int get() = (top + bottom) / 2
    val height: Int get() = (bottom - top).coerceAtLeast(1)
}

/**
 * Ricompone le righe dello scontrino.
 *
 * Il riconoscimento del testo restituisce blocchi, non righe: la colonna dei nomi e quella
 * dei prezzi arrivano spesso separate, e "LATTE 1,29" diventerebbe due righe senza legame.
 * Qui si rimettono insieme i frammenti che stanno alla stessa altezza (il centro di uno
 * cade entro meta' altezza del centro dell'altro), da sinistra a destra, separati da due
 * spazi perche' il nome resti distinguibile dal prezzo.
 */
fun groupIntoRows(fragments: List<TextFragment>): List<String> {
    val rows = mutableListOf<MutableList<TextFragment>>()
    for (fragment in fragments.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
        val row = rows.lastOrNull()
        val sameRow = row != null && row.any { other ->
            kotlin.math.abs(other.centerY - fragment.centerY) * 2 <= minOf(other.height, fragment.height)
        }
        if (sameRow) row!!.add(fragment) else rows.add(mutableListOf(fragment))
    }
    return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
}
