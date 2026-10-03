package com.igor.fridge.data

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Esegue un blocco di scritture come un'unica transazione: o tutte o nessuna. Serve dove
 * un'operazione tocca piu' tabelle (frigo, lista, prezzi) e un errore o la morte del
 * processo a meta' lascerebbero dati incoerenti o duplicati al tentativo successivo.
 */
interface Transactor {
    suspend fun <T> run(block: suspend () -> T): T

    /** Senza transazione: per i test con DAO in memoria. */
    object Direct : Transactor {
        override suspend fun <T> run(block: suspend () -> T): T = block()
    }
}

class RoomTransactor(private val database: RoomDatabase) : Transactor {
    override suspend fun <T> run(block: suspend () -> T): T = database.withTransaction(block)
}
