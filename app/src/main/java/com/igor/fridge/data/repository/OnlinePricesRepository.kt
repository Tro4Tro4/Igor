package com.igor.fridge.data.repository

import com.igor.fridge.data.Transactor
import com.igor.fridge.data.local.*
import com.igor.fridge.data.onlineprices.*
import com.igor.fridge.domain.prices.bindingMatches
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout

data class OnlineRefreshResult(val updatedSources: Int = 0, val errors: List<String> = emptyList(), val discarded: Boolean = false, val transient: Boolean = false)

class OnlinePricesRepository(
    private val dao: OnlinePricesDao,
    private val settings: Flow<OnlinePricesSettings>,
    private val findItem: suspend (String) -> ShoppingItem?,
    private val fetchSources: suspend () -> List<OnlineSource>,
    private val fetchOffers: suspend (String,String) -> List<OnlineOffer>,
    private val search: suspend (String,String) -> OnlinePage,
    private val transactor: Transactor = Transactor.Direct,
) {
    private val mutex = Mutex()
    private data class CandidateSnapshot(val nameKey: String, val brandKey: String, val postcode: String)
    private val candidates = mutableMapOf<Pair<String,String>,CandidateSnapshot>()
    fun observeOffers(postcode: String) = dao.observeOffers(postcode)
    fun observeBindings() = dao.observeBindings()
    fun observeSources() = dao.observeSources()
    suspend fun searchCandidates(item: ShoppingItem): List<OnlineOffer> {
        val initial = settings.first()
        if (!initial.enabled) throw OnlinePricesException("Attiva i prezzi online nelle impostazioni")
        val page = withTimeout(30_000) { search(listOfNotNull(item.name,item.brand).joinToString(" "),initial.postcode) }
        return mutex.withLock {
            if (settings.first() != initial || findItem(item.uuid)?.let { it.nameKey == item.nameKey && nameKeyOf(it.brand.orEmpty()) == nameKeyOf(item.brand.orEmpty()) } != true) return@withLock emptyList()
            transactor.run { dao.upsertOffers(page.items) }
            page.items.forEach { candidates[item.uuid to it.offerId] = CandidateSnapshot(item.nameKey,nameKeyOf(item.brand.orEmpty()),initial.postcode) }
            page.items
        }
    }
    suspend fun select(itemUuid: String, offerId: String) = mutex.withLock {
        val initial = settings.first()
        require(initial.enabled)
        transactor.run {
            val item = findItem(itemUuid) ?: throw OnlinePricesException("La voce non e' piu' in lista")
            val snapshot = candidates[itemUuid to offerId] ?: throw OnlinePricesException("Ripeti la ricerca prima di confermare il prodotto")
            if (snapshot.nameKey != item.nameKey || snapshot.brandKey != nameKeyOf(item.brand.orEmpty()) || snapshot.postcode != initial.postcode) throw OnlinePricesException("La voce e' cambiata: ripeti la ricerca")
            val offer = dao.allOffers().firstOrNull { it.offerId == offerId && it.requestedPostcode == initial.postcode } ?: throw OnlinePricesException("Prezzo non piu' disponibile")
            require(settings.first() == initial)
            dao.upsertBinding(OnlineBinding(item.uuid,offer.source,offer.productId,item.nameKey,nameKeyOf(item.brand.orEmpty()),offer.packAmount,offer.packUnit,true))
        }
    }
    suspend fun unselect(itemUuid: String, source: String) = mutex.withLock { dao.unselect(itemUuid,source) }
    suspend fun refresh(): OnlineRefreshResult = mutex.withLock {
        val initial = settings.first()
        if (!initial.enabled) return@withLock OnlineRefreshResult(discarded=true)
        try {
            withTimeout(30_000) {
                val sources = fetchSources()
                if (settings.first() != initial) return@withTimeout OnlineRefreshResult(discarded=true)
                // Lo stato operativo deve aggiornarsi anche se un prodotto viene ritirato.
                transactor.run {
                    if (settings.first() == initial) dao.upsertSources(sources)
                }
                val bindings = dao.observeBindings().first().filter { binding -> findItem(binding.shoppingUuid)?.let { bindingMatches(binding,it) } == true }
                val active = sources.filter { it.status == "active" }.map { it.source }.toSet()
                val batches = mutableMapOf<String,List<OnlineOffer>>()
                for (id in bindings.map { it.productId }.distinct()) {
                    if (settings.first() != initial) return@withTimeout OnlineRefreshResult(discarded=true)
                    if (bindings.none { it.productId == id && it.source in active }) continue
                    batches[id] = fetchOffers(id,initial.postcode)
                }
                transactor.run {
                    if (settings.first() != initial) return@run OnlineRefreshResult(discarded=true)
                    batches.forEach { (id,offers) ->
                        dao.deleteProduct(id,initial.postcode)
                        dao.upsertOffers(offers)
                        if (id.startsWith("gtin:")) {
                            bindings.filter { it.productId == id }.forEach { binding ->
                                val item = findItem(binding.shoppingUuid)
                                if (item != null && bindingMatches(binding,item)) offers.filter { offer ->
                                    offer.gtinVerified && offer.source in active && offer.productId == id &&
                                        offer.packUnit == binding.selectedPackUnit && offer.packAmount != null &&
                                        binding.selectedPackAmount != null && offer.packAmount.compareTo(binding.selectedPackAmount) == 0
                                }.forEach { offer ->
                                    if (bindings.none { it.shoppingUuid == item.uuid && it.source == offer.source }) {
                                        dao.upsertBinding(binding.copy(source=offer.source,selectedPackAmount=offer.packAmount,selectedPackUnit=offer.packUnit))
                                    }
                                }
                            }
                        }
                    }
                    OnlineRefreshResult(updatedSources=sources.count { it.status == "active" })
                }
            }
        } catch (e: kotlinx.coroutines.TimeoutCancellationException) {
            OnlineRefreshResult(errors=listOf("Servizio prezzi non risponde"),transient=true)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { OnlineRefreshResult(errors=listOf(e.message ?: "Aggiornamento prezzi non riuscito"),transient=(e as? OnlinePricesException)?.transient == true) }
    }
    suspend fun clear() = mutex.withLock { candidates.clear(); transactor.run { dao.clearOffers(); dao.clearBindings(); dao.clearSources() } }
}
