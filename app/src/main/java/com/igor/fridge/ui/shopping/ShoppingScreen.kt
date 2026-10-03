package com.igor.fridge.ui.shopping

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.igor.fridge.ui.components.PhotoThumbnail
import com.igor.fridge.ui.formatQuantity
import com.igor.fridge.ui.icon
import com.igor.fridge.ui.label
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ShoppingScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
    onOpenSavedLists: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShoppingViewModel = viewModel(factory = ShoppingViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var newItem by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }
    val undoLabel = stringResource(R.string.action_undo)

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
                        TextButton(onClick = { viewModel.moveCheckedToInventory() }) {
                            Text(stringResource(R.string.shopping_move_to_fridge, state.checkedCount))
                        }
                    }
                    IconButton(onClick = onOpenSavedLists) {
                        Icon(Icons.Filled.Bookmarks, contentDescription = stringResource(R.string.saved_lists_title))
                    }
                },
            )
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

            HorizontalDivider()

            if (state.items.isEmpty() && !state.isLoading) {
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
}

/** Le voci gia' prese: una spunta, per non confonderle con la categoria "Altro" (carrello). */
private const val CART_ICON = "\u2705" // segno di spunta

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

/** "2 kg · Barilla · presi 1 kg" */
@Composable
private fun shoppingDetails(item: ShoppingItem): String {
    val parts = mutableListOf(formatQuantity(item.quantity, item.unit))
    item.brand?.let { parts += it }
    item.purchasedQuantity?.let {
        parts += stringResource(R.string.shopping_purchased_short, formatQuantity(it, item.unit))
    }
    return parts.joinToString(" · ")
}
