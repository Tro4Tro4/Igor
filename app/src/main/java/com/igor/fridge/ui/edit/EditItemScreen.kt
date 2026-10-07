package com.igor.fridge.ui.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.ui.components.ConfirmDeleteDialog
import com.igor.fridge.ui.components.EnumDropdown
import com.igor.fridge.ui.components.CategoryPicker
import com.igor.fridge.ui.components.ExpiryDatePickerDialog
import com.igor.fridge.ui.formatShort
import com.igor.fridge.ui.icon
import com.igor.fridge.ui.label
import androidx.compose.ui.platform.LocalContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditItemScreen(
    uuid: String,
    scannedBarcode: String?,
    onBarcodeConsumed: () -> Unit,
    onOpenScanner: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: EditItemViewModel = viewModel(
        key = "edit-$uuid",
        factory = EditItemViewModel.factory(uuid),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) {
        ConfirmDeleteDialog(
            text = stringResource(R.string.confirm_delete, state.name.ifBlank { "questo alimento" }),
            onConfirm = {
                confirmDelete = false
                viewModel.delete()
            },
            onDismiss = { confirmDelete = false },
        )
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(scannedBarcode) {
        val barcode = scannedBarcode ?: return@LaunchedEffect
        viewModel.onBarcodeScanned(barcode)
        onBarcodeConsumed()
    }

    LaunchedEffect(state.isSaved) {
        if (state.isSaved) onDone()
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
                title = {
                    Text(
                        if (state.isNew) {
                            stringResource(R.string.edit_title_new)
                        } else {
                            stringResource(R.string.edit_title_existing)
                        },
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    if (!state.isNew) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(
                                Icons.Filled.DeleteOutline,
                                contentDescription = stringResource(R.string.action_delete),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text(stringResource(R.string.edit_name)) },
                singleLine = true,
                isError = state.nameError,
                supportingText = if (state.nameError) {
                    { Text(stringResource(R.string.error_name_required)) }
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.brand,
                onValueChange = viewModel::onBrandChange,
                label = { Text(stringResource(R.string.shopping_brand)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.barcode.orEmpty(),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.edit_barcode)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onOpenScanner) {
                    Icon(Icons.Filled.QrCodeScanner, contentDescription = stringResource(R.string.action_scan))
                }
            }

            if (state.barcodeLookup != BarcodeLookupStatus.IDLE) {
                val text = when (state.barcodeLookup) {
                    BarcodeLookupStatus.LOADING -> R.string.off_loading
                    BarcodeLookupStatus.FOUND -> R.string.off_found
                    BarcodeLookupStatus.INCOMPLETE -> R.string.off_incomplete
                    BarcodeLookupStatus.NOT_FOUND -> R.string.off_not_found
                    BarcodeLookupStatus.ERROR -> R.string.off_error
                    BarcodeLookupStatus.RATE_LIMITED -> R.string.off_rate_limited
                    BarcodeLookupStatus.DISABLED -> R.string.off_disabled
                    else -> R.string.off_unsupported
                }
                Column(modifier = Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }) {
                    if (state.barcodeLookup == BarcodeLookupStatus.LOADING) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Text(stringResource(text), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (state.barcodeLookup == BarcodeLookupStatus.ERROR ||
                        state.barcodeLookup == BarcodeLookupStatus.RATE_LIMITED) {
                        TextButton(onClick = viewModel::retryBarcodeLookup, enabled = !state.isSaving) {
                            Text(stringResource(R.string.off_retry))
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.quantityText,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text(stringResource(R.string.edit_quantity)) },
                    singleLine = true,
                    isError = state.quantityError,
                    supportingText = if (state.quantityError) {
                        { Text(stringResource(R.string.error_quantity_invalid)) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                EnumDropdown(
                    label = stringResource(R.string.edit_unit),
                    value = state.unit,
                    options = QuantityUnit.entries,
                    optionLabel = { it.label() },
                    onSelect = viewModel::onUnitChange,
                    modifier = Modifier.weight(1f),
                )
            }

            CategoryPicker(
                value = state.category,
                onSelect = viewModel::onCategoryChange,
                modifier = Modifier.fillMaxWidth(),
            )

            EnumDropdown(
                label = stringResource(R.string.edit_location),
                value = state.location,
                options = StorageLocation.entries,
                optionLabel = { it.label() },
                onSelect = viewModel::onLocationChange,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.expiryDate?.formatShort().orEmpty(),
                onValueChange = {},
                readOnly = true,
                label = { Text(stringResource(R.string.edit_expiry)) },
                placeholder = { Text(stringResource(R.string.edit_expiry_none)) },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { showDatePicker = true }) {
                    Text(
                        if (state.expiryDate == null) {
                            stringResource(R.string.edit_expiry_set)
                        } else {
                            stringResource(R.string.edit_expiry_change)
                        },
                    )
                }
                if (state.expiryDate != null) {
                    TextButton(onClick = { viewModel.onExpiryDateChange(null) }) {
                        Text(stringResource(R.string.edit_expiry_clear))
                    }
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.edit_notes)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = { viewModel.save() },
                enabled = state.isLoaded && !state.isSaving && !state.isSaved,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(if (state.isSaving) R.string.edit_saving else R.string.edit_save))
            }
        }
    }

    if (showDatePicker) {
        ExpiryDatePickerDialog(
            initialDate = state.expiryDate ?: LocalDate.now(),
            onDismiss = { showDatePicker = false },
            onConfirm = { date ->
                viewModel.onExpiryDateChange(date)
                showDatePicker = false
            },
        )
    }
}
