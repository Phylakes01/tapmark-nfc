package com.tapmark.nfc.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.tapmark.nfc.ui.theme.CardSurface
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.DividerHairline
import com.tapmark.nfc.ui.theme.MutedInk
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.RecessedSurface
import com.tapmark.nfc.ui.theme.SecondaryGraphite
import com.tapmark.nfc.ui.theme.TapmarkMonoCaption

/**
 * Vector QR code card rendered in Compose Canvas using ZXing [QRCodeWriter]
 * on a pure white quiet zone with warm copper L-corner framing brackets.
 */
@Composable
fun QrCodeCanvas(
    payload: String,
    modifier: Modifier = Modifier,
    caption: String = "QR PH · CHECKOUT"
) {
    val matrix: BitMatrix? = remember(payload) {
        if (payload.isBlank()) {
            null
        } else {
            runCatching {
                QRCodeWriter().encode(
                    payload,
                    BarcodeFormat.QR_CODE,
                    0,
                    0,
                    mapOf(EncodeHintType.MARGIN to 1)
                )
            }.getOrNull()
        }
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerHairline),
        shadowElevation = 2.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.White)
                    .padding(6.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Pure white quiet-zone substrate
                    drawRect(color = Color.White)

                    val bracketStroke = 2.dp.toPx()
                    val bracketLen = 14.dp.toPx()
                    val pad = 1.dp.toPx()
                    val right = size.width - pad
                    val bottom = size.height - pad

                    // Subtle copper L-corner framing brackets
                    drawLine(CopperTerracotta, Offset(pad, pad), Offset(pad + bracketLen, pad), bracketStroke, StrokeCap.Round)
                    drawLine(CopperTerracotta, Offset(pad, pad), Offset(pad, pad + bracketLen), bracketStroke, StrokeCap.Round)

                    drawLine(CopperTerracotta, Offset(right - bracketLen, pad), Offset(right, pad), bracketStroke, StrokeCap.Round)
                    drawLine(CopperTerracotta, Offset(right, pad), Offset(right, pad + bracketLen), bracketStroke, StrokeCap.Round)

                    drawLine(CopperTerracotta, Offset(pad, bottom), Offset(pad + bracketLen, bottom), bracketStroke, StrokeCap.Round)
                    drawLine(CopperTerracotta, Offset(pad, bottom - bracketLen), Offset(pad, bottom), bracketStroke, StrokeCap.Round)

                    drawLine(CopperTerracotta, Offset(right - bracketLen, bottom), Offset(right, bottom), bracketStroke, StrokeCap.Round)
                    drawLine(CopperTerracotta, Offset(right, bottom - bracketLen), Offset(right, bottom), bracketStroke, StrokeCap.Round)

                    if (matrix != null) {
                        val cols = matrix.width
                        val rows = matrix.height
                        if (cols > 0 && rows > 0) {
                            val inset = 10.dp.toPx()
                            val availW = (size.width - inset * 2f).coerceAtLeast(1f)
                            val availH = (size.height - inset * 2f).coerceAtLeast(1f)
                            val moduleSize = minOf(availW / cols, availH / rows)
                            val gridW = moduleSize * cols
                            val gridH = moduleSize * rows
                            val startX = (size.width - gridW) / 2f
                            val startY = (size.height - gridH) / 2f
                            val radius = CornerRadius(moduleSize * 0.22f, moduleSize * 0.22f)
                            val drawSize = Size(moduleSize * 0.96f, moduleSize * 0.96f)
                            val modulePad = moduleSize * 0.02f

                            for (y in 0 until rows) {
                                val top = startY + y * moduleSize + modulePad
                                for (x in 0 until cols) {
                                    if (matrix.get(x, y)) {
                                        drawRoundRect(
                                            color = PrimaryInk,
                                            topLeft = Offset(startX + x * moduleSize + modulePad, top),
                                            size = drawSize,
                                            cornerRadius = radius
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (matrix == null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(RecessedSurface)
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "QR payload unavailable",
                            style = TapmarkMonoCaption,
                            color = SecondaryGraphite
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(CopperTerracotta)
                )
                Text(
                    text = caption,
                    style = TapmarkMonoCaption.copy(
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.1.sp
                    ),
                    color = MutedInk
                )
            }
        }
    }
}
