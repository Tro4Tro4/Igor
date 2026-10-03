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
        ProductCode::class,
    ],
    version = 6,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class IgorDatabase : RoomDatabase() {

    abstract fun foodItemDao(): FoodItemDao

    abstract fun shoppingItemDao(): ShoppingItemDao

    abstract fun savedListDao(): SavedListDao

    abstract fun priceRecordDao(): PriceRecordDao

    abstract fun productCodeDao(): ProductCodeDao

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

        /** I codici a barre associati ai prodotti dello storico dei prezzi. */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                MIGRATION_4_5_SQL.forEach(db::execSQL)
            }
        }

        internal val MIGRATION_4_5_SQL: List<String> = listOf(
            "CREATE TABLE IF NOT EXISTS `product_codes` (`productKey` TEXT NOT NULL, " +
                "`barcode` TEXT NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`productKey`))",
        )

        /**
         * La colonna `nameKey` (vedi [nameKeyOf]) su inventario, lista e liste salvate, con
         * il suo indice: le ricerche per nome non dipendono piu' da `COLLATE NOCASE`, che
         * ignora le maiuscole solo per le lettere ASCII. L'indice sul nome dell'inventario
         * non serviva a quelle ricerche e lascia il posto al nuovo.
         *
         * SQLite non sa calcolare la chiave (la sua `lower()` e' solo ASCII): le righe
         * esistenti si leggono e si riscrivono da qui.
         */
        val MIGRATION_5_6: Migration = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS `index_food_items_name`")
                for (table in NAME_KEY_TABLES) {
                    db.execSQL("ALTER TABLE `$table` ADD COLUMN `nameKey` TEXT NOT NULL DEFAULT ''")
                    val rows = buildList {
                        db.query("SELECT `uuid`, `name` FROM `$table`").use { cursor ->
                            while (cursor.moveToNext()) add(cursor.getString(0) to cursor.getString(1))
                        }
                    }
                    rows.forEach { (uuid, name) ->
                        db.execSQL(
                            "UPDATE `$table` SET `nameKey` = ? WHERE `uuid` = ?",
                            arrayOf<Any>(nameKeyOf(name), uuid),
                        )
                    }
                    db.execSQL(
                        "CREATE INDEX IF NOT EXISTS `index_${table}_nameKey` ON `$table` (`nameKey`)",
                    )
                }
            }
        }

        private val NAME_KEY_TABLES = listOf("food_items", "shopping_items", "saved_lists")

        /**
         * Nessun fallback distruttivo: senza la migrazione giusta (o installando una
         * versione piu' vecchia sopra una piu' nuova) Room si rifiuta di aprire il
         * database invece di ricrearlo vuoto. Un errore visibile e' meglio di inventario,
         * storico dei prezzi e liste salvate cancellati senza dire niente; i dati restano
         * su disco e tornano accessibili con la versione giusta.
         *
         * Ogni cambio di schema alza quindi `version` e porta la sua migrazione: anche a
         * versione invariata Room rifiuta un database con un hash di identita' diverso.
         */
        fun build(context: Context, name: String = DATABASE_NAME): IgorDatabase =
            Room.databaseBuilder(
                context.applicationContext,
                IgorDatabase::class.java,
                name,
            ).addMigrations(
                MIGRATION_1_2,
                MIGRATION_2_3,
                MIGRATION_3_4,
                MIGRATION_4_5,
                MIGRATION_5_6,
            ).build()
    }
}
