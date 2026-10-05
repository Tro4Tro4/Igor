package com.igor.fridge.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import com.igor.fridge.ui.isPermissionPermanentlyDenied
import com.igor.fridge.ui.openAppSettings
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenAisleOrder: () -> Unit,
    onOpenOpenPrices: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    // Accendere le notifiche senza il permesso di Android non servirebbe: si chiede, e
    // se il sistema non lo chiede piu' si apre la pagina dell'app.
    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            viewModel.onNotificationsToggle(true)
        } else if (context.isPermissionPermanentlyDenied(Manifest.permission.POST_NOTIFICATIONS)) {
            context.openAppSettings()
        }
    }
    val onNotificationsChange: (Boolean) -> Unit = { enabled ->
        val missing = enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        if (missing) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.onNotificationsToggle(enabled)
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let(viewModel::exportTo) }

    LaunchedEffect(state.message) {
        val message = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        viewModel.onMessageShown()
    }

    // Posizione del cursore durante il trascinamento. onValueChange scatta a ogni frame:
    // scriverla subito significherebbe una riscrittura di DataStore e una riprogrammazione
    // del worker per ogni frame, e il pollice tornerebbe indietro perche' Slider rileggerebbe
    // un valore ancora vecchio. Si salva solo a trascinamento finito; la chiave di remember
    // risincronizza la posizione se il valore memorizzato cambia da un'altra parte.
    var warningDaysPosition by remember(state.warningDays) {
        mutableFloatStateOf(state.warningDays.toFloat())
    }
    var hourPosition by remember(state.notificationHour) {
        mutableFloatStateOf(state.notificationHour.toFloat())
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(
                    R.string.settings_warning_days,
                    warningDaysPosition.roundToInt(),
                ),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_warning_days_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = warningDaysPosition,
                onValueChange = { warningDaysPosition = it },
                onValueChangeFinished = {
                    viewModel.onWarningDaysChange(warningDaysPosition.roundToInt())
                },
                valueRange = 0f..14f,
                steps = 13,
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.settings_notifications),
                    style = MaterialTheme.typography.titleMedium,
                )
                Switch(
                    checked = state.notificationsEnabled,
                    onCheckedChange = onNotificationsChange,
                )
            }

            Text(
                text = stringResource(R.string.settings_hour, hourPosition.roundToInt()),
                style = MaterialTheme.typography.titleMedium,
            )
            Slider(
                value = hourPosition,
                onValueChange = { hourPosition = it },
                onValueChangeFinished = { viewModel.onHourChange(hourPosition.roundToInt()) },
                valueRange = 0f..23f,
                steps = 22,
                enabled = state.notificationsEnabled,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.settings_hour_help),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            HorizontalDivider()

            OutlinedButton(onClick = onOpenAisleOrder, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.aisle_order_title))
            }

            OutlinedButton(onClick = onOpenOpenPrices, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.open_prices_title))
            }

            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth().toggleable(value=state.onlinePrices.enabled,role=Role.Switch,onValueChange=viewModel::onOnlinePricesToggle),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.online_prices_title),modifier=Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                Switch(checked=state.onlinePrices.enabled,onCheckedChange=null)
            }
            Text(stringResource(R.string.online_prices_consent),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
            var postcode by remember(state.onlinePrices.postcode) { mutableStateOf(state.onlinePrices.postcode) }
            androidx.compose.material3.OutlinedTextField(
                value=postcode,onValueChange={ value -> if (value.length <= 5 && value.all { it in '0'..'9' }) postcode=value },
                label={ Text(stringResource(R.string.online_prices_postcode)) },singleLine=true,
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(keyboardType=androidx.compose.ui.text.input.KeyboardType.Number),
                modifier=Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick={ viewModel.onOnlinePostcodeChange(postcode) },enabled=postcode.matches(Regex("[0-9]{5}")) && postcode != state.onlinePrices.postcode) {
                Text(stringResource(R.string.online_prices_save_postcode))
            }

            HorizontalDivider()

            Text(
                text = stringResource(R.string.settings_privacy),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.settings_privacy_info),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = { exportLauncher.launch("igor-dati.json") },
                enabled = !state.isWorking,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_export))
            }
            OutlinedButton(
                onClick = { confirmDelete = true },
                enabled = !state.isWorking,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.settings_delete_all))
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.settings_delete_all)) },
            text = { Text(stringResource(R.string.settings_delete_all_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteEverything()
                    },
                ) {
                    Text(
                        stringResource(R.string.settings_delete_all_action),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}
