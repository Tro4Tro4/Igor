package com.igor.fridge.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Conversioni fra i tipi del modello e le colonne SQLite.
 * Le date sono salvate come epoch day (Long): indipendenti dal fuso orario e ordinabili in SQL.
 */
class Converters {

    @TypeConverter
    fun toLocalDate(epochDay: Long?): LocalDate? = epochDay?.let(LocalDate::ofEpochDay)

    @TypeConverter
    fun fromLocalDate(date: LocalDate?): Long? = date?.toEpochDay()

    @TypeConverter
    fun toFoodCategory(value: String?): FoodCategory? = value?.let(FoodCategory::valueOf)

    @TypeConverter
    fun fromFoodCategory(value: FoodCategory?): String? = value?.name

    @TypeConverter
    fun toStorageLocation(value: String?): StorageLocation? = value?.let(StorageLocation::valueOf)

    @TypeConverter
    fun fromStorageLocation(value: StorageLocation?): String? = value?.name

    @TypeConverter
    fun toQuantityUnit(value: String?): QuantityUnit? = value?.let(QuantityUnit::valueOf)

    @TypeConverter
    fun fromQuantityUnit(value: QuantityUnit?): String? = value?.name
}
