package com.igor.fridge.ui.inventory

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.domain.ExpiryStatus
import com.igor.fridge.domain.expiryStatus
import java.time.LocalDate

enum class InventoryFilter { TUTTI, IN_SCADENZA, SCADUTI, SENZA_DATA }

data class InventoryCriteria(
    val query: String = "",
    val filter: InventoryFilter = InventoryFilter.TUTTI,
    val location: StorageLocation? = null,
    val category: FoodCategory? = null,
) {
    val isFiltered: Boolean
        get() = query.isNotBlank() || filter != InventoryFilter.TUTTI || location != null || category != null
}

data class InventorySection(val category: FoodCategory, val items: List<FoodItem>)

data class InventoryListModel(
    val sections: List<InventorySection>,
    val availableCategories: List<FoodCategory>,
    val totalCount: Int,
    val matchingCount: Int,
    val expiringCount: Int,
    val expiredCount: Int,
    val noDateCount: Int,
) {
    val items: List<FoodItem> = sections.flatMap { it.items }
}

fun inventoryListOf(
    items: List<FoodItem>,
    criteria: InventoryCriteria,
    today: LocalDate,
    warningDays: Int,
): InventoryListModel {
    val active = items.filter { it.removedAt == null }
    val needle = criteria.query.trim()
    val base = active.filter { item ->
        (needle.isEmpty() || item.name.contains(needle, true) || item.brand?.contains(needle, true) == true) &&
            (criteria.location == null || item.location == criteria.location) &&
            (criteria.category == null || item.category == criteria.category)
    }
    val statuses = base.associate { it.uuid to it.expiryStatus(today, warningDays) }
    val visible = base.filter { item ->
        when (criteria.filter) {
            InventoryFilter.TUTTI -> true
            InventoryFilter.IN_SCADENZA -> statuses[item.uuid] == ExpiryStatus.IN_SCADENZA
            InventoryFilter.SCADUTI -> statuses[item.uuid] == ExpiryStatus.SCADUTO
            InventoryFilter.SENZA_DATA -> statuses[item.uuid] == ExpiryStatus.SENZA_DATA
        }
    }
    val grouped = visible.groupBy { it.category }
    val order = FoodCategory.entries.filterNot { it == FoodCategory.ALTRO } + FoodCategory.ALTRO
    val available = active.mapTo(mutableSetOf()) { it.category }
    criteria.category?.let { available.add(it) }
    return InventoryListModel(
        sections = order.mapNotNull { category ->
            grouped[category]?.let { InventorySection(category, it.sortedWith(INVENTORY_ORDER)) }
        },
        availableCategories = order.filter { it in available },
        totalCount = active.size,
        matchingCount = base.size,
        expiringCount = statuses.values.count { it == ExpiryStatus.IN_SCADENZA },
        expiredCount = statuses.values.count { it == ExpiryStatus.SCADUTO },
        noDateCount = statuses.values.count { it == ExpiryStatus.SENZA_DATA },
    )
}

private val INVENTORY_ORDER = compareBy<FoodItem> { it.expiryDate == null }
    .thenBy { it.expiryDate }
    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    .thenBy { it.uuid }
