package com.igor.fridge.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.IgorDatabase
import com.igor.fridge.data.local.SavedList
import com.igor.fridge.data.local.SavedListDao
import com.igor.fridge.data.local.SavedListItem
import com.igor.fridge.data.local.nameKeyOf
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.Instant

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SavedListDaoTest {

    @Test fun `categorie precedenti e nuove convivono senza cambiare foto e quantita'`() = runTest {
        val list = SavedList(uuid="compat", name="Compatibilita'", createdAt=now, updatedAt=now)
        val old = item("old", "compat", 0, "Latte").copy(category=FoodCategory.LATTICINI, quantity=2.0)
        val fresh = item("new", "compat", 1, "Biscotti").copy(category=FoodCategory.BISCOTTI, quantity=3.0)
        dao.replace(list, listOf(old, fresh))
        assertEquals(listOf(old, fresh), dao.itemsOf("compat"))
    }

    private lateinit var db: IgorDatabase
    private lateinit var dao: SavedListDao

    private val now = Instant.ofEpochMilli(1_774_000_000_000)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext(),
            IgorDatabase::class.java,
        ).build()
        dao = db.savedListDao()
    }

    @After
    fun tearDown() = db.close()

    private fun item(uuid: String, listUuid: String, position: Int, name: String) =
        SavedListItem(
            uuid = uuid,
            listUuid = listUuid,
            position = position,
            name = name,
            category = FoodCategory.DISPENSA,
            photoPath = "$name.jpg",
        )

    @Test
    fun `replace sostituisce le voci e il riepilogo le conta`() = runTest {
        val list = SavedList(uuid = "l1", name = "Settimanale", createdAt = now, updatedAt = now)
        dao.replace(list, listOf(item("a", "l1", 0, "Pasta"), item("b", "l1", 1, "Riso")))
        dao.replace(list, listOf(item("c", "l1", 0, "Farina")))

        assertEquals(listOf("Farina"), dao.itemsOf("l1").map { it.name })
        val summary = dao.observeSummaries().first().single()
        assertEquals("Settimanale", summary.name)
        assertEquals(1, summary.itemCount)
        assertEquals(now, summary.updatedAt)
        assertEquals(listOf("Farina.jpg"), dao.photoPaths())
    }

    @Test
    fun `le voci tornano nell'ordine in cui sono state salvate`() = runTest {
        val list = SavedList(uuid = "l1", name = "Grigliata", createdAt = now, updatedAt = now)
        dao.replace(list, listOf(item("z", "l1", 0, "Salsicce"), item("a", "l1", 1, "Birra")))

        assertEquals(listOf("Salsicce", "Birra"), dao.itemsOf("l1").map { it.name })
    }

    @Test
    fun `delete toglie la lista e le sue voci, e basta`() = runTest {
        dao.replace(
            SavedList(uuid = "l1", name = "Uno", createdAt = now, updatedAt = now),
            listOf(item("a", "l1", 0, "Pasta")),
        )
        dao.replace(
            SavedList(uuid = "l2", name = "Due", createdAt = now, updatedAt = now),
            listOf(item("b", "l2", 0, "Riso")),
        )

        dao.delete("l1")

        assertEquals(listOf("Due"), dao.observeSummaries().first().map { it.name })
        assertTrue(dao.itemsOf("l1").isEmpty())
        assertEquals(listOf("Riso"), dao.itemsOf("l2").map { it.name })
    }

    @Test
    fun `il nome si cerca senza distinguere le maiuscole`() = runTest {
        dao.upsertList(SavedList(uuid = "l1", name = "Settimanale", createdAt = now, updatedAt = now))

        assertEquals("l1", dao.findByNameKey(nameKeyOf("SETTIMANALE"))?.uuid)
    }
}
