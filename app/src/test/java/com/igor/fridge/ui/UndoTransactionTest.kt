package com.igor.fridge.ui

import android.content.Context
import androidx.lifecycle.ViewModelStore
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.igor.fridge.data.FakePhotoStore
import com.igor.fridge.data.RoomTransactor
import com.igor.fridge.data.local.*
import com.igor.fridge.data.repository.*
import com.igor.fridge.ui.inventory.InventoryViewModel
import com.igor.fridge.ui.shopping.ShoppingViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class UndoTransactionTest {
    private lateinit var db: IgorDatabase
    private val store = ViewModelStore()
    @Before fun setup() {
        Dispatchers.setMain(Dispatchers.Unconfined)
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
            IgorDatabase::class.java).build()
    }
    @After fun cleanup() { store.clear(); db.close(); Dispatchers.resetMain() }

    @Test fun `errore seconda tabella annulla anche uscita dal frigo reale`() = runBlocking {
        val food = FoodRepository(db.foodItemDao())
        val item = food.save(FoodItem("", "Latte"))
        val failing = object : ShoppingItemDao by db.shoppingItemDao() {
            override suspend fun upsert(item: ShoppingItem) { error("disco") }
        }
        val vm = InventoryViewModel(food, ShoppingRepository(failing), flowOf(3),
            flowOf(LocalDate.now()), RoomTransactor(db), computeDispatcher = Dispatchers.Unconfined)
        store.put("vm", vm)
        vm.consume(item)
        val state = withTimeout(10000) { vm.uiState.first { it.message != null } }
        assertTrue(state.message!!.contains("riprova"))
        assertNull(food.findByUuid(item.uuid)!!.removedAt)
        assertTrue(db.shoppingItemDao().observeAll().first().isEmpty())
    }

    @Test fun `undo multiplo fallito fa rollback e resta ripetibile sul database reale`() = runBlocking {
        var fail = false
        val actual = db.shoppingItemDao()
        val failing = object : ShoppingItemDao by actual {
            override suspend fun upsert(item: ShoppingItem) {
                if (fail && item.name == "Pane") error("disco")
                actual.upsert(item)
            }
        }
        val tx = RoomTransactor(db)
        val shopping = ShoppingRepository(failing, transactor = tx)
        val food = FoodRepository(db.foodItemDao())
        shopping.addIfAbsent("Latte"); shopping.addIfAbsent("Pane")
        shopping.currentItems().forEach { shopping.setChecked(it, true) }
        val vm = ShoppingViewModel(shopping, food, FakePhotoStore(), transactor = tx)
        store.put("vm", vm)
        vm.moveCheckedToInventory()
        withTimeout(10000) { vm.uiState.first { it.canUndo } }
        fail = true; vm.undoLastMove()
        withTimeout(10000) { vm.uiState.first { it.message?.contains("non riuscito") == true } }
        assertTrue(shopping.currentItems().isEmpty())
        assertTrue(food.all().all { it.removedAt == null })
        assertTrue(vm.uiState.value.canUndo)
        val previousMessage = vm.uiState.value.messageId
        fail = false; vm.undoLastMove()
        val restored = withTimeout(10000) { vm.uiState.first {
            it.message != null && it.messageId != previousMessage
        } }
        assertEquals("Spostamento annullato", restored.message)
        assertEquals(2, shopping.currentItems().size)
        assertTrue(food.all().all { it.removalReason == RemovalReason.ERRORE })
    }
}
