package com.igor.fridge.data

import com.igor.fridge.data.local.SavedList
import com.igor.fridge.data.local.SavedListDao
import com.igor.fridge.data.local.SavedListItem
import com.igor.fridge.data.local.SavedListSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update

/**
 * Liste salvate in memoria. Implementa solo i metodi astratti: replace() e delete() sono
 * quelli veri del DAO, cosi' i test esercitano la stessa sequenza di scritture.
 */
class FakeSavedListDao : SavedListDao() {
    private val listState = MutableStateFlow<List<SavedList>>(emptyList())
    private val itemState = MutableStateFlow<List<SavedListItem>>(emptyList())

    val lists: List<SavedList> get() = listState.value
    val items: List<SavedListItem> get() = itemState.value

    override fun observeSummaries(): Flow<List<SavedListSummary>> =
        combine(listState, itemState) { lists, items ->
            lists.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }).map { list ->
                SavedListSummary(
                    uuid = list.uuid,
                    name = list.name,
                    updatedAt = list.updatedAt,
                    itemCount = items.count { it.listUuid == list.uuid },
                )
            }
        }

    override suspend fun findByName(name: String): SavedList? =
        lists.firstOrNull { it.name.equals(name, ignoreCase = true) }

    override suspend fun itemsOf(listUuid: String): List<SavedListItem> =
        items.filter { it.listUuid == listUuid }.sortedBy { it.position }

    override suspend fun photoPaths(): List<String> = items.mapNotNull { it.photoPath }

    override suspend fun upsertList(list: SavedList) {
        listState.update { lists -> lists.filterNot { it.uuid == list.uuid } + list }
    }

    override suspend fun insertItems(items: List<SavedListItem>) {
        itemState.update { it + items }
    }

    override suspend fun deleteItemsOf(listUuid: String) {
        itemState.update { items -> items.filterNot { it.listUuid == listUuid } }
    }

    override suspend fun deleteListRow(uuid: String) {
        listState.update { lists -> lists.filterNot { it.uuid == uuid } }
    }
}
