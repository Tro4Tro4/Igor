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

        fun build(context: Context): IgorDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                IgorDatabase::class.java,
                DATABASE_NAME,
            ).build()
    }
}
