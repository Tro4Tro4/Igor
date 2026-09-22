package com.igor.fridge.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.RemoveShoppingCart
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.domain.ExpiryStatus
import com.igor.fridge.domain.daysUntilExpiry
import com.igor.fridge.domain.expiryStatus
import com.igor.fridge.ui.expiryLabel
import com.igor.fridge.ui.formatQuantity
import com.igor.fridge.ui.label
import com.igor.fridge.ui.theme.expiryColors
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodItemCard(
    item: FoodItem,
    today: LocalDate,
    warningDays: Int,
    onClick: () -> Unit,
    onConsume: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val status = item.expiryStatus(today, warningDays)
    val colors = expiryColors()
    val statusColor = when (status) {
        ExpiryStatus.SCADUTO -> colors.expired
        ExpiryStatus.IN_SCADENZA -> colors.warning
        ExpiryStatus.FRESCO -> colors.fresh
        ExpiryStatus.SENZA_DATA -> MaterialTheme.colorScheme.outline
    }

    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatusDot(color = statusColor)

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = expiryLabel(item.daysUntilExpiry(today)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = statusColor,
                )
                Text(
                    text = "${formatQuantity(item.quantity, item.unit)} · " +
                        "${item.location.label()} · ${item.category.label()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onConsume) {
                Icon(
                    imageVector = Icons.Filled.RemoveShoppingCart,
                    contentDescription = "Segna come consumato e aggiungi alla spesa",
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    imageVector = Icons.Filled.DeleteOutline,
                    contentDescription = "Elimina ${item.name}",
                )
            }
        }
    }
}

@Composable
private fun StatusDot(color: Color) {
    Surface(
        color = color,
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.size(width = 6.dp, height = 44.dp),
    ) {}
}
