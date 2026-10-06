package com.igor.fridge.di

import android.content.Context
import com.igor.fridge.data.RoomTransactor
import com.igor.fridge.data.Transactor
import com.igor.fridge.data.export.ExportContent
import com.igor.fridge.data.export.exportJson
import com.igor.fridge.data.local.IgorDatabase
import com.igor.fridge.data.openprices.OpenPricesClient
import com.igor.fridge.data.openprices.SimilarProductSearch
import com.igor.fridge.data.openprices.UrlConnectionTransport
import com.igor.fridge.data.photos.FilePhotoStore
import com.igor.fridge.data.photos.PhotoStore
import com.igor.fridge.data.prefs.SettingsStore
import com.igor.fridge.data.receipt.MlKitReceiptReader
import com.igor.fridge.data.receipt.ReceiptReader
import com.igor.fridge.data.prefs.sessionDataStore
import com.igor.fridge.data.prefs.settingsDataStore
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.SavedListRepository
import com.igor.fridge.data.repository.ShoppingRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.time.Instant

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
        ShoppingRepository(database.shoppingItemDao(), transactor = transactor)
    }

    val savedListRepository: SavedListRepository by lazy {
        SavedListRepository(database.savedListDao(), transactor = transactor)
    }

    val priceRepository: PriceRepository by lazy {
        PriceRepository(database.priceRecordDao(), database.productCodeDao())
    }

    val openPricesClient: OpenPricesClient by lazy {
        // La versione vera, non una scritta a mano che resta indietro.
        val version = runCatching {
            appContext.packageManager.getPackageInfo(appContext.packageName, 0).versionName
        }.getOrNull() ?: "?"
        OpenPricesClient(UrlConnectionTransport(), userAgent = "Igor/$version (Android)")
    }

    val similarProductSearch: SimilarProductSearch by lazy { SimilarProductSearch(openPricesClient) }

    val onlinePricesClient by lazy {
        com.igor.fridge.data.onlineprices.OnlinePricesClient(UrlConnectionTransport(),com.igor.fridge.BuildConfig.ONLINE_PRICES_URL,com.igor.fridge.BuildConfig.DEBUG,com.igor.fridge.BuildConfig.ONLINE_PRICES_TOKEN)
    }
    val onlinePricesRepository by lazy {
        com.igor.fridge.data.repository.OnlinePricesRepository(database.onlinePricesDao(),settingsStore.onlinePrices,shoppingRepository::findByUuid,
            fetchSources=onlinePricesClient::sources,fetchOffers=onlinePricesClient::offers,
            search={ q,postcode -> onlinePricesClient.search(q,null,postcode) },transactor=transactor)
    }
    val onlinePricesWorkScheduler by lazy { com.igor.fridge.notification.OnlinePricesWorkScheduler(appContext) }

    val photoStore: PhotoStore by lazy { FilePhotoStore(appContext) }

    val receiptReader: ReceiptReader by lazy { MlKitReceiptReader(appContext) }

    val settingsStore: SettingsStore by lazy {
        SettingsStore(appContext.settingsDataStore, appContext.sessionDataStore)
    }

    /** Tutti i dati dell'utente in un file JSON (vedi [exportJson]). */
    suspend fun exportData(): String = withContext(Dispatchers.IO) {
        val summaries = savedListRepository.observeSummaries().first()
        exportJson(
            ExportContent(
                food = foodRepository.all(),
                shopping = shoppingRepository.currentItems(),
                savedLists = summaries.map { it to savedListRepository.itemsOf(it.uuid) },
                prices = priceRepository.observeAll().first(),
                barcodes = priceRepository.allCodes(),
                onlineOffers = database.onlinePricesDao().allOffers(),
                onlineBindings = database.onlinePricesDao().observeBindings().first(),
                onlineSources = database.onlinePricesDao().observeSources().first(),
            ),
            exportedAt = Instant.now(),
        )
    }

    /**
     * "Elimina tutti i miei dati": inventario, liste, prezzi, foto, impostazioni e accesso
     * a Open Prices. I prezzi gia' condivisi su Open Prices restano li': sono pubblici con
     * licenza ODbL e si cancellano dal sito del servizio.
     */
    suspend fun deleteAllData() = withContext(Dispatchers.IO) {
        settingsStore.setOnlinePricesEnabled(false)
        onlinePricesWorkScheduler.sync(false)
        onlinePricesRepository.clear()
        database.clearAllTables()
        photoStore.deleteAllExcept(keep = emptySet(), graceMillis = 0)
        settingsStore.clearAll()
    }

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
