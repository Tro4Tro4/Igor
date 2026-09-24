package com.igor.fridge.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.igor.fridge.R
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                    onCheckedChange = viewModel::onNotificationsToggle,
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
        }
    }
}
