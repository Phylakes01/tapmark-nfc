package com.tapmark.nfc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.tapmark.nfc.ui.theme.BlueprintSurface
import com.tapmark.nfc.ui.theme.CopperSoftTint
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.DividerHairline
import com.tapmark.nfc.ui.theme.DraftingMark
import com.tapmark.nfc.ui.theme.MutedInk
import com.tapmark.nfc.ui.theme.TapmarkMonoCaption
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sin

private const val ID1_ASPECT_RATIO = 1.586f
private const val COIL_TURN_COUNT = 4
private val CopperHighlight = Color(0xFFEA8459)

private class TapPingRipple(
    val id: Long,
    val origin: Offset,
    val progress: Animatable<Float, AnimationVector1D> = Animatable(0f)
)

@Composable
fun CopperCoilCanvas(
    modifier: Modifier = Modifier,
    isReading: Boolean = false
) {
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val pingRipples = remember { mutableStateListOf<TapPingRipple>() }
    var nextRippleId by remember { mutableLongStateOf(0L) }

    val readingFactorState = animateFloatAsState(
        targetValue = if (isReading) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "coil_reading_factor"
    )

    var coilPhase by remember { mutableFloatStateOf(0f) }
    var ripplePhase by remember { mutableFloatStateOf(0f) }
    var levitationPhase by remember { mutableFloatStateOf(0f) }
    var scanlinePhase by remember { mutableFloatStateOf(0f) }

    // Frame-driven continuous phases so speed accelerates smoothly when a tag arrives
    LaunchedEffect(Unit) {
        var lastNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (lastNanos != 0L) {
                    val dt = ((frameNanos - lastNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
                    val reading = readingFactorState.value
                    val speedMultiplier = 1f + 1.35f * reading

                    coilPhase = (coilPhase + dt * 0.27f * speedMultiplier) % 1f
                    ripplePhase = (ripplePhase + dt * 0.36f * speedMultiplier) % 1f
                    levitationPhase = (levitationPhase + dt * (2f * PI.toFloat() / 4.2f)) % (2f * PI.toFloat())
                    scanlinePhase = (scanlinePhase + dt * 0.88f) % 1f
                }
                lastNanos = frameNanos
            }
        }
    }

    val cardClipPath = remember { Path() }
    val loopPaths = remember { List(COIL_TURN_COUNT) { Path() } }
    val pulseSegmentPath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(4.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ID1_ASPECT_RATIO)
                .graphicsLayer {
                    translationY = (sin(levitationPhase) * 3.5f).dp.toPx()
                }
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val ripple = TapPingRipple(
                            id = nextRippleId++,
                            origin = tapOffset
                        )
                        pingRipples.add(ripple)
                        scope.launch {
                            try {
                                ripple.progress.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = 760,
                                        easing = FastOutSlowInEasing
                                    )
                                )
                            } finally {
                                pingRipples.remove(ripple)
                            }
                        }
                    }
                }
        ) {
            val reading = readingFactorState.value
            val margin = 12.dp.toPx()
            val cardRect = Rect(
                left = margin,
                top = margin,
                right = size.width - margin,
                bottom = size.height - margin
            )
            val cardCorner = 14.dp.toPx()

            cardClipPath.reset()
            cardClipPath.addRoundRect(
                RoundRect(
                    rect = cardRect,
                    cornerRadius = CornerRadius(cardCorner, cardCorner)
                )
            )

            // Crisp archival card substrate
            drawRoundRect(
                color = BlueprintSurface,
                topLeft = cardRect.topLeft,
                size = cardRect.size,
                cornerRadius = CornerRadius(cardCorner, cardCorner)
            )
            drawRoundRect(
                color = DividerHairline,
                topLeft = cardRect.topLeft,
                size = cardRect.size,
                cornerRadius = CornerRadius(cardCorner, cardCorner),
                style = Stroke(width = 1.2.dp.toPx())
            )

            // Drafting crop marks outside all four corners + edge center ticks
            drawDraftingCropMarks(cardRect = cardRect, margin = margin)

            // Concentric rectangular copper NFC antenna turns + traveling copper current pulses
            val baseInset = cardRect.height * 0.085f
            val turnGap = cardRect.height * 0.044f
            val baseStrokePx = 1.55.dp.toPx()

            for (turnIndex in 0 until COIL_TURN_COUNT) {
                val inset = baseInset + turnIndex * turnGap
                val loopRect = Rect(
                    left = cardRect.left + inset,
                    top = cardRect.top + inset,
                    right = cardRect.right - inset,
                    bottom = cardRect.bottom - inset
                )
                val loopCorner = (cardCorner - inset * 0.45f).coerceAtLeast(4.dp.toPx())

                val waveValue = 0.5f + 0.5f * sin((coilPhase * 2f * PI.toFloat()) - turnIndex * 0.85f)
                val minAlpha = 0.24f + 0.14f * reading
                val maxAlpha = 0.62f + 0.20f * reading
                val baseAlpha = minAlpha + (maxAlpha - minAlpha) * waveValue

                drawRoundRect(
                    color = CopperTerracotta.copy(alpha = baseAlpha),
                    topLeft = loopRect.topLeft,
                    size = loopRect.size,
                    cornerRadius = CornerRadius(loopCorner, loopCorner),
                    style = Stroke(width = baseStrokePx)
                )

                val loopPath = loopPaths[turnIndex]
                loopPath.reset()
                loopPath.addRoundRect(
                    RoundRect(
                        rect = loopRect,
                        cornerRadius = CornerRadius(loopCorner, loopCorner)
                    )
                )
                pathMeasure.setPath(loopPath, forceClosed = true)
                val totalLength = pathMeasure.length
                if (totalLength > 0f) {
                    val headProgress = (coilPhase + turnIndex * 0.23f) % 1f
                    val headDist = headProgress * totalLength
                    val tailLength = totalLength * (0.16f + 0.04f * reading)
                    drawTravelingLoopPulse(
                        pathMeasure = pathMeasure,
                        segmentPath = pulseSegmentPath,
                        totalLength = totalLength,
                        headDist = headDist,
                        tailLength = tailLength,
                        readingFactor = reading
                    )
                }
            }

            // Etched ISO 7816 contact chip pad in the left-middle
            val chipWidth = cardRect.width * 0.145f
            val chipHeight = cardRect.height * 0.215f
            val chipLeft = cardRect.left + cardRect.width * 0.14f
            val chipTop = cardRect.center.y - chipHeight / 2f
            val chipRect = Rect(
                left = chipLeft,
                top = chipTop,
                right = chipLeft + chipWidth,
                bottom = chipTop + chipHeight
            )

            // Feed bridge traces connecting inner coil side to the chip module + animated current beads
            val innerInset = baseInset + (COIL_TURN_COUNT - 1) * turnGap
            val innerLeftX = cardRect.left + innerInset
            val bridgeStroke = 1.4.dp.toPx()
            val bridgeAlpha = 0.56f + 0.28f * reading
            val topBridgeY = chipRect.top + chipRect.height * 0.28f
            val bottomBridgeY = chipRect.bottom - chipRect.height * 0.28f

            drawLine(
                color = CopperTerracotta.copy(alpha = bridgeAlpha),
                start = Offset(innerLeftX, topBridgeY),
                end = Offset(chipRect.left, topBridgeY),
                strokeWidth = bridgeStroke,
                cap = StrokeCap.Round
            )
            drawLine(
                color = CopperTerracotta.copy(alpha = bridgeAlpha),
                start = Offset(innerLeftX, bottomBridgeY),
                end = Offset(chipRect.left, bottomBridgeY),
                strokeWidth = bridgeStroke,
                cap = StrokeCap.Round
            )

            val feedInPhase = (coilPhase * 2f) % 1f
            val feedOutPhase = (coilPhase * 2f + 0.5f) % 1f
            val feedInX = innerLeftX + (chipRect.left - innerLeftX) * feedInPhase
            val feedOutX = chipRect.left - (chipRect.left - innerLeftX) * feedOutPhase

            drawCircle(
                color = CopperHighlight.copy(alpha = 0.72f + 0.25f * reading),
                radius = 1.9.dp.toPx(),
                center = Offset(feedInX, topBridgeY)
            )
            drawCircle(
                color = CopperHighlight.copy(alpha = 0.65f + 0.25f * reading),
                radius = 1.8.dp.toPx(),
                center = Offset(feedOutX, bottomBridgeY)
            )

            val chipExcitation = 0.5f + 0.5f * sin(coilPhase * 4f * PI.toFloat())
            drawEtchedChipModule(
                chipRect = chipRect,
                outlineColor = CopperTerracotta.copy(alpha = 0.85f + 0.13f * reading),
                fillColor = CopperSoftTint,
                excitation = chipExcitation * (0.45f + 0.55f * reading)
            )

            // Expanding concentric RF field ripples radiating outward from contactless reticle
            val reticleCenter = Offset(
                x = cardRect.left + cardRect.width * 0.66f,
                y = cardRect.center.y
            )
            val rippleCount = 4
            val minRippleRadius = cardRect.height * 0.06f
            val maxRippleRadius = cardRect.height * (0.35f - 0.065f * reading)

            for (ring in 0 until rippleCount) {
                val t = (ripplePhase + ring.toFloat() / rippleCount) % 1f
                val radius = minRippleRadius + (maxRippleRadius - minRippleRadius) * t
                val envelope = sin(t * PI.toFloat()).coerceIn(0f, 1f).pow(0.8f)
                val ringAlpha = envelope * (0.34f + 0.42f * reading)
                val strokeWidth = (1.9f - 0.6f * t).dp.toPx()

                drawArc(
                    color = CopperTerracotta.copy(alpha = ringAlpha),
                    startAngle = -52f,
                    sweepAngle = 104f,
                    useCenter = false,
                    topLeft = Offset(reticleCenter.x - radius, reticleCenter.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            val anchorDotCenter = Offset(reticleCenter.x - cardRect.height * 0.02f, reticleCenter.y)
            val corePulse = 0.5f + 0.5f * sin(ripplePhase * 2f * PI.toFloat())
            if (reading > 0.02f) {
                drawCircle(
                    color = CopperTerracotta.copy(alpha = reading * (0.14f + 0.14f * corePulse)),
                    radius = (6.5f + 3f * corePulse).dp.toPx(),
                    center = anchorDotCenter
                )
            }
            drawCircle(
                color = CopperTerracotta.copy(alpha = 0.62f + 0.30f * corePulse),
                radius = (2.5f + 0.4f * reading).dp.toPx(),
                center = anchorDotCenter
            )

            // Clipped interior effects: Tap-to-Ping ripples & Active Reading Scanline Sweep
            clipPath(cardClipPath) {
                for (ripple in pingRipples) {
                    val p = ripple.progress.value
                    val maxPingRadius = cardRect.width * 0.46f
                    val radius = 6.dp.toPx() + p * maxPingRadius
                    val alpha = (1f - p) * (1f - p) * 0.78f
                    val pingStroke = (2.3f - 1.2f * p).dp.toPx()

                    drawCircle(
                        color = CopperTerracotta.copy(alpha = alpha),
                        radius = radius,
                        center = ripple.origin,
                        style = Stroke(width = pingStroke)
                    )

                    if (p > 0.14f) {
                        val pEcho = ((p - 0.14f) / 0.86f).coerceIn(0f, 1f)
                        val echoRadius = 4.dp.toPx() + pEcho * (cardRect.width * 0.32f)
                        drawCircle(
                            color = CopperHighlight.copy(alpha = (1f - pEcho) * 0.42f),
                            radius = echoRadius,
                            center = ripple.origin,
                            style = Stroke(width = 1.2.dp.toPx())
                        )
                    }

                    if (p < 0.35f) {
                        val centerAlpha = (1f - p / 0.35f) * 0.28f
                        drawCircle(
                            color = CopperTerracotta.copy(alpha = centerAlpha),
                            radius = 5.dp.toPx(),
                            center = ripple.origin
                        )
                    }
                }

                if (reading > 0.01f) {
                    val scanX = cardRect.left + scanlinePhase * cardRect.width
                    val edgeEnvelope = sin(scanlinePhase * PI.toFloat()).coerceIn(0f, 1f)
                    val scanAlpha = reading * (0.28f + 0.72f * edgeEnvelope)
                    val washWidth = cardRect.width * 0.16f

                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                CopperTerracotta.copy(alpha = 0.16f * scanAlpha)
                            ),
                            startX = scanX - washWidth,
                            endX = scanX
                        ),
                        topLeft = Offset(scanX - washWidth, cardRect.top),
                        size = Size(washWidth, cardRect.height)
                    )

                    drawLine(
                        color = CopperTerracotta.copy(alpha = 0.88f * scanAlpha),
                        start = Offset(scanX, cardRect.top),
                        end = Offset(scanX, cardRect.bottom),
                        strokeWidth = 1.6.dp.toPx()
                    )
                    drawLine(
                        color = CopperHighlight.copy(alpha = scanAlpha),
                        start = Offset(scanX, cardRect.top),
                        end = Offset(scanX, cardRect.top + 6.dp.toPx()),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = CopperHighlight.copy(alpha = scanAlpha),
                        start = Offset(scanX, cardRect.bottom - 6.dp.toPx()),
                        end = Offset(scanX, cardRect.bottom),
                        strokeWidth = 2.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ISO/IEC 7810 ID-1",
                style = TapmarkMonoCaption,
                color = MutedInk
            )
            Text(
                text = "13.56 MHz ANTENNA",
                style = TapmarkMonoCaption,
                color = MutedInk
            )
        }
    }
}

private fun DrawScope.drawTravelingLoopPulse(
    pathMeasure: PathMeasure,
    segmentPath: Path,
    totalLength: Float,
    headDist: Float,
    tailLength: Float,
    readingFactor: Float
) {
    val steps = 7
    val stepLength = tailLength / steps
    for (i in 0 until steps) {
        val segStart = headDist - (steps - i) * stepLength
        val segEnd = segStart + stepLength + 1.5f
        val progress = (i + 1).toFloat() / steps
        val segAlpha = (0.08f + 0.82f * progress * progress) * (0.85f + 0.15f * readingFactor)
        val segColor = lerp(CopperTerracotta, CopperHighlight, progress)
        val segStroke = (1.6f + 1.15f * progress).dp.toPx()

        drawWrappedSegment(
            pathMeasure = pathMeasure,
            segmentPath = segmentPath,
            totalLength = totalLength,
            startDist = segStart,
            endDist = segEnd,
            color = segColor.copy(alpha = segAlpha.coerceIn(0f, 1f)),
            strokeWidth = segStroke
        )
    }

    val normalizedHead = (((headDist % totalLength) + totalLength) % totalLength)
        .coerceIn(0f, (totalLength - 0.01f).coerceAtLeast(0f))
    val headPos = pathMeasure.getPosition(normalizedHead)
    drawCircle(
        color = CopperHighlight.copy(alpha = 0.22f + 0.16f * readingFactor),
        radius = 4.5.dp.toPx(),
        center = headPos
    )
    drawCircle(
        color = CopperHighlight.copy(alpha = 0.94f),
        radius = 2.0.dp.toPx(),
        center = headPos
    )
}

private fun DrawScope.drawWrappedSegment(
    pathMeasure: PathMeasure,
    segmentPath: Path,
    totalLength: Float,
    startDist: Float,
    endDist: Float,
    color: Color,
    strokeWidth: Float
) {
    if (startDist < 0f && endDist <= 0f) {
        segmentPath.reset()
        pathMeasure.getSegment(
            startDistance = totalLength + startDist,
            stopDistance = totalLength + endDist,
            destination = segmentPath,
            startWithMoveTo = true
        )
        drawPath(
            path = segmentPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    } else if (startDist < 0f && endDist > 0f) {
        segmentPath.reset()
        pathMeasure.getSegment(
            startDistance = totalLength + startDist,
            stopDistance = totalLength,
            destination = segmentPath,
            startWithMoveTo = true
        )
        drawPath(
            path = segmentPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        segmentPath.reset()
        pathMeasure.getSegment(
            startDistance = 0f,
            stopDistance = endDist,
            destination = segmentPath,
            startWithMoveTo = true
        )
        drawPath(
            path = segmentPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    } else if (startDist >= 0f && endDist > totalLength) {
        segmentPath.reset()
        pathMeasure.getSegment(
            startDistance = startDist.coerceIn(0f, totalLength),
            stopDistance = totalLength,
            destination = segmentPath,
            startWithMoveTo = true
        )
        drawPath(
            path = segmentPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        segmentPath.reset()
        pathMeasure.getSegment(
            startDistance = 0f,
            stopDistance = (endDist - totalLength).coerceIn(0f, totalLength),
            destination = segmentPath,
            startWithMoveTo = true
        )
        drawPath(
            path = segmentPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    } else {
        segmentPath.reset()
        pathMeasure.getSegment(
            startDistance = startDist.coerceIn(0f, totalLength),
            stopDistance = endDist.coerceIn(0f, totalLength),
            destination = segmentPath,
            startWithMoveTo = true
        )
        drawPath(
            path = segmentPath,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )
    }
}

private fun DrawScope.drawDraftingCropMarks(cardRect: Rect, margin: Float) {
    val markColor = DraftingMark
    val stroke = 1.dp.toPx()
    val arm = 8.dp.toPx()
    val gap = 4.dp.toPx()

    // Top-left crop mark
    drawLine(markColor, Offset(cardRect.left - gap - arm, cardRect.top), Offset(cardRect.left - gap, cardRect.top), stroke)
    drawLine(markColor, Offset(cardRect.left, cardRect.top - gap - arm), Offset(cardRect.left, cardRect.top - gap), stroke)

    // Top-right crop mark
    drawLine(markColor, Offset(cardRect.right + gap, cardRect.top), Offset(cardRect.right + gap + arm, cardRect.top), stroke)
    drawLine(markColor, Offset(cardRect.right, cardRect.top - gap - arm), Offset(cardRect.right, cardRect.top - gap), stroke)

    // Bottom-left crop mark
    drawLine(markColor, Offset(cardRect.left - gap - arm, cardRect.bottom), Offset(cardRect.left - gap, cardRect.bottom), stroke)
    drawLine(markColor, Offset(cardRect.left, cardRect.bottom + gap), Offset(cardRect.left, cardRect.bottom + gap + arm), stroke)

    // Bottom-right crop mark
    drawLine(markColor, Offset(cardRect.right + gap, cardRect.bottom), Offset(cardRect.right + gap + arm, cardRect.bottom), stroke)
    drawLine(markColor, Offset(cardRect.right, cardRect.bottom + gap), Offset(cardRect.right, cardRect.bottom + gap + arm), stroke)

    // Subtle centerline ticks along top & bottom margins
    val tickLen = (margin * 0.42f).coerceAtLeast(3.dp.toPx())
    val midX = cardRect.center.x
    val midY = cardRect.center.y
    drawLine(markColor.copy(alpha = 0.65f), Offset(midX, cardRect.top - tickLen), Offset(midX, cardRect.top), stroke)
    drawLine(markColor.copy(alpha = 0.65f), Offset(midX, cardRect.bottom), Offset(midX, cardRect.bottom + tickLen), stroke)
    drawLine(markColor.copy(alpha = 0.65f), Offset(cardRect.left - tickLen, midY), Offset(cardRect.left, midY), stroke)
    drawLine(markColor.copy(alpha = 0.65f), Offset(cardRect.right, midY), Offset(cardRect.right + tickLen, midY), stroke)
}

private fun DrawScope.drawEtchedChipModule(
    chipRect: Rect,
    outlineColor: Color,
    fillColor: Color,
    excitation: Float
) {
    val corner = 4.5.dp.toPx()
    val stroke = 1.25.dp.toPx()

    drawRoundRect(
        color = fillColor,
        topLeft = chipRect.topLeft,
        size = chipRect.size,
        cornerRadius = CornerRadius(corner, corner)
    )
    drawRoundRect(
        color = outlineColor,
        topLeft = chipRect.topLeft,
        size = chipRect.size,
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = stroke)
    )

    // ISO 7816 contact pad divisions
    val leftThird = chipRect.left + chipRect.width * 0.34f
    val rightThird = chipRect.left + chipRect.width * 0.66f
    val topThird = chipRect.top + chipRect.height * 0.33f
    val bottomThird = chipRect.top + chipRect.height * 0.67f
    val midY = chipRect.center.y

    val innerStroke = 1.dp.toPx()
    val innerColor = outlineColor.copy(alpha = 0.72f)

    // Central vertical dividers & horizontal contact rows
    drawLine(innerColor, Offset(leftThird, chipRect.top), Offset(leftThird, chipRect.bottom), innerStroke)
    drawLine(innerColor, Offset(rightThird, chipRect.top), Offset(rightThird, chipRect.bottom), innerStroke)
    drawLine(innerColor, Offset(chipRect.left, topThird), Offset(leftThird, topThird), innerStroke)
    drawLine(innerColor, Offset(chipRect.left, bottomThird), Offset(leftThird, bottomThird), innerStroke)
    drawLine(innerColor, Offset(rightThird, topThird), Offset(chipRect.right, topThird), innerStroke)
    drawLine(innerColor, Offset(rightThird, bottomThird), Offset(chipRect.right, bottomThird), innerStroke)
    drawLine(innerColor, Offset(leftThird, midY), Offset(rightThird, midY), innerStroke)

    // Small center bridge loop path with warm copper excitation pulse
    val centerPath = Path().apply {
        moveTo(leftThird, topThird)
        lineTo(rightThird, topThird)
        lineTo(rightThird, bottomThird)
        lineTo(leftThird, bottomThird)
        close()
    }
    drawPath(
        path = centerPath,
        color = lerp(innerColor, CopperHighlight, excitation * 0.55f),
        style = Stroke(width = innerStroke + 0.35f * excitation, join = StrokeJoin.Round)
    )
}
