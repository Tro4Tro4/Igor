package com.igor.fridge.domain.prices

/**
 * Normalizza un codice a barre EAN/UPC (GTIN-8, 12, 13, 14): solo cifre, lunghezza
 * ammessa e cifra di controllo giusta. Un codice letto male o scritto male non deve finire
 * in un database condiviso.
 *
 * @return il codice pulito, oppure null se non e' valido.
 */
fun normalizeGtin(text: String): String? {
    val digits = text.filter { !it.isWhitespace() && it != '-' }
    if (digits.isEmpty() || !digits.all(Char::isDigit)) return null
    if (digits.length !in setOf(8, 12, 13, 14)) return null
    val body = digits.dropLast(1)
    // Da destra, pesi alterni 3 e 1 (la cifra di controllo esclusa).
    val sum = body.reversed().mapIndexed { index, c -> (c - '0') * if (index % 2 == 0) 3 else 1 }.sum()
    val check = (10 - sum % 10) % 10
    return digits.takeIf { check == digits.last() - '0' }
}
