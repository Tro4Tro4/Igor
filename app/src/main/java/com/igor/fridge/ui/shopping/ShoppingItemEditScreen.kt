package com.igor.fridge.ui.shopping

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
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
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.ui.components.EnumDropdown
import com.igor.fridge.ui.components.PhotoThumbnail
import com.igor.fridge.ui.formatShort
import com.igor.fridge.ui.formatUnitPrice
import com.igor.fridge.ui.icon
import com.igor.fridge.ui.label

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShoppingItemEditScreen(
    uuid: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ShoppingItemEditViewModel = viewModel(
        key = "shopping-edit-$uuid",
        factory = ShoppingItemEditViewModel.factory(uuid),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    // L'Uri in cui la fotocamera scrive deve sopravvivere a una rotazione: l'app della
    // fotocamera e' in primo piano, e la nostra Activity puo' essere ricreata nel frattempo.
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = pendingCapture
        pendingCapture = null
        if (saved && target != null) viewModel.onPhotoChosen(Uri.parse(target))
    }
    fun launchCamera() {
        val uri = viewModel.newCaptureUri()
        pendingCapture = uri.toString()
        takePicture.launch(uri)
    }
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() }
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onPhotoChosen(uri) }

    LaunchedEffect(state.isDone) {
        if (state.isDone) onDone()
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
                title = { Text(stringResource(R.string.shopping_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.delete() }) {
                        Icon(Icons.Filled.DeleteOutline, contentDescription = stringResource(R.string.action_delete))
                    }
                },
            )
        },
    ) { padding ->
        if (!state.isLoaded) return@Scaffold

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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { viewModel.stepQuantity(-1) }) {
                    Icon(Icons.Filled.Remove, contentDescription = stringResource(R.string.action_decrease))
                }
                OutlinedTextField(
                    value = state.quantityText,
                    onValueChange = viewModel::onQuantityChange,
                    label = { Text(stringResource(R.string.shopping_quantity_to_buy)) },
                    singleLine = true,
                    isError = state.quantityError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.stepQuantity(1) }) {
                    Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.action_increase))
                }
                EnumDropdown(
                    label = stringResource(R.string.edit_unit),
                    value = state.unit,
                    options = QuantityUnit.entries,
                    optionLabel = { it.label() },
                    onSelect = viewModel::onUnitChange,
                    modifier = Modifier.weight(1f),
                )
            }
            if (state.quantityError) {
                Text(
                    text = stringResource(R.string.error_quantity_invalid),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }

            EnumDropdown(
                label = stringResource(R.string.edit_category),
                value = state.category,
                options = FoodCategory.entries,
                optionLabel = { "${it.icon()}  ${it.label()}" },
                onSelect = viewModel::onCategoryChange,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.brand,
                onValueChange = viewModel::onBrandChange,
                label = { Text(stringResource(R.string.shopping_brand)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.priceText,
                onValueChange = viewModel::onPriceChange,
                label = { Text(stringResource(R.string.shopping_price, state.unit.label())) },
                singleLine = true,
                isError = state.priceError,
                supportingText = if (state.priceError) {
                    { Text(stringResource(R.string.error_price_invalid)) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            state.lastPaid?.let { paid ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(
                            R.string.shopping_last_paid,
                            listOfNotNull(
                                formatUnitPrice(paid.unitPriceCents, paid.referenceUnit),
                                paid.store,
                                paid.purchasedOn.formatShort(),
                            ).joinToString(" · "),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = viewModel::useLastPaid) {
                        Text(stringResource(R.string.shopping_use_last_paid))
                    }
                }
            }

            OutlinedTextField(
                value = state.store,
                onValueChange = viewModel::onStoreChange,
                label = { Text(stringResource(R.string.shopping_store)) },
                placeholder = { Text(stringResource(R.string.shopping_store_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            val suggestions = state.knownStores.filterNot { it.equals(state.store.trim(), ignoreCase = true) }
            if (suggestions.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    suggestions.forEach { store ->
                        SuggestionChip(
                            onClick = { viewModel.onStoreChange(store) },
                            label = { Text(store) },
                        )
                    }
                }
            }

            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotesChange,
                label = { Text(stringResource(R.string.edit_notes)) },
                placeholder = { Text(stringResource(R.string.shopping_notes_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.shopping_photo),
                style = MaterialTheme.typography.titleSmall,
            )
            val photoName = state.photoName
            if (photoName != null) {
                PhotoThumbnail(
                    file = viewModel.photoFile(photoName),
                    contentDescription = stringResource(R.string.shopping_photo_of, state.name),
                    maxSidePx = 1024,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(4f / 3f),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        val granted = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.CAMERA,
                        ) == PackageManager.PERMISSION_GRANTED
                        if (granted) launchCamera() else requestCamera.launch(Manifest.permission.CAMERA)
                    },
                    enabled = !state.isImportingPhoto,
                ) {
                    Icon(Icons.Filled.PhotoCamera, contentDescription = null)
                    Text(stringResource(R.string.shopping_photo_take), modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    enabled = !state.isImportingPhoto,
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
                    Text(stringResource(R.string.shopping_photo_pick), modifier = Modifier.padding(start = 8.dp))
                }
                if (state.isImportingPhoto) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                }
            }
            if (photoName != null) {
                TextButton(onClick = viewModel::removePhoto) {
                    Text(stringResource(R.string.shopping_photo_remove))
                }
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.shopping_purchase_section),
                style = MaterialTheme.typography.titleSmall,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = state.purchasedText,
                    onValueChange = viewModel::onPurchasedChange,
                    label = { Text(stringResource(R.string.shopping_quantity_purchased, state.unit.label())) },
                    singleLine = true,
                    isError = state.purchasedError,
                    supportingText = {
                        Text(
                            if (state.purchasedError) {
                                stringResource(R.string.error_quantity_invalid)
                            } else {
                                stringResource(R.string.shopping_quantity_purchased_help)
                            },
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = viewModel::purchaseAll) {
                    Text(stringResource(R.string.shopping_purchase_all))
                }
            }

            Button(
                onClick = { viewModel.save() },
                enabled = !state.isImportingPhoto,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.edit_save))
            }
        }
    }
}
