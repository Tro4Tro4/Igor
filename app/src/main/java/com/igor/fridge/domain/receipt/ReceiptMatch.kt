package com.igor.fridge.domain.receipt

import com.igor.fridge.domain.nameWords

/**
 * Dice se una riga dello scontrino corrisponde a una voce della lista della spesa.
 *
 * Ogni parola significativa della voce ("latte", "parmigiano") deve comparire nella riga,
 * intera o abbreviata come la stampano le casse ("PARMIG"). Le parole in piu' della riga
 * (marca, formato) non contano: "Latte" corrisponde a "LATTE PS GRANAROLO 1L", ma "Latte di
 * soia" non corrisponde a "LATTE PS".
 */
fun receiptMatches(listName: String, receiptName: String): Boolean {
    val wanted = nameWords(listName).filter { it.length >= MIN_WORD }
    if (wanted.isEmpty()) return false
    val found = nameWords(receiptName)
    return wanted.all { word ->
        found.any { it == word || (it.length >= MIN_ABBREVIATION && word.startsWith(it)) }
    }
}

/** "di", "al", "la" non distinguono un prodotto. */
private const val MIN_WORD = 3

/** Un'abbreviazione piu' corta ("LA", "PA") prenderebbe troppe parole. */
private const val MIN_ABBREVIATION = 4
