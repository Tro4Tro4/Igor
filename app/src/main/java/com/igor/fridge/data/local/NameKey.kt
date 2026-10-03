package com.igor.fridge.data.local

import java.text.Normalizer
import java.util.Locale

private val SPACES = Regex("\\s+")

/**
 * La chiave con cui si cerca un prodotto per nome: minuscole, spazi compattati, forma
 * Unicode composta. `COLLATE NOCASE` di SQLite confronta senza maiuscole solo le lettere
 * ASCII, quindi "CAFFÈ" letto da uno scontrino non trovava "Caffè" in inventario.
 *
 * Gli accenti restano: "pere" e "però" sono parole diverse. La chiave vive in una colonna
 * indicizzata, scritta da Room insieme al nome a ogni salvataggio.
 */
fun nameKeyOf(name: String): String =
    Normalizer.normalize(name.trim(), Normalizer.Form.NFC)
        .lowercase(Locale.ROOT)
        .replace(SPACES, " ")
