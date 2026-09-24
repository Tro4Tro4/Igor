package com.igor.fridge.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.igor.fridge.ui.edit.EditItemScreen
import com.igor.fridge.ui.edit.NEW_ITEM_UUID
import com.igor.fridge.ui.inventory.InventoryScreen
import com.igor.fridge.ui.scanner.BarcodeScannerScreen
import com.igor.fridge.ui.settings.SettingsScreen
import com.igor.fridge.ui.shopping.ShoppingScreen

object Routes {
    const val INVENTORY = "inventory"
    const val EDIT = "edit/{uuid}"
    const val SCANNER = "scanner"
    const val SHOPPING = "shopping"
    const val SETTINGS = "settings"

    fun edit(uuid: String): String = "edit/$uuid"
}

@Composable
fun IgorApp(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    RequestNotificationPermissionOnce()

    // Il codice letto dallo scanner viene consegnato alla schermata di modifica:
    // le due destinazioni non condividono ViewModel, quindi lo stato vive qui.
    var scannedBarcode by rememberSaveable { mutableStateOf<String?>(null) }

    NavHost(
        navController = navController,
        startDestination = Routes.INVENTORY,
        modifier = modifier,
    ) {
        composable(Routes.INVENTORY) {
            InventoryScreen(
                onAddItem = { navController.navigate(Routes.edit(NEW_ITEM_UUID)) },
                onEditItem = { uuid -> navController.navigate(Routes.edit(uuid)) },
                onOpenShoppingList = { navController.navigate(Routes.SHOPPING) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
            )
        }

        composable(
            route = Routes.EDIT,
            arguments = listOf(
                navArgument("uuid") {
                    type = NavType.StringType
                    defaultValue = NEW_ITEM_UUID
                },
            ),
        ) { backStackEntry ->
            val uuid = backStackEntry.arguments?.getString("uuid") ?: NEW_ITEM_UUID
            EditItemScreen(
                uuid = uuid,
                scannedBarcode = scannedBarcode,
                onBarcodeConsumed = { scannedBarcode = null },
                onOpenScanner = { navController.navigate(Routes.SCANNER) },
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.SCANNER) {
            BarcodeScannerScreen(
                onBarcodeDetected = { barcode ->
                    scannedBarcode = barcode
                    navController.popBackStack()
                },
                onClose = { navController.popBackStack() },
            )
        }

        composable(Routes.SHOPPING) {
            ShoppingScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}

/** Da Android 13 le notifiche richiedono un permesso: lo chiediamo al primo avvio. */
@Composable
private fun RequestNotificationPermissionOnce() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { /* l'esito non cambia il flusso: senza permesso l'app funziona ma non notifica */ }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
}
