package com.igor.fridge.ui

import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.ui.settings.AisleOrderViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AisleOrderViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val stored = MutableStateFlow<List<FoodCategory>>(FoodCategory.entries)

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `spostare una corsia la salva subito`() = runTest(dispatcher) {
        val vm = AisleOrderViewModel(stored) { stored.value = it }
        backgroundScope.launch { vm.order.collect {} }
        dispatcher.scheduler.advanceUntilIdle()

        vm.move(FoodCategory.BEVANDE, -9)
        dispatcher.scheduler.advanceUntilIdle()

        assertEquals(FoodCategory.BEVANDE, stored.value.first())
        assertEquals(FoodCategory.BEVANDE, vm.order.first().first())

        vm.reset()
        dispatcher.scheduler.advanceUntilIdle()
        assertEquals(FoodCategory.entries, stored.value)
    }
}
