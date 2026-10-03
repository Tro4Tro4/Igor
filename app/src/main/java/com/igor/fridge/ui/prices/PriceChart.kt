package com.igor.fridge.ui.prices

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.ui.formatShort
import com.igor.fridge.ui.formatUnitPrice
import kotlin.math.abs

/**
 * Andamento del prezzo unitario di un prodotto, un punto per acquisto.
 *
 * Una sola serie: niente legenda, il titolo della schermata dice di che prodotto si tratta.
 * Linea da 2 dp e punti da 8 dp con un anello del colore di fondo, griglia tenue, etichette
 * dirette solo su minimo e massimo (mai un numero su ogni punto). Toccando il grafico si
 * legge il dettaglio dell'acquisto piu' vicino nella riga sopra; la tabella sotto il
 * grafico riporta comunque ogni valore, per chi non lo vede.
 *
 * Le ascisse sono le date d'acquisto, cosi' un mese di pausa si vede come tale; se tutti
 * gli acquisti cadono nello stesso giorno si distribuiscono in ordine.
 */
@Composable
fun PriceChart(
    points: List<PriceRecord>,
    description: String,
    modifier: Modifier = Modifier,
) {
    if (points.size < 2) return
    val line = MaterialTheme.colorScheme.primary
    val surface = MaterialTheme.colorScheme.surface
    val grid = MaterialTheme.colorScheme.outlineVariant
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val labelStyle = MaterialTheme.typography.labelSmall.merge(TextStyle(color = labelColor))
    val measurer = rememberTextMeasurer()
    var selected by remember(points) { mutableStateOf(points.lastIndex) }

    val minPrice = points.minOf { it.unitPriceCents }
    val maxPrice = points.maxOf { it.unitPriceCents }
    val firstDay = points.first().purchasedOn.toEpochDay()
    val lastDay = points.last().purchasedOn.toEpochDay()

    Column(modifier = modifier) {
        val chosen = points[selected.coerceIn(0, points.lastIndex)]
        Text(
            text = listOfNotNull(
                chosen.purchasedOn.formatShort(),
                chosen.store,
                formatUnitPrice(chosen.unitPriceCents, chosen.referenceUnit),
            ).joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
        )
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .semantics { contentDescription = description }
                .pointerInput(points) {
                    detectTapGestures { tap ->
                        val xs = xPositions(points, size.width.toFloat(), firstDay, lastDay)
                        selected = xs.indices.minByOrNull { abs(xs[it] - tap.x) } ?: selected
                    }
                },
        ) {
            val padTop = 20.dp.toPx()
            val padBottom = 20.dp.toPx()
            val plotHeight = size.height - padTop - padBottom
            // Un margine del 10% sopra e sotto: la linea non tocca i bordi.
            val span = (maxPrice - minPrice).coerceAtLeast(1)
            val low = minPrice - span * 0.1
            val high = maxPrice + span * 0.1
            fun y(cents: Long): Float = padTop + plotHeight * (1f - ((cents - low) / (high - low)).toFloat())

            // Griglia tenue: tre righe, al minimo, a meta' e al massimo.
            listOf(minPrice, (minPrice + maxPrice) / 2, maxPrice).distinct().forEach { cents ->
                drawLine(grid, Offset(0f, y(cents)), Offset(size.width, y(cents)), strokeWidth = 1.dp.toPx())
            }

            val xs = xPositions(points, size.width, firstDay, lastDay)
            val path = Path()
            points.forEachIndexed { index, record ->
                val p = Offset(xs[index], y(record.unitPriceCents))
                if (index == 0) path.moveTo(p.x, p.y) else path.lineTo(p.x, p.y)
            }
            drawPath(
                path,
                color = line,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            points.forEachIndexed { index, record ->
                val center = Offset(xs[index], y(record.unitPriceCents))
                val radius = if (index == selected) 6.dp.toPx() else 4.dp.toPx()
                drawCircle(surface, radius = radius + 2.dp.toPx(), center = center)
                drawCircle(line, radius = radius, center = center)
            }

            // Etichette dirette solo agli estremi del prezzo.
            fun label(cents: Long, above: Boolean) {
                val index = points.indexOfFirst { it.unitPriceCents == cents }
                val text = measurer.measure(formatUnitPrice(cents, points[index].referenceUnit), labelStyle)
                // maxOf: in una misura intermedia il grafico puo' essere piu' stretto
                // dell'etichetta, e coerceIn con il massimo sotto il minimo lancia.
                val x = (xs[index] - text.size.width / 2f)
                    .coerceIn(0f, maxOf(0f, size.width - text.size.width))
                val yPos = y(cents) + if (above) -text.size.height - 6.dp.toPx() else 6.dp.toPx()
                drawText(text, topLeft = Offset(x, yPos.coerceIn(0f, maxOf(0f, size.height - text.size.height))))
            }
            label(maxPrice, above = true)
            if (minPrice != maxPrice) label(minPrice, above = false)
        }
    }
}

/** Ascisse dei punti, con un margine ai lati perche' i punti non vengano tagliati. */
private fun xPositions(points: List<PriceRecord>, width: Float, firstDay: Long, lastDay: Long): List<Float> {
    val margin = 12f
    val usable = width - 2 * margin
    return if (lastDay > firstDay) {
        points.map { margin + usable * (it.purchasedOn.toEpochDay() - firstDay) / (lastDay - firstDay).toFloat() }
    } else {
        points.indices.map { margin + usable * it / (points.size - 1).toFloat() }
    }
}
