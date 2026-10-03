package com.igor.fridge.domain

import com.igor.fridge.data.local.FoodCategory

/**
 * Ordine delle corsie scelto dall'utente, salvato come nomi separati da virgola.
 *
 * Il risultato contiene sempre tutte le categorie, una volta sola: un nome sconosciuto
 * (una categoria tolta in una versione futura) si ignora, e una categoria che manca (una
 * categoria aggiunta dopo il salvataggio) si accoda nella posizione predefinita.
 */
fun categoryOrderFrom(stored: String?): List<FoodCategory> {
    val chosen = stored.orEmpty()
        .split(',')
        .mapNotNull { name -> FoodCategory.entries.firstOrNull { it.name == name.trim() } }
        .distinct()
    return chosen + FoodCategory.entries.filterNot { it in chosen }
}

fun List<FoodCategory>.toStoredOrder(): String = joinToString(",") { it.name }

/** Sposta [category] di [delta] posizioni, restando dentro la lista. */
fun List<FoodCategory>.moved(category: FoodCategory, delta: Int): List<FoodCategory> {
    val from = indexOf(category)
    if (from < 0) return this
    val to = (from + delta).coerceIn(0, lastIndex)
    if (to == from) return this
    return toMutableList().apply {
        removeAt(from)
        add(to, category)
    }
}
