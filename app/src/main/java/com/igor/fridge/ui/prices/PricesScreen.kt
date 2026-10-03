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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.domain.prices.ProductPriceSummary
import com.igor.fridge.domain.prices.SimilarOffer
import com.igor.fridge.ui.compare.SearchOnChains
import com.igor.fridge.ui.compare.SimilarOfferRow
import com.igor.fridge.ui.components.ConfirmDeleteDialog
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
    // Il testo vive qui, aggiornato in modo sincrono: passando dallo stato del ViewModel,
    // che arriva con un giro di coroutine, una digitazione veloce perde lettere.
    var query by rememberSaveable { mutableStateOf(state.query) }

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
                    value = query,
                    onValueChange = {
                        query = it
                        viewModel.onQueryChange(it)
                    },
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

/**
 * La scheda di un prodotto: riepilogo, grafico, prezzi nei negozi (tuoi e della comunita'),
 * ricerca sui siti delle catene, codice a barre e ogni acquisto.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceHistoryScreen(
    productKey: String,
    onBack: () -> Unit,
    onScanBarcode: () -> Unit,
    scannedBarcode: String?,
    onBarcodeConsumed: () -> Unit,
    onOpenOpenPrices: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PriceHistoryViewModel = viewModel(
        key = "prices-$productKey",
        factory = PriceHistoryViewModel.factory(productKey),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<PriceRecord?>(null) }
    pendingDelete?.let { record ->
        ConfirmDeleteDialog(
            text = stringResource(R.string.prices_delete_confirm, record.purchasedOn.formatShort()),
            onConfirm = {
                pendingDelete = null
                viewModel.delete(record)
            },
            onDismiss = { pendingDelete = null },
        )
    }
    val summary = state.summary
    val snackbarHostState = remember { SnackbarHostState() }
    var typingBarcode by remember { mutableStateOf(false) }

    LaunchedEffect(scannedBarcode) {
        val code = scannedBarcode ?: return@LaunchedEffect
        viewModel.setBarcode(code)
        onBarcodeConsumed()
    }

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
            if (state.byStore.isNotEmpty()) {
                item { Text(stringResource(R.string.prices_by_store), style = MaterialTheme.typography.titleSmall) }
                items(items = state.byStore, key = { "store-${it.store}" }) { observation ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(observation.store, modifier = Modifier.weight(1f))
                        Text(
                            text = observation.date.formatShort(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(formatUnitPrice(observation.unitPriceCents, observation.referenceUnit))
                    }
                }
            }
            item {
                Text(stringResource(R.string.prices_search_online), style = MaterialTheme.typography.titleSmall)
                SearchOnChains(product = summary.productName, modifier = Modifier.padding(top = 4.dp))
            }
            item {
                BarcodeSection(
                    barcode = state.barcode,
                    onScan = onScanBarcode,
                    onType = { typingBarcode = true },
                    onClear = viewModel::clearBarcode,
                )
            }
            item {
                CommunitySection(
                    state = state,
                    onLoad = viewModel::loadCommunityPrices,
                    onOpenSettings = onOpenOpenPrices,
                )
            }
            item {
                SimilarSection(
                    state = state,
                    onSearch = viewModel::loadSimilarProducts,
                    onOpenSettings = onOpenOpenPrices,
                )
            }
            item { Text(stringResource(R.string.prices_purchases), style = MaterialTheme.typography.titleSmall) }
            items(items = state.records, key = { it.uuid }) { record ->
                PurchaseRow(record = record, onDelete = { pendingDelete = record })
            }
        }
    }

    if (typingBarcode) {
        BarcodeDialog(
            onDismiss = { typingBarcode = false },
            onConfirm = { code ->
                typingBarcode = false
                viewModel.setBarcode(code)
            },
        )
    }
}

@Composable
private fun BarcodeSection(
    barcode: String?,
    onScan: () -> Unit,
    onType: () -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(R.string.prices_barcode), style = MaterialTheme.typography.titleSmall)
        Text(
            text = barcode ?: stringResource(R.string.prices_barcode_none),
            style = MaterialTheme.typography.bodyMedium,
            color = if (barcode == null) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onScan) { Text(stringResource(R.string.prices_barcode_scan)) }
            TextButton(onClick = onType) { Text(stringResource(R.string.prices_barcode_type)) }
            if (barcode != null) {
                TextButton(onClick = onClear) { Text(stringResource(R.string.prices_barcode_remove)) }
            }
        }
    }
}

/**
 * Prodotti dello stesso tipo: dai propri scontrini sempre, dalla comunita' su richiesta
 * (serve la rete). Evidenziati quelli che costano meno al kg o al litro di quanto si paga.
 */
@Composable
private fun SimilarSection(
    state: PriceHistoryUiState,
    onSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val summary = state.summary ?: return
    fun cheaper(offer: SimilarOffer) =
        offer.isComparable && offer.referenceUnit == summary.referenceUnit && offer.unitPriceCents < summary.lastCents
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.similar_title), style = MaterialTheme.typography.titleSmall)
            Text(
                text = stringResource(R.string.similar_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(stringResource(R.string.similar_from_mine), style = MaterialTheme.typography.labelLarge)
            if (state.similarMine.isEmpty()) {
                Text(stringResource(R.string.similar_mine_none), style = MaterialTheme.typography.bodySmall)
            }
            state.similarMine.take(SIMILAR_SHOWN).forEach { SimilarOfferRow(it, cheaper = cheaper(it)) }

            Text(stringResource(R.string.similar_from_community), style = MaterialTheme.typography.labelLarge)
            val community = state.similarCommunity
            when {
                !state.communityEnabled -> {
                    Text(stringResource(R.string.compare_community_off), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_prices_title)) }
                }
                state.isLoadingSimilar -> CircularProgressIndicator()
                community == null -> OutlinedButton(onClick = onSearch) {
                    Text(stringResource(R.string.similar_search))
                }
                else -> {
                    if (community.isEmpty()) {
                        Text(stringResource(R.string.similar_community_none), style = MaterialTheme.typography.bodySmall)
                    }
                    community.take(SIMILAR_SHOWN).forEach { SimilarOfferRow(it, cheaper = cheaper(it)) }
                    TextButton(onClick = onSearch) { Text(stringResource(R.string.similar_search_again)) }
                }
            }
        }
    }
}

@Composable
private fun CommunitySection(
    state: PriceHistoryUiState,
    onLoad: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(R.string.compare_community_title), style = MaterialTheme.typography.titleSmall)
            when {
                !state.communityEnabled -> {
                    Text(stringResource(R.string.compare_community_off), style = MaterialTheme.typography.bodySmall)
                    OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.open_prices_title)) }
                }
                state.barcode == null -> Text(
                    text = stringResource(R.string.prices_community_needs_barcode),
                    style = MaterialTheme.typography.bodySmall,
                )
                state.isLoadingCommunity -> CircularProgressIndicator()
                state.community == null -> OutlinedButton(onClick = onLoad) {
                    Text(stringResource(R.string.compare_community_load))
                }
                state.community.isEmpty() -> Text(
                    text = stringResource(R.string.prices_community_empty),
                    style = MaterialTheme.typography.bodySmall,
                )
                else -> state.community.take(COMMUNITY_SHOWN).forEach { price ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = price.location?.label.orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = price.date.formatShort(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Text(formatEuro(price.priceCents))
                    }
                }
            }
            Text(
                text = stringResource(R.string.open_prices_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** I prezzi della comunita' piu' recenti bastano a farsi un'idea. */
private const val COMMUNITY_SHOWN = 15
private const val SIMILAR_SHOWN = 8

@Composable
private fun BarcodeDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var code by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.prices_barcode_type)) },
        text = {
            OutlinedTextField(
                value = code,
                onValueChange = { text -> code = text.filter { it.isDigit() } },
                label = { Text(stringResource(R.string.prices_barcode)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(code) }, enabled = code.isNotEmpty()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
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
