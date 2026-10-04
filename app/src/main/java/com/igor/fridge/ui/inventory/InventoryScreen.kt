package com.igor.fridge.ui.inventory

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.ui.components.FoodItemCard
import com.igor.fridge.ui.label
import com.igor.fridge.ui.labelRes
import com.igor.fridge.ui.icon
import com.igor.fridge.ui.components.CategoryPickerSheet
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun InventoryScreen(
    onAddItem: () -> Unit,
    onEditItem: (String) -> Unit,
    onOpenShoppingList: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenReceipt: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    // Il testo vive qui, aggiornato in modo sincrono: passando dallo stato del ViewModel,
    // che arriva con un giro di coroutine, una digitazione veloce perde lettere.
    var query by rememberSaveable { mutableStateOf(state.query) }
    val snackbarHostState = remember { SnackbarHostState() }

    val undoLabel = stringResource(R.string.action_undo)
    var menuOpen by remember { mutableStateOf(false) }
    var categoryOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.messageId) {
        val message = state.message ?: return@LaunchedEffect
        val messageId = state.messageId
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (state.canUndo) undoLabel else null,
            duration = if (state.canUndo) SnackbarDuration.Long else SnackbarDuration.Short,
        )
        if (result == SnackbarResult.ActionPerformed) viewModel.undo(messageId) else viewModel.onMessageShown(messageId)
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.inventory_title)) },
                // Due azioni in vista e il resto nel menu: con quattro icone il titolo
                // veniva troncato sugli schermi stretti.
                actions = {
                    IconButton(onClick = onOpenShoppingList) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = stringResource(R.string.action_open_shopping),
                        )
                    }
                    IconButton(onClick = onOpenReceipt) {
                        Icon(
                            imageVector = Icons.Filled.Receipt,
                            contentDescription = stringResource(R.string.receipt_title),
                        )
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(
                                imageVector = Icons.Filled.MoreVert,
                                contentDescription = stringResource(R.string.action_more),
                            )
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_add_expiring)) },
                                leadingIcon = {
                                    Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null)
                                },
                                onClick = {
                                    menuOpen = false
                                    viewModel.addExpiringToShoppingList()
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.action_settings)) },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                onClick = {
                                    menuOpen = false
                                    onOpenSettings()
                                },
                            )
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add_item))
            }
        },
    ) { padding ->
        // Anche i filtri scorrono: font grandi e altezza ridotta non nascondono le azioni.
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "controls") {
                Column {
                    OutlinedTextField(
                        value = query,
                        onValueChange = {
                            query = it
                            viewModel.onQueryChange(it)
                        },
                        label = { Text(stringResource(R.string.inventory_search)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )

                    FilterRow(state, viewModel::onFilterChange, viewModel::onLocationChange) {
                        categoryOpen = true
                    }
                    Text(stringResource(R.string.inventory_shown_count, state.items.size, state.totalCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))

                }
            }
            when {
                state.isLoading -> item(key = "loading") {
                    Box(Modifier.fillParentMaxHeight(0.5f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Text(stringResource(R.string.inventory_loading), Modifier.padding(top = 12.dp))
                        }
                    }
                }
                state.items.isEmpty() -> item(key = "empty") {
                    EmptyState(
                        filtered = state.query.isNotBlank() || state.filter != InventoryFilter.TUTTI ||
                            state.category != null || state.location != null,
                        onAdd = onAddItem,
                        onReset = { query = ""; viewModel.resetFilters() },
                    )
                }
                else -> state.sections.forEach { section ->
                    stickyHeader(key = "category:${section.category.name}") {
                        Surface(color = MaterialTheme.colorScheme.surface) {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Text(section.category.icon(), Modifier.clearAndSetSemantics {})
                                Text(stringResource(section.category.labelRes()),
                                    modifier = Modifier.weight(1f).padding(start = 8.dp).semantics { heading() },
                                    style = MaterialTheme.typography.titleSmall)
                                Text(section.items.size.toString(), style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    items(section.items, key = { "food:${it.uuid}" }) { item ->
                        FoodItemCard(item, state.today, state.warningDays,
                            onClick = { onEditItem(item.uuid) },
                            onConsume = { viewModel.consume(item) },
                            onDelete = { viewModel.delete(item) },
                            modifier = Modifier.padding(horizontal = 16.dp))
                    }
                }
            }
        }
    }

    if (categoryOpen) {
        CategoryPickerSheet(state.category, state.availableCategories, true,
            onSelect = viewModel::onCategoryChange, onDismiss = { categoryOpen = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterRow(
    state: InventoryUiState,
    onFilterChange: (InventoryFilter) -> Unit,
    onLocationChange: (StorageLocation?) -> Unit,
    onCategoryClick: () -> Unit,
) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
        .padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(state.filter == InventoryFilter.TUTTI, { onFilterChange(InventoryFilter.TUTTI) },
            label = { Text(stringResource(R.string.filter_all, state.matchingCount)) })
        FilterChip(state.filter == InventoryFilter.IN_SCADENZA, { onFilterChange(InventoryFilter.IN_SCADENZA) },
            label = { Text(stringResource(R.string.filter_expiring, state.expiringCount)) })
        FilterChip(state.filter == InventoryFilter.SCADUTI, { onFilterChange(InventoryFilter.SCADUTI) },
            label = { Text(stringResource(R.string.filter_expired, state.expiredCount)) })
        FilterChip(state.filter == InventoryFilter.SENZA_DATA, { onFilterChange(InventoryFilter.SENZA_DATA) },
            label = { Text(stringResource(R.string.filter_no_date, state.noDateCount)) })
    }
    var locationOpen by remember { mutableStateOf(false) }
    FlowRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onCategoryClick) {
            val category = state.category
            Text(stringResource(R.string.inventory_category_filter,
                if (category == null) stringResource(R.string.category_all) else stringResource(category.labelRes())))
        }
        Box {
            OutlinedButton(onClick = { locationOpen = true }) {
                Text(stringResource(R.string.inventory_location_filter,
                    state.location?.label() ?: stringResource(R.string.filter_anywhere)))
            }
            DropdownMenu(expanded = locationOpen, onDismissRequest = { locationOpen = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.filter_anywhere)) },
                    onClick = { onLocationChange(null); locationOpen = false })
                StorageLocation.entries.forEach { location ->
                    DropdownMenuItem(text = { Text(location.label()) },
                        onClick = { onLocationChange(location); locationOpen = false })
                }
            }
        }
    }
}

@Composable
private fun EmptyState(filtered: Boolean, onAdd: () -> Unit, onReset: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text(stringResource(if (filtered) R.string.inventory_empty_filtered else R.string.inventory_empty),
            style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextButton(onClick = if (filtered) onReset else onAdd) {
            Text(stringResource(if (filtered) R.string.inventory_reset_filters else R.string.action_add_item))
        }
    }
}
