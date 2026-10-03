package com.igor.fridge.ui.receipt

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
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
import com.igor.fridge.data.openprices.CommunityLocation
import com.igor.fridge.data.photos.VerticalCrop
import com.igor.fridge.data.photos.decodeUpright
import com.igor.fridge.ui.components.EnumDropdown
import com.igor.fridge.ui.components.ExpiryDatePickerDialog
import com.igor.fridge.ui.formatEuro
import com.igor.fridge.ui.formatShort
import com.igor.fridge.ui.icon
import com.igor.fridge.ui.label
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReceiptScreen(
    onDone: () -> Unit,
    onOpenPrices: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReceiptViewModel = viewModel(factory = ReceiptViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var pendingCapture by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingExpiryFor by remember { mutableStateOf<ReceiptDraft?>(null) }
    var pickingDate by remember { mutableStateOf(false) }

    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val target = pendingCapture
        pendingCapture = null
        if (saved && target != null) viewModel.onImageChosen(Uri.parse(target))
    }
    fun launchCamera() {
        val uri = viewModel.newCaptureUri()
        pendingCapture = uri.toString()
        try {
            takePicture.launch(uri)
        } catch (e: ActivityNotFoundException) {
            // Tablet o profili di lavoro senza un'app fotocamera: resta la galleria.
            pendingCapture = null
            viewModel.onCameraUnavailable()
        }
    }
    val requestCamera = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> if (granted) launchCamera() }
    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) viewModel.onImageChosen(uri) }

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
                title = { Text(stringResource(R.string.receipt_title)) },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
            )
        },
        bottomBar = {
            if (state.phase == ReceiptPhase.REVIEW) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = { viewModel.confirm() },
                        enabled = state.includedCount > 0,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                    ) {
                        Text(stringResource(R.string.receipt_confirm, state.includedCount))
                    }
                }
            }
        },
    ) { padding ->
        val contentModifier = Modifier
            .fillMaxSize()
            .padding(padding)
        when (state.phase) {
            ReceiptPhase.CHOOSE -> ChooseImage(
                modifier = contentModifier,
                onCamera = {
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA,
                    ) == PackageManager.PERMISSION_GRANTED
                    if (granted) launchCamera() else requestCamera.launch(Manifest.permission.CAMERA)
                },
                onGallery = {
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                },
            )

            ReceiptPhase.READING, ReceiptPhase.SAVING -> Column(
                modifier = contentModifier,
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                CircularProgressIndicator()
                Text(
                    text = stringResource(
                        if (state.phase == ReceiptPhase.READING) R.string.receipt_reading else R.string.receipt_saving,
                    ),
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            ReceiptPhase.REVIEW -> LazyColumn(
                modifier = contentModifier,
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Text(
                        text = stringResource(R.string.receipt_review_help),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedTextField(
                            value = state.store,
                            onValueChange = viewModel::onStoreChange,
                            label = { Text(stringResource(R.string.shopping_store)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedButton(onClick = { pickingDate = true }) {
                            Text(state.purchaseDate.formatShort())
                        }
                    }
                }
                items(items = state.drafts, key = { it.id }) { draft ->
                    DraftCard(
                        draft = draft,
                        onInclude = { viewModel.onIncludeChange(draft.id, it) },
                        onName = { viewModel.onNameChange(draft.id, it) },
                        onCategory = { viewModel.onCategoryChange(draft.id, it) },
                        onQuantity = { viewModel.onQuantityChange(draft.id, it) },
                        onUnit = { viewModel.onUnitChange(draft.id, it) },
                        onPrice = { viewModel.onPriceChange(draft.id, it) },
                        onPickExpiry = { pickingExpiryFor = draft },
                        onClearExpiry = { viewModel.onExpiryChange(draft.id, null) },
                    )
                }
                item {
                    TextButton(onClick = viewModel::addDraft) {
                        Text(stringResource(R.string.receipt_add_row))
                    }
                }
            }

            ReceiptPhase.DONE -> Column(
                modifier = contentModifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
            ) {
                Text(
                    text = state.summary.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
                Button(onClick = onDone) { Text(stringResource(R.string.receipt_done)) }
                OutlinedButton(onClick = onOpenPrices) { Text(stringResource(R.string.prices_open)) }
                if (state.canContribute) {
                    OutlinedButton(onClick = viewModel::openContribution) {
                        Text(stringResource(R.string.contribute_open, state.contributable.size))
                    }
                } else if (state.contributable.isNotEmpty() && state.openPrices.enabled) {
                    Text(
                        text = stringResource(R.string.contribute_needs_login),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = viewModel::restart) { Text(stringResource(R.string.receipt_another)) }
            }
        }
    }

    pickingExpiryFor?.let { draft ->
        ExpiryDatePickerDialog(
            initialDate = draft.expiryDate ?: LocalDate.now().plusDays(draft.category.typicalShelfLifeDays),
            onDismiss = { pickingExpiryFor = null },
            onConfirm = { date ->
                viewModel.onExpiryChange(draft.id, date)
                pickingExpiryFor = null
            },
        )
    }

    if (pickingDate) {
        ExpiryDatePickerDialog(
            initialDate = state.purchaseDate,
            onDismiss = { pickingDate = false },
            onConfirm = { date ->
                viewModel.onDateChange(date)
                pickingDate = false
            },
        )
    }

    state.contribution?.let { contribution ->
        ContributionDialog(
            state = contribution,
            imageUri = state.imageUri,
            onCropChange = viewModel::onCropChange,
            onQuery = viewModel::onContributionQuery,
            onCity = viewModel::onContributionCity,
            onSearch = viewModel::searchStores,
            onSelectLocation = viewModel::selectLocation,
            onToggleItem = viewModel::toggleContributionItem,
            onSend = viewModel::sendContribution,
            onClose = viewModel::closeContribution,
        )
    }

    state.missingExpiryPrompt?.let { missing ->
        AlertDialog(
            onDismissRequest = viewModel::dismissMissingExpiry,
            title = { Text(stringResource(R.string.receipt_missing_expiry_title)) },
            text = { Text(stringResource(R.string.receipt_missing_expiry_text, missing)) },
            confirmButton = {
                TextButton(onClick = { viewModel.confirm(skipExpiryCheck = true) }) {
                    Text(stringResource(R.string.receipt_add_anyway))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissMissingExpiry) {
                    Text(stringResource(R.string.receipt_add_dates))
                }
            },
        )
    }
}

@Composable
private fun ChooseImage(
    onCamera: () -> Unit,
    onGallery: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Text(
            text = stringResource(R.string.receipt_intro),
            style = MaterialTheme.typography.bodyLarge,
        )
        Button(onClick = onCamera, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PhotoCamera, contentDescription = null)
            Text(stringResource(R.string.receipt_take_photo), modifier = Modifier.padding(start = 8.dp))
        }
        OutlinedButton(onClick = onGallery, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Filled.PhotoLibrary, contentDescription = null)
            Text(stringResource(R.string.receipt_pick_photo), modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            text = stringResource(R.string.receipt_tips),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DraftCard(
    draft: ReceiptDraft,
    onInclude: (Boolean) -> Unit,
    onName: (String) -> Unit,
    onCategory: (FoodCategory) -> Unit,
    onQuantity: (String) -> Unit,
    onUnit: (QuantityUnit) -> Unit,
    onPrice: (String) -> Unit,
    onPickExpiry: () -> Unit,
    onClearExpiry: () -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = draft.include, onCheckedChange = onInclude)
                OutlinedTextField(
                    value = draft.name,
                    onValueChange = onName,
                    label = { Text(stringResource(R.string.edit_name)) },
                    singleLine = true,
                    isError = draft.nameError,
                    modifier = Modifier.weight(1f),
                )
            }

            // Una bozza esclusa resta visibile ma attenuata: la si puo' sempre riprendere.
            Column(
                modifier = Modifier.alpha(if (draft.include) 1f else 0.5f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val details = listOfNotNull(
                    draft.shoppingMatch?.let { stringResource(R.string.receipt_from_list, it.name) },
                    if (!draft.category.isFood) stringResource(R.string.receipt_not_food) else null,
                )
                if (details.isNotEmpty()) {
                    Text(
                        text = details.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                EnumDropdown(
                    label = stringResource(R.string.edit_category),
                    value = draft.category,
                    options = FoodCategory.entries,
                    optionLabel = { "${it.icon()}  ${it.label()}" },
                    onSelect = onCategory,
                    modifier = Modifier.fillMaxWidth(),
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draft.quantityText,
                        onValueChange = onQuantity,
                        label = { Text(stringResource(R.string.edit_quantity)) },
                        singleLine = true,
                        isError = draft.quantityError || draft.quantityToConfirm,
                        supportingText = when {
                            draft.quantityError -> {
                                { Text(stringResource(R.string.error_quantity_invalid)) }
                            }
                            draft.quantityToConfirm -> {
                                { Text(stringResource(R.string.receipt_quantity_needed)) }
                            }
                            else -> null
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.weight(1f),
                    )
                    EnumDropdown(
                        label = stringResource(R.string.edit_unit),
                        value = draft.unit,
                        options = QuantityUnit.entries,
                        optionLabel = { it.label() },
                        onSelect = onUnit,
                        modifier = Modifier.weight(1f),
                    )
                }

                OutlinedTextField(
                    value = draft.priceText,
                    onValueChange = onPrice,
                    label = { Text(stringResource(R.string.receipt_price)) },
                    singleLine = true,
                    isError = draft.priceError,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (draft.category.isFood) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val expiry = draft.expiryDate
                        OutlinedButton(onClick = onPickExpiry) {
                            Text(
                                text = expiry?.let { stringResource(R.string.receipt_expiry_on, it.formatShort()) }
                                    ?: stringResource(R.string.edit_expiry_set),
                                color = if (draft.needsExpiry) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        if (expiry != null) {
                            TextButton(onClick = onClearExpiry) {
                                Text(stringResource(R.string.edit_expiry_clear))
                            }
                        } else if (draft.needsExpiry) {
                            Text(
                                text = stringResource(R.string.receipt_expiry_needed),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Condividere i prezzi su Open Prices: avviso su cosa si pubblica, scelta del negozio fra
 * quelli noti alla comunita', scelta dei prodotti, invio.
 */
@Composable
private fun ContributionDialog(
    state: ContributionUiState,
    imageUri: String?,
    onCropChange: (Float, Float) -> Unit,
    onQuery: (String) -> Unit,
    onCity: (String) -> Unit,
    onSearch: () -> Unit,
    onSelectLocation: (CommunityLocation) -> Unit,
    onToggleItem: (String) -> Unit,
    onSend: () -> Unit,
    onClose: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!state.isSending) onClose() },
        title = { Text(stringResource(R.string.contribute_title)) },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val result = state.result
                if (result != null) {
                    Text(result, style = MaterialTheme.typography.bodyLarge)
                    state.error?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    return@Column
                }
                Text(
                    text = stringResource(R.string.contribute_privacy),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (imageUri != null) {
                    ProofPreview(imageUri = imageUri, crop = state.crop, onCropChange = onCropChange)
                }
                Text(stringResource(R.string.contribute_store), style = MaterialTheme.typography.titleSmall)
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQuery,
                    label = { Text(stringResource(R.string.shopping_store)) },
                    singleLine = true,
                )
                OutlinedTextField(
                    value = state.city,
                    onValueChange = onCity,
                    label = { Text(stringResource(R.string.contribute_city)) },
                    singleLine = true,
                )
                OutlinedButton(onClick = onSearch, enabled = !state.isSearching && state.query.isNotBlank()) {
                    Text(stringResource(R.string.contribute_search))
                }
                if (state.isSearching) CircularProgressIndicator(modifier = Modifier.size(24.dp))
                if (state.searched && state.results.isEmpty()) {
                    Text(
                        text = stringResource(R.string.contribute_no_store),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                state.results.forEach { location ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = location == state.location,
                            onClick = { onSelectLocation(location) },
                        )
                        Text(location.label, modifier = Modifier.weight(1f))
                    }
                }
                Text(stringResource(R.string.contribute_items), style = MaterialTheme.typography.titleSmall)
                state.items.forEach { item ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = item.barcode in state.selected,
                            onCheckedChange = { onToggleItem(item.barcode) },
                        )
                        Text(
                            text = "${item.name} · ${item.quantity} × ${formatEuro(item.unitPriceCents)}",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                state.error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                if (state.isSending) CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        },
        confirmButton = {
            if (state.result == null) {
                TextButton(onClick = onSend, enabled = state.canSend) {
                    Text(stringResource(R.string.contribute_send))
                }
            } else {
                TextButton(onClick = onClose) { Text(stringResource(R.string.receipt_done)) }
            }
        },
        dismissButton = {
            if (state.result == null) {
                TextButton(onClick = onClose, enabled = !state.isSending) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        },
    )
}

/**
 * La foto come diventera' pubblica: le strisce scure in alto e in basso sono quelle che
 * il ritaglio toglie, di solito dove lo scontrino stampa carta fedelta' e pagamento.
 */
@Composable
private fun ProofPreview(
    imageUri: String,
    crop: VerticalCrop,
    onCropChange: (Float, Float) -> Unit,
) {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(initialValue = null, imageUri) {
        value = withContext(Dispatchers.IO) {
            context.contentResolver.decodeUpright(Uri.parse(imageUri), PREVIEW_SIDE_PX)?.asImageBitmap()
        }
    }
    Text(stringResource(R.string.contribute_preview), style = MaterialTheme.typography.titleSmall)
    val image = bitmap
    if (image == null) {
        CircularProgressIndicator(modifier = Modifier.size(24.dp))
        return
    }
    val scrim = MaterialTheme.colorScheme.scrim.copy(alpha = 0.7f)
    Image(
        bitmap = image,
        contentDescription = stringResource(R.string.contribute_preview),
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp)
            .aspectRatio(image.width.toFloat() / image.height)
            .drawWithContent {
                drawContent()
                drawRect(scrim, size = Size(size.width, size.height * crop.top))
                drawRect(
                    scrim,
                    topLeft = Offset(0f, size.height * (1f - crop.bottom)),
                    size = Size(size.width, size.height * crop.bottom),
                )
            },
    )
    Text(stringResource(R.string.contribute_crop_top), style = MaterialTheme.typography.bodySmall)
    Slider(
        value = crop.top,
        onValueChange = { onCropChange(it, crop.bottom) },
        valueRange = 0f..MAX_CROP,
    )
    Text(stringResource(R.string.contribute_crop_bottom), style = MaterialTheme.typography.bodySmall)
    Slider(
        value = crop.bottom,
        onValueChange = { onCropChange(crop.top, it) },
        valueRange = 0f..MAX_CROP,
    )
}

private const val PREVIEW_SIDE_PX = 800
private const val MAX_CROP = 0.4f
