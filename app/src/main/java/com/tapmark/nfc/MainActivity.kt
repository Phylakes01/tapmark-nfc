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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.IntentCompat
import androidx.lifecycle.lifecycleScope
import com.tapmark.nfc.data.CardHistoryRepository
import com.tapmark.nfc.data.SampleCards
import com.tapmark.nfc.model.NfcHardwareState
import com.tapmark.nfc.model.ScanResult
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.nfc.TagScanner
import com.tapmark.nfc.ui.TapmarkApp
import com.tapmark.nfc.ui.TapmarkTab
import com.tapmark.nfc.ui.theme.TapmarkTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_DEMO_INDEX = "com.tapmark.nfc.EXTRA_DEMO_INDEX"
        const val EXTRA_START_FLIPPED = "com.tapmark.nfc.EXTRA_START_FLIPPED"
        private const val READER_PRESENCE_DELAY_MS = 250
    }

    private lateinit var historyRepository: CardHistoryRepository
    private var nfcAdapter: NfcAdapter? = null
    private var activeScanJob: Job? = null
    private val scanInProgress = AtomicBoolean(false)

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
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        refreshNfcHardwareState()

        lifecycleScope.launch {
            val loaded = withContext(Dispatchers.IO) {
                historyRepository.loadAll()
            }
            history = loaded
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
        return IntentCompat.getParcelableExtra(launchIntent, NfcAdapter.EXTRA_TAG, Tag::class.java) != null
    }

    private fun handleLaunchIntent(intent: Intent?) {
        if (intent == null) return

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

    private fun onNfcTagDiscovered(tag: Tag) {
        if (!scanInProgress.compareAndSet(false, true)) return

        activeScanJob?.cancel()
        activeScanJob = lifecycleScope.launch(Dispatchers.Main.immediate) {
            try {
                skipIntroSignal += 1
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
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                isReading = false
                errorMessage = error.message?.takeIf { it.isNotBlank() }
                    ?: "Failed to read NFC card"
            } finally {
                isReading = false
                scanInProgress.set(false)
            }
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
