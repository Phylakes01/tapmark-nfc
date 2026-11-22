package com.tapmark.nfc.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapmark.nfc.ui.theme.CopperSoftTint
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.PaperCanvas
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.SecondaryGraphite
import com.tapmark.nfc.ui.theme.TapmarkMonoCaption
import com.tapmark.nfc.ui.theme.TapmarkWordmarkStyle
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.PI
import kotlin.math.sin

private val IntroCopperHighlight = Color(0xFFEA8459)
private val IntroChipTopTint = Color(0xFFFCF4EF)
private val IntroChipBottomTint = Color(0xFFF1D7CA)

/**
 * Full-screen warm archival paper launch sequence drawing the concentric copper
 * NFC coil emblem, chip pad, expanding RF ripples, wordmark, and creator attribution.
 * Supports tap-anywhere-to-skip with a fast ~180ms fade and guaranteed single completion callback.
 */
@Composable
fun OpeningIntroOverlay(
    onFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currentOnFinished by rememberUpdatedState(onFinished)
    val finishedOnce = remember { AtomicBoolean(false) }
    var isSkipping by remember { mutableStateOf(false) }

    val loop0 = remember { Animatable(0f) }
    val loop1 = remember { Animatable(0f) }
    val loop2 = remember { Animatable(0f) }
    val chipProgress = remember { Animatable(0f) }
    val ripple1 = remember { Animatable(0f) }
    val ripple2 = remember { Animatable(0f) }
    val wordmarkProgress = remember { Animatable(0f) }
    val ruleProgress = remember { Animatable(0f) }
    val subtitleProgress = remember { Animatable(0f) }
    val attributionProgress = remember { Animatable(0f) }
    val overlayAlpha = remember { Animatable(1f) }
    val overlayScale = remember { Animatable(1f) }

    val notifyFinishedOnce = {
        if (finishedOnce.compareAndSet(false, true)) {
            currentOnFinished()
        }
    }

    LaunchedEffect(isSkipping) {
        if (isSkipping) {
            coroutineScope {
                launch {
                    overlayScale.animateTo(
                        targetValue = 1.025f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    overlayAlpha.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing)
                    )
                }
            }
            notifyFinishedOnce()
        } else {
            coroutineScope {
                // 0-650ms: 3 concentric copper coil loops trace stroke-by-stroke + center chip pad springs in
                launch {
                    loop0.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing)
                    )
                }

                delay(90L)
                launch {
                    loop1.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                    )
                }

                delay(90L)
                launch {
                    loop2.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 380, easing = FastOutSlowInEasing)
                    )
                }

                delay(140L) // t = 320ms
                launch {
                    chipProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.68f,
                            stiffness = 420f
                        )
                    )
                }

                // 550-1050ms: Expanding RF ripple rings + Tapmark wordmark + copper rule + monospace subtitle
                delay(230L) // t = 550ms
                launch {
                    ripple1.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    delay(100L)
                    ripple2.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    wordmarkProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(
                            dampingRatio = 0.78f,
                            stiffness = 360f
                        )
                    )
                }
                launch {
                    delay(90L)
                    ruleProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing)
                    )
                }
                launch {
                    delay(170L)
                    subtitleProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 330, easing = FastOutSlowInEasing)
                    )
                }

                // 850-1550ms: Bottom attribution glides up smoothly
                delay(300L) // t = 850ms
                launch {
                    attributionProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 540, easing = FastOutSlowInEasing)
                    )
                }

                // 1700-2100ms: Subtle scale up and cross-fade into live Scanner screen
                delay(850L) // t = 1700ms
                coroutineScope {
                    launch {
                        overlayScale.animateTo(
                            targetValue = 1.04f,
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                        )
                    }
                    launch {
                        overlayAlpha.animateTo(
                            targetValue = 0f,
                            animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing)
                        )
                    }
                }
            }
            notifyFinishedOnce()
        }
    }

    val emblemPaths = remember { List(3) { Path() } }
    val segmentPath = remember { Path() }
    val pathMeasure = remember { PathMeasure() }
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = overlayScale.value
                scaleY = overlayScale.value
                alpha = overlayAlpha.value
            }
            .background(PaperCanvas)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isSkipping,
                onClickLabel = "Skip intro"
            ) {
                isSkipping = true
            }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Canvas(
                modifier = Modifier.size(width = 192.dp, height = 140.dp)
            ) {
                // Expanding copper RF ripple rings behind the coil emblem
                drawIntroRippleRing(progress = ripple1.value)
                drawIntroRippleRing(progress = ripple2.value)

                val outerWidth = 124.dp.toPx()
                val outerHeight = 78.dp.toPx()
                val outerLeft = center.x - outerWidth / 2f
                val outerTop = center.y - outerHeight / 2f
                val outerRect = Rect(
                    left = outerLeft,
                    top = outerTop,
                    right = outerLeft + outerWidth,
                    bottom = outerTop + outerHeight
                )

                val loopProgresses = floatArrayOf(loop0.value, loop1.value, loop2.value)
                val loopGap = 7.5.dp.toPx()
                val baseCorner = 12.dp.toPx()

                for (i in 0 until 3) {
                    val progress = loopProgresses[i].coerceIn(0f, 1f)
                    if (progress <= 0.004f) continue

                    val inset = i * loopGap
                    val loopRect = Rect(
                        left = outerRect.left + inset,
                        top = outerRect.top + inset,
                        right = outerRect.right - inset,
                        bottom = outerRect.bottom - inset
                    )
                    val corner = (baseCorner - inset * 0.42f).coerceAtLeast(4.5.dp.toPx())
                    val loopPath = emblemPaths[i]
                    loopPath.reset()
                    loopPath.addRoundRect(
                        RoundRect(
                            rect = loopRect,
                            cornerRadius = CornerRadius(corner, corner)
                        )
                    )

                    pathMeasure.setPath(loopPath, forceClosed = true)
                    val totalLength = pathMeasure.length
                    if (totalLength > 0f) {
                        val stopDist = (totalLength * progress).coerceIn(0f, totalLength)
                        segmentPath.reset()
                        pathMeasure.getSegment(
                            startDistance = 0f,
                            stopDistance = stopDist,
                            destination = segmentPath,
                            startWithMoveTo = true
                        )

                        val strokeWidth = (1.85f - i * 0.16f).dp.toPx()
                        val loopAlpha = 0.92f - i * 0.08f
                        drawPath(
                            path = segmentPath,
                            color = CopperTerracotta.copy(alpha = loopAlpha),
                            style = Stroke(
                                width = strokeWidth,
                                cap = StrokeCap.Round,
                                join = StrokeJoin.Round
                            )
                        )

                        if (progress in 0.02f..0.97f) {
                            val tipPos = pathMeasure.getPosition(stopDist)
                            drawCircle(
                                color = IntroCopperHighlight.copy(alpha = 0.28f),
                                radius = 4.2.dp.toPx(),
                                center = tipPos
                            )
                            drawCircle(
                                color = IntroCopperHighlight,
                                radius = 2.0.dp.toPx(),
                                center = tipPos
                            )
                        }
                    }
                }

                // Etched metallic center chip pad springing into place
                val chipVal = chipProgress.value
                if (chipVal > 0.005f) {
                    val chipAlpha = chipVal.coerceIn(0f, 1f)
                    val chipScale = 0.60f + 0.40f * chipVal
                    val chipW = 34.dp.toPx()
                    val chipH = 25.dp.toPx()
                    val chipRect = Rect(
                        left = center.x - chipW / 2f,
                        top = center.y - chipH / 2f,
                        right = center.x + chipW / 2f,
                        bottom = center.y + chipH / 2f
                    )

                    withTransform({
                        scale(scaleX = chipScale, scaleY = chipScale, pivot = center)
                    }) {
                        drawIntroChipPad(
                            chipRect = chipRect,
                            alpha = chipAlpha
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Text(
                text = "Tapmark",
                style = TapmarkWordmarkStyle.copy(
                    fontSize = 32.sp,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.8).sp
                ),
                color = PrimaryInk,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val p = wordmarkProgress.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 16.dp.toPx()
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Canvas(
                modifier = Modifier
                    .width(68.dp)
                    .height(2.dp)
            ) {
                val p = ruleProgress.value.coerceIn(0f, 1f)
                if (p > 0.005f) {
                    drawLine(
                        color = CopperTerracotta.copy(alpha = 0.90f),
                        start = Offset(0f, size.height / 2f),
                        end = Offset(size.width * p, size.height / 2f),
                        strokeWidth = 1.5.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(9.dp))

            Text(
                text = "NFC CARD INSPECTOR",
                style = TapmarkMonoCaption.copy(
                    letterSpacing = 1.5.sp
                ),
                color = SecondaryGraphite,
                textAlign = TextAlign.Center,
                modifier = Modifier.graphicsLayer {
                    val p = subtitleProgress.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 8.dp.toPx()
                }
            )
        }

        Text(
            text = "Made by Jeremiah Temporado",
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.45.sp
            ),
            color = SecondaryGraphite,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 26.dp)
                .graphicsLayer {
                    val p = attributionProgress.value
                    alpha = p.coerceIn(0f, 1f)
                    translationY = (1f - p) * 14.dp.toPx()
                }
        )
    }
}

private fun DrawScope.drawIntroRippleRing(progress: Float) {
    val p = progress.coerceIn(0f, 1f)
    if (p <= 0.004f || p >= 0.996f) return

    val startRadius = 30.dp.toPx()
    val endRadius = 86.dp.toPx()
    val radius = startRadius + (endRadius - startRadius) * p
    val fadeIn = (p / 0.14f).coerceIn(0f, 1f)
    val fadeOut = sin((1f - p) * (PI.toFloat() / 2f)).coerceIn(0f, 1f)
    val ringAlpha = fadeIn * fadeOut * 0.42f
    val strokeWidth = (1.8f - 0.85f * p).dp.toPx()

    drawCircle(
        color = CopperTerracotta.copy(alpha = ringAlpha),
        radius = radius,
        center = center,
        style = Stroke(width = strokeWidth)
    )
}

private fun DrawScope.drawIntroChipPad(
    chipRect: Rect,
    alpha: Float
) {
    val corner = 4.5.dp.toPx()
    val metallicBrush = Brush.linearGradient(
        colors = listOf(
            IntroChipTopTint.copy(alpha = alpha),
            CopperSoftTint.copy(alpha = alpha),
            IntroChipBottomTint.copy(alpha = alpha)
        ),
        start = chipRect.topLeft,
        end = chipRect.bottomRight
    )

    drawRoundRect(
        brush = metallicBrush,
        topLeft = chipRect.topLeft,
        size = chipRect.size,
        cornerRadius = CornerRadius(corner, corner)
    )
    drawRoundRect(
        color = CopperTerracotta.copy(alpha = 0.94f * alpha),
        topLeft = chipRect.topLeft,
        size = chipRect.size,
        cornerRadius = CornerRadius(corner, corner),
        style = Stroke(width = 1.35.dp.toPx())
    )

    val leftThird = chipRect.left + chipRect.width * 0.34f
    val rightThird = chipRect.left + chipRect.width * 0.66f
    val topThird = chipRect.top + chipRect.height * 0.33f
    val bottomThird = chipRect.top + chipRect.height * 0.67f
    val midY = chipRect.center.y

    val gridColor = CopperTerracotta.copy(alpha = 0.72f * alpha)
    val gridStroke = 1.0.dp.toPx()

    drawLine(gridColor, Offset(leftThird, chipRect.top), Offset(leftThird, chipRect.bottom), gridStroke)
    drawLine(gridColor, Offset(rightThird, chipRect.top), Offset(rightThird, chipRect.bottom), gridStroke)
    drawLine(gridColor, Offset(chipRect.left, topThird), Offset(leftThird, topThird), gridStroke)
    drawLine(gridColor, Offset(chipRect.left, bottomThird), Offset(leftThird, bottomThird), gridStroke)
    drawLine(gridColor, Offset(rightThird, topThird), Offset(chipRect.right, topThird), gridStroke)
    drawLine(gridColor, Offset(rightThird, bottomThird), Offset(chipRect.right, bottomThird), gridStroke)
    drawLine(gridColor, Offset(leftThird, midY), Offset(rightThird, midY), gridStroke)

    val centerBridge = Path().apply {
        moveTo(leftThird, topThird)
        lineTo(rightThird, topThird)
        lineTo(rightThird, bottomThird)
        lineTo(leftThird, bottomThird)
        close()
    }
    drawPath(
        path = centerBridge,
        color = CopperTerracotta.copy(alpha = 0.85f * alpha),
        style = Stroke(width = gridStroke, join = StrokeJoin.Round)
    )
}
