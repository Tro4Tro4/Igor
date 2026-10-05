package com.igor.fridge.data

import com.igor.fridge.data.local.*
import com.igor.fridge.data.onlineprices.*
import com.igor.fridge.data.repository.OnlinePricesRepository
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlinePricesRepositoryTest {
    @Test fun `fonte sospesa salvata anche se prodotti non rispondono e cache conservata`() = runTest {
        val dao=FakeOnlinePricesDao()
        val offer=OnlinePricesJson.parseOffers(onlineEnvelope(),"20125").items.single()
        dao.upsertOffers(listOf(offer)); dao.upsertSources(listOf(OnlineSource("conad","active",1,null)))
        dao.upsertBinding(OnlineBinding("s","conad",offer.productId,"latte","",offer.packAmount,offer.packUnit,true))
        val repo=OnlinePricesRepository(dao,MutableStateFlow(OnlinePricesSettings(true)),{ShoppingItem(it,"Latte")},fetchSources={listOf(OnlineSource("conad","suspended",1,null))},fetchOffers={_,_->throw OnlinePricesException("HTTP404")},search={_,_->OnlinePage(emptyList(),null,0)})
        repo.refresh()
        assertEquals("suspended",dao.sources.value.single().status)
        assertEquals(249L,dao.offers.value.single().packPriceCents)
    }
    @Test fun `candidato del vecchio nome non associato alla voce rinominata`() = runTest {
        val dao=FakeOnlinePricesDao()
        val offer=OnlinePricesJson.parseOffers(onlineEnvelope(),"20125").items.single()
        var item=ShoppingItem("s","Latte")
        val repo=OnlinePricesRepository(dao,MutableStateFlow(OnlinePricesSettings(true)),{item},fetchSources={emptyList()},fetchOffers={_,_->emptyList()},search={_,_->OnlinePage(listOf(offer),null,1)})
        repo.searchCandidates(item)
        item=item.copy(name="Detersivo")
        assertTrue(runCatching { repo.select("s",offer.offerId) }.isFailure)
        assertTrue(dao.bindings.value.isEmpty())
    }
    @Test fun `gtin verificato associa altre fonti solo se formato coerente`() = runTest {
        val dao=FakeOnlinePricesDao()
        val first=OnlinePricesJson.parseOffers(onlineEnvelope(),"20125").items.single()
        val second=first.copy(offerId="carrefour:a",source="carrefour",sourceUrl="https://www.carrefour.it/p/a")
        dao.upsertOffers(listOf(first)); dao.upsertBinding(OnlineBinding("s","conad",first.productId,"latte","",first.packAmount,first.packUnit,true))
        val repo=OnlinePricesRepository(dao,MutableStateFlow(OnlinePricesSettings(true)),{ShoppingItem(it,"Latte")},fetchSources={listOf(OnlineSource("conad","active",1,null),OnlineSource("carrefour","active",2,null))},fetchOffers={_,_->listOf(first,second)},search={_,_->OnlinePage(emptyList(),null,0)})
        repo.refresh()
        assertEquals(setOf("conad","carrefour"),dao.bindings.value.map { it.source }.toSet())
    }
    @Test fun `revoca o cambio cap durante richiesta scarta lotto`() = runTest {
        for (changeCap in listOf(false,true)) {
            val settings=MutableStateFlow(OnlinePricesSettings(true))
            val dao=FakeOnlinePricesDao()
            val offer=OnlinePricesJson.parseOffers(onlineEnvelope(),"20125").items.single()
            dao.upsertOffers(listOf(offer)); dao.upsertBinding(OnlineBinding("s","conad",offer.productId,"latte","",offer.packAmount,offer.packUnit,true))
            val pending=CompletableDeferred<List<OnlineOffer>>()
            val repo=OnlinePricesRepository(dao,settings,{ ShoppingItem(it,"Latte") },fetchSources={listOf(OnlineSource("conad","active",1,null))},fetchOffers={_,_->pending.await()},search={_,_->OnlinePage(emptyList(),null,0)})
            val job=async { repo.refresh() }
            runCurrent()
            settings.value=if(changeCap) OnlinePricesSettings(true,"20126") else OnlinePricesSettings(false)
            pending.complete(listOf(offer.copy(packPriceCents=999)))
            assertTrue(job.await().discarded)
            assertEquals(249L,dao.offers.value.single().packPriceCents)
        }
    }
    @Test fun `associazione riletta e prezzo scontrino intatto`() = runTest {
        val settings=MutableStateFlow(OnlinePricesSettings(true))
        val dao=FakeOnlinePricesDao()
        val offer=OnlinePricesJson.parseOffers(onlineEnvelope(),"20125").items.single()
        dao.upsertOffers(listOf(offer))
        var item: ShoppingItem?=ShoppingItem("s","Latte",brand="Conad",unitPriceCents=777,store="Fisico")
        val repo=OnlinePricesRepository(dao,settings,{item},fetchSources={emptyList()},fetchOffers={_,_->emptyList()},search={_,_->OnlinePage(listOf(offer),null,1)})
        repo.searchCandidates(item!!)
        repo.select("s",offer.offerId)
        assertEquals("conad",dao.bindings.value.single().selectedBrandKey)
        assertEquals(777L,item!!.unitPriceCents)
        item=null
        assertTrue(runCatching { repo.select("s",offer.offerId) }.isFailure)
    }
    @Test fun `consenso spento non avvia rete`() = runTest {
        val repo=OnlinePricesRepository(FakeOnlinePricesDao(),MutableStateFlow(OnlinePricesSettings()),{null},fetchSources={error("Rete non autorizzata")},fetchOffers={_,_->error("Rete non autorizzata")},search={_,_->error("Rete non autorizzata")})
        assertTrue(repo.refresh().discarded)
    }
}
