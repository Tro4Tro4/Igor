package com.igor.fridge.ui

import com.igor.fridge.data.openprices.OpenPricesException
import com.igor.fridge.data.openprices.OpenPricesSession
import com.igor.fridge.data.prefs.OpenPricesSettings
import com.igor.fridge.ui.settings.OpenPricesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OpenPricesViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val settings = MutableStateFlow(OpenPricesSettings())

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(login: suspend (String, String) -> OpenPricesSession) = OpenPricesViewModel(
        settings = settings,
        setEnabled = { settings.value = settings.value.copy(enabled = it) },
        saveSession = { user, token -> settings.value = settings.value.copy(userId = user, token = token) },
        authenticate = login,
    )

    @Test
    fun `si attiva, si entra salvando il token e si esce`() = runTest(dispatcher) {
        val vm = viewModel { user, _ -> OpenPricesSession(user, "tok") }
        backgroundScope.launch { vm.uiState.collect {} }

        vm.onEnabledChange(true)
        vm.login("mario", "segreta")
        dispatcher.scheduler.advanceUntilIdle()

        assertTrue(settings.value.enabled)
        assertEquals(OpenPricesSettings(true, "mario", "tok"), settings.value)
        assertEquals("Accesso eseguito come mario", vm.uiState.value.message)

        vm.logout()
        dispatcher.scheduler.advanceUntilIdle()
        assertFalse(settings.value.isLoggedIn)
    }

    @Test
    fun `un accesso rifiutato lo dice e non salva nulla`() = runTest(dispatcher) {
        val vm = viewModel { _, _ -> throw OpenPricesException("Utente o password non corretti", 401) }
        backgroundScope.launch { vm.uiState.collect {} }

        vm.login("mario", "sbagliata")
        dispatcher.scheduler.advanceUntilIdle()

        assertFalse(settings.value.isLoggedIn)
        assertEquals("Utente o password non corretti", vm.uiState.value.message)
        assertFalse(vm.uiState.value.isLoggingIn)
    }
}
