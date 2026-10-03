package com.igor.fridge.di

import android.content.Context
import com.igor.fridge.data.RoomTransactor
import com.igor.fridge.data.Transactor
import com.igor.fridge.data.local.IgorDatabase
import com.igor.fridge.data.openprices.OpenPricesClient
import com.igor.fridge.data.openprices.UrlConnectionTransport
import com.igor.fridge.data.photos.FilePhotoStore
import com.igor.fridge.data.photos.PhotoStore
import com.igor.fridge.data.prefs.SettingsStore
import com.igor.fridge.data.receipt.MlKitReceiptReader
import com.igor.fridge.data.receipt.ReceiptReader
import com.igor.fridge.data.prefs.settingsDataStore
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.SavedListRepository
import com.igor.fridge.data.repository.ShoppingRepository

/**
 * Dependency injection manuale: per un'app di queste dimensioni un container creato
 * nell'Application e' sufficiente e non aggiunge annotation processor al build.
 * Le istanze sono lazy, cosi' il database non viene aperto finche' non serve davvero.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: IgorDatabase by lazy { IgorDatabase.build(appContext) }

    val transactor: Transactor by lazy { RoomTransactor(database) }

    val foodRepository: FoodRepository by lazy { FoodRepository(database.foodItemDao()) }

    val shoppingRepository: ShoppingRepository by lazy {
        ShoppingRepository(database.shoppingItemDao())
    }

    val savedListRepository: SavedListRepository by lazy {
        SavedListRepository(database.savedListDao())
    }

    val priceRepository: PriceRepository by lazy {
        PriceRepository(database.priceRecordDao(), database.productCodeDao())
    }

    val openPricesClient: OpenPricesClient by lazy { OpenPricesClient(UrlConnectionTransport()) }

    val photoStore: PhotoStore by lazy { FilePhotoStore(appContext) }

    val receiptReader: ReceiptReader by lazy { MlKitReceiptReader(appContext) }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext.settingsDataStore) }

    /**
     * Le foto restano su disco quando una voce esce dalla lista o cambia foto, perche'
     * l'annullamento di "Metti in frigo" e le liste salvate possono ancora volerle.
     * All'avvio nessun annullamento e' in sospeso: si cancella cio' che nessuno usa piu'.
     */
    suspend fun deleteOrphanPhotos() {
        val keep = shoppingRepository.photoNames() + savedListRepository.photoNames()
        photoStore.deleteAllExcept(keep, graceMillis = PHOTO_GRACE_MILLIS)
    }

    private companion object {
        const val PHOTO_GRACE_MILLIS = 60 * 60 * 1000L
    }
}
