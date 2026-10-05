package com.igor.fridge.ui

import com.igor.fridge.data.FakeOnlinePricesDao
import com.igor.fridge.data.local.*
import com.igor.fridge.data.onlineprices.*
import com.igor.fridge.data.repository.OnlinePricesRepository
import com.igor.fridge.ui.compare.OnlineCompareViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnlineCompareViewModelTest {
    @Test fun `fonti inattive non appaiono gratuite`() = runTest {
        val dispatcher=StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val dao=FakeOnlinePricesDao()
            dao.upsertSources(listOf(OnlineSource("esselunga","candidate",3,null)))
            val settings=MutableStateFlow(OnlinePricesSettings(true))
            val shopping=MutableStateFlow(listOf(ShoppingItem("s","Latte")))
            val repo=OnlinePricesRepository(dao,settings,{shopping.value.firstOrNull()},fetchSources={dao.sources.value},fetchOffers={_,_->emptyList()},search={_,_->OnlinePage(emptyList(),null,0)})
            val vm=OnlineCompareViewModel(repo,shopping,settings,configured=true,computeDispatcher=dispatcher)
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { vm.uiState.collect() }
            runCurrent()
            assertTrue(vm.uiState.value.comparison.estimates.isEmpty())
            assertFalse(vm.uiState.value.hasWinner)
            assertEquals("candidate",vm.uiState.value.sources.single().status)
        } finally { Dispatchers.resetMain() }
    }
}
