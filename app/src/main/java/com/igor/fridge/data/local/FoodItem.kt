package com.igor.fridge.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * Un alimento presente in casa.
 *
 * [uuid] e' generato sul dispositivo invece che da SQLite: identificatori autoincrementali
 * collidono fra dispositivi diversi, e l'identita' deve restare valida se un domani
 * l'inventario verra' sincronizzato.
 *
 * [removedAt] realizza la cancellazione logica. Una riga cancellata fisicamente sarebbe
 * invisibile a una sincronizzazione e cancellerebbe la storia di cosa e' stato consumato.
 *
 * [nameKey] e' il nome in forma di ricerca (vedi [nameKeyOf]).
 *
 * [expiryDate] e' nullable perche' non tutti i prodotti riportano una scadenza: gli
 * articoli senza data restano in inventario ma non generano notifiche.
 */
@Entity(
    tableName = "food_items",
    indices = [Index("barcode"), Index("expiryDate"), Index("nameKey"), Index("removedAt")],
)
data class FoodItem(
    @PrimaryKey
    val uuid: String,
    val name: String,
    val barcode: String? = null,
    val category: FoodCategory = FoodCategory.ALTRO,
    val location: StorageLocation = StorageLocation.FRIGO,
    val quantity: Double = 1.0,
    val unit: QuantityUnit = QuantityUnit.PZ,
    val expiryDate: LocalDate? = null,
    val addedAt: LocalDate = LocalDate.now(),
    val notes: String? = null,
    val updatedAt: Instant = Instant.now(),
    val removedAt: Instant? = null,
    val removalReason: RemovalReason? = null,
    val brand: String? = null,
) {
    /**
     * Sempre derivata dal nome: sta fuori dal costruttore, cosi' ogni `copy(name = ...)`
     * la ricalcola. E' un `var` solo perche' Room possa riscriverla leggendo la riga.
     */
    var nameKey: String = nameKeyOf(name)
}
