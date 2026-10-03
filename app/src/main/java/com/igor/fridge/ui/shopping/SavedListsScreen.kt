package com.igor.fridge.ui.shopping

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.data.local.SavedListSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedListsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SavedListsViewModel = viewModel(factory = SavedListsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<SavedListSummary?>(null) }

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
                title = { Text(stringResource(R.string.saved_lists_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Button(
                onClick = { showSaveDialog = true },
                enabled = state.currentItemCount > 0,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(stringResource(R.string.saved_lists_save_current, state.currentItemCount))
            }

            if (state.lists.isEmpty() && !state.isLoading) {
                Text(
                    text = stringResource(R.string.saved_lists_empty),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(32.dp),
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(items = state.lists, key = { it.uuid }) { list ->
                        SavedListCard(
                            list = list,
                            onLoad = { viewModel.load(list) },
                            onDelete = { pendingDelete = list },
                        )
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        SaveListDialog(
            existingNames = state.lists.map { it.name },
            onDismiss = { showSaveDialog = false },
            onConfirm = { name ->
                viewModel.saveCurrent(name)
                showSaveDialog = false
            },
        )
    }

    pendingDelete?.let { list ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.saved_lists_delete_title)) },
            text = { Text(stringResource(R.string.saved_lists_delete_text, list.name)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(list)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SavedListCard(
    list: SavedListSummary,
    onLoad: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = list.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = if (list.itemCount == 1) {
                        stringResource(R.string.saved_lists_one_item)
                    } else {
                        stringResource(R.string.saved_lists_item_count, list.itemCount)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onLoad) { Text(stringResource(R.string.saved_lists_load)) }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = stringResource(R.string.action_delete_item, list.name),
                )
            }
        }
    }
}

/** Chiede il nome; se e' gia' usato avvisa che la lista verra' sovrascritta. */
@Composable
private fun SaveListDialog(
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf("") }
    val overwrites = existingNames.any { it.equals(name.trim(), ignoreCase = true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.saved_lists_save_title)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.saved_lists_name)) },
                singleLine = true,
                supportingText = if (overwrites) {
                    { Text(stringResource(R.string.saved_lists_overwrite)) }
                } else {
                    null
                },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
