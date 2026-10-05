package com.igor.fridge.data

import com.igor.fridge.data.local.*
import kotlinx.coroutines.flow.*

class FakeOnlinePricesDao : OnlinePricesDao {
    val offers = MutableStateFlow<List<OnlineOffer>>(emptyList())
    val bindings = MutableStateFlow<List<OnlineBinding>>(emptyList())
    val sources = MutableStateFlow<List<OnlineSource>>(emptyList())
    override fun observeOffers(postcode: String) = offers.map { all -> all.filter { it.requestedPostcode == postcode } }
    override suspend fun allOffers() = offers.value
    override fun observeBindings() = bindings
    override fun observeSources() = sources
    override suspend fun upsertOffers(offers: List<OnlineOffer>) { this.offers.update { old -> old.filterNot { a -> offers.any { it.offerId == a.offerId && it.requestedPostcode == a.requestedPostcode } } + offers } }
    override suspend fun upsertSources(sources: List<OnlineSource>) { this.sources.value = sources }
    override suspend fun upsertBinding(binding: OnlineBinding) { bindings.update { it.filterNot { b -> b.shoppingUuid == binding.shoppingUuid && b.source == binding.source } + binding } }
    override suspend fun unselect(uuid: String, source: String) { bindings.update { it.filterNot { b -> b.shoppingUuid == uuid && b.source == source } } }
    override suspend fun deleteProduct(productId: String, postcode: String) { offers.update { it.filterNot { o -> o.productId == productId && o.requestedPostcode == postcode } } }
    override suspend fun clearOffers() { offers.value = emptyList() }
    override suspend fun clearBindings() { bindings.value = emptyList() }
    override suspend fun clearSources() { sources.value = emptyList() }
}
