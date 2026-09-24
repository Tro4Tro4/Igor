package com.igor.fridge.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [FoodItem::class, ShoppingItem::class],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IgorDatabase : RoomDatabase() {

    abstract fun foodItemDao(): FoodItemDao

    abstract fun shoppingItemDao(): ShoppingItemDao

    companion object {
        const val DATABASE_NAME = "igor-database"

        /**
         * DA RIMUOVERE non appena esistano dati reali da proteggere.
         *
         * Lo schema e' cambiato mentre la versione restava 1: una build precedente gia'
         * installata morirebbe all'apertura del database ("Room cannot verify the data
         * integrity"). Oggi nessun dispositivo contiene dati che valga la pena salvare,
         * quindi ricreare il database e' accettabile; da quando ce ne saranno, questa riga
         * cancellerebbe l'inventario dell'utente senza dire niente e va sostituita da una
         * migrazione vera.
         */
        fun build(context: Context): IgorDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                IgorDatabase::class.java,
                DATABASE_NAME,
            ).fallbackToDestructiveMigration()
                .build()
    }
}
