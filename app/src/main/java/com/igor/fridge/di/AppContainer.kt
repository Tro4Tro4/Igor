package com.igor.fridge.di

import android.content.Context
import com.igor.fridge.data.local.IgorDatabase
import com.igor.fridge.data.prefs.SettingsStore
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.ShoppingRepository

/**
 * Dependency injection manuale: per un'app di queste dimensioni un container creato
 * nell'Application e' sufficiente e non aggiunge annotation processor al build.
 * Le istanze sono lazy, cosi' il database non viene aperto finche' non serve davvero.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    private val database: IgorDatabase by lazy { IgorDatabase.build(appContext) }

    val foodRepository: FoodRepository by lazy { FoodRepository(database.foodItemDao()) }

    val shoppingRepository: ShoppingRepository by lazy {
        ShoppingRepository(database.shoppingItemDao())
    }

    val settingsStore: SettingsStore by lazy { SettingsStore(appContext) }
}
