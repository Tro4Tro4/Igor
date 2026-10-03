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
 * Qui si rimettono insieme i frammenti che stanno alla stessa altezza, da sinistra a
 * destra, separati da due spazi perche' il nome resti distinguibile dal prezzo.
 *
 * Un frammento entra in una riga se si sovrappone in verticale alla fascia media della riga
 * (non a un frammento qualsiasi: righe vicine si fonderebbero a catena) per almeno
 * [MIN_OVERLAP] della minore delle due altezze, cosi' un prezzo stampato mezza riga piu' in
 * basso resta col suo nome. Due frammenti che si sovrappongono in orizzontale stanno nella
 * stessa colonna, quindi su righe diverse.
 */
fun groupIntoRows(fragments: List<TextFragment>): List<String> {
    val rows = mutableListOf<MutableList<TextFragment>>()
    for (fragment in fragments.filter { it.text.isNotBlank() }.sortedBy { it.centerY }) {
        // Bastano le ultime righe: i frammenti arrivano dall'alto in basso.
        val row = rows.takeLast(2)
            .filter { row -> row.none { it.overlapsHorizontally(fragment) } }
            .map { row -> row to verticalOverlap(row, fragment) }
            .filter { (_, overlap) -> overlap >= MIN_OVERLAP }
            .maxByOrNull { it.second }
            ?.first
        if (row != null) row.add(fragment) else rows.add(mutableListOf(fragment))
    }
    return rows.map { row -> row.sortedBy { it.left }.joinToString("  ") { it.text.trim() } }
}

/** Quanto il frammento copre la fascia media della riga, rispetto alla minore delle due altezze. */
private fun verticalOverlap(row: List<TextFragment>, fragment: TextFragment): Double {
    val top = row.map { it.top }.average()
    val bottom = row.map { it.bottom }.average()
    val overlap = minOf(bottom, fragment.bottom.toDouble()) - maxOf(top, fragment.top.toDouble())
    return overlap / minOf((bottom - top).coerceAtLeast(1.0), fragment.height.toDouble())
}

private fun TextFragment.overlapsHorizontally(other: TextFragment): Boolean =
    left < other.right && other.left < right

/** Sovrapposizione minima per stare sulla stessa riga. */
private const val MIN_OVERLAP = 0.4
