package com.igor.fridge.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FoodItem::class,
        ShoppingItem::class,
        SavedList::class,
        SavedListItem::class,
        PriceRecord::class,
    ],
    version = 4,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IgorDatabase : RoomDatabase() {

    abstract fun foodItemDao(): FoodItemDao

    abstract fun shoppingItemDao(): ShoppingItemDao

    abstract fun savedListDao(): SavedListDao

    abstract fun priceRecordDao(): PriceRecordDao

    companion object {
        const val DATABASE_NAME = "igor-database"

        /**
         * Dettagli della lista della spesa (categoria, marca, note, foto, quantita' presa)
         * e liste salvate.
         *
         * E' la prima migrazione vera: dalla versione 1 esistono inventari reali, e
         * ricreare il database li cancellerebbe. Le voci gia' in lista ricevono la categoria
         * che lo stesso nome ha avuto per ultimo in inventario, come accade a una voce nuova.
         */
        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_1_2_SQL.forEach(db::execSQL)
            }
        }

        /** Separate dalla [Migration] per poterle provare anche senza Room. */
        internal val MIGRATION_1_2_SQL: List<String> = listOf(
            "ALTER TABLE `shopping_items` ADD COLUMN `category` TEXT NOT NULL DEFAULT 'ALTRO'",
            "ALTER TABLE `shopping_items` ADD COLUMN `brand` TEXT",
            "ALTER TABLE `shopping_items` ADD COLUMN `notes` TEXT",
            "ALTER TABLE `shopping_items` ADD COLUMN `photoPath` TEXT",
            "ALTER TABLE `shopping_items` ADD COLUMN `purchasedQuantity` REAL",
            """
            UPDATE `shopping_items` SET `category` = COALESCE(
                (SELECT f.`category` FROM `food_items` f
                 WHERE f.`name` = `shopping_items`.`name` COLLATE NOCASE
                 ORDER BY f.`updatedAt` DESC LIMIT 1),
                'ALTRO')
            """.trimIndent(),
            "CREATE TABLE IF NOT EXISTS `saved_lists` (`uuid` TEXT NOT NULL, `name` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`uuid`))",
            "CREATE TABLE IF NOT EXISTS `saved_list_items` (`uuid` TEXT NOT NULL, " +
                "`listUuid` TEXT NOT NULL, `position` INTEGER NOT NULL, `name` TEXT NOT NULL, " +
                "`quantity` REAL NOT NULL, `unit` TEXT NOT NULL, `category` TEXT NOT NULL, " +
                "`brand` TEXT, `notes` TEXT, `photoPath` TEXT, PRIMARY KEY(`uuid`))",
            "CREATE INDEX IF NOT EXISTS `index_saved_list_items_listUuid` " +
                "ON `saved_list_items` (`listUuid`)",
        )

        /**
         * Marca anche in inventario; prezzo e negozio sulle voci della spesa e delle liste
         * salvate. Solo colonne nuove e facoltative: le righe esistenti restano valide.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_2_3_SQL.forEach(db::execSQL)
            }
        }

        internal val MIGRATION_2_3_SQL: List<String> = listOf(
            "ALTER TABLE `food_items` ADD COLUMN `brand` TEXT",
            "ALTER TABLE `shopping_items` ADD COLUMN `unitPriceCents` INTEGER",
            "ALTER TABLE `shopping_items` ADD COLUMN `store` TEXT",
            "ALTER TABLE `saved_list_items` ADD COLUMN `unitPriceCents` INTEGER",
            "ALTER TABLE `saved_list_items` ADD COLUMN `store` TEXT",
        )

        /** Lo storico dei prezzi letti dagli scontrini: una tabella nuova, nient'altro. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_3_4_SQL.forEach(db::execSQL)
            }
        }

        internal val MIGRATION_3_4_SQL: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS `price_records` (`uuid` TEXT NOT NULL, " +
                "`productKey` TEXT NOT NULL, `productName` TEXT NOT NULL, " +
                "`purchasedOn` INTEGER NOT NULL, `store` TEXT, `quantity` REAL NOT NULL, " +
                "`unit` TEXT NOT NULL, `totalCents` INTEGER NOT NULL, " +
                "`unitPriceCents` INTEGER NOT NULL, `referenceUnit` TEXT NOT NULL, " +
                "`createdAt` INTEGER NOT NULL, PRIMARY KEY(`uuid`))",
            "CREATE INDEX IF NOT EXISTS `index_price_records_productKey` " +
                "ON `price_records` (`productKey`)",
            "CREATE INDEX IF NOT EXISTS `index_price_records_purchasedOn` " +
                "ON `price_records` (`purchasedOn`)",
        )

        /**
         * DA RIMUOVERE il fallback distruttivo: ora che esiste una migrazione serve solo a
         * non far morire l'app su un salto di versione per cui manchi la migrazione, ma
         * in quel caso cancellerebbe l'inventario senza dire niente. Resta finche' non si
         * decide come gestire quel caso.
         *
         * Nota: il fallback NON copre uno schema cambiato a versione invariata (Room
         * rifiuta di aprire il database per hash di identita' diverso). Ogni cambio di
         * schema deve quindi alzare `version` e portare la sua migrazione.
         */
        fun build(context: Context, name: String = DATABASE_NAME): IgorDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                IgorDatabase::class.java,
                name,
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .fallbackToDestructiveMigration()
                .build()
    }
}
