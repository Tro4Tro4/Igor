package com.igor.fridge.ui

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Apre la pagina dell'app nelle impostazioni di Android. Dopo due rifiuti il sistema non
 * mostra piu' la richiesta di permesso: da li' l'utente puo' ancora concederlo.
 */
fun Context.openAppSettings() {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        // Impostazioni non raggiungibili: non resta altro da proporre.
    }
}

/**
 * Vero se il permesso e' stato negato e il sistema non lo richiedera' piu' (rifiutato due
 * volte o "non chiedere piu'"). Va chiesto dopo almeno una richiesta: prima della prima
 * richiesta la risposta del sistema e' la stessa.
 */
fun Context.isPermissionPermanentlyDenied(permission: String): Boolean {
    val activity = findActivity() ?: return false
    return !activity.shouldShowRequestPermissionRationale(permission)
}

private fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
