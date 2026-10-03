package com.igor.fridge.ui.compare

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.domain.prices.ItemQuote
import com.igor.fridge.domain.prices.PriceSource
import com.igor.fridge.domain.prices.StoreEstimate
import com.igor.fridge.ui.formatEuro
import com.igor.fridge.ui.formatQuantity
import com.igor.fridge.ui.formatShort

/** La lista della spesa nei vari supermercati. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompareScreen(
    onBack: () -> Unit,
    onOpenOpenPrices: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: CompareViewModel = viewModel(factory = CompareViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                CommunityBox(
                    enabled = state.communityEnabled,
                    itemsWithBarcode = state.itemsWithBarcode,
                    loaded = state.communityLoaded,
                    loading = state.isLoadingCommunity,
                    onLoad = viewModel::loadCommunityPrices,
                    onOpenSettings = onOpenOpenPrices,
                )
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
                QuoteCard(quote)
            }
            item {
                Text(
                    text = stringResource(R.string.open_prices_attribution),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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
private fun CommunityBox(
    enabled: Boolean,
    itemsWithBarcode: Int,
    loaded: Boolean,
    loading: Boolean,
    onLoad: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.compare_community_title), style = MaterialTheme.typography.titleSmall)
            when {
                !enabled -> {
                    Text(stringResource(R.string.compare_community_off), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_prices_title)) }
                }
                loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(stringResource(R.string.compare_community_loading), modifier = Modifier.padding(start = 12.dp))
                }
                else -> {
                    Text(
                        text = stringResource(R.string.compare_community_help, itemsWithBarcode),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    OutlinedButton(onClick = onLoad) {
                        Text(
                            stringResource(
                                if (loaded) R.string.compare_community_reload else R.string.compare_community_load,
                            ),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QuoteCard(quote: ItemQuote) {
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
                            if (observation.source == PriceSource.MINE) {
                                R.string.compare_source_mine
                            } else {
                                R.string.compare_source_community
                            },
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
            SearchOnChains(product = quote.item.name, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
