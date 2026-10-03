package com.igor.fridge.domain.receipt

import java.time.DateTimeException
import java.time.LocalDate
import java.util.Locale

/** Dove e quando: le informazioni che danno senso a un prezzo. */
data class ReceiptMeta(
    val store: String? = null,
    val date: LocalDate? = null,
)

/**
 * Legge negozio e data di uno scontrino.
 *
 * Il negozio e' la prima riga con un nome vero in testa allo scontrino: l'insegna viene
 * prima dell'indirizzo e della partita IVA. La data e' la prima data plausibile in tutto lo
 * scontrino (di solito in fondo, accanto all'ora): non piu' di un giorno nel futuro rispetto
 * a [today] e non prima del 2000, cosi' un codice che somiglia a una data non passa.
 * Cio' che non si trova resta null e lo si chiede nella revisione.
 */
fun parseReceiptMeta(lines: List<String>, today: LocalDate): ReceiptMeta =
    ReceiptMeta(store = storeOf(lines), date = dateOf(lines, today))

private fun storeOf(lines: List<String>): String? {
    for (raw in lines.take(STORE_SEARCH_LINES)) {
        val line = raw.replace(Regex("\\s+"), " ").trim()
        if (line.isEmpty()) continue
        val upper = line.uppercase(Locale.ITALIAN)
        if (NOT_STORE.containsMatchIn(upper)) continue
        // Una riga con un prezzo e' gia' un prodotto: l'intestazione e' finita.
        if (Regex("\\d+[.,]\\d{2}\\s*$").containsMatchIn(line)) return null
        if (line.count { it.isLetter() } < 3) continue
        return line.lowercase(Locale.ITALIAN)
            .split(' ')
            .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ITALIAN) } }
    }
    return null
}

private fun dateOf(lines: List<String>, today: LocalDate): LocalDate? {
    for (line in lines) {
        for (match in DATE.findAll(line)) {
            val (d, m, y) = match.destructured
            val year = y.toInt().let { if (y.length == 2) 2000 + it else it }
            val date = try {
                LocalDate.of(year, m.toInt(), d.toInt())
            } catch (e: DateTimeException) {
                continue
            }
            if (date.year >= 2000 && !date.isAfter(today.plusDays(1))) return date
        }
    }
    return null
}

/** Insegna e indirizzo stanno nelle prime righe; piu' in basso ci sono i prodotti. */
private const val STORE_SEARCH_LINES = 6

private val DATE = Regex("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?!\\d)")

/** Righe d'intestazione che non sono l'insegna. */
private val NOT_STORE = Regex(
    "DOCUMENTO|COMMERCIALE|SCONTRINO|P\\.?\\s?IVA|PARTITA|C\\.?F\\.|CODICE FISCALE|\\bVIA\\b|" +
        "\\bVIALE\\b|\\bPIAZZA\\b|\\bCORSO\\b|\\bTEL\\b|TELEFONO|BENVENUT|DESCRIZIONE|VENDITA",
)
