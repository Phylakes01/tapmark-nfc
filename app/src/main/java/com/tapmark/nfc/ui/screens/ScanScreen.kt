package com.tapmark.nfc.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tapmark.nfc.model.NfcHardwareState
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.ui.components.CardInspectionPane
import com.tapmark.nfc.ui.components.CopperCoilCanvas
import com.tapmark.nfc.ui.theme.CopperSoftTint
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.DividerHairline
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.RecessedSurface
import com.tapmark.nfc.ui.theme.SecondaryGraphite
import com.tapmark.nfc.ui.theme.StatusAmber
import com.tapmark.nfc.ui.theme.StatusAmberTint
import com.tapmark.nfc.ui.theme.StatusCrimson
import com.tapmark.nfc.ui.theme.StatusCrimsonTint
import kotlin.math.PI
import kotlin.math.sin

@Composable
fun ScanScreen(
    nfcState: NfcHardwareState,
    scannedCard: ScannedCard?,
    isReading: Boolean,
    tagLostMessage: String?,
    errorMessage: String?,
    sampleIndex: Int,
    sampleCount: Int,
    onPreviewSample: () -> Unit,
    onNextSample: () -> Unit,
    onNewScan: () -> Unit,
    onUpdateLabel: (String, String?) -> Unit,
    onFeedbackMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    AnimatedContent(
        targetState = scannedCard,
        transitionSpec = {
            if (targetState != null) {
                (fadeIn(tween(260)) +
                    scaleIn(
                        initialScale = 0.94f,
                        animationSpec = spring(
                            dampingRatio = 0.82f,
                            stiffness = Spring.StiffnessMediumLow
                        )
                    ) +
                    slideInVertically(tween(280)) { it / 14 }
                ).togetherWith(
                    fadeOut(tween(180)) +
                        scaleOut(targetScale = 0.96f, animationSpec = tween(180))
                )
            } else {
                (fadeIn(tween(240)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(240))
                ).togetherWith(
                    fadeOut(tween(170)) +
                        slideOutVertically(tween(200)) { it / 18 }
                )
            }
        },
        contentKey = { it?.id ?: "idle_scanner" },
        label = "scan_screen_transition",
        modifier = modifier.fillMaxSize()
    ) { card ->
        if (card == null) {
            IdleScannerStage(
                nfcState = nfcState,
                isReading = isReading,
                tagLostMessage = tagLostMessage,
                errorMessage = errorMessage,
                onPreviewSample = onPreviewSample
            )
        } else {
            ScannedCardDetailStage(
                card = card,
                sampleIndex = sampleIndex,
                sampleCount = sampleCount,
                onNextSample = onNextSample,
                onNewScan = onNewScan,
                onUpdateLabel = { label -> onUpdateLabel(card.id, label) },
                onFeedbackMessage = onFeedbackMessage
            )
        }
    }
}

@Composable
private fun IdleScannerStage(
    nfcState: NfcHardwareState,
    isReading: Boolean,
    tagLostMessage: String?,
    errorMessage: String?,
    onPreviewSample: () -> Unit
) {
    val context = LocalContext.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize()
    ) {
        val minHeight = maxHeight
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 12.dp, bottom = 122.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            Column(
                modifier = Modifier.widthIn(max = 420.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CopperCoilCanvas(
                    modifier = Modifier.fillMaxWidth(),
                    isReading = isReading
                )

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = "Hold card against the center back of your phone",
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = PrimaryInk,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Calm inline status banner for Reading / TagLost / Error
                val statusBanner = resolveStatusBanner(
                    isReading = isReading,
                    tagLostMessage = tagLostMessage,
                    errorMessage = errorMessage
                )
                AnimatedVisibility(
                    visible = statusBanner != null,
                    enter = fadeIn(tween(200)) + expandVertically(tween(220)),
                    exit = fadeOut(tween(160)) + shrinkVertically(tween(180))
                ) {
                    if (statusBanner != null) {
                        InlineStatusBanner(
                            message = statusBanner.message,
                            accentColor = statusBanner.accentColor,
                            backgroundColor = statusBanner.backgroundColor,
                            isReadingWave = statusBanner.isReadingWave,
                            modifier = Modifier.padding(bottom = 12.dp)
                        )
                    }
                }

                // Hardware availability / disabled banner
                when (nfcState) {
                    NfcHardwareState.DISABLED -> {
                        NfcDisabledCard(
                            onOpenSettings = { openNfcSettings(context) }
                        )
                    }

                    NfcHardwareState.UNAVAILABLE -> {
                        NfcUnavailableNotice()
                    }

                    NfcHardwareState.READY -> Unit
                }
            }

            // Unobtrusive bottom text action to preview sample cards
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 20.dp)
            ) {
                TextButton(
                    onClick = onPreviewSample,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Preview sample card",
                        style = MaterialTheme.typography.labelLarge,
                        color = SecondaryGraphite
                    )
                }
            }
        }
    }
}

@Composable
private fun ScannedCardDetailStage(
    card: ScannedCard,
    sampleIndex: Int,
    sampleCount: Int,
    onNextSample: () -> Unit,
    onNewScan: () -> Unit,
    onUpdateLabel: (String?) -> Unit,
    onFeedbackMessage: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 122.dp)
    ) {
        CardInspectionPane(
            card = card,
            onUpdateLabel = onUpdateLabel,
            onFeedbackMessage = onFeedbackMessage,
            onNewScan = onNewScan,
            sampleIndicatorText = if (card.isSample) {
                "Sample ${sampleIndex + 1} of $sampleCount · Not saved to history"
            } else {
                null
            },
            onNextSample = if (card.isSample && sampleCount > 1) onNextSample else null
        )
    }
}

@Composable
private fun InlineStatusBanner(
    message: String,
    accentColor: Color,
    backgroundColor: Color,
    isReadingWave: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, accentColor.copy(alpha = 0.28f), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (isReadingWave) {
            CopperWaveformBars(color = accentColor)
        } else {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(accentColor)
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color = PrimaryInk
        )
    }
}

@Composable
private fun CopperWaveformBars(
    color: Color,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "reading_waveform")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 2f * PI.toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 780, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "reading_waveform_phase"
    )

    Row(
        modifier = modifier.height(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.5.dp)
    ) {
        val offsets = listOf(0f, 1.35f, 2.7f)
        offsets.forEach { offset ->
            val wave = 0.5f + 0.5f * sin(phase - offset)
            val barHeight = (4.dp + 10.dp * wave)
            Box(
                modifier = Modifier
                    .width(2.6.dp)
                    .height(barHeight)
                    .clip(RoundedCornerShape(999.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun NfcDisabledCard(
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = StatusAmberTint,
        border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.30f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "NFC is turned off on this device. Enable NFC to read contactless cards and tags.",
                style = MaterialTheme.typography.bodySmall,
                color = PrimaryInk
            )
            Button(
                onClick = onOpenSettings,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CopperTerracotta,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Open NFC Settings",
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun NfcUnavailableNotice() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RecessedSurface)
            .border(1.dp, DividerHairline, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "NFC hardware unavailable on this device",
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryGraphite,
            textAlign = TextAlign.Center
        )
    }
}

private data class StatusBannerSpec(
    val message: String,
    val accentColor: Color,
    val backgroundColor: Color,
    val isReadingWave: Boolean = false
)

private fun resolveStatusBanner(
    isReading: Boolean,
    tagLostMessage: String?,
    errorMessage: String?
): StatusBannerSpec? = when {
    isReading -> StatusBannerSpec(
        message = "Reading chip...",
        accentColor = CopperTerracotta,
        backgroundColor = CopperSoftTint,
        isReadingWave = true
    )

    !tagLostMessage.isNullOrBlank() -> StatusBannerSpec(
        message = tagLostMessage,
        accentColor = StatusAmber,
        backgroundColor = StatusAmberTint
    )

    !errorMessage.isNullOrBlank() -> StatusBannerSpec(
        message = errorMessage,
        accentColor = StatusCrimson,
        backgroundColor = StatusCrimsonTint
    )

    else -> null
}

private fun openNfcSettings(context: Context) {
    val primary = Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(primary)
    } catch (_: Throwable) {
        try {
            context.startActivity(fallback)
        } catch (_: Throwable) {
            // No settings screen available on device
        }
    }
}
