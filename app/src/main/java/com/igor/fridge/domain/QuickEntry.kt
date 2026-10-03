package com.igor.fridge.domain

import com.igor.fridge.data.local.QuantityUnit

/** Cio' che si capisce da una riga scritta di corsa: "2 kg mele", "latte x2", "6 uova". */
data class QuickEntry(
    val name: String,
    /** null se la riga non dice quanto: vale la quantita' di default. */
    val quantity: Double? = null,
    val unit: QuantityUnit? = null,
)

/**
 * Separa nome e quantita' in una riga dell'aggiunta rapida.
 *
 * La quantita' puo' stare davanti ("2 kg mele", "500g farina", "6 uova", "mezzo chilo di
 * pane") o in fondo ("mele 2 kg", "latte x2", "uova 6"). Un numero senza unita' conta i
 * pezzi. Se la riga non e' riconoscibile resta tutta nome: meglio un nome con un numero
 * dentro che una quantita' inventata.
 */
fun parseQuickEntry(text: String): QuickEntry {
    val tokens = tokenize(text)
    if (tokens.isEmpty()) return QuickEntry(name = "")

    leading(tokens)?.let { return it }
    trailing(tokens)?.let { return it }
    return QuickEntry(name = tokens.joinToString(" "))
}

/** "2 kg mele", "2kg mele", "6 uova", "mezzo chilo di farina", "2 x latte". */
private fun leading(tokens: List<String>): QuickEntry? {
    val (amount, used) = amountAt(tokens, 0) ?: return null
    var index = used
    var unit: QuantityUnit? = amount.unit
    var quantity = amount.value
    if (unit == null && index < tokens.size) {
        val parsed = UNITS[tokens[index].lowercase()]
        if (parsed != null) {
            unit = parsed.first
            quantity *= parsed.second
            index++
        }
    }
    if (index < tokens.size && tokens[index].lowercase() in setOf("x", "di", "d")) index++
    val name = tokens.drop(index).joinToString(" ")
    if (name.none { it.isLetter() }) return null
    return QuickEntry(name = name, quantity = quantity, unit = unit ?: QuantityUnit.PZ)
}

/** "mele 2 kg", "mele 2kg", "latte x2", "latte x 2", "uova 6". */
private fun trailing(tokens: List<String>): QuickEntry? {
    if (tokens.size < 2) return null
    val last = tokens.last().lowercase()

    // "latte x2" / "latte x 2"
    Regex("^x(\\d+)$").matchEntire(last)?.let { match ->
        val name = tokens.dropLast(1).joinToString(" ")
        return QuickEntry(name, match.groupValues[1].toDouble(), QuantityUnit.PZ)
    }
    if (tokens.size >= 3 && tokens[tokens.size - 2].lowercase() == "x") {
        number(last)?.let { value ->
            return QuickEntry(tokens.dropLast(2).joinToString(" "), value, QuantityUnit.PZ)
        }
    }

    // "mele 2kg"
    attachedUnit(last)?.let { (value, unit) ->
        return QuickEntry(tokens.dropLast(1).joinToString(" "), value, unit)
    }
    // "mele 2 kg"
    if (tokens.size >= 3) {
        val unit = UNITS[last]
        val value = number(tokens[tokens.size - 2].lowercase())
        if (unit != null && value != null) {
            return QuickEntry(tokens.dropLast(2).joinToString(" "), value * unit.second, unit.first)
        }
    }
    // "uova 6"
    number(last)?.let { value ->
        if (value % 1.0 == 0.0) return QuickEntry(tokens.dropLast(1).joinToString(" "), value, QuantityUnit.PZ)
    }
    return null
}

private data class Amount(val value: Double, val unit: QuantityUnit?)

/** Un numero ("2", "1,5", "mezzo", "un") con l'unita' eventualmente attaccata ("500g"). */
private fun amountAt(tokens: List<String>, index: Int): Pair<Amount, Int>? {
    val token = tokens.getOrNull(index)?.lowercase() ?: return null
    attachedUnit(token)?.let { (value, unit) -> return Amount(value, unit) to index + 1 }
    number(token)?.let { return Amount(it, null) to index + 1 }
    WORD_NUMBERS[token]?.let { value ->
        // "un chilo di", "mezzo kg": la parola da sola ("una mela") non e' una quantita'.
        val next = tokens.getOrNull(index + 1)?.lowercase()
        if (next != null && next in UNITS) return Amount(value, null) to index + 1
    }
    return null
}

private fun attachedUnit(token: String): Pair<Double, QuantityUnit>? {
    val match = Regex("^(\\d+(?:[.,]\\d+)?)([a-z]+\\.?)$").matchEntire(token) ?: return null
    val unit = UNITS[match.groupValues[2]] ?: return null
    val value = number(match.groupValues[1]) ?: return null
    return value * unit.second to unit.first
}

private fun number(token: String): Double? =
    token.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0.0 && it.isFinite() }

private fun tokenize(text: String): List<String> =
    text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }

private val WORD_NUMBERS = mapOf("un" to 1.0, "uno" to 1.0, "una" to 1.0, "mezzo" to 0.5, "mezza" to 0.5)

/** Unita' riconosciute, con il fattore che le porta all'unita' dell'app ("etto" = 100 g). */
private val UNITS: Map<String, Pair<QuantityUnit, Double>> = buildMap {
    fun put(unit: QuantityUnit, factor: Double, vararg words: String) =
        words.forEach { put(it, unit to factor) }
    put(QuantityUnit.KG, 1.0, "kg", "kg.", "chilo", "chili", "kilo", "chilogrammi")
    put(QuantityUnit.G, 1.0, "g", "g.", "gr", "gr.", "grammi", "grammo")
    put(QuantityUnit.G, 100.0, "etto", "etti", "hg")
    put(QuantityUnit.L, 1.0, "l", "l.", "lt", "lt.", "litro", "litri")
    put(QuantityUnit.ML, 1.0, "ml", "ml.")
    put(QuantityUnit.ML, 10.0, "cl", "cl.")
    put(QuantityUnit.PZ, 1.0, "pz", "pz.", "pezzi", "pezzo")
    put(QuantityUnit.CONF, 1.0, "conf", "conf.", "confezione", "confezioni", "pacco", "pacchi", "pacchetto", "pacchetti", "scatola", "scatole", "bottiglia", "bottiglie")
}
