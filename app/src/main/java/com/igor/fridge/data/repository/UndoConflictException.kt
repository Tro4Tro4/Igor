package com.igor.fridge.data.repository

/** L'annullamento non puo' cancellare modifiche intervenute dopo l'operazione. */
class UndoConflictException : IllegalStateException("Dati modificati dopo l’operazione: annullamento non eseguito")
