package com.igor.fridge.ui.prices

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
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.domain.prices.ProductPriceSummary
import com.igor.fridge.ui.formatChange
import com.igor.fridge.ui.formatEuro
import com.igor.fridge.ui.formatMonth
import com.igor.fridge.ui.formatQuantity
import com.igor.fridge.ui.formatShort
import com.igor.fridge.ui.formatUnitPrice
import com.igor.fridge.ui.label

/** I prodotti comprati, con il prezzo dell'ultima volta e come si e' mosso. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PricesScreen(
    onBack: () -> Unit,
    onOpenProduct: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PricesViewModel = viewModel(factory = PricesViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.prices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (state.isEmpty) {
            Text(
                text = stringResource(R.string.prices_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(padding)
                    .padding(32.dp),
            )
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.monthly.isNotEmpty()) {
                item {
                    Text(stringResource(R.string.prices_monthly), style = MaterialTheme.typography.titleSmall)
                }
                items(items = state.monthly.take(MONTHS_SHOWN), key = { it.first.toString() }) { (month, cents) ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Text(month.formatMonth(), modifier = Modifier.weight(1f))
                        Text(formatEuro(cents), fontWeight = FontWeight.SemiBold)
                    }
                }
                item { HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp)) }
            }
            item {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = viewModel::onQueryChange,
                    label = { Text(stringResource(R.string.inventory_search)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            items(items = state.products, key = { it.productKey }) { product ->
                ProductCard(product = product, onClick = { onOpenProduct(product.productKey) })
            }
        }
    }
}

/** Gli ultimi mesi bastano a vedere l'andamento della spesa. */
private const val MONTHS_SHOWN = 6

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProductCard(product: ProductPriceSummary, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = product.productName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = formatUnitPrice(product.lastCents, product.referenceUnit),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            product.changePercent?.let { ChangeLabel(it) }
            Text(
                text = stringResource(
                    R.string.prices_range,
                    formatUnitPrice(product.minCents, product.referenceUnit),
                    formatUnitPrice(product.maxCents, product.referenceUnit),
                    product.purchases,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = listOfNotNull(product.lastDate.formatShort(), product.lastStore).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Freccia e percentuale rispetto all'acquisto precedente: il colore accompagna, non
 * sostituisce, il segno e la freccia (chi non distingue i colori legge comunque).
 */
@Composable
private fun ChangeLabel(percent: Double) {
    val (arrow, color) = when {
        percent > 0.05 -> "▲" to MaterialTheme.colorScheme.error
        percent < -0.05 -> "▼" to MaterialTheme.colorScheme.primary
        else -> "=" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = stringResource(R.string.prices_change, arrow, formatChange(percent)),
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}

/** La storia di un prodotto: riepilogo, grafico e ogni acquisto. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceHistoryScreen(
    productKey: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PriceHistoryViewModel = viewModel(
        key = "prices-$productKey",
        factory = PriceHistoryViewModel.factory(productKey),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val summary = state.summary

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(summary?.productName ?: stringResource(R.string.prices_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        if (summary == null) {
            if (!state.isLoading) {
                Text(
                    text = stringResource(R.string.prices_none_left),
                    modifier = Modifier
                        .padding(padding)
                        .padding(32.dp),
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatTile(stringResource(R.string.prices_last), formatEuro(summary.lastCents), Modifier.weight(1f))
                    StatTile(stringResource(R.string.prices_min), formatEuro(summary.minCents), Modifier.weight(1f))
                    StatTile(stringResource(R.string.prices_average), formatEuro(summary.averageCents), Modifier.weight(1f))
                    StatTile(stringResource(R.string.prices_max), formatEuro(summary.maxCents), Modifier.weight(1f))
                }
            }
            item {
                Text(
                    text = stringResource(
                        R.string.prices_reference,
                        summary.referenceUnit.label(),
                        formatEuro(summary.totalSpentCents),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state.chartPoints.size >= 2) {
                item {
                    PriceChart(
                        points = state.chartPoints,
                        description = stringResource(
                            R.string.prices_chart_description,
                            summary.productName,
                            state.chartPoints.size,
                        ),
                    )
                }
            }
            item { Text(stringResource(R.string.prices_purchases), style = MaterialTheme.typography.titleSmall) }
            items(items = state.records, key = { it.uuid }) { record ->
                PurchaseRow(record = record, onDelete = { viewModel.delete(record) })
            }
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

@Composable
private fun PurchaseRow(record: PriceRecord, onDelete: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = listOfNotNull(record.purchasedOn.formatShort(), record.store).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "${formatQuantity(record.quantity, record.unit)} · ${formatEuro(record.totalCents)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(formatUnitPrice(record.unitPriceCents, record.referenceUnit))
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.prices_delete, record.purchasedOn.formatShort()),
            )
        }
    }
}
