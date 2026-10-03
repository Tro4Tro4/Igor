package com.igor.fridge.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import com.igor.fridge.ui.compare.CompareScreen
import com.igor.fridge.ui.edit.EditItemScreen
import com.igor.fridge.ui.edit.NEW_ITEM_UUID
import com.igor.fridge.ui.inventory.InventoryScreen
import com.igor.fridge.ui.prices.PriceHistoryScreen
import com.igor.fridge.ui.prices.PricesScreen
import com.igor.fridge.ui.receipt.ReceiptScreen
import com.igor.fridge.ui.scanner.BarcodeScannerScreen
import com.igor.fridge.ui.settings.AisleOrderScreen
import com.igor.fridge.ui.settings.OpenPricesScreen
import com.igor.fridge.ui.settings.SettingsScreen
import com.igor.fridge.ui.shopping.SavedListsScreen
import com.igor.fridge.ui.shopping.ShoppingItemEditScreen
import com.igor.fridge.ui.shopping.ShoppingScreen

object Routes {
    const val INVENTORY = "inventory"
    const val EDIT = "edit/{uuid}"
    const val SCANNER = "scanner"
    const val SHOPPING = "shopping"
    const val SHOPPING_ITEM = "shopping/item/{uuid}"
    const val SAVED_LISTS = "shopping/saved"
    const val AISLE_ORDER = "shopping/aisles"
    const val RECEIPT = "receipt"
    const val PRICES = "prices"
    const val PRICE_HISTORY = "prices/{key}"
    const val SETTINGS = "settings"
    const val COMPARE = "compare"
    const val OPEN_PRICES = "settings/open-prices"

    /** Chiave con cui lo scanner restituisce il codice alla scheda prezzi che l'ha chiesto. */
    const val SCANNED_BARCODE = "scanned_barcode"

    fun edit(uuid: String): String = "edit/$uuid"

    fun shoppingItem(uuid: String): String = "shopping/item/$uuid"

    /** La chiave di un prodotto contiene spazi: va codificata per stare in un percorso. */
    fun priceHistory(key: String): String = "prices/${Uri.encode(key)}"
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
                onOpenReceipt = { navController.navigate(Routes.RECEIPT) },
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
                    // Lo scanner serve due schermate: la scheda prezzi riceve il codice nel
                    // proprio SavedStateHandle, la modifica dell'inventario come prima.
                    val previous = navController.previousBackStackEntry
                    if (previous?.destination?.route == Routes.PRICE_HISTORY) {
                        previous.savedStateHandle[Routes.SCANNED_BARCODE] = barcode
                    } else {
                        scannedBarcode = barcode
                    }
                    navController.popBackStack()
                },
                onClose = { navController.popBackStack() },
            )
        }

        composable(Routes.SHOPPING) {
            ShoppingScreen(
                onBack = { navController.popBackStack() },
                onOpenItem = { uuid -> navController.navigate(Routes.shoppingItem(uuid)) },
                onOpenSavedLists = { navController.navigate(Routes.SAVED_LISTS) },
                onOpenAisleOrder = { navController.navigate(Routes.AISLE_ORDER) },
                onOpenReceipt = { navController.navigate(Routes.RECEIPT) },
                onOpenPrices = { navController.navigate(Routes.PRICES) },
                onOpenCompare = { navController.navigate(Routes.COMPARE) },
            )
        }

        composable(
            route = Routes.SHOPPING_ITEM,
            arguments = listOf(navArgument("uuid") { type = NavType.StringType }),
        ) { backStackEntry ->
            ShoppingItemEditScreen(
                uuid = backStackEntry.arguments?.getString("uuid").orEmpty(),
                onDone = { navController.popBackStack() },
            )
        }

        composable(Routes.SAVED_LISTS) {
            SavedListsScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.AISLE_ORDER) {
            AisleOrderScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.RECEIPT) {
            ReceiptScreen(
                onDone = { navController.popBackStack() },
                onOpenPrices = {
                    navController.navigate(Routes.PRICES) {
                        popUpTo(Routes.RECEIPT) { inclusive = true }
                    }
                },
            )
        }

        composable(Routes.PRICES) {
            PricesScreen(
                onBack = { navController.popBackStack() },
                onOpenProduct = { key -> navController.navigate(Routes.priceHistory(key)) },
            )
        }

        composable(
            route = Routes.PRICE_HISTORY,
            arguments = listOf(navArgument("key") { type = NavType.StringType }),
        ) { backStackEntry ->
            val scanned by backStackEntry.savedStateHandle
                .getStateFlow<String?>(Routes.SCANNED_BARCODE, null)
                .collectAsState()
            PriceHistoryScreen(
                productKey = backStackEntry.arguments?.getString("key").orEmpty(),
                onBack = { navController.popBackStack() },
                onScanBarcode = { navController.navigate(Routes.SCANNER) },
                scannedBarcode = scanned,
                onBarcodeConsumed = { backStackEntry.savedStateHandle[Routes.SCANNED_BARCODE] = null },
                onOpenOpenPrices = { navController.navigate(Routes.OPEN_PRICES) },
            )
        }

        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onOpenAisleOrder = { navController.navigate(Routes.AISLE_ORDER) },
                onOpenOpenPrices = { navController.navigate(Routes.OPEN_PRICES) },
            )
        }

        composable(Routes.OPEN_PRICES) {
            OpenPricesScreen(onBack = { navController.popBackStack() })
        }

        composable(Routes.COMPARE) {
            CompareScreen(
                onBack = { navController.popBackStack() },
                onOpenOpenPrices = { navController.navigate(Routes.OPEN_PRICES) },
            )
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
