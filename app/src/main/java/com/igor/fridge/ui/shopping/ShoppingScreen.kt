package com.igor.fridge.ui.shopping

import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.ui.components.ExpiryDatePickerDialog
import com.igor.fridge.ui.components.PhotoThumbnail
import com.igor.fridge.ui.formatEuro
import com.igor.fridge.ui.formatQuantity
import com.igor.fridge.ui.formatShort
import com.igor.fridge.ui.icon
import com.igor.fridge.ui.label
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSavedLists: () -> Unit,
    onOpenAisleOrder: () -> Unit,
    onOpenReceipt: () -> Unit,
    onOpenPrices: () -> Unit,
    onOpenCompare: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newItem by remember { mutableStateOf("") }
    var menuOpen by remember { mutableStateOf(false) }
    // Le voci fresche spuntate per cui chiedere la scadenza prima di metterle in frigo.
    var expiryPrompt by remember { mutableStateOf<List<ShoppingItem>?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)
    val context = LocalContext.current
    val shareTitle = stringResource(R.string.shopping_share)

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        val result = snackbarHostState.showSnackbar(
            message = message,
            actionLabel = if (state.canUndo) undoLabel else null,
            withDismissAction = false,
            duration = SnackbarDuration.Long,
        )
        // Una sola chiamata per ramo: undoLastMove() consuma gia' il messaggio, e chiamare
        // anche onMessageShown() dopo di lui renderebbe il risultato dipendente dal fatto
        // che l'annullamento sospenda prima di annunciarsi.
        if (result == SnackbarResult.ActionPerformed) {
            viewModel.undoLastMove()
        } else {
            viewModel.onMessageShown()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.shopping_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (state.checkedCount > 0) {
                        TextButton(
                            onClick = {
                                // I prodotti freschi entrano in frigo con la loro scadenza:
                                // prima di spostarli la si chiede, gli altri passano subito.
                                val fresh = state.items.filter {
                                    it.isChecked && it.category.isFood && it.category.isPerishable
                                }
                                if (fresh.isEmpty()) viewModel.moveCheckedToInventory() else expiryPrompt = fresh
                            },
                        ) {
                            Text(stringResource(R.string.shopping_move_to_fridge, state.checkedCount))
                        }
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.action_more))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.shopping_share)) },
                            enabled = state.toBuy.isNotEmpty(),
                            onClick = {
                                menuOpen = false
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, viewModel.shareText())
                                }
                                context.startActivity(Intent.createChooser(send, shareTitle))
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.receipt_title)) },
                            onClick = {
                                menuOpen = false
                                onOpenReceipt()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.compare_title)) },
                            onClick = {
                                menuOpen = false
                                onOpenCompare()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.prices_title)) },
                            onClick = {
                                menuOpen = false
                                onOpenPrices()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.saved_lists_title)) },
                            onClick = {
                                menuOpen = false
                                onOpenSavedLists()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.aisle_order_title)) },
                            onClick = {
                                menuOpen = false
                                onOpenAisleOrder()
                            },
                        )
                    }
                },
            )
        },
        bottomBar = {
            if (state.totals.hasPrices) TotalsBar(state.totals)
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = newItem,
                    onValueChange = { newItem = it },
                    label = { Text(stringResource(R.string.shopping_add)) },
                    placeholder = { Text(stringResource(R.string.shopping_add_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = {
                        viewModel.add(newItem)
                        newItem = ""
                    },
                    enabled = newItem.isNotBlank(),
                ) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_add))
                }
            }

            if (state.stores.isNotEmpty()) {
                StoreFilterRow(
                    stores = state.stores,
                    selected = state.activeStore,
                    onSelect = viewModel::setStoreFilter,
                )
            }

            HorizontalDivider()

            if (state.visibleItems.isEmpty() && !state.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.shopping_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(vertical = 8.dp),
                ) {
                    state.toBuy.forEach { section ->
                        stickyHeader(key = "header-${section.category.name}") {
                            SectionHeader(
                                icon = section.category.icon(),
                                title = section.category.label(),
                                count = section.items.size,
                            )
                        }
                        items(items = section.items, key = { it.uuid }) { item ->
                            ShoppingRow(
                                item = item,
                                photoFile = item.photoPath?.let(viewModel::photoFile),
                                onCheckedChange = { viewModel.setChecked(item, it) },
                                onClick = { onOpenItem(item.uuid) },
                                onDelete = { viewModel.delete(item) },
                            )
                        }
                    }
                    if (state.inCart.isNotEmpty()) {
                        stickyHeader(key = "header-cart") {
                            SectionHeader(
                                icon = CART_ICON,
                                title = stringResource(R.string.shopping_in_cart),
                                count = state.inCart.size,
                            )
                        }
                        items(items = state.inCart, key = { it.uuid }) { item ->
                            ShoppingRow(
                                item = item,
                                photoFile = item.photoPath?.let(viewModel::photoFile),
                                onCheckedChange = { viewModel.setChecked(item, it) },
                                onClick = { onOpenItem(item.uuid) },
                                onDelete = { viewModel.delete(item) },
                            )
                        }
                    }
                }
            }
        }
    }

    expiryPrompt?.let { fresh ->
        ExpiryPromptDialog(
            items = fresh,
            onDismiss = { expiryPrompt = null },
            onConfirm = { expiries ->
                expiryPrompt = null
                viewModel.moveCheckedToInventory(expiries)
            },
        )
    }
}

/** Le voci gia' prese: una spunta, per non confonderle con la categoria "Altro" (carrello). */
private const val CART_ICON = "✅" // segno di spunta

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StoreFilterRow(
    stores: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text(stringResource(R.string.shopping_store_all)) },
        )
        stores.forEach { store ->
            FilterChip(
                selected = store == selected,
                onClick = { onSelect(store) },
                label = { Text(store) },
            )
        }
    }
}

@Composable
private fun TotalsBar(totals: ShoppingTotals) {
    Surface(tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(
                    R.string.shopping_totals,
                    formatEuro(totals.estimatedCents),
                    formatEuro(totals.inCartCents),
                ),
                style = MaterialTheme.typography.titleSmall,
            )
            if (totals.unpricedCount > 0) {
                Text(
                    text = stringResource(R.string.shopping_totals_unpriced, totals.unpricedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Chiede la scadenza dei prodotti freschi prima di metterli in frigo. Ognuna e' facoltativa:
 * "Metti in frigo" sposta tutto, con le date indicate e senza le altre. Il calendario parte
 * dalla durata tipica della categoria, ma nessuna data entra senza essere scelta.
 */
@Composable
private fun ExpiryPromptDialog(
    items: List<ShoppingItem>,
    onDismiss: () -> Unit,
    onConfirm: (Map<String, LocalDate>) -> Unit,
) {
    val chosen = remember { mutableStateMapOf<String, LocalDate>() }
    var picking by remember { mutableStateOf<ShoppingItem?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.expiry_prompt_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 360.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.expiry_prompt_text),
                    style = MaterialTheme.typography.bodySmall,
                )
                items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "${item.category.icon()} ${item.name}",
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        OutlinedButton(onClick = { picking = item }) {
                            Text(chosen[item.uuid]?.formatShort() ?: stringResource(R.string.edit_expiry_set))
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(chosen.toMap()) }) {
                Text(stringResource(R.string.shopping_move_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )

    picking?.let { item ->
        ExpiryDatePickerDialog(
            initialDate = chosen[item.uuid]
                ?: LocalDate.now().plusDays(item.category.typicalShelfLifeDays),
            onDismiss = { picking = null },
            onConfirm = { date ->
                chosen[item.uuid] = date
                picking = null
            },
        )
    }
}

@Composable
private fun SectionHeader(icon: String, title: String, count: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(text = icon, fontSize = 20.sp)
            Text(
                text = "$title ($count)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun ShoppingRow(
    item: ShoppingItem,
    photoFile: File?,
    onCheckedChange: (Boolean) -> Unit,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onCheckedChange,
        )
        if (photoFile != null) {
            PhotoThumbnail(
                file = photoFile,
                contentDescription = stringResource(R.string.shopping_photo_of, item.name),
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(40.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (item.isChecked) {
                    TextDecoration.LineThrough
                } else {
                    TextDecoration.None
                },
            )
            Text(
                text = shoppingDetails(item),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            item.notes?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Filled.DeleteOutline,
                contentDescription = stringResource(R.string.action_delete_item, item.name),
            )
        }
    }
}

/** "2 kg · Barilla · 1,29 €/kg · Esselunga · presi 1 kg" */
@Composable
private fun shoppingDetails(item: ShoppingItem): String {
    val parts = mutableListOf(formatQuantity(item.quantity, item.unit))
    item.brand?.let { parts += it }
    item.unitPriceCents?.let { parts += "${formatEuro(it)}/${item.unit.label()}" }
    item.store?.let { parts += it }
    item.purchasedQuantity?.let {
        parts += stringResource(R.string.shopping_purchased_short, formatQuantity(it, item.unit))
    }
    return parts.joinToString(" · ")
}
