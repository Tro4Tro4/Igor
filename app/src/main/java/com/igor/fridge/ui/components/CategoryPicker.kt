package com.igor.fridge.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import com.igor.fridge.R
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.ui.icon
import com.igor.fridge.ui.labelRes

@Composable
fun CategoryPicker(
    value: FoodCategory,
    onSelect: (FoodCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    OutlinedButton(onClick = { expanded = true }, modifier = modifier) {
        Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Text(stringResource(R.string.edit_category), style = MaterialTheme.typography.labelMedium)
            Text(stringResource(value.labelRes()), style = MaterialTheme.typography.bodyLarge)
        }
    }
    if (expanded) {
        CategoryPickerSheet(value, FoodCategory.entries, false,
            onSelect = { it?.let(onSelect) }, onDismiss = { expanded = false })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(
    selected: FoodCategory?,
    options: List<FoodCategory>,
    allowAll: Boolean,
    onSelect: (FoodCategory?) -> Unit,
    onDismiss: () -> Unit,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val labels = options.associateWith { stringResource(it.labelRes()) }
    val visible = options.filter { labels.getValue(it).contains(query.trim(), ignoreCase = true) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding()) {
            Text(stringResource(R.string.edit_category),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 16.dp))
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text(stringResource(R.string.category_search)) },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )
            LazyColumn(Modifier.fillMaxWidth().weight(1f).selectableGroup(),
                contentPadding = PaddingValues(bottom = 16.dp)) {
                if (allowAll) item(key = "all") {
                    CategoryOption(stringResource(R.string.category_all), null, selected == null) {
                        onSelect(null)
                        onDismiss()
                    }
                }
                items(visible, key = { it.name }) { category ->
                    CategoryOption(labels.getValue(category), category.icon(), category == selected) {
                        onSelect(category)
                        onDismiss()
                    }
                }
                if (visible.isEmpty()) item(key = "empty") {
                    Text(stringResource(R.string.category_no_results),
                        modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun CategoryOption(label: String, icon: String?, selected: Boolean, onSelect: () -> Unit) {
    ListItem(
        headlineContent = { Text(label) },
        leadingContent = icon?.let { { Text(it, Modifier.clearAndSetSemantics {}) } },
        trailingContent = { RadioButton(selected = selected, onClick = null) },
        modifier = Modifier.fillMaxWidth().selectable(
            selected = selected, role = Role.RadioButton, onClick = onSelect),
    )
}
