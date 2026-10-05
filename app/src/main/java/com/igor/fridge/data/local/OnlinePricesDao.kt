package com.igor.fridge.data.local

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface OnlinePricesDao {
    @Query("SELECT * FROM online_offers WHERE requestedPostcode = :postcode")
    fun observeOffers(postcode: String): Flow<List<OnlineOffer>>
    @Query("SELECT * FROM online_offers") suspend fun allOffers(): List<OnlineOffer>
    @Query("SELECT * FROM online_bindings") fun observeBindings(): Flow<List<OnlineBinding>>
    @Query("SELECT * FROM online_sources ORDER BY priority") fun observeSources(): Flow<List<OnlineSource>>
    @Upsert suspend fun upsertOffers(offers: List<OnlineOffer>)
    @Upsert suspend fun upsertSources(sources: List<OnlineSource>)
    @Upsert suspend fun upsertBinding(binding: OnlineBinding)
    @Query("DELETE FROM online_bindings WHERE shoppingUuid = :uuid AND source = :source")
    suspend fun unselect(uuid: String, source: String)
    @Query("DELETE FROM online_offers WHERE productId = :productId AND requestedPostcode = :postcode")
    suspend fun deleteProduct(productId: String, postcode: String)
    @Query("DELETE FROM online_offers") suspend fun clearOffers()
    @Query("DELETE FROM online_bindings") suspend fun clearBindings()
    @Query("DELETE FROM online_sources") suspend fun clearSources()
}
