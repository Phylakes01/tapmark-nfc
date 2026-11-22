package com.tapmark.nfc.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapmark.nfc.model.NfcHardwareState
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.ui.components.OpeningIntroOverlay
import com.tapmark.nfc.ui.screens.HistoryScreen
import com.tapmark.nfc.ui.screens.ScanScreen
import com.tapmark.nfc.ui.theme.CardSurface
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.DividerHairline
import com.tapmark.nfc.ui.theme.MutedInk
import com.tapmark.nfc.ui.theme.PaperCanvas
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.RecessedSurface
import com.tapmark.nfc.ui.theme.SecondaryGraphite
import com.tapmark.nfc.ui.theme.StatusAmber
import com.tapmark.nfc.ui.theme.StatusAmberTint
import com.tapmark.nfc.ui.theme.StatusSage
import com.tapmark.nfc.ui.theme.StatusSageTint
import com.tapmark.nfc.ui.theme.TapmarkMonoCaption
import com.tapmark.nfc.ui.theme.TapmarkWordmarkStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

enum class TapmarkTab {
    SCAN,
    SAVED
}

@Composable
fun TapmarkApp(
    nfcState: NfcHardwareState,
    selectedTab: TapmarkTab,
    onSelectTab: (TapmarkTab) -> Unit,
    scannedCard: ScannedCard?,
    isReading: Boolean,
    tagLostMessage: String?,
    errorMessage: String?,
    sampleIndex: Int,
    sampleCount: Int,
    history: List<ScannedCard>,
    onPreviewSample: () -> Unit,
    onNextSample: () -> Unit,
    onNewScan: () -> Unit,
    onUpdateLabel: (String, String?) -> Unit,
    onDeleteCard: (String) -> Unit,
    onClearHistory: () -> Unit,
    showOpeningIntro: Boolean = false,
    onIntroFinished: () -> Unit = {},
    onSelectSample: (Int) -> Unit = {},
    startCardFlipped: Boolean = false
) {
    var feedbackBanner by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(feedbackBanner) {
        if (feedbackBanner != null) {
            delay(2000L)
            feedbackBanner = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PaperCanvas)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            TapmarkTopBar(
                nfcState = nfcState,
                isReading = isReading
            )

            AnimatedContent(
                targetState = selectedTab,
                transitionSpec = {
                    val forward = targetState.ordinal > initialState.ordinal
                    if (forward) {
                        (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { it / 8 })
                            .togetherWith(
                                fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { -it / 10 }
                            )
                    } else {
                        (fadeIn(tween(240)) + slideInHorizontally(tween(260)) { -it / 8 })
                            .togetherWith(
                                fadeOut(tween(180)) + slideOutHorizontally(tween(220)) { it / 10 }
                            )
                    }
                },
                label = "tapmark_tab_transition",
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { tab ->
                when (tab) {
                    TapmarkTab.SCAN -> {
                        ScanScreen(
                            nfcState = nfcState,
                            scannedCard = scannedCard,
                            isReading = isReading,
                            tagLostMessage = tagLostMessage,
                            errorMessage = errorMessage,
                            sampleIndex = sampleIndex,
                            sampleCount = sampleCount,
                            onPreviewSample = onPreviewSample,
                            onNextSample = onNextSample,
                            onNewScan = onNewScan,
                            onUpdateLabel = onUpdateLabel,
                            onFeedbackMessage = { feedbackBanner = it },
                            onSelectSample = onSelectSample,
                            startCardFlipped = startCardFlipped
                        )
                    }

                    TapmarkTab.SAVED -> {
                        HistoryScreen(
                            cards = history,
                            onUpdateLabel = onUpdateLabel,
                            onDeleteCard = onDeleteCard,
                            onClearAll = onClearHistory,
                            onGoToScanner = { onSelectTab(TapmarkTab.SCAN) },
                            onPreviewSample = {
                                onPreviewSample()
                                onSelectTab(TapmarkTab.SCAN)
                            },
                            onFeedbackMessage = { feedbackBanner = it }
                        )
                    }
                }
            }
        }

        // Bottom overlay: transient feedback pill + floating 2-tab pill switcher + creator credit
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            PaperCanvas.copy(alpha = 0f),
                            PaperCanvas.copy(alpha = 0.90f),
                            PaperCanvas
                        )
                    )
                )
                .navigationBarsPadding()
                .padding(top = 14.dp, bottom = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(
                visible = feedbackBanner != null,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                feedbackBanner?.let { message ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = PrimaryInk,
                            shadowElevation = 3.dp
                        ) {
                            Text(
                                text = message,
                                style = MaterialTheme.typography.labelLarge,
                                color = PaperCanvas,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                }
            }

            FloatingTabSwitcher(
                selectedTab = selectedTab,
                savedCount = history.size,
                onSelectTab = onSelectTab
            )

            Text(
                text = "Made by Jeremiah Temporado",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.4.sp
                ),
                color = MutedInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp)
            )
        }

        if (showOpeningIntro) {
            OpeningIntroOverlay(onFinished = onIntroFinished)
        }
    }
}

@Composable
private fun TapmarkTopBar(
    nfcState: NfcHardwareState,
    isReading: Boolean
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            TapmarkMiniCoilMark(isReading = isReading)
            Text(
                text = "Tapmark",
                style = TapmarkWordmarkStyle,
                color = PrimaryInk
            )
        }

        NfcStatusPill(
            nfcState = nfcState,
            onClick = if (nfcState == NfcHardwareState.DISABLED) {
                {
                    try {
                        context.startActivity(
                            Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    } catch (_: Throwable) {
                    }
                }
            } else {
                null
            }
        )
    }
}

@Composable
private fun TapmarkMiniCoilMark(isReading: Boolean) {
    val activeWeight by animateFloatAsState(
        targetValue = if (isReading) 1f else 0f,
        animationSpec = tween(durationMillis = 280),
        label = "mini_coil_reading_weight"
    )
    val transition = rememberInfiniteTransition(label = "mini_coil_pulse")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "mini_coil_pulse_val"
    )

    val livePulse = activeWeight * pulse
    val scale = 1f + 0.06f * livePulse

    Canvas(
        modifier = Modifier
            .size(20.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
    ) {
        val stroke = (1.6f + 0.25f * livePulse).dp.toPx()
        drawRoundRect(
            color = CopperTerracotta.copy(alpha = 0.85f + 0.15f * livePulse),
            topLeft = Offset(1.dp.toPx(), 2.dp.toPx()),
            size = Size(size.width - 2.dp.toPx(), size.height - 4.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
            style = Stroke(width = stroke)
        )
        drawRoundRect(
            color = CopperTerracotta.copy(alpha = 0.72f + 0.28f * livePulse),
            topLeft = Offset(4.5.dp.toPx(), 5.5.dp.toPx()),
            size = Size(size.width - 9.dp.toPx(), size.height - 11.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx(), 2.dp.toPx()),
            style = Stroke(width = (1.25f + 0.2f * livePulse).dp.toPx())
        )
        if (activeWeight > 0.01f) {
            drawCircle(
                color = CopperTerracotta.copy(alpha = 0.24f * livePulse),
                radius = (3.4f + 0.8f * livePulse).dp.toPx(),
                center = center
            )
        }
        drawCircle(
            color = CopperTerracotta,
            radius = (1.6f + 0.45f * livePulse).dp.toPx(),
            center = center
        )
    }
}

@Composable
private fun NfcStatusPill(
    nfcState: NfcHardwareState,
    onClick: (() -> Unit)?
) {
    val (label, dotColor, bgColor, textColor, borderColor) = when (nfcState) {
        NfcHardwareState.READY -> StatusPillColors(
            label = "NFC Ready",
            dotColor = StatusSage,
            bgColor = StatusSageTint,
            textColor = StatusSage,
            borderColor = StatusSage.copy(alpha = 0.25f)
        )

        NfcHardwareState.DISABLED -> StatusPillColors(
            label = "NFC Off",
            dotColor = StatusAmber,
            bgColor = StatusAmberTint,
            textColor = StatusAmber,
            borderColor = StatusAmber.copy(alpha = 0.28f)
        )

        NfcHardwareState.UNAVAILABLE -> StatusPillColors(
            label = "No NFC",
            dotColor = SecondaryGraphite,
            bgColor = RecessedSurface,
            textColor = SecondaryGraphite,
            borderColor = DividerHairline
        )
    }

    val haloTransition = rememberInfiniteTransition(label = "nfc_ready_halo")
    val haloProgress by haloTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "nfc_ready_halo_progress"
    )

    val clickableModifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .then(clickableModifier)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Canvas(modifier = Modifier.size(12.dp)) {
            val dotRadius = 3.5.dp.toPx()
            if (nfcState == NfcHardwareState.READY) {
                val haloRadius = dotRadius + haloProgress * 4.2.dp.toPx()
                val haloAlpha = (1f - haloProgress) * 0.44f
                drawCircle(
                    color = dotColor.copy(alpha = haloAlpha),
                    radius = haloRadius,
                    center = center
                )
            }
            drawCircle(
                color = dotColor,
                radius = dotRadius,
                center = center
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = textColor
        )
    }
}

@Composable
private fun FloatingTabSwitcher(
    selectedTab: TapmarkTab,
    savedCount: Int,
    onSelectTab: (TapmarkTab) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerHairline),
        shadowElevation = 4.dp
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            var rowHeightPx by remember { mutableIntStateOf(0) }
            var scanLeftPx by remember { mutableFloatStateOf(0f) }
            var scanWidthPx by remember { mutableFloatStateOf(0f) }
            var savedLeftPx by remember { mutableFloatStateOf(0f) }
            var savedWidthPx by remember { mutableFloatStateOf(0f) }

            val targetLeft = if (selectedTab == TapmarkTab.SCAN) scanLeftPx else savedLeftPx
            val targetWidth = if (selectedTab == TapmarkTab.SCAN) scanWidthPx else savedWidthPx

            val indicatorLeft = remember { Animatable(0f) }
            val indicatorWidth = remember { Animatable(0f) }
            var hasSnapped by remember { mutableStateOf(false) }

            LaunchedEffect(targetLeft, targetWidth) {
                if (targetWidth <= 0f) return@LaunchedEffect
                if (!hasSnapped) {
                    indicatorLeft.snapTo(targetLeft)
                    indicatorWidth.snapTo(targetWidth)
                    hasSnapped = true
                } else {
                    launch {
                        indicatorLeft.animateTo(
                            targetValue = targetLeft,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = 420f
                            )
                        )
                    }
                    launch {
                        indicatorWidth.animateTo(
                            targetValue = targetWidth,
                            animationSpec = spring(
                                dampingRatio = 0.78f,
                                stiffness = 420f
                            )
                        )
                    }
                }
            }

            val density = LocalDensity.current
            if (hasSnapped && indicatorWidth.value > 0f && rowHeightPx > 0) {
                Box(
                    modifier = Modifier
                        .offset { IntOffset(indicatorLeft.value.roundToInt(), 0) }
                        .width(with(density) { indicatorWidth.value.toDp() })
                        .height(with(density) { rowHeightPx.toDp() })
                        .clip(RoundedCornerShape(999.dp))
                        .background(CopperTerracotta)
                )
            }

            Row(
                modifier = Modifier.onGloballyPositioned { coords ->
                    rowHeightPx = coords.size.height
                },
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TabPillItem(
                    label = "Scan",
                    badgeCount = null,
                    selected = selectedTab == TapmarkTab.SCAN,
                    useFallbackBg = !hasSnapped,
                    onClick = { onSelectTab(TapmarkTab.SCAN) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        scanLeftPx = coords.positionInParent().x
                        scanWidthPx = coords.size.width.toFloat()
                    }
                )
                TabPillItem(
                    label = "Saved",
                    badgeCount = if (savedCount > 0) savedCount else null,
                    selected = selectedTab == TapmarkTab.SAVED,
                    useFallbackBg = !hasSnapped,
                    onClick = { onSelectTab(TapmarkTab.SAVED) },
                    modifier = Modifier.onGloballyPositioned { coords ->
                        savedLeftPx = coords.positionInParent().x
                        savedWidthPx = coords.size.width.toFloat()
                    }
                )
            }
        }
    }
}

@Composable
private fun TabPillItem(
    label: String,
    badgeCount: Int?,
    selected: Boolean,
    useFallbackBg: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor = if (useFallbackBg && selected) CopperTerracotta else Color.Transparent
    val textColor by animateColorAsState(
        targetValue = if (selected) Color.White else SecondaryGraphite,
        animationSpec = tween(durationMillis = 180),
        label = "tab_text_color"
    )
    val badgeBgColor by animateColorAsState(
        targetValue = if (selected) Color.White.copy(alpha = 0.22f) else RecessedSurface,
        animationSpec = tween(durationMillis = 180),
        label = "tab_badge_bg"
    )
    val badgeTextColor by animateColorAsState(
        targetValue = if (selected) Color.White else PrimaryInk,
        animationSpec = tween(durationMillis = 180),
        label = "tab_badge_text"
    )

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .clickable(role = Role.Tab, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor
        )
        if (badgeCount != null) {
            Spacer(modifier = Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(badgeBgColor)
                    .padding(horizontal = 6.dp, vertical = 1.dp)
            ) {
                Text(
                    text = badgeCount.toString(),
                    style = TapmarkMonoCaption,
                    color = badgeTextColor
                )
            }
        }
    }
}

private data class StatusPillColors(
    val label: String,
    val dotColor: Color,
    val bgColor: Color,
    val textColor: Color,
    val borderColor: Color
)
