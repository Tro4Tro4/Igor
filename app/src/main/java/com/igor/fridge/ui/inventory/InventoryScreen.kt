package com.igor.fridge.ui.inventory

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.ui.components.FoodItemCard
import com.igor.fridge.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    onAddItem: () -> Unit,
    onEditItem: (String) -> Unit,
    onOpenShoppingList: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: InventoryViewModel = viewModel(factory = InventoryViewModel.Factory),
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
                title = { Text("Igor · Frigorifero") },
                actions = {
                    IconButton(onClick = { viewModel.addExpiringToShoppingList() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                            contentDescription = "Aggiungi i prodotti in scadenza alla lista della spesa",
                        )
                    }
                    IconButton(onClick = onOpenShoppingList) {
                        Icon(
                            imageVector = Icons.Filled.ShoppingCart,
                            contentDescription = "Apri la lista della spesa",
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Filled.Settings,
                            contentDescription = stringResource(R.string.action_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAddItem) {
                Icon(Icons.Filled.Add, contentDescription = "Aggiungi un alimento")
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text("Cerca") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )

            FilterRow(
                state = state,
                onFilterChange = viewModel::onFilterChange,
                onLocationChange = viewModel::onLocationChange,
            )

            if (state.items.isEmpty() && !state.isLoading) {
                EmptyState(hasItems = state.totalCount > 0)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.items, key = { it.uuid }) { item ->
                        FoodItemCard(
                            item = item,
                            today = state.today,
                            warningDays = state.warningDays,
                            onClick = { onEditItem(item.uuid) },
                            onConsume = { viewModel.consume(item) },
                            onDelete = { viewModel.delete(item) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterRow(
    state: InventoryUiState,
    onFilterChange: (InventoryFilter) -> Unit,
    onLocationChange: (StorageLocation?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.filter == InventoryFilter.TUTTI,
            onClick = { onFilterChange(InventoryFilter.TUTTI) },
            label = { Text("Tutti (${state.totalCount})") },
        )
        FilterChip(
            selected = state.filter == InventoryFilter.IN_SCADENZA,
            onClick = { onFilterChange(InventoryFilter.IN_SCADENZA) },
            label = { Text("In scadenza (${state.expiringCount})") },
        )
        FilterChip(
            selected = state.filter == InventoryFilter.SCADUTI,
            onClick = { onFilterChange(InventoryFilter.SCADUTI) },
            label = { Text("Scaduti (${state.expiredCount})") },
        )
        FilterChip(
            selected = state.filter == InventoryFilter.SENZA_DATA,
            onClick = { onFilterChange(InventoryFilter.SENZA_DATA) },
            label = { Text("Senza data (${state.noDateCount})") },
        )
        FilterChip(
            selected = state.location == null,
            onClick = { onLocationChange(null) },
            label = { Text("Ovunque") },
        )
        StorageLocation.entries.forEach { location ->
            FilterChip(
                selected = state.location == location,
                onClick = { onLocationChange(location) },
                label = { Text(location.label()) },
            )
        }
    }
}

@Composable
private fun EmptyState(hasItems: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (hasItems) {
                "Nessun prodotto corrisponde ai filtri"
            } else {
                "Il frigo e' vuoto.\nTocca + per aggiungere il primo alimento."
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
