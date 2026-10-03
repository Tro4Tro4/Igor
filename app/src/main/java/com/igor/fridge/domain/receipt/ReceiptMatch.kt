package com.igor.fridge.domain.receipt

import com.igor.fridge.domain.nameWords

/**
 * Dice se una riga dello scontrino corrisponde a una voce della lista della spesa.
 *
 * Ogni parola significativa della voce ("latte", "parmigiano") deve comparire nella riga,
 * intera o abbreviata come la stampano le casse ("PARMIG"). Le parole in piu' della riga
 * (marca, formato) non contano: "Latte" corrisponde a "LATTE PS GRANAROLO 1L", ma "Latte di
 * soia" non corrisponde a "LATTE PS".
 *
 * Un'abbreviazione vale se copre almeno meta' della parola ("MOZZAR" per mozzarella) oppure
 * se la cassa la segna col punto ("MOZZ."): "PANE" e' una parola intera, non l'inizio di
 * "panettone".
 */
fun receiptMatches(listName: String, receiptName: String): Boolean {
    val wanted = nameWords(listName).filter { it.length >= MIN_WORD }
    if (wanted.isEmpty()) return false
    val found = nameWords(receiptName)
    val dotted = ABBREVIATED.findAll(receiptName).flatMap { nameWords(it.groupValues[1]) }.toSet()
    return wanted.all { word ->
        found.any { it == word || isAbbreviation(it, word, it in dotted) }
    }
}

private fun isAbbreviation(short: String, word: String, dotted: Boolean): Boolean =
    short.length >= MIN_ABBREVIATION && word.startsWith(short) &&
        (dotted || short.length * 2 >= word.length)

/** Una parola seguita dal punto: "MOZZ.", "PARMIG.". */
private val ABBREVIATED = Regex("([\\p{L}\\p{M}]+)\\.")

/** "di", "al", "la" non distinguono un prodotto. */
private const val MIN_WORD = 3

/** Un'abbreviazione piu' corta ("LA", "PA") prenderebbe troppe parole. */
private const val MIN_ABBREVIATION = 4
