package com.tapmark.nfc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapmark.nfc.model.EmvCardInfo
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.SecondaryGraphite
import com.tapmark.nfc.ui.theme.StatusAmber
import com.tapmark.nfc.ui.theme.StatusAmberTint
import com.tapmark.nfc.ui.theme.TapmarkMonoBody
import com.tapmark.nfc.ui.theme.TapmarkMonoCaption
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val ISO_CARD_RATIO = 1.586f

@Composable
fun PhysicalCardView(
    card: ScannedCard,
    modifier: Modifier = Modifier,
    revealPan: Boolean? = null,
    onToggleRevealPan: (() -> Unit)? = null,
    initialFlipped: Boolean = false,
    isFlipped: Boolean? = null,
    onToggleFlip: (() -> Unit)? = null
) {
    val theme = resolveBankCardTheme(card)
    val palette = theme.palette

    var internalReveal by remember(card.id) { mutableStateOf(false) }
    val isPanRevealed = revealPan ?: internalReveal
    val togglePan = onToggleRevealPan ?: { internalReveal = !internalReveal }

    var internalFlipped by remember(card.id, initialFlipped) { mutableStateOf(initialFlipped) }
    val targetFlipped = isFlipped ?: internalFlipped
    val toggleFlip = onToggleFlip ?: { internalFlipped = !internalFlipped }

    val flipAngle by animateFloatAsState(
        targetValue = if (targetFlipped) 180f else 0f,
        animationSpec = spring(
            dampingRatio = 0.76f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "physical_card_flip_angle"
    )

    // Tactile spring materialization entrance + diagonal metallic sheen sweep per card.id
    val enterProgress = remember(card.id) { Animatable(0f) }
    val enterAlpha = remember(card.id) { Animatable(0f) }
    val sheenProgress = remember(card.id) { Animatable(-0.4f) }

    LaunchedEffect(card.id) {
        launch {
            enterProgress.snapTo(0f)
            enterProgress.animateTo(
                targetValue = 1f,
                animationSpec = spring(
                    dampingRatio = 0.74f,
                    stiffness = 380f
                )
            )
        }
        launch {
            enterAlpha.snapTo(0f)
            enterAlpha.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
            )
        }
        launch {
            sheenProgress.snapTo(-0.4f)
            delay(75L)
            sheenProgress.animateTo(
                targetValue = 1.4f,
                animationSpec = tween(durationMillis = 820, easing = FastOutSlowInEasing)
            )
        }
    }

    // Interactive 3D touch-tilt state
    var targetTiltX by remember(card.id) { mutableFloatStateOf(0f) }
    var targetTiltY by remember(card.id) { mutableFloatStateOf(0f) }
    var isTouchingCard by remember(card.id) { mutableStateOf(false) }

    val tiltX by animateFloatAsState(
        targetValue = targetTiltX,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "physical_card_tilt_x"
    )
    val tiltY by animateFloatAsState(
        targetValue = targetTiltY,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "physical_card_tilt_y"
    )
    val touchSpecular by animateFloatAsState(
        targetValue = if (isTouchingCard) 1f else 0f,
        animationSpec = tween(durationMillis = 220),
        label = "physical_card_specular"
    )

    val density = LocalDensity.current.density
    val enterScale = 0.90f + 0.10f * enterProgress.value
    val enterRotationY = -14f * (1f - enterProgress.value)
    val shape = RoundedCornerShape(18.dp)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(ISO_CARD_RATIO)
            .graphicsLayer {
                scaleX = enterScale
                scaleY = enterScale
                rotationX = tiltX
                rotationY = flipAngle + enterRotationY + tiltY
                alpha = enterAlpha.value.coerceIn(0f, 1f)
                cameraDistance = 14f * density
            }
            .pointerInput(card.id) {
                val touchSlop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (down.isConsumed) return@awaitEachGesture

                    fun updateTiltFrom(pos: Offset) {
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        val height = size.height.toFloat().coerceAtLeast(1f)
                        val nx = ((pos.x / width) - 0.5f) * 2f
                        val ny = ((pos.y / height) - 0.5f) * 2f
                        targetTiltY = (nx * 10f).coerceIn(-10f, 10f)
                        targetTiltX = (-ny * 10f).coerceIn(-10f, 10f)
                    }

                    isTouchingCard = true
                    updateTiltFrom(down.position)

                    var totalDx = 0f
                    var totalDy = 0f
                    var capturedTiltDrag = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed || change.isConsumed) break

                        val delta = change.position - change.previousPosition
                        totalDx += delta.x
                        totalDy += delta.y

                        if (!capturedTiltDrag) {
                            val absDx = abs(totalDx)
                            val absDy = abs(totalDy)
                            if (absDy > touchSlop * 1.4f && absDy > absDx * 1.35f) {
                                // Yield vertical swipes back to the scrolling ledger container
                                break
                            }
                            if (absDx > touchSlop) {
                                capturedTiltDrag = true
                            }
                        }

                        if (capturedTiltDrag) {
                            change.consume()
                        }
                        updateTiltFrom(change.position)
                    }

                    isTouchingCard = false
                    targetTiltX = 0f
                    targetTiltY = 0f
                }
            },
        shape = shape,
        color = palette.baseColor,
        border = BorderStroke(1.dp, palette.borderColor),
        shadowElevation = 4.dp
    ) {
        if (flipAngle <= 90f) {
            CardFrontFace(
                card = card,
                theme = theme,
                palette = palette,
                isPanRevealed = isPanRevealed,
                onToggleRevealPan = togglePan,
                onToggleFlip = toggleFlip,
                tiltX = tiltX,
                tiltY = tiltY,
                touchSpecular = touchSpecular,
                sheenProgress = sheenProgress.value
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { rotationY = 180f }
            ) {
                CardBackFace(
                    card = card,
                    theme = theme,
                    palette = palette,
                    isPanRevealed = isPanRevealed,
                    onToggleRevealPan = togglePan,
                    onToggleFlip = toggleFlip,
                    tiltX = tiltX,
                    tiltY = tiltY,
                    touchSpecular = touchSpecular
                )
            }
        }
    }
}

@Composable
private fun CardFrontFace(
    card: ScannedCard,
    theme: BankCardTheme,
    palette: CardStylePalette,
    isPanRevealed: Boolean,
    onToggleRevealPan: () -> Unit,
    onToggleFlip: () -> Unit,
    tiltX: Float,
    tiltY: Float,
    touchSpecular: Float,
    sheenProgress: Float
) {
    val emv = card.emvInfo

    Box(modifier = Modifier.fillMaxSize()) {
        // Tactile bank gradient + bespoke geometric artwork + machined grain + light sheen
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(palette.baseColor, palette.secondaryColor),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )

            drawBankArtwork(theme)

            val normX = (tiltY / 10f).coerceIn(-1f, 1f)
            val normY = (-tiltX / 10f).coerceIn(-1f, 1f)

            val step = 7.dp.toPx()
            val grainStroke = 0.7.dp.toPx()
            val grainBoost = 1f + 0.45f * touchSpecular
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = palette.grainColor.copy(
                        alpha = (palette.grainColor.alpha * grainBoost).coerceAtMost(0.22f)
                    ),
                    start = Offset(x, size.height),
                    end = Offset(x + size.height * 0.55f, 0f),
                    strokeWidth = grainStroke
                )
                x += step
            }

            // Dynamic touch-tilt specular light reflection
            if (touchSpecular > 0.01f) {
                val lightCenter = Offset(
                    x = size.width * (0.5f + normX * 0.36f),
                    y = size.height * (0.5f + normY * 0.36f)
                )
                val lightTint = if (palette.isDark) Color(0xFFFFF8EE) else Color.White
                val peakAlpha = (if (palette.isDark) 0.14f else 0.24f) * touchSpecular
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            lightTint.copy(alpha = peakAlpha),
                            lightTint.copy(alpha = peakAlpha * 0.32f),
                            Color.Transparent
                        ),
                        center = lightCenter,
                        radius = size.width * 0.68f
                    )
                )
            }

            // Diagonal metallic sheen sweep on materialization entrance
            if (sheenProgress > -0.39f && sheenProgress < 1.39f) {
                val bandWidth = size.width * 0.34f
                val centerX = sheenProgress * size.width
                val sheenTint = if (palette.isDark) Color(0xFFFFF6EA) else Color.White
                val peakAlpha = if (palette.isDark) 0.19f else 0.34f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            sheenTint.copy(alpha = peakAlpha * 0.42f),
                            sheenTint.copy(alpha = peakAlpha),
                            sheenTint.copy(alpha = peakAlpha * 0.42f),
                            Color.Transparent
                        ),
                        start = Offset(centerX - bandWidth, 0f),
                        end = Offset(centerX + bandWidth * 0.35f, size.height)
                    )
                )
            }

            // Hairline inner perimeter rule for pressed-card crispness
            val inset = 6.dp.toPx()
            drawRoundRect(
                color = palette.inkColor.copy(alpha = if (palette.isDark) 0.09f else 0.06f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx()),
                style = Stroke(width = 0.9.dp.toPx())
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Bank Emblem + Bank Name Header + Micro-Badges + Flip Pill + Contactless Mark
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(9.dp)
                ) {
                    if (emv != null && theme.emblemCode.isNotBlank()) {
                        BankBrandEmblem(
                            theme = theme,
                            modifier = Modifier.padding(top = 1.dp)
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        if (emv != null) {
                            val headerName = emv.bankName?.takeIf { it.isNotBlank() } ?: theme.displayBrand
                            Text(
                                text = headerName.uppercase(),
                                style = MaterialTheme.typography.labelLarge.copy(
                                    fontSize = 11.sp,
                                    lineHeight = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.95.sp
                                ),
                                color = palette.inkColor,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val badgeLabel = if (emv != null) {
                                listOfNotNull(
                                    emv.cardType?.takeIf { it.isNotBlank() },
                                    emv.cardTier?.takeIf { it.isNotBlank() }
                                )
                                    .distinctBy { it.lowercase() }
                                    .joinToString(" · ")
                                    .uppercase()
                                    .ifEmpty { emv.network.uppercase() }
                            } else {
                                card.category.displayName().uppercase()
                            }

                            Box(modifier = Modifier.weight(1f, fill = false)) {
                                CardMicroBadge(
                                    text = badgeLabel,
                                    bgColor = palette.badgeBgColor,
                                    textColor = palette.badgeInkColor,
                                    borderColor = palette.inkColor.copy(alpha = 0.14f)
                                )
                            }

                            if (card.isSample) {
                                CardMicroBadge(
                                    text = if (emv != null) "SAMPLE" else "SAMPLE PREVIEW",
                                    bgColor = Color.Transparent,
                                    textColor = palette.secondaryInkColor,
                                    borderColor = palette.secondaryInkColor.copy(alpha = 0.35f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (emv != null) {
                        CardFlipPill(
                            label = "Flip",
                            palette = palette,
                            accentColor = theme.accentColor,
                            onClick = onToggleFlip
                        )
                    }
                    ContactlessArcMark(
                        color = palette.inkColor.copy(alpha = 0.80f),
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(20.dp)
                    )
                }
            }

            // Middle Row: Metallic contact chip + optional custom nickname pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MetallicChipGraphic(
                    chipColor = palette.chipGoldTint,
                    isDarkCard = palette.isDark,
                    sheenProgress = sheenProgress,
                    tiltNormX = (tiltY / 10f).coerceIn(-1f, 1f),
                    touchSpecular = touchSpecular,
                    modifier = Modifier.size(width = 40.dp, height = 28.dp)
                )

                if (!card.customLabel.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .padding(start = 12.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(palette.badgeBgColor)
                            .border(
                                width = 0.9.dp,
                                color = palette.inkColor.copy(alpha = 0.16f),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = card.customLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = palette.inkColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Bottom Section: EMV PAN/Expiry/Network OR UID/Card Title
            if (emv != null) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    val displayedPan = if (isPanRevealed && !emv.panFull.isNullOrBlank()) {
                        formatPanGroups(emv.panFull)
                    } else {
                        emv.panMasked
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        AnimatedContent(
                            targetState = displayedPan,
                            transitionSpec = {
                                (fadeIn(tween(200)) + slideInVertically(tween(220)) { it / 3 })
                                    .togetherWith(
                                        fadeOut(tween(150)) + slideOutVertically(tween(180)) { -it / 3 }
                                    )
                            },
                            label = "pan_reveal_transition",
                            modifier = Modifier.weight(1f)
                        ) { panText ->
                            Text(
                                text = panText,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Medium,
                                fontSize = if (panText.length > 20) 14.5.sp else 16.5.sp,
                                letterSpacing = 0.9.sp,
                                color = palette.inkColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        if (!emv.panFull.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(palette.badgeBgColor)
                                    .clickable(onClick = onToggleRevealPan)
                                    .padding(horizontal = 7.dp, vertical = 4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isPanRevealed) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (isPanRevealed) {
                                        "Hide full card number"
                                    } else {
                                        "Reveal full card number"
                                    },
                                    tint = palette.inkColor,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(5.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val holderOrLabel = emv.cardholderName
                                ?.takeIf { it.isNotBlank() }
                                ?: emv.appLabel
                                ?: card.cardSubtitle
                            Text(
                                text = holderOrLabel.uppercase(),
                                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 0.75.sp),
                                color = palette.secondaryInkColor,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (!emv.expiryFormatted.isNullOrBlank()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "VALID THRU ",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            letterSpacing = 0.6.sp
                                        ),
                                        color = palette.secondaryInkColor
                                    )
                                    Text(
                                        text = emv.expiryFormatted,
                                        style = TapmarkMonoCaption.copy(fontSize = 11.5.sp),
                                        color = palette.inkColor
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(10.dp))

                        NetworkBrandMark(
                            network = emv.network,
                            inkColor = palette.inkColor,
                            isDark = palette.isDark
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "UID",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.sp),
                        color = palette.secondaryInkColor
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = card.uidHex,
                        style = TapmarkMonoBody.copy(
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 0.7.sp
                        ),
                        color = palette.inkColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = card.cardTitle,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.labelLarge,
                            color = palette.inkColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = card.manufacturer,
                            style = MaterialTheme.typography.labelSmall,
                            color = palette.secondaryInkColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CardBackFace(
    card: ScannedCard,
    theme: BankCardTheme,
    palette: CardStylePalette,
    isPanRevealed: Boolean,
    onToggleRevealPan: () -> Unit,
    onToggleFlip: () -> Unit,
    tiltX: Float,
    tiltY: Float,
    touchSpecular: Float
) {
    val emv = card.emvInfo

    Box(modifier = Modifier.fillMaxSize()) {
        // Matching bank color base + subtle grain + specular highlight
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(palette.secondaryColor, palette.baseColor),
                    start = Offset.Zero,
                    end = Offset(size.width, size.height)
                )
            )

            val step = 7.dp.toPx()
            var x = -size.height
            while (x < size.width + size.height) {
                drawLine(
                    color = palette.grainColor.copy(alpha = palette.grainColor.alpha * 0.85f),
                    start = Offset(x, size.height),
                    end = Offset(x + size.height * 0.55f, 0f),
                    strokeWidth = 0.7.dp.toPx()
                )
                x += step
            }

            if (touchSpecular > 0.01f) {
                val normX = (-tiltY / 10f).coerceIn(-1f, 1f)
                val normY = (-tiltX / 10f).coerceIn(-1f, 1f)
                val lightCenter = Offset(
                    x = size.width * (0.5f + normX * 0.36f),
                    y = size.height * (0.5f + normY * 0.36f)
                )
                val lightTint = if (palette.isDark) Color(0xFFFFF8EE) else Color.White
                val peakAlpha = (if (palette.isDark) 0.12f else 0.22f) * touchSpecular
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            lightTint.copy(alpha = peakAlpha),
                            Color.Transparent
                        ),
                        center = lightCenter,
                        radius = size.width * 0.65f
                    )
                )
            }

            val inset = 6.dp.toPx()
            drawRoundRect(
                color = palette.inkColor.copy(alpha = if (palette.isDark) 0.09f else 0.06f),
                topLeft = Offset(inset, inset),
                size = Size(size.width - inset * 2f, size.height - inset * 2f),
                cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx()),
                style = Stroke(width = 0.9.dp.toPx())
            )
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Top Back Header with Brand + Flip pill button
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 14.dp, top = 8.dp, bottom = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(theme.accentColor)
                        )
                        Text(
                            text = "${theme.displayBrand.uppercase()} · EMV SECURITY",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.9.sp
                            ),
                            color = palette.secondaryInkColor,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    CardFlipPill(
                        label = "Flip",
                        palette = palette,
                        accentColor = theme.accentColor,
                        onClick = onToggleFlip
                    )
                }

                // Brushed dark magnetic stripe across full width
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(26.dp)
                ) {
                    drawRect(color = Color(0xFF161513))
                    val streakColor = Color(0xFF282521)
                    for (i in 1..4) {
                        val yPos = size.height * (i / 5f)
                        drawLine(
                            color = streakColor,
                            start = Offset(0f, yPos),
                            end = Offset(size.width, yPos),
                            strokeWidth = 0.8.dp.toPx()
                        )
                    }
                    drawLine(
                        color = Color(0xFF0A0908),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, 0f),
                        strokeWidth = 1.dp.toPx()
                    )
                    drawLine(
                        color = Color(0xFF2F2C27),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 0.8.dp.toPx()
                    )
                }

                Spacer(modifier = Modifier.height(7.dp))

                // Warm archival signature strip with inset CVV2 / CVC2 box & honest EMVCo badge
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Min)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFFF3EFE6))
                            .border(0.8.dp, Color(0xFFD5CCB9), RoundedCornerShape(6.dp)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Signature panel with archival hatch pattern
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(horizontal = 8.dp, vertical = 5.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val lineCol = Color(0xFFE0D8C8)
                                var sx = -size.height
                                while (sx < size.width + size.height) {
                                    drawLine(
                                        color = lineCol,
                                        start = Offset(sx, size.height),
                                        end = Offset(sx + size.height * 0.8f, 0f),
                                        strokeWidth = 0.7.dp.toPx()
                                    )
                                    sx += 5.dp.toPx()
                                }
                            }
                            Text(
                                text = "AUTHORIZED SIGNATURE",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = 8.sp,
                                    letterSpacing = 0.7.sp
                                ),
                                color = Color(0xFF8C857B),
                                maxLines = 1
                            )
                        }

                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .fillMaxHeight()
                                .background(Color(0xFFD1C8B7))
                        )

                        // Inset CVV2 / CVC2 box
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFE9E2D4))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "CVV2 / CVC2",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 7.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.4.sp
                                    ),
                                    color = SecondaryGraphite
                                )
                                Text(
                                    text = "•••",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    letterSpacing = 1.5.sp,
                                    color = PrimaryInk
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(StatusAmberTint)
                                    .border(0.8.dp, StatusAmber.copy(alpha = 0.45f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Not on EMV chip",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontSize = 8.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = StatusAmber,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(3.dp))

                    Text(
                        text = "EMVCo never stores static CVV2 on NFC chips",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 9.sp,
                            letterSpacing = 0.2.sp
                        ),
                        color = palette.secondaryInkColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Bottom Section: Chip Security Data (CVC/iCVV, Service Code, AID, Cryptogram)
            if (emv != null) {
                BackChipSecurityPanel(
                    emv = emv,
                    palette = palette,
                    accentColor = theme.accentColor,
                    isPanRevealed = isPanRevealed,
                    onToggleRevealPan = onToggleRevealPan,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 9.dp)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(
                            if (palette.isDark) Color.Black.copy(alpha = 0.22f)
                            else Color.White.copy(alpha = 0.42f)
                        )
                        .border(0.8.dp, palette.inkColor.copy(alpha = 0.16f), RoundedCornerShape(9.dp))
                        .padding(horizontal = 10.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "${card.chipGuess} · UID ${card.uidHex}",
                        style = TapmarkMonoCaption.copy(fontSize = 10.sp),
                        color = palette.inkColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun BackChipSecurityPanel(
    emv: EmvCardInfo,
    palette: CardStylePalette,
    accentColor: Color,
    isPanRevealed: Boolean,
    onToggleRevealPan: () -> Unit,
    modifier: Modifier = Modifier
) {
    val panelBg = if (palette.isDark) {
        Color.Black.copy(alpha = 0.24f)
    } else {
        Color.White.copy(alpha = 0.48f)
    }
    val rawCvc = emv.chipCvcData?.takeIf { it.isNotBlank() }
    val maskedCvc = when {
        rawCvc == null -> "Not exposed"
        isPanRevealed -> rawCvc
        rawCvc.length <= 4 -> "••••"
        else -> "•••• ••••"
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(panelBg)
            .border(0.8.dp, palette.inkColor.copy(alpha = 0.16f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = emv.chipCvcLabel?.takeIf { it.isNotBlank() } ?: "Chip Security Data",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.25.sp
                ),
                color = palette.secondaryInkColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = maskedCvc,
                    style = TapmarkMonoCaption.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (rawCvc != null) palette.inkColor else palette.secondaryInkColor,
                    maxLines = 1
                )

                if (rawCvc != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(palette.badgeBgColor)
                            .clickable(onClick = onToggleRevealPan)
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isPanRevealed) {
                                Icons.Outlined.VisibilityOff
                            } else {
                                Icons.Outlined.Visibility
                            },
                            contentDescription = if (isPanRevealed) {
                                "Hide chip security value"
                            } else {
                                "Reveal chip security value"
                            },
                            tint = palette.inkColor,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "SVC ${emv.serviceCode ?: "—"} · AID ${emv.aid}",
                style = TapmarkMonoCaption.copy(fontSize = 9.5.sp),
                color = palette.inkColor.copy(alpha = 0.90f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }

        emv.cryptogramHex?.takeIf { it.isNotBlank() }?.let { crypto ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "CRYPTOGRAM (9F26)",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 8.5.sp,
                        letterSpacing = 0.4.sp
                    ),
                    color = accentColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = crypto,
                    style = TapmarkMonoCaption.copy(fontSize = 9.5.sp),
                    color = palette.inkColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CardFlipPill(
    label: String,
    palette: CardStylePalette,
    accentColor: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(palette.badgeBgColor)
            .border(0.8.dp, palette.inkColor.copy(alpha = 0.22f), RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 3.5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        CardFlipVectorIcon(
            tint = accentColor,
            modifier = Modifier.size(11.dp)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp
            ),
            color = palette.inkColor
        )
    }
}

@Composable
fun CardFlipVectorIcon(
    tint: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = 1.35.dp.toPx()
        val w = size.width
        val h = size.height
        val topArc = Path().apply {
            moveTo(w * 0.14f, h * 0.42f)
            cubicTo(w * 0.25f, h * 0.14f, w * 0.75f, h * 0.14f, w * 0.86f, h * 0.42f)
        }
        drawPath(
            path = topArc,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Arrow head on right of top arc
        val arrowTop = Path().apply {
            moveTo(w * 0.72f, h * 0.36f)
            lineTo(w * 0.88f, h * 0.44f)
            lineTo(w * 0.88f, h * 0.24f)
        }
        drawPath(
            path = arrowTop,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )

        val bottomArc = Path().apply {
            moveTo(w * 0.86f, h * 0.58f)
            cubicTo(w * 0.75f, h * 0.86f, w * 0.25f, h * 0.86f, w * 0.14f, h * 0.58f)
        }
        drawPath(
            path = bottomArc,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round)
        )
        // Arrow head on left of bottom arc
        val arrowBottom = Path().apply {
            moveTo(w * 0.28f, h * 0.64f)
            lineTo(w * 0.12f, h * 0.56f)
            lineTo(w * 0.12f, h * 0.76f)
        }
        drawPath(
            path = arrowBottom,
            color = tint,
            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
        )
    }
}

@Composable
private fun CardMicroBadge(
    text: String,
    bgColor: Color,
    textColor: Color,
    borderColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(width = 0.8.dp, color = borderColor, shape = RoundedCornerShape(6.dp))
            .padding(horizontal = 7.dp, vertical = 2.5.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.6.sp
            ),
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ContactlessArcMark(
    color: Color,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val stroke = 1.6.dp.toPx()
        val center = Offset(size.width * 0.22f, size.height * 0.5f)
        for (i in 1..4) {
            val radius = size.height * (0.14f * i)
            drawArc(
                color = color,
                startAngle = -44f,
                sweepAngle = 88f,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2f, radius * 2f),
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }
    }
}

@Composable
private fun MetallicChipGraphic(
    chipColor: Color,
    isDarkCard: Boolean,
    sheenProgress: Float,
    tiltNormX: Float,
    touchSpecular: Float,
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val corner = 5.dp.toPx()
        val shift = tiltNormX * size.width * 0.25f * touchSpecular
        drawRoundRect(
            brush = Brush.linearGradient(
                colors = listOf(
                    chipColor,
                    chipColor.copy(alpha = 0.80f),
                    Color(0xFFF6E7C3).copy(alpha = 0.55f + 0.25f * touchSpecular),
                    chipColor
                ),
                start = Offset(shift, 0f),
                end = Offset(size.width + shift, size.height)
            ),
            cornerRadius = CornerRadius(corner, corner)
        )

        val localSheen = ((sheenProgress - 0.02f) / 0.40f)
        if (localSheen in 0f..1f) {
            val glintCenter = localSheen * size.width
            drawRoundRect(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color.Transparent,
                        Color.White.copy(alpha = 0.48f),
                        Color.Transparent
                    ),
                    start = Offset(glintCenter - size.width * 0.45f, 0f),
                    end = Offset(glintCenter + size.width * 0.45f, size.height)
                ),
                cornerRadius = CornerRadius(corner, corner)
            )
        }

        val borderColor = if (isDarkCard) Color(0x66000000) else Color(0x55171614)
        drawRoundRect(
            color = borderColor,
            cornerRadius = CornerRadius(corner, corner),
            style = Stroke(width = 0.9.dp.toPx())
        )

        val lineStroke = 0.85.dp.toPx()
        val lineColor = Color(0x66221D15)
        val x1 = size.width * 0.34f
        val x2 = size.width * 0.66f
        val y1 = size.height * 0.33f
        val y2 = size.height * 0.67f
        val midY = size.height * 0.5f

        drawLine(lineColor, Offset(x1, 0f), Offset(x1, size.height), lineStroke)
        drawLine(lineColor, Offset(x2, 0f), Offset(x2, size.height), lineStroke)
        drawLine(lineColor, Offset(0f, y1), Offset(x1, y1), lineStroke)
        drawLine(lineColor, Offset(0f, y2), Offset(x1, y2), lineStroke)
        drawLine(lineColor, Offset(x2, y1), Offset(size.width, y1), lineStroke)
        drawLine(lineColor, Offset(x2, y2), Offset(size.width, y2), lineStroke)
        drawLine(lineColor, Offset(x1, midY), Offset(x2, midY), lineStroke)
    }
}
