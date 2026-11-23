package com.tapmark.nfc

import android.content.Intent
import android.graphics.Color
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.tapmark.nfc.data.CardHistoryRepository
import com.tapmark.nfc.data.SampleCards
import com.tapmark.nfc.model.CheckoutSessionResult
import com.tapmark.nfc.model.GatewayConfig
import com.tapmark.nfc.model.GatewayEnvironment
import com.tapmark.nfc.model.GatewayProvider
import com.tapmark.nfc.model.NfcHardwareState
import com.tapmark.nfc.model.ReceiptStatus
import com.tapmark.nfc.model.ScanResult
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.model.TerminalReceipt
import com.tapmark.nfc.nfc.TagScanner
import com.tapmark.nfc.payment.GatewayConfigRepository
import com.tapmark.nfc.payment.PaymentGatewayClient
import com.tapmark.nfc.payment.TerminalReceiptRepository
import com.tapmark.nfc.ui.TapmarkApp
import com.tapmark.nfc.ui.TapmarkTab
import com.tapmark.nfc.ui.screens.TerminalStage
import com.tapmark.nfc.ui.theme.TapmarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEMO_INDEX = "com.tapmark.nfc.EXTRA_DEMO_INDEX"
        const val EXTRA_START_FLIPPED = "com.tapmark.nfc.EXTRA_START_FLIPPED"
        const val EXTRA_START_TAB = "com.tapmark.nfc.EXTRA_START_TAB"
        const val EXTRA_TERMINAL_DEMO = "com.tapmark.nfc.EXTRA_TERMINAL_DEMO"
        private const val READER_PRESENCE_DELAY_MS = 250
    }

    private lateinit var historyRepository: CardHistoryRepository
    private lateinit var gatewayConfigRepository: GatewayConfigRepository
    private lateinit var terminalReceiptRepository: TerminalReceiptRepository
    private var nfcAdapter: NfcAdapter? = null
    private var initDataJob: Job? = null
    private var activeScanJob: Job? = null
    private var activeQrJob: Job? = null
    private val scanInProgress = AtomicBoolean(false)

    // Scanner & history state
    private var nfcState by mutableStateOf(NfcHardwareState.UNAVAILABLE)
    private var selectedTab by mutableStateOf(TapmarkTab.SCAN)
    private var scannedCard by mutableStateOf<ScannedCard?>(null)
    private var isReading by mutableStateOf(false)
    private var tagLostMessage by mutableStateOf<String?>(null)
    private var errorMessage by mutableStateOf<String?>(null)
    private var sampleIndex by mutableIntStateOf(0)
    private var startFlipped by mutableStateOf(false)
    private var history by mutableStateOf<List<ScannedCard>>(emptyList())
    private var skipIntroSignal by mutableIntStateOf(0)

    // Terminal & payment gateway state
    private var gatewayConfig by mutableStateOf(GatewayConfig())
    private var terminalReceipts by mutableStateOf<List<TerminalReceipt>>(emptyList())
    private var todayTotalCentavos by mutableLongStateOf(0L)
    private var terminalStage by mutableStateOf(TerminalStage.KEYPAD)
    private var terminalAmountCentavos by mutableLongStateOf(0L)
    private var terminalDescription by mutableStateOf("Tapmark Sale")
    private var terminalTappedCard by mutableStateOf<ScannedCard?>(null)
    private var terminalActiveReceipt by mutableStateOf<TerminalReceipt?>(null)
    private var terminalQrSession by mutableStateOf<CheckoutSessionResult?>(null)
    private var terminalIsCreatingQr by mutableStateOf(false)
    private var terminalIsCheckingQrStatus by mutableStateOf(false)
    private var terminalQrError by mutableStateOf<String?>(null)
    private var terminalIsReadingNfc by mutableStateOf(false)
    private var terminalTagLostMessage by mutableStateOf<String?>(null)
    private var terminalErrorMessage by mutableStateOf<String?>(null)
    private var externalFeedbackMessage by mutableStateOf<String?>(null)

    private val isTerminalAwaitingTap: Boolean
        get() = selectedTab == TapmarkTab.TERMINAL && terminalStage == TerminalStage.WAITING_FOR_NFC

    private val readerCallback = NfcAdapter.ReaderCallback { tag ->
        onNfcTagDiscovered(tag)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
        )
        super.onCreate(savedInstanceState)

        historyRepository = CardHistoryRepository(applicationContext)
        gatewayConfigRepository = GatewayConfigRepository(applicationContext)
        terminalReceiptRepository = TerminalReceiptRepository(applicationContext)
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        refreshNfcHardwareState()

        initDataJob = lifecycleScope.launch {
            val (loadedHistory, loadedGateway, loadedReceipts) = withContext(Dispatchers.IO) {
                Triple(
                    historyRepository.loadAll(),
                    gatewayConfigRepository.load(),
                    terminalReceiptRepository.loadAll()
                )
            }
            history = loadedHistory
            gatewayConfig = loadedGateway
            terminalReceipts = loadedReceipts
            todayTotalCentavos = terminalReceiptRepository.todayTotalCentavos(loadedReceipts)
        }

        if (savedInstanceState == null) {
            handleLaunchIntent(intent)
        }

        setContent {
            TapmarkTheme {
                val samples = SampleCards.all
                val launchedDirectlyWithTagOrDemo = remember { isDirectCardOrDemoIntent(intent) }
                var showOpeningIntro by rememberSaveable {
                    mutableStateOf(!launchedDirectlyWithTagOrDemo)
                }

                LaunchedEffect(skipIntroSignal) {
                    if (skipIntroSignal > 0) {
                        showOpeningIntro = false
                    }
                }

                TapmarkApp(
                    nfcState = nfcState,
                    selectedTab = selectedTab,
                    onSelectTab = { selectedTab = it },
                    scannedCard = scannedCard,
                    isReading = isReading,
                    tagLostMessage = tagLostMessage,
                    errorMessage = errorMessage,
                    sampleIndex = sampleIndex,
                    sampleCount = samples.size,
                    history = history,
                    onPreviewSample = {
                        if (samples.isNotEmpty()) {
                            val idx = sampleIndex.coerceIn(samples.indices)
                            sampleIndex = idx
                            scannedCard = samples[idx]
                            startFlipped = false
                            tagLostMessage = null
                            errorMessage = null
                        }
                    },
                    onNextSample = {
                        if (samples.isNotEmpty()) {
                            sampleIndex = (sampleIndex + 1) % samples.size
                            scannedCard = samples[sampleIndex]
                            startFlipped = false
                            tagLostMessage = null
                            errorMessage = null
                        }
                    },
                    onSelectSample = { idx ->
                        if (samples.isNotEmpty()) {
                            val bounded = idx.coerceIn(samples.indices)
                            sampleIndex = bounded
                            scannedCard = samples[bounded]
                            startFlipped = false
                            tagLostMessage = null
                            errorMessage = null
                        }
                    },
                    onNewScan = {
                        scannedCard = null
                        startFlipped = false
                        tagLostMessage = null
                        errorMessage = null
                    },
                    onUpdateLabel = { cardId, newLabel ->
                        updateCardLabel(cardId, newLabel)
                    },
                    onDeleteCard = { cardId ->
                        deleteHistoryCard(cardId)
                    },
                    onClearHistory = {
                        clearAllHistory()
                    },
                    gatewayConfig = gatewayConfig,
                    terminalReceipts = terminalReceipts,
                    todayTotalCentavos = todayTotalCentavos,
                    terminalStage = terminalStage,
                    terminalAmountCentavos = terminalAmountCentavos,
                    terminalDescription = terminalDescription,
                    terminalTappedCard = terminalTappedCard,
                    terminalActiveReceipt = terminalActiveReceipt,
                    terminalQrSession = terminalQrSession,
                    terminalIsCreatingQr = terminalIsCreatingQr,
                    terminalIsCheckingQrStatus = terminalIsCheckingQrStatus,
                    terminalQrError = terminalQrError,
                    terminalIsReadingNfc = terminalIsReadingNfc,
                    terminalTagLostMessage = terminalTagLostMessage,
                    terminalErrorMessage = terminalErrorMessage,
                    onTerminalAmountChange = { terminalAmountCentavos = it },
                    onTerminalDescriptionChange = { terminalDescription = it },
                    onTerminalStageChange = { nextStage ->
                        terminalStage = nextStage
                        if (nextStage != TerminalStage.WAITING_FOR_NFC) {
                            terminalIsReadingNfc = false
                            terminalTagLostMessage = null
                            terminalErrorMessage = null
                        }
                    },
                    onTerminalStartNfcTap = {
                        if (terminalAmountCentavos > 0L) {
                            terminalTappedCard = null
                            terminalActiveReceipt = null
                            terminalIsReadingNfc = false
                            terminalTagLostMessage = null
                            terminalErrorMessage = null
                            terminalStage = TerminalStage.WAITING_FOR_NFC
                        }
                    },
                    onTerminalSimulateBpiTap = {
                        simulateTerminalBpiTap()
                    },
                    onTerminalCancelNfcTap = {
                        if (terminalIsReadingNfc) {
                            activeScanJob?.cancel()
                        }
                        terminalIsReadingNfc = false
                        terminalTagLostMessage = null
                        terminalErrorMessage = null
                        terminalStage = TerminalStage.KEYPAD
                    },
                    onTerminalStartQrCheckout = {
                        startTerminalQrCheckout()
                    },
                    onTerminalCheckQrStatus = {
                        checkTerminalQrStatus()
                    },
                    onTerminalMarkSandboxPaid = {
                        markSandboxQrPaid()
                    },
                    onTerminalNewSale = {
                        resetTerminalForNewSale()
                    },
                    onSaveGatewayConfig = { updated ->
                        saveGatewayConfig(updated)
                    },
                    onDeleteTerminalReceipt = { id ->
                        deleteTerminalReceipt(id)
                    },
                    onClearTerminalReceipts = {
                        clearAllTerminalReceipts()
                    },
                    externalFeedbackMessage = externalFeedbackMessage,
                    onConsumeExternalFeedback = { externalFeedbackMessage = null },
                    showOpeningIntro = showOpeningIntro,
                    onIntroFinished = { showOpeningIntro = false },
                    startCardFlipped = startFlipped
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        refreshNfcHardwareState()
        enableReaderModeIfReady()
    }

    override fun onPause() {
        disableReaderModeSafely()
        super.onPause()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleLaunchIntent(intent)
    }

    private fun refreshNfcHardwareState() {
        val adapter = nfcAdapter
        nfcState = when {
            adapter == null -> NfcHardwareState.UNAVAILABLE
            adapter.isEnabled -> NfcHardwareState.READY
            else -> NfcHardwareState.DISABLED
        }
    }

    private fun enableReaderModeIfReady() {
        val adapter = nfcAdapter ?: return
        if (!adapter.isEnabled) return

        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_NFC_F or
            NfcAdapter.FLAG_READER_NFC_V or
            NfcAdapter.FLAG_READER_NFC_BARCODE
        val extras = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, READER_PRESENCE_DELAY_MS)
        }
        try {
            adapter.enableReaderMode(this, readerCallback, flags, extras)
        } catch (_: Throwable) {
            // Ignore if activity is no longer in resumed foreground state
        }
    }

    private fun disableReaderModeSafely() {
        try {
            nfcAdapter?.disableReaderMode(this)
        } catch (_: Throwable) {
            // Ignore hardware/lifecycle edge cases during pause
        }
    }

    private fun isDirectCardOrDemoIntent(launchIntent: Intent?): Boolean {
        if (launchIntent == null) return false
        val demoIndex = launchIntent.getIntExtra(EXTRA_DEMO_INDEX, -1)
        if (demoIndex in SampleCards.all.indices) return true
        if (launchIntent.getBooleanExtra(EXTRA_START_FLIPPED, false)) return true
        if (!launchIntent.getStringExtra(EXTRA_START_TAB).isNullOrBlank()) return true
        if (!launchIntent.getStringExtra(EXTRA_TERMINAL_DEMO).isNullOrBlank()) return true
        val data = launchIntent.data
        if (data?.scheme == "tapmark" && data.host == "payment") return true
        return IntentCompat.getParcelableExtra(launchIntent, NfcAdapter.EXTRA_TAG, Tag::class.java) != null
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return

        // 1. Handle payment redirect deep links: tapmark://payment/{success|cancel|failed}?ref=...
        val uri = intent.data
        if (uri != null && uri.scheme == "tapmark" && uri.host == "payment") {
            handlePaymentDeepLink(
                action = uri.path?.trim('/')?.lowercase(Locale.US).orEmpty(),
                referenceNumber = uri.getQueryParameter("ref")?.trim().orEmpty()
            )
            return
        }

        // 2. Handle Terminal QA demo shortcuts
        val terminalDemo = intent.getStringExtra(EXTRA_TERMINAL_DEMO)?.trim()?.lowercase(Locale.US)
        if (!terminalDemo.isNullOrBlank()) {
            applyTerminalDemoShortcut(terminalDemo)
            return
        }

        // 3. Handle explicit tab start extra
        val startTabExtra = intent.getStringExtra(EXTRA_START_TAB)?.trim()?.lowercase(Locale.US)
        if (!startTabExtra.isNullOrBlank()) {
            skipIntroSignal += 1
            selectedTab = when (startTabExtra) {
                "terminal" -> TapmarkTab.TERMINAL
                "saved" -> TapmarkTab.SAVED
                else -> TapmarkTab.SCAN
            }
            return
        }

        // 4. Handle card inspection demo extras
        val requestedFlipped = intent.getBooleanExtra(EXTRA_START_FLIPPED, false)
        val demoIndex = intent.getIntExtra(EXTRA_DEMO_INDEX, -1)
        val samples = SampleCards.all
        if (demoIndex in samples.indices) {
            skipIntroSignal += 1
            sampleIndex = demoIndex
            scannedCard = samples[demoIndex]
            startFlipped = requestedFlipped
            tagLostMessage = null
            errorMessage = null
            selectedTab = TapmarkTab.SCAN
            return
        } else if (requestedFlipped && samples.isNotEmpty()) {
            skipIntroSignal += 1
            val bounded = sampleIndex.coerceIn(samples.indices)
            sampleIndex = bounded
            scannedCard = samples[bounded]
            startFlipped = true
            tagLostMessage = null
            errorMessage = null
            selectedTab = TapmarkTab.SCAN
            return
        }

        val tag = IntentCompat.getParcelableExtra(intent, NfcAdapter.EXTRA_TAG, Tag::class.java)
        if (tag != null) {
            onNfcTagDiscovered(tag)
        }
    }

    private fun handlePaymentDeepLink(action: String, referenceNumber: String) {
        skipIntroSignal += 1
        selectedTab = TapmarkTab.TERMINAL

        val targetStatus = when (action) {
            "success" -> ReceiptStatus.PAID
            "cancel" -> ReceiptStatus.CANCELLED
            "failed" -> ReceiptStatus.FAILED
            else -> return
        }

        if (targetStatus == ReceiptStatus.PAID) {
            performSuccessHaptic()
        }

        lifecycleScope.launch {
            initDataJob?.join()
            if (referenceNumber.isNotBlank()) {
                val updated = withContext(Dispatchers.IO) {
                    terminalReceiptRepository.updateStatusByReference(referenceNumber, targetStatus)
                }
                terminalReceipts = updated
                todayTotalCentavos = terminalReceiptRepository.todayTotalCentavos(updated)
                if (terminalActiveReceipt?.referenceNumber.equals(referenceNumber, ignoreCase = true)) {
                    terminalActiveReceipt = terminalActiveReceipt?.copy(status = targetStatus)
                }
            }

            externalFeedbackMessage = when (targetStatus) {
                ReceiptStatus.PAID -> if (referenceNumber.isNotBlank()) {
                    "Payment confirmed ($referenceNumber)"
                } else {
                    "Payment confirmed"
                }
                ReceiptStatus.CANCELLED -> if (referenceNumber.isNotBlank()) {
                    "Payment cancelled ($referenceNumber)"
                } else {
                    "Payment cancelled"
                }
                ReceiptStatus.FAILED -> if (referenceNumber.isNotBlank()) {
                    "Payment failed ($referenceNumber)"
                } else {
                    "Payment failed"
                }
                else -> null
            }
        }
    }

    private fun applyTerminalDemoShortcut(mode: String) {
        skipIntroSignal += 1
        selectedTab = TapmarkTab.TERMINAL
        terminalAmountCentavos = 25_000L
        terminalDescription = "Tapmark Sale"
        terminalIsReadingNfc = false
        terminalTagLostMessage = null
        terminalErrorMessage = null
        terminalQrError = null
        activeQrJob?.cancel()

        when (mode) {
            "keypad" -> {
                terminalTappedCard = null
                terminalActiveReceipt = null
                terminalQrSession = null
                terminalIsCreatingQr = false
                terminalStage = TerminalStage.KEYPAD
            }

            "nfc_approved" -> {
                val bpiCard = SampleCards.all.firstOrNull { it.id == "sample-visa-debit" }
                    ?: SampleCards.all.first()
                val receipt = PaymentGatewayClient.buildNfcDemoReceipt(
                    card = bpiCard,
                    amountCentavos = 25_000L,
                    description = "Tapmark Sale",
                    config = GatewayConfig()
                )
                terminalTappedCard = bpiCard
                terminalActiveReceipt = receipt
                terminalQrSession = null
                terminalIsCreatingQr = false
                terminalStage = TerminalStage.NFC_RECEIPT
            }

            "qr_checkout" -> {
                terminalTappedCard = null
                terminalQrSession = null
                terminalActiveReceipt = null
                terminalIsCreatingQr = true
                terminalStage = TerminalStage.QR_CHECKOUT
                val sandboxConfig = GatewayConfig(
                    provider = GatewayProvider.DEMO_SANDBOX,
                    environment = GatewayEnvironment.SANDBOX
                )
                activeQrJob = lifecycleScope.launch {
                    val result = PaymentGatewayClient.createCheckoutSession(
                        config = sandboxConfig,
                        amountCentavos = 25_000L,
                        description = "Tapmark Sale"
                    )
                    terminalIsCreatingQr = false
                    result.onSuccess { session ->
                        terminalQrSession = session
                        terminalActiveReceipt = PaymentGatewayClient.buildQrReceipt(
                            session = session,
                            amountCentavos = 25_000L,
                            description = "Tapmark Sale",
                            config = sandboxConfig,
                            status = ReceiptStatus.PENDING
                        )
                    }.onFailure { err ->
                        terminalQrError = err.message ?: "Failed to create demo QR session"
                    }
                }
            }
        }
    }

    private fun onNfcTagDiscovered(tag: Tag) {
        if (!scanInProgress.compareAndSet(false, true)) return

        activeScanJob?.cancel()
        activeScanJob = lifecycleScope.launch(Dispatchers.Main.immediate) {
            val routeToTerminal = isTerminalAwaitingTap
            try {
                skipIntroSignal += 1
                if (routeToTerminal) {
                    terminalIsReadingNfc = true
                    terminalTagLostMessage = null
                    terminalErrorMessage = null

                    when (val result = TagScanner.readTag(tag)) {
                        is ScanResult.Success -> {
                            performSuccessHaptic()
                            val capturedCard = result.card
                            val cleanDesc = terminalDescription.trim().ifBlank { "Tapmark Sale" }
                            val receipt = PaymentGatewayClient.buildNfcDemoReceipt(
                                card = capturedCard,
                                amountCentavos = terminalAmountCentavos,
                                description = cleanDesc,
                                config = gatewayConfig
                            )
                            val updatedReceipts = withContext(Dispatchers.IO) {
                                terminalReceiptRepository.saveReceipt(receipt)
                            }
                            terminalReceipts = updatedReceipts
                            todayTotalCentavos = terminalReceiptRepository.todayTotalCentavos(updatedReceipts)
                            terminalTappedCard = capturedCard
                            terminalActiveReceipt = receipt
                            terminalIsReadingNfc = false
                            terminalStage = TerminalStage.NFC_RECEIPT
                        }

                        is ScanResult.TagLost -> {
                            terminalIsReadingNfc = false
                            terminalTagLostMessage = "Card lifted too soon — hold steady for a moment"
                        }

                        is ScanResult.Error -> {
                            terminalIsReadingNfc = false
                            terminalErrorMessage = result.message
                        }
                    }
                } else {
                    selectedTab = TapmarkTab.SCAN
                    scannedCard = null
                    startFlipped = false
                    isReading = true
                    tagLostMessage = null
                    errorMessage = null

                    when (val result = TagScanner.readTag(tag)) {
                        is ScanResult.Success -> {
                            performSuccessHaptic()
                            val capturedCard = result.card
                            scannedCard = capturedCard
                            isReading = false
                            tagLostMessage = null
                            errorMessage = null

                            if (!capturedCard.isSample) {
                                val updatedHistory = withContext(Dispatchers.IO) {
                                    historyRepository.saveScan(capturedCard)
                                }
                                history = updatedHistory
                            }
                        }

                        is ScanResult.TagLost -> {
                            isReading = false
                            tagLostMessage = "Card lifted too soon — hold steady for a moment"
                        }

                        is ScanResult.Error -> {
                            isReading = false
                            errorMessage = result.message
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                val msg = error.message?.takeIf { it.isNotBlank() } ?: "Failed to read NFC card"
                if (routeToTerminal) {
                    terminalIsReadingNfc = false
                    terminalErrorMessage = msg
                } else {
                    isReading = false
                    errorMessage = msg
                }
            } finally {
                if (routeToTerminal) {
                    terminalIsReadingNfc = false
                } else {
                    isReading = false
                }
                scanInProgress.set(false)
            }
        }
    }

    private fun simulateTerminalBpiTap() {
        val bpiCard = SampleCards.all.firstOrNull { it.id == "sample-visa-debit" }
            ?: SampleCards.all.firstOrNull()
            ?: return
        val amount = terminalAmountCentavos
        if (amount <= 0L) return

        val cleanDesc = terminalDescription.trim().ifBlank { "Tapmark Sale" }
        performSuccessHaptic()

        lifecycleScope.launch {
            val receipt = PaymentGatewayClient.buildNfcDemoReceipt(
                card = bpiCard,
                amountCentavos = amount,
                description = cleanDesc,
                config = gatewayConfig
            )
            val updated = withContext(Dispatchers.IO) {
                terminalReceiptRepository.saveReceipt(receipt)
            }
            terminalReceipts = updated
            todayTotalCentavos = terminalReceiptRepository.todayTotalCentavos(updated)
            terminalTappedCard = bpiCard
            terminalActiveReceipt = receipt
            terminalIsReadingNfc = false
            terminalTagLostMessage = null
            terminalErrorMessage = null
            terminalStage = TerminalStage.NFC_RECEIPT
        }
    }

    private fun startTerminalQrCheckout() {
        val amount = terminalAmountCentavos
        if (amount <= 0L) return

        val cleanDesc = terminalDescription.trim().ifBlank { "Tapmark Sale" }
        val activeConfig = gatewayConfig
        activeQrJob?.cancel()
        terminalStage = TerminalStage.QR_CHECKOUT
        terminalIsCreatingQr = true
        terminalIsCheckingQrStatus = false
        terminalQrError = null
        terminalQrSession = null
        terminalActiveReceipt = null

        activeQrJob = lifecycleScope.launch {
            val result = PaymentGatewayClient.createCheckoutSession(
                config = activeConfig,
                amountCentavos = amount,
                description = cleanDesc
            )
            terminalIsCreatingQr = false
            result.onSuccess { session ->
                val receipt = PaymentGatewayClient.buildQrReceipt(
                    session = session,
                    amountCentavos = amount,
                    description = cleanDesc,
                    config = activeConfig,
                    status = ReceiptStatus.PENDING
                )
                val updated = withContext(Dispatchers.IO) {
                    terminalReceiptRepository.saveReceipt(receipt)
                }
                terminalReceipts = updated
                todayTotalCentavos = terminalReceiptRepository.todayTotalCentavos(updated)
                terminalQrSession = session
                terminalActiveReceipt = receipt
            }.onFailure { err ->
                terminalQrError = err.message?.takeIf { it.isNotBlank() }
                    ?: "Could not create QR checkout session"
            }
        }
    }

    private fun checkTerminalQrStatus() {
        val session = terminalQrSession ?: return
        val activeConfig = gatewayConfig
        if (terminalIsCheckingQrStatus) return

        terminalIsCheckingQrStatus = true
        lifecycleScope.launch {
            try {
                val result = PaymentGatewayClient.checkPayMongoSessionStatus(
                    config = activeConfig,
                    sessionId = session.sessionId
                )
                result.onSuccess { newStatus ->
                    val updated = withContext(Dispatchers.IO) {
                        val current = terminalActiveReceipt
                        if (current != null) {
                            terminalReceiptRepository.updateStatus(current.id, newStatus)
                        } else {
                            terminalReceiptRepository.updateStatusByReference(session.referenceNumber, newStatus)
                        }
                    }
                    terminalReceipts = updated
                    todayTotalCentavos = terminalReceiptRepository.todayTotalCentavos(updated)
                    terminalActiveReceipt = terminalActiveReceipt?.copy(status = newStatus)
                    if (newStatus == ReceiptStatus.PAID) {
                        performSuccessHaptic()
                    }
                    externalFeedbackMessage = when (newStatus) {
                        ReceiptStatus.PAID -> "Payment confirmed (PAID)"
                        ReceiptStatus.FAILED -> "Payment failed"
                        ReceiptStatus.CANCELLED -> "Payment cancelled"
                        ReceiptStatus.PENDING -> "Payment still pending"
                        ReceiptStatus.APPROVED -> "Payment approved"
                    }
                }.onFailure { err ->
                    externalFeedbackMessage = err.message?.takeIf { it.isNotBlank() }
                        ?: "Could not check PayMongo status"
                }
            } finally {
                terminalIsCheckingQrStatus = false
            }
        }
    }

    private fun markSandboxQrPaid() {
        val receipt = terminalActiveReceipt ?: return
        performSuccessHaptic()
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                terminalReceiptRepository.updateStatus(receipt.id, ReceiptStatus.PAID)
            }
            terminalReceipts = updated
            todayTotalCentavos = terminalReceiptRepository.todayTotalCentavos(updated)
            terminalActiveReceipt = receipt.copy(status = ReceiptStatus.PAID)
            externalFeedbackMessage = "Test payment marked PAID"
        }
    }

    private fun resetTerminalForNewSale() {
        activeQrJob?.cancel()
        terminalAmountCentavos = 0L
        terminalDescription = "Tapmark Sale"
        terminalTappedCard = null
        terminalActiveReceipt = null
        terminalQrSession = null
        terminalIsCreatingQr = false
        terminalIsCheckingQrStatus = false
        terminalQrError = null
        terminalIsReadingNfc = false
        terminalTagLostMessage = null
        terminalErrorMessage = null
        terminalStage = TerminalStage.KEYPAD
    }

    private fun saveGatewayConfig(updated: GatewayConfig) {
        gatewayConfig = updated
        lifecycleScope.launch {
            withContext(Dispatchers.IO) {
                gatewayConfigRepository.save(updated)
            }
            externalFeedbackMessage = "Gateway settings saved"
        }
    }

    private fun deleteTerminalReceipt(receiptId: String) {
        if (terminalActiveReceipt?.id == receiptId) {
            terminalActiveReceipt = null
        }
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                terminalReceiptRepository.delete(receiptId)
            }
            terminalReceipts = updated
            todayTotalCentavos = terminalReceiptRepository.todayTotalCentavos(updated)
        }
    }

    private fun clearAllTerminalReceipts() {
        terminalActiveReceipt = null
        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                terminalReceiptRepository.clearAll()
            }
            terminalReceipts = updated
            todayTotalCentavos = 0L
        }
    }

    private fun updateCardLabel(cardId: String, newLabel: String?) {
        val current = scannedCard
        if (current != null && current.id == cardId) {
            scannedCard = current.copy(customLabel = newLabel)
        }

        if (current != null && current.id == cardId && current.isSample) {
            return
        }

        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                historyRepository.updateLabel(cardId, newLabel)
            }
            history = updated
        }
    }

    private fun deleteHistoryCard(cardId: String) {
        if (scannedCard?.id == cardId && scannedCard?.isSample == false) {
            scannedCard = null
        }

        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                historyRepository.delete(cardId)
            }
            history = updated
        }
    }

    private fun clearAllHistory() {
        if (scannedCard?.isSample == false) {
            scannedCard = null
        }

        lifecycleScope.launch {
            val updated = withContext(Dispatchers.IO) {
                historyRepository.clearAll()
            }
            history = updated
        }
    }

    private fun performSuccessHaptic() {
        try {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(VibratorManager::class.java)?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Vibrator::class.java)
            }
            if (vibrator == null || !vibrator.hasVibrator()) return

            val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
            } else {
                VibrationEffect.createOneShot(25L, VibrationEffect.DEFAULT_AMPLITUDE)
            }
            vibrator.vibrate(effect)
        } catch (_: Throwable) {
            // Haptic feedback is best-effort
        }
    }
}
