package com.igor.fridge.domain.receipt

import com.igor.fridge.data.local.QuantityUnit
import java.util.Locale

/** Un prodotto letto dallo scontrino. */
data class ReceiptEntry(
    val name: String,
    /** null se lo scontrino non dice quanto: decide chi conferma. */
    val quantity: Double? = null,
    val unit: QuantityUnit? = null,
    /** Importo della riga in euro, cosi' come stampato. */
    val price: Double? = null,
    /**
     * Quante confezioni: 2 per "LATTE 1L" con "2 x 1,29", 1 per "PASTA 500G" senza
     * dettaglio, 3 per "MOZZARELLA 125G X3". Null se non si sa (un prodotto a peso): a quel
     * punto il prezzo di una confezione non e' ricavabile.
     */
    val pieces: Int? = null,
)

/**
 * Estrae i prodotti dalle righe di uno scontrino italiano (documento commerciale).
 *
 * Una riga prodotto e' un nome seguito da un importo ("LATTE PS 1L  4%  1,29"); l'aliquota
 * IVA e le lettere di reparto in mezzo si ignorano. Le righe di dettaglio danno la quantita'
 * del prodotto che accompagnano: "2 x 0,89" (pezzi) e "0,725 kg x 2,00 €/kg" (peso). Alcuni
 * negozi le stampano sotto il prodotto, altri sopra: si attaccano a quello il cui importo
 * corrisponde al conto, o in mancanza a quello sopra. Altri ancora stampano il nome da solo
 * e l'importo in fondo al dettaglio ("BANANE" e poi "1,234 kg x 1,49  1,84"): il prodotto
 * nasce dal dettaglio. Un formato nel nome ("1L", "500G", "100 G", "6X1,5L", "125G X3")
 * vale come quantita' quando manca il dettaglio.
 *
 * Uno sconto ("SCONTO PASTA -0,20", "PROMO 0,50-") riduce l'importo del prodotto sopra:
 * il prezzo che interessa tenere d'occhio e' quello pagato. Senza segno meno una riga e'
 * uno sconto solo se comincia con la parola ("SCONTO 0,30") e l'importo e' minore di quello
 * sopra: "OFFERTA SPECIALE BISCOTTI 2,50" e "PROMO PASTA 1,29" sono prodotti. Resi e righe
 * di servizio si scartano; dopo il totale non ci sono piu' prodotti.
 * Cio' che non e' leggibile si salta: la schermata di conferma permette di correggere, ma
 * non di scoprire una riga inventata.
 */
fun parseReceipt(lines: List<String>): List<ReceiptEntry> {
    val entries = mutableListOf<ReceiptEntry>()
    // Il dettaglio di una riga gia' completata non deve finire su un'altra.
    var lastHasDetail = false
    // Un dettaglio che non torna col prodotto sopra aspetta quello sotto; se nemmeno
    // quello torna, va a quello sopra (pendingAbove), che e' la disposizione piu' comune.
    var pending: Detail? = null
    var pendingAbove = -1
    // La riga sopra, se era un nome senza importo: lo prende dal dettaglio sotto.
    var nameAbove: List<String>? = null

    fun settlePending() {
        val waiting = pending
        if (waiting != null && pendingAbove >= 0) {
            entries[pendingAbove] = waiting.applyTo(entries[pendingAbove])
        }
        pending = null
        pendingAbove = -1
    }

    for (raw in lines) {
        val line = clean(raw)
        if (line.isEmpty()) continue
        if (isEnd(line)) break
        val tokens = line.split(SPACES)
        val waitingName = nameAbove
        nameAbove = null

        val detail = parseDetail(line)
        if (detail != null) {
            val named = if (waitingName != null && detail.total != null) entryFrom(waitingName, detail.total) else null
            val above = entries.lastIndex
            if (named != null) {
                settlePending()
                entries += detail.applyTo(named)
                lastHasDetail = true
            } else if (above >= 0 && !lastHasDetail && detail.matches(entries[above].price)) {
                entries[above] = detail.applyTo(entries[above])
                lastHasDetail = true
            } else {
                pending = detail
                pendingAbove = if (above >= 0 && !lastHasDetail) above else -1
            }
            continue
        }

        val discount = parseDiscount(tokens, entries.lastOrNull()?.price)
        if (discount != null) {
            val last = entries.lastIndex
            if (last >= 0) {
                val price = entries[last].price
                if (price != null) entries[last] = entries[last].copy(price = (price - discount).coerceAtLeast(0.0))
            }
            continue
        }

        val product = parseProductLine(tokens)
        if (product == null) {
            // Una riga senza importo puo' essere il nome del dettaglio sotto.
            if (tokens.none { PRICE.matches(it) }) nameAbove = tokens
            continue
        }
        val waiting = pending
        if (waiting != null && waiting.matches(product.price)) {
            entries += waiting.applyTo(product)
            lastHasDetail = true
            pending = null
            pendingAbove = -1
        } else {
            settlePending()
            entries += product
            lastHasDetail = false
        }
    }
    settlePending()
    return entries
}

/**
 * Quantita' letta da una riga di dettaglio, con il prezzo unitario per verificarla e,
 * se stampato in fondo, l'importo della riga.
 */
private data class Detail(
    val quantity: Double,
    val unit: QuantityUnit,
    val unitPrice: Double,
    val total: Double? = null,
) {
    fun matches(price: Double?): Boolean =
        price != null && kotlin.math.abs(quantity * unitPrice - price) <= 0.02

    /**
     * "2 x LATTE 1L" fa 2 litri in 2 confezioni: il formato nel nome moltiplica il numero
     * di pezzi, e i pezzi restano a parte. Un dettaglio a peso non dice quante confezioni.
     */
    fun applyTo(entry: ReceiptEntry): ReceiptEntry {
        val count = quantity.toInt().takeIf { unit == QuantityUnit.PZ && quantity % 1.0 == 0.0 }
        return if (count != null && entry.quantity != null && entry.unit != null) {
            entry.copy(quantity = entry.quantity * quantity, pieces = count * (entry.pieces ?: 1))
        } else {
            entry.copy(quantity = quantity, unit = unit, pieces = count)
        }
    }
}

private fun parseDetail(line: String): Detail? {
    WEIGHT.matchEntire(line)?.let { m ->
        val weight = amount(m.groupValues[1]) ?: return null
        val unitPrice = amount(m.groupValues[2]) ?: return null
        return Detail(weight, QuantityUnit.KG, unitPrice, totalIn(m.groupValues[3]))
    }
    PIECES.matchEntire(line)?.let { m ->
        val count = m.groupValues[1].toDoubleOrNull() ?: return null
        val unitPrice = amount(m.groupValues[2]) ?: return null
        if (count <= 0.0) return null
        return Detail(count, QuantityUnit.PZ, unitPrice, totalIn(m.groupValues[3]))
    }
    return null
}

/** L'importo in fondo a un dettaglio ("€/kg  1,84"), se c'e'. */
private fun totalIn(rest: String): Double? =
    rest.split(SPACES).lastOrNull { PRICE.matches(it) && !it.startsWith("-") }?.let(::amount)

/**
 * L'importo di una riga di sconto, in positivo: un importo col segno meno (davanti o, come
 * stampano molte casse, dietro), oppure una riga che comincia dichiarandosi sconto con un
 * importo minore di [priceAbove], il prodotto a cui si applica.
 */
private fun parseDiscount(tokens: List<String>, priceAbove: Double?): Double? {
    var index = tokens.lastIndex
    while (index >= 0 && isMarker(tokens[index])) index--
    if (index < 0) return null
    val token = tokens[index]
    val match = DISCOUNT_AMOUNT.matchEntire(token) ?: return null
    val value = amount(match.groupValues[1])?.takeIf { it > 0.0 } ?: return null
    if (token.startsWith("-") || token.endsWith("-")) return value
    val declared = DISCOUNT_START.containsMatchIn(tokens.first().uppercase(Locale.ITALIAN))
    return value.takeIf { declared && priceAbove != null && it < priceAbove }
}

private fun parseProductLine(tokens: List<String>): ReceiptEntry? {
    // L'importo e' l'ultimo numero con due decimali; dopo possono esserci solo marcatori.
    var priceIndex = tokens.lastIndex
    while (priceIndex >= 0 && isMarker(tokens[priceIndex])) priceIndex--
    if (priceIndex < 1) return null
    val priceToken = tokens[priceIndex]
    if (!PRICE.matches(priceToken)) return null
    val price = amount(priceToken) ?: return null
    if (priceToken.startsWith("-")) return null
    return entryFrom(tokens.subList(0, priceIndex), price)
}

/** Il prodotto dal nome (con formato, codici e marcatori) e dal suo importo. */
private fun entryFrom(tokens: List<String>, price: Double): ReceiptEntry? {
    // Prima dei marcatori: "DI CUI IVA" perderebbe proprio la parola che la scarta.
    if (SKIP_WORDS.containsMatchIn(tokens.joinToString(" ").uppercase(Locale.ITALIAN))) return null
    val nameTokens = tokens.filterNot { isMarker(it) }.toMutableList()
    // I codici articolo (solo cifre, lunghi) non sono parte del nome.
    nameTokens.removeAll { it.length >= 5 && it.all { c -> c in '0'..'9' } }
    // "PROMO PASTA" e' la pasta, in promozione.
    if (nameTokens.firstOrNull()?.uppercase(Locale.ITALIAN) == "PROMO") nameTokens.removeAt(0)

    val size = takeSize(nameTokens)
    val name = nameTokens.joinToString(" ").trim()
    if (name.count { it.isLetter() } < 2) return null

    return ReceiptEntry(
        name = prettify(name),
        quantity = size?.quantity,
        unit = size?.unit,
        price = price,
        // Un formato nel nome senza dettaglio e' una confezione sola, o quelle del multipack.
        pieces = size?.pieces,
    )
}

/** Un formato letto nel nome: quantita' totale e numero di confezioni. */
private data class Size(val quantity: Double, val unit: QuantityUnit, val pieces: Int)

/**
 * Cerca il formato nel nome, dall'ultima parola, e lo toglie: "500G", "100 G", "O,5L" (la O
 * letta al posto dello zero), i multipack "6X1,5L", "125GX3", "125G X3" e "3X 80G".
 */
private fun takeSize(tokens: MutableList<String>): Size? {
    for (i in tokens.indices.reversed()) {
        val token = tokens[i]
        MULTI_SIZE.matchEntire(token)?.let { m ->
            val one = sizeOf(m.groupValues[2], m.groupValues[3])
            val count = m.groupValues[1].toInt()
            if (one != null && count > 0) {
                tokens.removeAt(i)
                return Size(one.first * count, one.second, count)
            }
        }
        SIZE_MULTI.matchEntire(token)?.let { m ->
            val one = sizeOf(m.groupValues[1], m.groupValues[2])
            val count = m.groupValues[3].toInt()
            if (one != null && count > 0) {
                tokens.removeAt(i)
                return Size(one.first * count, one.second, count)
            }
        }
        var start = i
        var end = i
        var one = SIZE.matchEntire(token)?.let { sizeOf(it.groupValues[1], it.groupValues[2]) }
        if (one == null) {
            // Numero e unita' staccati: "PROSCIUTTO 100 G".
            val unit = tokens.getOrNull(i + 1) ?: continue
            if (!NUMBER.matches(token) || !UNIT_WORD.matches(unit)) continue
            one = sizeOf(token, unit) ?: continue
            end = i + 1
        }
        var count = 1
        val after = tokens.getOrNull(end + 1)?.let { MULTIPLIER_AFTER.matchEntire(it) }
        val before = tokens.getOrNull(start - 1)?.let { MULTIPLIER_BEFORE.matchEntire(it) }
        if (after != null) {
            count = after.groupValues[1].toInt()
            end++
        } else if (before != null) {
            count = before.groupValues[1].toInt()
            start--
        }
        if (count <= 0) continue
        repeat(end - start + 1) { tokens.removeAt(start) }
        return Size(one.first * count, one.second, count)
    }
    return null
}

/** Il formato di una confezione; la O al posto dello zero si corregge ("O,5L"). */
private fun sizeOf(number: String, unit: String): Pair<Double, QuantityUnit>? {
    if (number.none { it in '0'..'9' }) return null
    val (reference, factor) = SIZE_UNITS[unit.uppercase(Locale.ITALIAN)] ?: return null
    val value = amount(number.replace('O', '0').replace('o', '0'))?.takeIf { it > 0.0 } ?: return null
    return value * factor to reference
}

/**
 * Il totale chiude i prodotti, anche letto male: l'OCR scambia la O con lo zero
 * ("T0TALE"), quindi nelle parole lo zero torna O prima del confronto.
 */
private fun isEnd(line: String): Boolean {
    val upper = line.uppercase(Locale.ITALIAN).split(SPACES).joinToString(" ") { token ->
        if (token.any { it.isLetter() }) token.replace('0', 'O') else token
    }
    return END.containsMatchIn(upper)
}

/** Aliquote IVA, lettere di reparto, simboli di valuta: stanno fra nome e prezzo. */
private fun isMarker(token: String): Boolean = MARKER.matches(token)

/** "LATTE PS GRANAROLO" diventa "Latte ps granarolo". */
private fun prettify(name: String): String =
    name.lowercase(Locale.ITALIAN).replaceFirstChar { it.titlecase(Locale.ITALIAN) }

/** Toglie il simbolo dell'euro e gli spazi superflui, che l'OCR mette dove capita. */
private fun clean(line: String): String =
    line.replace("€", " ").replace(SPACES, " ").trim()

private fun amount(text: String): Double? =
    text.removePrefix("-").replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

private val SPACES = Regex("\\s+")
private val MARKER = Regex("\\d{1,2}(?:[.,]\\d+)?%|[A-Ea-e]|\\*+|€|EUR|eur|E|VI|IVA\\d*")
private val PRICE = Regex("^-?\\d{1,4}[.,]\\d{2}$")
private val NUMBER = Regex("^[\\dO]+(?:[.,][\\dO]+)?$", RegexOption.IGNORE_CASE)
private val DISCOUNT_AMOUNT = Regex("^-?(\\d{1,4}[.,]\\d{2})-?$")

/** Una riga che si dichiara sconto in apertura; "OFFERTA" invece fa parte dei nomi. */
private val DISCOUNT_START = Regex("^(?:(?:SCONTO|SCONTI|PROMO|ABBUONO|RIDUZIONE)\\b|SC\\.)")
private val PIECES = Regex("^(\\d{1,3})\\s*[xX*]\\s*(\\d{1,4}[.,]\\d{2})(?:\\s(.*))?$")
private val WEIGHT = Regex(
    "^(\\d{1,3}[.,]\\d{1,3})\\s*(?:kg|KG|Kg)\\s*[xX*]\\s*(\\d{1,4}[.,]\\d{2})(?:\\s(.*))?$",
)

private const val SIZE_NUMBER = "([\\dO]+(?:[.,][\\dO]+)?)"
private const val SIZE_UNIT = "(KG|GR|G|LT|L|ML|CL)"
private val SIZE = Regex("^$SIZE_NUMBER$SIZE_UNIT$", RegexOption.IGNORE_CASE)
private val UNIT_WORD = Regex("^$SIZE_UNIT$", RegexOption.IGNORE_CASE)

/** "6X1,5L": sei confezioni da 1,5 litri. */
private val MULTI_SIZE = Regex("^(\\d{1,2})[X*]$SIZE_NUMBER$SIZE_UNIT$", RegexOption.IGNORE_CASE)

/** "125GX3": tre confezioni da 125 grammi. */
private val SIZE_MULTI = Regex("^$SIZE_NUMBER$SIZE_UNIT[X*](\\d{1,2})$", RegexOption.IGNORE_CASE)
private val MULTIPLIER_AFTER = Regex("^[xX*](\\d{1,2})$")
private val MULTIPLIER_BEFORE = Regex("^(\\d{1,2})[xX*]$")

private val SIZE_UNITS: Map<String, Pair<QuantityUnit, Double>> = mapOf(
    "KG" to (QuantityUnit.KG to 1.0),
    "G" to (QuantityUnit.G to 1.0),
    "GR" to (QuantityUnit.G to 1.0),
    "L" to (QuantityUnit.L to 1.0),
    "LT" to (QuantityUnit.L to 1.0),
    "ML" to (QuantityUnit.ML to 1.0),
    "CL" to (QuantityUnit.ML to 10.0),
)

/** Da qui in giu' lo scontrino parla di pagamento, non di prodotti. */
private val END = Regex("^(?:SUB|SOTTO)?TOTALE\\b|^TOT(?:\\.|\\b)|^IMPORTO\\b|\\bIMPORTO PAGATO\\b")

/** Righe con un importo che non sono prodotti. */
private val SKIP_WORDS = Regex(
    "\\b(?:" + listOf(
        "SCONTO", "SCONTI", "RESO", "STORNO", "ABBUONO", "BUONO", "BUONI", "RESTO",
        "CONTANTE", "CONTANTI", "PAGAMENTO", "BANCOMAT", "CREDITO", "ELETTRONICO",
        "ARROTONDAMENTO", "IVA", "IMPONIBILE", "PUNTI", "SALDO", "TESSERA", "TICKET", "CASSA",
        "OPERATORE", "SCONTRINO", "DOCUMENTO", "SHOPPER", "SACCHETTO", "SACCHETTI", "BORSA",
    ).joinToString("|") + ")\\b",
)
