package com.igor.fridge.ui.compare

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.igor.fridge.R
import com.igor.fridge.data.local.OnlineOffer
import com.igor.fridge.domain.prices.OnlineFreshness
import com.igor.fridge.ui.formatEuro
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun OnlinePricesSection(
    state: OnlineCompareUiState,
    onRefresh: () -> Unit,
    onSearch: (String) -> Unit,
    onSelect: (String,String) -> Unit,
    onUnselect: (String,String) -> Unit,
    onOpenSource: (String) -> Unit,
    onSettings: () -> Unit,
) {
    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.online_prices_title),style=MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.online_prices_scope,state.settings.postcode),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        when {
            !state.settings.enabled -> {
                Text(stringResource(R.string.online_prices_off))
                OutlinedButton(onClick=onSettings) { Text(stringResource(R.string.settings_title)) }
            }
            !state.configured -> Text(stringResource(R.string.online_prices_unconfigured),color=MaterialTheme.colorScheme.onSurfaceVariant)
            else -> OutlinedButton(onClick=onRefresh,enabled=!state.refreshing) {
                Text(stringResource(if (state.refreshing) R.string.online_prices_refreshing else R.string.online_prices_refresh))
            }
        }
        if (state.refreshing) LinearProgressIndicator(modifier=Modifier.fillMaxWidth())
        state.message?.let { Text(it,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite },color=MaterialTheme.colorScheme.error) }
        val sources = state.sources.sortedBy { if (it.source == "esselunga" && it.status != "active") 0 else it.priority }
        if (sources.isEmpty()) {
            Text(stringResource(R.string.online_prices_pending),style=MaterialTheme.typography.bodyMedium)
        } else sources.forEach { source ->
            val estimate=state.comparison.estimates.firstOrNull { it.source == source.source }
            Text(chainLabel(source.source),style=MaterialTheme.typography.titleSmall)
            if (source.status == "active") {
                Text(stringResource(R.string.online_prices_coverage,estimate?.covered ?: 0,state.items.size),style=MaterialTheme.typography.bodySmall)
                estimate?.commonTotalCents?.let { Text(formatEuro(it),style=MaterialTheme.typography.titleMedium) }
            } else Text(stringResource(if (source.status == "suspended") R.string.online_prices_suspended else R.string.online_prices_unavailable),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (state.comparison.commonItemUuids.isNotEmpty()) {
            Text(stringResource(R.string.online_prices_common,state.comparison.commonItemUuids.size),style=MaterialTheme.typography.bodySmall)
            val totals=state.comparison.estimates.mapNotNull { it.commonTotalCents }
            if (totals.size >= 2 && totals.count { it == totals.minOrNull() } > 1) Text(stringResource(R.string.online_prices_tie))
        } else if (state.comparison.quotes.isNotEmpty()) Text(stringResource(R.string.online_prices_no_common),style=MaterialTheme.typography.bodySmall)
        if (state.settings.enabled) state.items.forEach { item ->
            HorizontalDivider()
            Text(listOfNotNull(item.name,item.brand).joinToString(" · "),style=MaterialTheme.typography.titleMedium)
            state.comparison.quotes.filter { it.shoppingUuid == item.uuid }.forEach { quote ->
                val offer=state.offers.firstOrNull { it.offerId == quote.offerId }
                if (offer != null) {
                    OfferDetails(offer,onOpenSource)
                    if (quote.freshness == OnlineFreshness.OLD) Text(stringResource(R.string.online_prices_old),style=MaterialTheme.typography.bodySmall)
                    quote.totalCents?.let { Text(stringResource(R.string.online_prices_item_cost,formatEuro(it))) }
                    TextButton(onClick={onUnselect(item.uuid,quote.source)}) { Text(stringResource(R.string.online_prices_unselect)) }
                }
            }
            if (state.configured) OutlinedButton(onClick={onSearch(item.uuid)},enabled=item.uuid !in state.searching && !state.refreshing) {
                Text(stringResource(if (item.uuid in state.searching) R.string.online_prices_searching else R.string.online_prices_search))
            }
            state.candidates[item.uuid]?.let { candidates ->
                if (candidates.isEmpty()) Text(stringResource(R.string.online_prices_no_candidates))
                candidates.forEach { candidate ->
                    OfferDetails(candidate,onOpenSource)
                    OutlinedButton(onClick={onSelect(item.uuid,candidate.offerId)}) { Text(stringResource(R.string.online_prices_select)) }
                }
            }
        }
        Text(stringResource(R.string.online_prices_delivery),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
    }
}

@Composable
private fun OfferDetails(offer: OnlineOffer,onOpenSource: (String)->Unit) {
    Text(listOfNotNull(offer.name,offer.brand).joinToString(" · "),style=MaterialTheme.typography.bodyMedium)
    val format=offer.packAmount?.let { "$it ${offer.packUnit?.lowercase()}" } ?: stringResource(R.string.online_prices_no_format)
    Text(stringResource(R.string.online_prices_offer,chainLabel(offer.source),format,formatEuro(offer.packPriceCents)),style=MaterialTheme.typography.bodyMedium)
    Text(stringResource(if (offer.scope == "generic") R.string.online_prices_generic else R.string.online_prices_zone,offer.postcode.orEmpty()),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    if (offer.condition != "ordinary") Text(stringResource(R.string.online_prices_conditional),style=MaterialTheme.typography.bodySmall)
    Text(stringResource(R.string.online_prices_observed,DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.systemDefault()).format(offer.observedAt)),style=MaterialTheme.typography.bodySmall)
    TextButton(onClick={onOpenSource(offer.sourceUrl)}) { Text(stringResource(R.string.online_prices_source)) }
}
private fun chainLabel(source: String): String = when(source) {
    "carrefour" -> "Carrefour"; "conad" -> "Conad"; "esselunga" -> "Esselunga"
    "tigros" -> "Tigros"; "lidl" -> "Lidl"; "eurospin" -> "Eurospin"; else -> source
}
