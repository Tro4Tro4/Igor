package com.igor.fridge.ui.compare

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.igor.fridge.R
import com.igor.fridge.domain.prices.PriceSource
import com.igor.fridge.domain.prices.SimilarOffer
import com.igor.fridge.ui.formatEuro
import com.igor.fridge.ui.formatShort
import com.igor.fridge.ui.formatUnitPrice

/**
 * Un prodotto simile: nome e prezzo al kg o al litro, poi negozio, marca e da dove viene
 * il prezzo. [cheaper] lo evidenzia: costa meno, a parita' di peso o volume, di quello
 * che si compra di solito.
 */
@Composable
fun SimilarOfferRow(offer: SimilarOffer, cheaper: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = offer.productName,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            )
            Text(
                text = if (offer.isComparable) {
                    formatUnitPrice(offer.unitPriceCents, offer.referenceUnit)
                } else {
                    formatEuro(offer.priceCents)
                },
                fontWeight = if (cheaper) FontWeight.SemiBold else FontWeight.Normal,
                color = if (cheaper) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = offerDetails(offer),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun offerDetails(offer: SimilarOffer): String {
    val brand = offer.privateLabel?.let { stringResource(R.string.similar_private_label, it) } ?: offer.brands
    val source = stringResource(
        if (offer.source == PriceSource.MINE) R.string.compare_source_mine else R.string.compare_source_community,
        offer.date.formatShort(),
    )
    val pack = if (offer.isComparable) formatEuro(offer.priceCents) else stringResource(R.string.similar_pack_only)
    return listOfNotNull(offer.store, brand, pack, source).joinToString(" · ")
}
