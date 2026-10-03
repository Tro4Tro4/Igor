package com.igor.fridge.data

import com.igor.fridge.data.local.PriceRecord
import com.igor.fridge.data.local.PriceRecordDao
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Storico dei prezzi in memoria, ordinato come la query vera. */
class FakePriceRecordDao : PriceRecordDao {
    private val state = MutableStateFlow<List<PriceRecord>>(emptyList())

    val records: List<PriceRecord> get() = state.value

    override fun observeAll(): Flow<List<PriceRecord>> = state.map { it.sortedWith(ORDER) }

    override fun observeByProduct(productKey: String): Flow<List<PriceRecord>> =
        state.map { list -> list.filter { it.productKey == productKey }.sortedWith(ORDER) }

    override suspend fun findLatest(productKey: String): PriceRecord? =
        records.filter { it.productKey == productKey }.sortedWith(ORDER).firstOrNull()

    override suspend fun insertAll(records: List<PriceRecord>) {
        state.update { it + records }
    }

    override suspend fun delete(record: PriceRecord) {
        state.update { list -> list.filterNot { it.uuid == record.uuid } }
    }

    private companion object {
        /** ORDER BY purchasedOn DESC, createdAt DESC */
        val ORDER: Comparator<PriceRecord> =
            compareByDescending<PriceRecord> { it.purchasedOn }.thenByDescending { it.createdAt }
    }
}
