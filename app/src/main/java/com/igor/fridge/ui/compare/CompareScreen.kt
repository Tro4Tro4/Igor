package com.igor.fridge.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.domain.prices.Alternative
import com.igor.fridge.domain.prices.ItemQuote
import com.igor.fridge.domain.prices.StoreEstimate
import com.igor.fridge.ui.formatEuro
import com.igor.fridge.ui.formatQuantity
import com.igor.fridge.ui.formatShort
import com.igor.fridge.ui.label

/** La lista della spesa nei vari supermercati. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompareViewModel = viewModel(factory = CompareViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.compare_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        val comparison = state.comparison
        if (comparison == null || state.itemCount == 0) {
            if (!state.isLoading) {
                Text(
                    text = stringResource(R.string.compare_empty_list),
                    modifier = Modifier
                        .padding(padding)
                        .padding(32.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(stringResource(R.string.compare_stores), style = MaterialTheme.typography.titleSmall)
                Text(stringResource(R.string.compare_receipts_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (comparison.stores.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.compare_no_prices),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items(items = comparison.stores, key = { "store-${it.store}" }) { estimate ->
                    StoreRow(estimate)
                }
                item {
                    Text(
                        text = stringResource(
                            R.string.compare_split,
                            formatEuro(comparison.splitCents),
                            comparison.pricedItems,
                            state.itemCount,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }

            item {
                HorizontalDivider()
                Text(
                    stringResource(R.string.compare_items),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            items(items = comparison.quotes, key = { "item-${it.item.uuid}" }) { quote ->
                QuoteCard(quote, alternative = state.alternatives[quote.item.uuid])
            }
        }
    }
}

@Composable
private fun StoreRow(estimate: StoreEstimate) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(estimate.store, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (estimate.isComplete) {
                    stringResource(R.string.compare_complete)
                } else {
                    stringResource(R.string.compare_coverage, estimate.covered, estimate.itemCount)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatEuro(estimate.totalCents), fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun QuoteCard(quote: ItemQuote, alternative: Alternative?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = quote.item.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatQuantity(quote.item.quantity, quote.item.unit),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (quote.byStore.isEmpty()) {
                Text(
                    text = stringResource(R.string.compare_item_no_prices),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            quote.byStore.forEachIndexed { index, (observation, cents) ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = observation.store,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = stringResource(
                            R.string.compare_source_mine,
                            observation.date.formatShort(),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                    Text(
                        text = formatEuro(cents),
                        fontWeight = if (index == 0) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
            if (alternative != null) {
                Text(
                    text = stringResource(
                        R.string.compare_alternative,
                        alternative.savingPercent,
                        alternative.offer.referenceUnit.label(),
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
                SimilarOfferRow(alternative.offer, cheaper = true)
            }
        }
    }
}
