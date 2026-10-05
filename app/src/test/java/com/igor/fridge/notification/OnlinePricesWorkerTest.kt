package com.igor.fridge.notification

import com.igor.fridge.data.repository.OnlineRefreshResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class OnlinePricesWorkerTest {
    @Test fun `revoca non avvia aggiornamento e errori transitori chiedono retry`() = runTest {
        assertEquals(OnlineWorkOutcome.SUCCESS,runOnlinePricesWork({false},{error("Rete inattesa")}))
        assertEquals(OnlineWorkOutcome.RETRY,runOnlinePricesWork({true},{OnlineRefreshResult(errors=listOf("Rete"),transient=true)}))
        assertEquals(OnlineWorkOutcome.FAILURE,runOnlinePricesWork({true},{OnlineRefreshResult(errors=listOf("Schema"))}))
        assertEquals(OnlineWorkOutcome.SUCCESS,runOnlinePricesWork({true},{OnlineRefreshResult(discarded=true)}))
    }
}
