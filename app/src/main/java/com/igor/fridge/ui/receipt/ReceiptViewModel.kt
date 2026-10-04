package com.igor.fridge.ui.receipt

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.igor.fridge.data.Transactor
import com.igor.fridge.data.local.FoodCategory
import com.igor.fridge.data.local.FoodItem
import com.igor.fridge.data.local.QuantityUnit
import com.igor.fridge.data.local.ShoppingItem
import com.igor.fridge.data.local.StorageLocation
import com.igor.fridge.data.openprices.CommunityLocation
import com.igor.fridge.data.openprices.ContributionItem
import com.igor.fridge.data.openprices.OpenPricesClient
import com.igor.fridge.data.openprices.OpenPricesException
import com.igor.fridge.data.openprices.ReceiptContributor
import com.igor.fridge.data.photos.PhotoStore
import com.igor.fridge.data.photos.VerticalCrop
import com.igor.fridge.data.prefs.OpenPricesSettings
import com.igor.fridge.data.receipt.ReceiptReader
import com.igor.fridge.data.repository.FoodRepository
import com.igor.fridge.data.repository.PriceRepository
import com.igor.fridge.data.repository.Purchase
import com.igor.fridge.data.repository.ShoppingRepository
import com.igor.fridge.domain.guessCategory
import com.igor.fridge.domain.prices.normalizeGtin
import com.igor.fridge.domain.prices.storeLabel
import com.igor.fridge.domain.receipt.ReceiptEntry
import com.igor.fridge.domain.receipt.groupIntoRows
import com.igor.fridge.domain.receipt.parseReceipt
import com.igor.fridge.domain.receipt.parseReceiptMeta
import com.igor.fridge.domain.receipt.receiptMatches
import com.igor.fridge.ui.formatQuantityInput
import com.igor.fridge.ui.formatPriceInput
import com.igor.fridge.ui.igorApplication
import com.igor.fridge.ui.parsePriceCents
import com.igor.fridge.ui.parseQuantity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

enum class ReceiptPhase { CHOOSE, READING, REVIEW, SAVING, DONE }

/** Un prodotto letto dallo scontrino, come l'utente lo sta confermando. */
data class ReceiptDraft(
    val id: Int,
    val include: Boolean,
    val name: String,
    val category: FoodCategory,
    val quantityText: String,
    val unit: QuantityUnit,
    val expiryDate: LocalDate? = null,
    /** Importo della riga com'e' stampato (sconti compresi), modificabile se l'OCR sbaglia. */
    val priceText: String = "",
    /** La quantita' non si legge dallo scontrino e va chiesta (prodotti a peso). */
    val quantityToConfirm: Boolean = false,
    /** La voce della lista della spesa che questo acquisto soddisfa. */
    val shoppingMatch: ShoppingItem? = null,
    /** Categoria e posizione dell'ultima volta che il prodotto e' stato in casa. */
    val knownCategory: FoodCategory? = null,
    val knownLocation: StorageLocation? = null,
    /** Il codice a barre che il prodotto aveva in inventario, se c'era. */
    val knownBarcode: String? = null,
    /** Confezioni comprate, se lo scontrino lo dice: serve al prezzo per confezione. */
    val pieces: Int? = null,
    val quantityError: Boolean = false,
    val nameError: Boolean = false,
    val priceError: Boolean = false,
) {
    /** Va in frigo: e' stato tenuto ed e' un alimento. Gli altri lasciano solo il prezzo. */
    val goesToFridge: Boolean get() = include && category.isFood

    /** Un prodotto fresco senza scadenza: la si chiede prima di salvare. */
    val needsExpiry: Boolean get() = goesToFridge && category.isPerishable && expiryDate == null

    /** In frigo va dove era stato messo l'ultima volta, se e' lo stesso prodotto. */
    val location: StorageLocation
        get() = knownLocation?.takeIf { category == knownCategory } ?: category.defaultLocation
}

data class ReceiptUiState(
    val phase: ReceiptPhase = ReceiptPhase.CHOOSE,
    val drafts: List<ReceiptDraft> = emptyList(),
    /** Negozio e data dello scontrino: danno senso ai prezzi registrati. */
    val store: String = "",
    val purchaseDate: LocalDate = LocalDate.now(),
    val message: String? = null,
    /** Quanti prodotti freschi sono ancora senza scadenza, mentre si chiede se procedere. */
    val missingExpiryPrompt: Int? = null,
    val summary: String? = null,
    /** La foto letta: serve come prova se si condividono i prezzi su Open Prices. */
    val imageUri: String? = null,
    /** I prezzi di questo scontrino condivisibili su Open Prices (prodotti con codice a barre). */
    val contributable: List<ContributionItem> = emptyList(),
    /** Quante righe con un prezzo aveva lo scontrino: Open Prices lo chiede con la prova. */
    val pricedLines: Int = 0,
    val openPrices: OpenPricesSettings = OpenPricesSettings(),
    /** La finestra di condivisione su Open Prices, se aperta. */
    val contribution: ContributionUiState? = null,
) {
    /** Si puo' proporre di condividere i prezzi: c'e' qualcosa da inviare e un account. */
    val canContribute: Boolean
        get() = phase == ReceiptPhase.DONE && contributable.isNotEmpty() && imageUri != null &&
            openPrices.enabled && openPrices.isLoggedIn

    val includedCount: Int get() = drafts.count { it.include }

    val fridgeCount: Int get() = drafts.count { it.goesToFridge }
}

/** Condivisione dei prezzi di uno scontrino su Open Prices: negozio, prodotti, invio. */
data class ContributionUiState(
    val items: List<ContributionItem>,
    val selected: Set<String>,
    val query: String,
    val city: String = "",
    val results: List<CommunityLocation> = emptyList(),
    val searched: Boolean = false,
    val isSearching: Boolean = false,
    val location: CommunityLocation? = null,
    val isSending: Boolean = false,
    val result: String? = null,
    val error: String? = null,
    /** Strisce da togliere dalla foto prima di renderla pubblica (dati personali). */
    val crop: VerticalCrop = VerticalCrop(),
) {
    val canSend: Boolean get() = location != null && selected.isNotEmpty() && !isSending && result == null
}

/**
 * Dalla foto di uno scontrino all'inventario.
 *
 * Il testo letto diventa una bozza per prodotto; nulla entra in frigo finche' l'utente non
 * conferma. La categoria si decide come altrove: quella dell'ultima volta che il prodotto e'
 * stato in casa, poi quella della voce di lista che lo scontrino soddisfa, poi la proposta
 * dal nome. I prodotti a peso senza peso stampato chiedono la quantita', quelli freschi la
 * scadenza. Confermando, gli alimenti entrano in frigo, ogni riga con un importo finisce
 * nello storico dei prezzi (con negozio e data dello scontrino) e le voci della lista della
 * spesa soddisfatte escono dalla lista. Cio' che non si mangia non va in frigo, ma il suo
 * prezzo si registra: anche il detersivo fa parte della spesa.
 */
class ReceiptViewModel(
    private val reader: ReceiptReader,
    private val photoStore: PhotoStore,
    private val foodRepository: FoodRepository,
    private val shoppingRepository: ShoppingRepository,
    private val priceRepository: PriceRepository,
    private val today: () -> LocalDate = LocalDate::now,
    openPrices: Flow<OpenPricesSettings> = flowOf(OpenPricesSettings()),
    private val openPricesClient: OpenPricesClient? = null,
    private val transactor: Transactor = Transactor.Direct,
    private val computeDispatcher: CoroutineDispatcher = Dispatchers.Default,
    private val clearOpenPricesSession: suspend () -> Unit = {},
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReceiptUiState(purchaseDate = today()))
    val uiState: StateFlow<ReceiptUiState> = _uiState.asStateFlow()

    private val contributor = openPricesClient?.let { ReceiptContributor(it, photoStore) }

    init {
        viewModelScope.launch {
            openPrices.collect { settings -> _uiState.update { it.copy(openPrices = settings) } }
        }
    }

    private var nextId = 0

    /**
     * L'esito di un invio riuscito. Vive fuori da [ContributionUiState], che si ricrea a ogni
     * apertura: altrimenti chiudere e riaprire la finestra permetterebbe di caricare una
     * seconda volta la stessa prova e gli stessi prezzi su un database pubblico.
     */
    private var sentResult: String? = null

    fun newCaptureUri(): Uri = photoStore.newCaptureUri()

    fun onImageChosen(source: Uri) {
        _uiState.update { it.copy(phase = ReceiptPhase.READING, message = null, imageUri = source.toString()) }
        viewModelScope.launch {
            val fragments = reader.read(source)
            if (fragments == null) {
                _uiState.update {
                    it.copy(phase = ReceiptPhase.CHOOSE, message = "Impossibile leggere lo scontrino")
                }
                return@launch
            }
            // Riordinare il testo e abbinarlo alla lista sono calcoli lunghi su uno scontrino
            // fitto: fuori dal thread dell'interfaccia.
            val (rows, entries) = withContext(computeDispatcher) {
                val rows = groupIntoRows(fragments)
                rows to parseReceipt(rows)
            }
            if (entries.isEmpty()) {
                _uiState.update {
                    it.copy(
                        phase = ReceiptPhase.CHOOSE,
                        message = "Nessun prodotto riconosciuto: prova con una foto più nitida e dritta",
                    )
                }
                return@launch
            }
            val drafts = withContext(computeDispatcher) { buildDrafts(entries) }
            val meta = withContext(computeDispatcher) { parseReceiptMeta(rows, today()) }
            _uiState.update {
                it.copy(
                    phase = ReceiptPhase.REVIEW,
                    drafts = drafts,
                    store = meta.store.orEmpty(),
                    purchaseDate = meta.date ?: today(),
                )
            }
        }
    }

    private suspend fun buildDrafts(entries: List<ReceiptEntry>): List<ReceiptDraft> {
        val shopping = shoppingRepository.currentItems()
        val matched = mutableSetOf<String>()
        return entries.map { entry ->
            val match = shopping.firstOrNull { it.uuid !in matched && receiptMatches(it.name, entry.name) }
            match?.let { matched += it.uuid }
            // Il nome scelto dall'utente per la lista e' piu' leggibile di quello della cassa.
            val name = match?.name ?: entry.name
            val known = foodRepository.findLastByName(name)
            val category = match?.category?.takeIf { it != FoodCategory.ALTRO }
                ?: known?.category?.takeIf { it != FoodCategory.ALTRO }
                ?: guessCategory(entry.name)
            val toConfirm = entry.quantity == null && category.isSoldByWeight
            ReceiptDraft(
                id = nextId++,
                include = true,
                name = name,
                category = category,
                quantityText = when {
                    entry.quantity != null -> formatQuantityInput(entry.quantity)
                    toConfirm -> ""
                    else -> "1"
                },
                unit = entry.unit ?: if (toConfirm) QuantityUnit.KG else QuantityUnit.PZ,
                priceText = entry.price?.let { formatPriceInput(Math.round(it * 100)) }.orEmpty(),
                quantityToConfirm = toConfirm,
                shoppingMatch = match,
                knownCategory = known?.category,
                knownLocation = known?.location,
                knownBarcode = known?.barcode,
                pieces = entry.pieces,
            )
        }
    }

    fun onIncludeChange(id: Int, include: Boolean) = updateDraft(id) { it.copy(include = include) }

    fun onNameChange(id: Int, name: String) = updateDraft(id) { it.copy(name = name, nameError = false) }

    fun onCategoryChange(id: Int, category: FoodCategory) = updateDraft(id) { it.copy(category = category) }

    // Cambiare quantita' o unita' rende ignoto il numero di confezioni letto dallo
    // scontrino: per i pezzi lo si ricava dalla quantita', per pesi e volumi non piu'.
    fun onQuantityChange(id: Int, text: String) = updateDraft(id) {
        it.copy(quantityText = text, quantityError = false, quantityToConfirm = false, pieces = null)
    }

    fun onUnitChange(id: Int, unit: QuantityUnit) = updateDraft(id) { it.copy(unit = unit, pieces = null) }

    fun onExpiryChange(id: Int, date: LocalDate?) = updateDraft(id) { it.copy(expiryDate = date) }

    fun onPriceChange(id: Int, text: String) = updateDraft(id) { it.copy(priceText = text, priceError = false) }

    fun onStoreChange(store: String) = _uiState.update { it.copy(store = store) }

    fun onDateChange(date: LocalDate) = _uiState.update { it.copy(purchaseDate = date) }

    /** Una riga che il riconoscimento ha perso si aggiunge a mano. */
    fun addDraft() = _uiState.update { state ->
        state.copy(
            drafts = state.drafts + ReceiptDraft(
                id = nextId++,
                include = true,
                name = "",
                category = FoodCategory.ALTRO,
                quantityText = "1",
                unit = QuantityUnit.PZ,
            ),
        )
    }

    fun onCameraUnavailable() = _uiState.update {
        it.copy(message = "Nessuna app fotocamera disponibile: scegli la foto dalla galleria")
    }

    /** Senza un messaggio il rifiuto lascerebbe il pulsante muto, senza spiegare perche'. */
    fun onFilePickerUnavailable() = _uiState.update {
        it.copy(message = "Nessuna app per scegliere i file: fotografa lo scontrino o sceglilo dalla galleria")
    }

    fun onCameraPermissionDenied() = _uiState.update {
        it.copy(
            message = "Senza il permesso della fotocamera scegli la foto dalla galleria, " +
                "oppure concedilo dalle impostazioni di Android",
        )
    }

    fun onMessageShown() = _uiState.update { it.copy(message = null) }

    fun dismissMissingExpiry() = _uiState.update { it.copy(missingExpiryPrompt = null) }

    /** Riparte da capo con un'altra foto. */
    fun restart() {
        sentResult = null
        _uiState.update { ReceiptUiState(purchaseDate = today(), openPrices = it.openPrices) }
    }

    /** Apre la condivisione: tutti i prodotti scelti, il negozio da cercare col nome letto. */
    fun openContribution() = _uiState.update { state ->
        state.copy(
            contribution = ContributionUiState(
                items = state.contributable,
                selected = state.contributable.map { it.barcode }.toSet(),
                query = storeLabel(state.store).orEmpty(),
                result = sentResult,
            ),
        )
    }

    /** [top] e [bottom] in frazioni dell'altezza; un ritaglio che lascerebbe troppo poco si ignora. */
    fun onCropChange(top: Float, bottom: Float) {
        val crop = runCatching { VerticalCrop(top.coerceIn(0f, 1f), bottom.coerceIn(0f, 1f)) }.getOrNull()
            ?: return
        updateContribution { it.copy(crop = crop) }
    }

    fun closeContribution() = _uiState.update { it.copy(contribution = null) }

    fun onContributionQuery(text: String) = updateContribution { it.copy(query = text) }

    fun onContributionCity(text: String) = updateContribution { it.copy(city = text) }

    fun toggleContributionItem(barcode: String) = updateContribution {
        it.copy(selected = if (barcode in it.selected) it.selected - barcode else it.selected + barcode)
    }

    fun selectLocation(location: CommunityLocation) = updateContribution { it.copy(location = location) }

    /** Cerca il negozio fra quelli gia' noti a Open Prices. */
    fun searchStores() {
        val client = openPricesClient ?: return
        val current = _uiState.value.contribution ?: return
        if (current.query.isBlank() || current.isSearching) return
        updateContribution { it.copy(isSearching = true, error = null) }
        viewModelScope.launch {
            try {
                val found = client.searchLocations(current.query, current.city)
                updateContribution {
                    it.copy(results = found, searched = true, isSearching = false, location = found.singleOrNull())
                }
            } catch (e: OpenPricesException) {
                updateContribution { it.copy(isSearching = false, error = e.message) }
            }
        }
    }

    /** Invia la foto come prova e i prezzi scelti. */
    fun sendContribution() {
        val worker = contributor ?: return
        val state = _uiState.value
        val contribution = state.contribution ?: return
        val location = contribution.location ?: return
        val token = state.openPrices.token ?: return
        val photo = state.imageUri ?: return
        if (!contribution.canSend) return
        updateContribution { it.copy(isSending = true, error = null) }
        viewModelScope.launch {
            try {
                // Prova e prezzi vanno insieme: uscire dalla schermata a meta' lascerebbe una
                // prova pubblica senza i suoi prezzi.
                val result = withContext(NonCancellable) {
                    worker.contribute(
                        token = token,
                        photo = Uri.parse(photo),
                        location = location,
                        date = state.purchaseDate,
                        items = contribution.items.filter { it.barcode in contribution.selected },
                        receiptPriceCount = state.pricedLines,
                        crop = contribution.crop,
                    )
                }
                if (result.unauthorized) forgetSession()
                val message = contributionMessage(result.sent, result.failed)
                if (result.sent > 0) sentResult = message
                updateContribution {
                    it.copy(
                        isSending = false,
                        // Senza nulla di inviato si puo' riprovare, per esempio dopo il login.
                        result = message.takeIf { result.sent > 0 },
                        error = if (result.unauthorized) SESSION_EXPIRED else result.lastError,
                    )
                }
            } catch (e: OpenPricesException) {
                if (e.isUnauthorized) forgetSession()
                updateContribution {
                    it.copy(isSending = false, error = if (e.isUnauthorized) SESSION_EXPIRED else e.message)
                }
            }
        }
    }

    /** Un token rifiutato non serve piu': senza, l'app torna a chiedere l'accesso. */
    private suspend fun forgetSession() {
        runCatching { clearOpenPricesSession() }
    }

    private fun updateContribution(change: (ContributionUiState) -> ContributionUiState) =
        _uiState.update { state -> state.copy(contribution = state.contribution?.let(change)) }

    /**
     * Mette in inventario le bozze incluse. Se dei prodotti freschi non hanno scadenza lo
     * chiede prima ([ReceiptUiState.missingExpiryPrompt]); [skipExpiryCheck] e' la risposta
     * "aggiungi comunque".
     */
    fun confirm(skipExpiryCheck: Boolean = false) {
        val state = _uiState.value
        if (state.phase != ReceiptPhase.REVIEW) return
        val checked = state.drafts.map { draft ->
            if (!draft.include) {
                draft
            } else {
                draft.copy(
                    nameError = draft.name.isBlank(),
                    quantityError = parseQuantity(draft.quantityText) == null,
                    priceError = draft.priceText.isNotBlank() && parsePriceCents(draft.priceText) == null,
                )
            }
        }
        if (checked.any { it.nameError || it.quantityError || it.priceError }) {
            _uiState.update {
                it.copy(drafts = checked, message = "Correggi i campi evidenziati")
            }
            return
        }
        val included = checked.filter { it.include }
        if (included.isEmpty()) {
            _uiState.update { it.copy(message = "Nessun prodotto selezionato") }
            return
        }
        val missing = included.count { it.needsExpiry }
        if (missing > 0 && !skipExpiryCheck) {
            _uiState.update { it.copy(drafts = checked, missingExpiryPrompt = missing) }
            return
        }

        _uiState.update { it.copy(phase = ReceiptPhase.SAVING, missingExpiryPrompt = null) }
        viewModelScope.launch {
            try {
                // Frigo, prezzi e lista in una sola transazione: un errore a meta' non lascia
                // mezzo scontrino salvato, e riprovare non duplica cio' che era gia' entrato.
                // NonCancellable la porta a termine anche se si lascia la schermata.
                val saved = withContext(NonCancellable) { transactor.run { save(state, included) } }
                _uiState.update {
                    it.copy(
                        phase = ReceiptPhase.DONE,
                        summary = summaryOf(
                            saved.toFridge,
                            saved.prices,
                            saved.fromList,
                            saved.pricesAlreadyRecorded,
                        ),
                        contributable = saved.contributable,
                        pricedLines = included.count { d -> parsePriceCents(d.priceText) != null },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        phase = ReceiptPhase.REVIEW,
                        message = "Salvataggio non riuscito: nulla è stato registrato, riprova",
                    )
                }
            }
        }
    }

    private class Saved(
        val toFridge: Int,
        val prices: Int,
        val fromList: Int,
        val contributable: List<ContributionItem>,
        val pricesAlreadyRecorded: Boolean,
    )

    private suspend fun save(state: ReceiptUiState, included: List<ReceiptDraft>): Saved {
        val toFridge = included.filter { it.goesToFridge }
        toFridge.forEach { draft ->
            foodRepository.save(
                FoodItem(
                    uuid = "",
                    name = draft.name.trim(),
                    category = draft.category,
                    location = draft.location,
                    quantity = parseQuantity(draft.quantityText) ?: 1.0,
                    unit = draft.unit,
                    expiryDate = draft.expiryDate,
                ),
            )
        }
        val purchases = included.mapNotNull { draft ->
            val total = parsePriceCents(draft.priceText) ?: return@mapNotNull null
            Purchase(
                name = draft.name,
                quantity = parseQuantity(draft.quantityText) ?: 1.0,
                unit = draft.unit,
                totalCents = total,
            )
        }
        val duplicate = priceRepository.isAlreadyRecorded(purchases, state.store, state.purchaseDate)
        val prices = priceRepository.record(purchases, state.store, state.purchaseDate)
        val fromList = included.mapNotNull { it.shoppingMatch }
        fromList.forEach { shoppingRepository.delete(it) }

        val contributable = included.mapNotNull { draft ->
            val barcode = priceRepository.barcodeForName(draft.name) ?: draft.knownBarcode
            contributionItem(draft, barcode)
        }
        return Saved(toFridge.size, prices, fromList.size, contributable, duplicate)
    }

    private fun updateDraft(id: Int, change: (ReceiptDraft) -> ReceiptDraft) = _uiState.update { state ->
        state.copy(drafts = state.drafts.map { if (it.id == id) change(it) else it })
    }

    companion object {
        /**
         * Il prezzo di una riga come lo vuole Open Prices: per pezzo, con il numero di pezzi.
         * Se la riga e' a pezzi o confezioni intere il totale si divide; se e' a peso o a
         * volume si tratta come una confezione sola ("PASTA 500G" e' un pacco). Senza codice
         * a barre valido o senza prezzo non c'e' niente da condividere.
         */
        internal fun contributionItem(draft: ReceiptDraft, barcode: String?): ContributionItem? {
            val code = barcode?.let(::normalizeGtin) ?: return null
            // I codici EAN-13 che iniziano con 2 li assegna il singolo negozio (pesati,
            // banco): non identificano un prodotto per tutti.
            if (code.length == 13 && code.startsWith("2")) return null
            val total = parsePriceCents(draft.priceText)?.takeIf { it > 0 } ?: return null
            val quantity = parseQuantity(draft.quantityText) ?: return null
            // Il prezzo va per confezione. I pezzi si contano se lo scontrino li dice o se
            // la quantita' e' in pezzi interi; per un peso o un volume senza confezioni
            // note il prezzo per confezione non si conosce, e un dato sbagliato in un
            // database condiviso e' peggio di nessun dato.
            val pieces = draft.pieces
                ?: quantity.takeIf {
                    (draft.unit == QuantityUnit.PZ || draft.unit == QuantityUnit.CONF) && it >= 1.0 && it % 1.0 == 0.0
                }?.toInt()
                ?: return null
            if (pieces < 1) return null
            return ContributionItem(draft.name.trim(), code, Math.round(total.toDouble() / pieces), pieces)
        }

        internal const val SESSION_EXPIRED =
            "Accesso a Open Prices scaduto: accedi di nuovo dalle impostazioni"

        internal fun contributionMessage(sent: Int, failed: Int): String = when {
            failed == 0 && sent == 1 -> "1 prezzo condiviso su Open Prices. Grazie!"
            failed == 0 -> "$sent prezzi condivisi su Open Prices. Grazie!"
            sent == 0 -> "Nessun prezzo condiviso"
            else -> "$sent prezzi condivisi, $failed non accettati"
        }

        internal fun summaryOf(
            added: Int,
            prices: Int,
            fromList: Int,
            pricesAlreadyRecorded: Boolean = false,
        ): String = listOfNotNull(
            when (added) {
                0 -> "Nessun prodotto in frigo"
                1 -> "1 prodotto aggiunto in frigo"
                else -> "$added prodotti aggiunti in frigo"
            },
            "prezzi già registrati con questo scontrino".takeIf { pricesAlreadyRecorded },
            when (prices) {
                0 -> null
                1 -> "1 prezzo registrato"
                else -> "$prices prezzi registrati"
            },
            when (fromList) {
                0 -> null
                1 -> "1 tolto dalla lista della spesa"
                else -> "$fromList tolti dalla lista della spesa"
            },
        ).joinToString("; ")

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val container = igorApplication().container
                ReceiptViewModel(
                    reader = container.receiptReader,
                    photoStore = container.photoStore,
                    foodRepository = container.foodRepository,
                    shoppingRepository = container.shoppingRepository,
                    priceRepository = container.priceRepository,
                    openPrices = container.settingsStore.openPrices,
                    openPricesClient = container.openPricesClient,
                    transactor = container.transactor,
                    clearOpenPricesSession = { container.settingsStore.setOpenPricesSession(null, null) },
                )
            }
        }
    }
}
