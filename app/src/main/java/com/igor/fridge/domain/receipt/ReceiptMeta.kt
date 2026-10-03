package com.igor.fridge.domain.receipt

import com.igor.fridge.domain.prices.chainOf
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
 * Il negozio e' l'insegna in testa allo scontrino: la prima riga in cui si riconosce una
 * catena nota, altrimenti la prima riga con un nome vero (l'insegna viene prima
 * dell'indirizzo e della partita IVA). CAP, indirizzi, date e parole generiche come
 * "Supermercato" non sono un'insegna.
 *
 * La data e' quella stampata accanto all'ora, di solito in fondo; in mancanza, l'ultima
 * data plausibile dello scontrino, perche' lotti e numeri di documento che somigliano a una
 * data stanno piu' in alto. Plausibile vuol dire non piu' di un giorno nel futuro rispetto
 * a [today] e non prima del 2000. Cio' che non si trova resta null e lo si chiede nella
 * revisione.
 */
fun parseReceiptMeta(lines: List<String>, today: LocalDate): ReceiptMeta =
    ReceiptMeta(store = storeOf(lines), date = dateOf(lines, today))

private fun storeOf(lines: List<String>): String? {
    val header = mutableListOf<String>()
    for (raw in lines.take(STORE_SEARCH_LINES)) {
        val line = raw.replace(SPACES, " ").trim()
        if (line.isEmpty()) continue
        // Una riga con un prezzo e' gia' un prodotto: l'intestazione e' finita. Un telefono
        // ("TEL 02.123.45.67") somiglia a un prezzo ma e' ancora intestazione.
        val headerOnly = NOT_STORE.containsMatchIn(line.uppercase(Locale.ITALIAN))
        if (!headerOnly && PRICE_AT_END.containsMatchIn(line)) break
        header += line
    }
    val store = header.firstOrNull { chainOf(it) != null } ?: header.firstOrNull(::looksLikeStore)
    return store?.lowercase(Locale.ITALIAN)
        ?.split(' ')
        ?.joinToString(" ") { word -> word.replaceFirstChar { it.titlecase(Locale.ITALIAN) } }
}

private fun looksLikeStore(line: String): Boolean {
    val upper = line.uppercase(Locale.ITALIAN)
    if (NOT_STORE.containsMatchIn(upper) || ADDRESS.containsMatchIn(upper)) return false
    if (DATE.containsMatchIn(line) || ISO_DATE.containsMatchIn(line)) return false
    return upper.replace(GENERIC, " ").count { it.isLetter() } >= 3
}

private fun dateOf(lines: List<String>, today: LocalDate): LocalDate? {
    var last: LocalDate? = null
    for (line in lines) {
        val dates = datesIn(line).filter { it.year >= 2000 && !it.isAfter(today.plusDays(1)) }
        if (dates.isEmpty()) continue
        if (TIME.containsMatchIn(line)) return dates.first()
        last = dates.last()
    }
    return last
}

/** Le date valide di una riga, nell'ordine in cui compaiono. */
private fun datesIn(line: String): List<LocalDate> {
    val found = DATE.findAll(line).map { m ->
        val (d, mo, y) = m.destructured
        m.range.first to Triple(d, mo, y)
    } + ISO_DATE.findAll(line).map { m ->
        val (y, mo, d) = m.destructured
        m.range.first to Triple(d, mo, y)
    }
    return found.sortedBy { it.first }.mapNotNull { (_, parts) ->
        val (d, m, y) = parts
        val year = y.toInt().let { if (y.length == 2) 2000 + it else it }
        try {
            LocalDate.of(year, m.toInt(), d.toInt())
        } catch (e: DateTimeException) {
            null
        }
    }.toList()
}

/** Insegna e indirizzo stanno nelle prime righe; piu' in basso ci sono i prodotti. */
private const val STORE_SEARCH_LINES = 6

private val SPACES = Regex("\\s+")
private val PRICE_AT_END = Regex("\\d+[.,]\\d{2}\\s*$")

private val DATE = Regex("(?<!\\d)(\\d{1,2})[/.\\-](\\d{1,2})[/.\\-](\\d{4}|\\d{2})(?!\\d)")
private val ISO_DATE = Regex("(?<!\\d)(\\d{4})-(\\d{1,2})-(\\d{1,2})(?!\\d)")
private val TIME = Regex("(?<!\\d)\\d{1,2}:\\d{2}(?!\\d)")

/** Righe d'intestazione che non sono l'insegna. */
private val NOT_STORE = Regex(
    "DOCUMENTO|COMMERCIALE|SCONTRINO|P\\.?\\s?IVA|PARTITA|C\\.?F\\.|CODICE FISCALE|\\bVIA\\b|" +
        "\\bVIALE\\b|\\bPIAZZA\\b|\\bCORSO\\b|\\bTEL\\b|TELEFONO|BENVENUT|DESCRIZIONE|VENDITA",
)

/** Un CAP in testa ("20121 MILANO MI") o un indirizzo abbreviato ("C.SO", "V.LE", "P.ZZA"). */
private val ADDRESS = Regex("^\\d{5}\\b|^(?:C\\.?SO|V\\.?LE|P\\.?ZZA|VIA|VIALE|PIAZZA)\\b")

/** Parole che dicono che e' un negozio, non quale. */
private val GENERIC = Regex("\\b(?:SUPERMERCAT[OI]|IPERMERCAT[OI])\\b")
