package com.tapmark.nfc.ui.screens

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Contactless
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tapmark.nfc.model.CardVisualStyle
import com.tapmark.nfc.model.CheckoutSessionResult
import com.tapmark.nfc.model.GatewayConfig
import com.tapmark.nfc.model.GatewayEnvironment
import com.tapmark.nfc.model.GatewayProvider
import com.tapmark.nfc.model.NfcHardwareState
import com.tapmark.nfc.model.PaymentMethodMode
import com.tapmark.nfc.model.ReceiptStatus
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.model.TerminalReceipt
import com.tapmark.nfc.payment.PaymentGatewayClient
import com.tapmark.nfc.ui.components.CopperCoilCanvas
import com.tapmark.nfc.ui.components.PhysicalCardView
import com.tapmark.nfc.ui.components.QrCodeCanvas
import com.tapmark.nfc.ui.components.copyTextToClipboard
import com.tapmark.nfc.ui.components.formatAbsoluteTimestamp
import com.tapmark.nfc.ui.components.formatRelativeTimestamp
import com.tapmark.nfc.ui.components.openExternalUrl
import com.tapmark.nfc.ui.components.toPalette
import com.tapmark.nfc.ui.theme.BlueprintSurface
import com.tapmark.nfc.ui.theme.CardSurface
import com.tapmark.nfc.ui.theme.CopperSoftTint
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.DividerHairline
import com.tapmark.nfc.ui.theme.MutedInk
import com.tapmark.nfc.ui.theme.PaperCanvas
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.RecessedSurface
import com.tapmark.nfc.ui.theme.SecondaryGraphite
import com.tapmark.nfc.ui.theme.StatusAmber
import com.tapmark.nfc.ui.theme.StatusAmberTint
import com.tapmark.nfc.ui.theme.StatusCrimson
import com.tapmark.nfc.ui.theme.StatusCrimsonTint
import com.tapmark.nfc.ui.theme.StatusSage
import com.tapmark.nfc.ui.theme.StatusSageTint
import com.tapmark.nfc.ui.theme.TapmarkMonoBody
import com.tapmark.nfc.ui.theme.TapmarkMonoCaption
import com.tapmark.nfc.ui.theme.TapmarkSectionOverline

enum class TerminalStage {
    KEYPAD,
    WAITING_FOR_NFC,
    NFC_RECEIPT,
    QR_CHECKOUT,
    LEDGER
}

private const val MAX_CENTAVOS = 999_999_999L
private val NOTE_PRESETS = listOf("Coffee", "Order", "Service")

@Composable
fun TerminalScreen(
    nfcState: NfcHardwareState,
    gatewayConfig: GatewayConfig,
    receipts: List<TerminalReceipt>,
    todayTotalCentavos: Long,
    stage: TerminalStage,
    amountCentavos: Long,
    description: String,
    tappedCard: ScannedCard?,
    activeReceipt: TerminalReceipt?,
    qrSession: CheckoutSessionResult?,
    isCreatingQrSession: Boolean,
    isCheckingQrStatus: Boolean,
    qrErrorMessage: String?,
    isReadingNfc: Boolean,
    nfcTagLostMessage: String?,
    nfcErrorMessage: String?,
    onAmountChange: (Long) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onStageChange: (TerminalStage) -> Unit,
    onStartNfcTap: () -> Unit,
    onSimulateBpiTap: () -> Unit,
    onCancelNfcTap: () -> Unit,
    onStartQrCheckout: () -> Unit,
    onCheckQrStatus: () -> Unit,
    onMarkSandboxPaid: () -> Unit,
    onNewSale: () -> Unit,
    onSaveGatewayConfig: (GatewayConfig) -> Unit,
    onDeleteReceipt: (String) -> Unit,
    onClearAllReceipts: () -> Unit,
    onFeedbackMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var showGatewaySettings by remember { mutableStateOf(false) }

    BackHandler(enabled = stage != TerminalStage.KEYPAD) {
        when (stage) {
            TerminalStage.WAITING_FOR_NFC -> onCancelNfcTap()
            TerminalStage.NFC_RECEIPT,
            TerminalStage.QR_CHECKOUT -> onNewSale()
            TerminalStage.LEDGER -> onStageChange(TerminalStage.KEYPAD)
            TerminalStage.KEYPAD -> Unit
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        TerminalHeaderRow(
            gatewayConfig = gatewayConfig,
            receiptCount = receipts.size,
            todayTotalCentavos = todayTotalCentavos,
            isLedgerActive = stage == TerminalStage.LEDGER,
            onOpenGatewaySettings = { showGatewaySettings = true },
            onToggleLedger = {
                onStageChange(
                    if (stage == TerminalStage.LEDGER) TerminalStage.KEYPAD else TerminalStage.LEDGER
                )
            }
        )

        AnimatedContent(
            targetState = stage,
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically(tween(240)) { it / 16 })
                    .togetherWith(fadeOut(tween(150)))
            },
            label = "terminal_stage_transition",
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) { currentStage ->
            when (currentStage) {
                TerminalStage.KEYPAD -> KeypadStage(
                    gatewayConfig = gatewayConfig,
                    amountCentavos = amountCentavos,
                    description = description,
                    onAmountChange = onAmountChange,
                    onDescriptionChange = onDescriptionChange,
                    onTapCardNfc = onStartNfcTap,
                    onQrCheckout = onStartQrCheckout
                )

                TerminalStage.WAITING_FOR_NFC -> WaitingForNfcStage(
                    nfcState = nfcState,
                    amountCentavos = amountCentavos,
                    description = description,
                    isReading = isReadingNfc,
                    tagLostMessage = nfcTagLostMessage,
                    errorMessage = nfcErrorMessage,
                    onSimulateBpiTap = onSimulateBpiTap,
                    onCancel = onCancelNfcTap
                )

                TerminalStage.NFC_RECEIPT -> NfcReceiptStage(
                    receipt = activeReceipt,
                    tappedCard = tappedCard,
                    onFeedbackMessage = onFeedbackMessage,
                    onNewSale = onNewSale
                )

                TerminalStage.QR_CHECKOUT -> QrCheckoutStage(
                    gatewayConfig = gatewayConfig,
                    amountCentavos = amountCentavos,
                    description = description,
                    session = qrSession,
                    activeReceipt = activeReceipt,
                    isCreatingSession = isCreatingQrSession,
                    isCheckingStatus = isCheckingQrStatus,
                    errorMessage = qrErrorMessage,
                    onOpenGatewaySettings = { showGatewaySettings = true },
                    onRetrySession = onStartQrCheckout,
                    onCheckStatus = onCheckQrStatus,
                    onMarkTestPaid = onMarkSandboxPaid,
                    onFeedbackMessage = onFeedbackMessage,
                    onNewSale = onNewSale
                )

                TerminalStage.LEDGER -> TerminalLedgerStage(
                    receipts = receipts,
                    todayTotalCentavos = todayTotalCentavos,
                    onBackToKeypad = { onStageChange(TerminalStage.KEYPAD) },
                    onDeleteReceipt = onDeleteReceipt,
                    onClearAll = onClearAllReceipts,
                    onFeedbackMessage = onFeedbackMessage
                )
            }
        }
    }

    if (showGatewaySettings) {
        GatewaySettingsDialog(
            initialConfig = gatewayConfig,
            onDismiss = { showGatewaySettings = false },
            onSave = { updated ->
                showGatewaySettings = false
                onSaveGatewayConfig(updated)
            }
        )
    }
}

@Composable
private fun TerminalHeaderRow(
    gatewayConfig: GatewayConfig,
    receiptCount: Int,
    todayTotalCentavos: Long,
    isLedgerActive: Boolean,
    onOpenGatewaySettings: () -> Unit,
    onToggleLedger: () -> Unit
) {
    val (gatewayLabel, dotColor, bgColor, borderColor) = resolveGatewayPillStyle(gatewayConfig)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = bgColor,
            border = BorderStroke(1.dp, borderColor),
            modifier = Modifier
                .weight(1f, fill = false)
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onOpenGatewaySettings)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(dotColor)
                )
                Text(
                    text = gatewayLabel,
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Gateway Settings",
                    tint = SecondaryGraphite,
                    modifier = Modifier.size(14.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (isLedgerActive) CopperSoftTint else CardSurface,
            border = BorderStroke(
                width = 1.dp,
                color = if (isLedgerActive) CopperTerracotta.copy(alpha = 0.45f) else DividerHairline
            ),
            modifier = Modifier
                .clip(RoundedCornerShape(999.dp))
                .clickable(onClick = onToggleLedger)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.ReceiptLong,
                    contentDescription = "Receipts",
                    tint = if (isLedgerActive) CopperTerracotta else SecondaryGraphite,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$receiptCount · ${PaymentGatewayClient.formatPhpAmount(todayTotalCentavos)}",
                    style = TapmarkMonoCaption.copy(fontWeight = FontWeight.SemiBold),
                    color = if (isLedgerActive) CopperTerracotta else PrimaryInk,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun KeypadStage(
    gatewayConfig: GatewayConfig,
    amountCentavos: Long,
    description: String,
    onAmountChange: (Long) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onTapCardNfc: () -> Unit,
    onQrCheckout: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    val formattedAmount = PaymentGatewayClient.formatPhpAmount(amountCentavos)

    val amountScale = remember { Animatable(1f) }
    LaunchedEffect(amountCentavos) {
        amountScale.snapTo(1f)
        amountScale.animateTo(
            targetValue = 1.045f,
            animationSpec = tween(durationMillis = 70, easing = FastOutSlowInEasing)
        )
        amountScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.7f, stiffness = 480f)
        )
    }

    val amountFontSize = when {
        formattedAmount.length >= 13 -> 28.sp
        formattedAmount.length >= 10 -> 33.sp
        else -> 39.sp
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = 122.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Compact note input + 3 quick preset chips
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, DividerHairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = if (description == "Tapmark Sale") "" else description,
                    onValueChange = { raw ->
                        if (raw.length <= 42) {
                            onDescriptionChange(raw)
                        }
                    },
                    singleLine = true,
                    placeholder = {
                        Text(
                            text = "Tapmark Sale (add item or note)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MutedInk
                        )
                    },
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = PrimaryInk),
                    shape = RoundedCornerShape(11.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CopperTerracotta,
                        unfocusedBorderColor = DividerHairline,
                        focusedContainerColor = BlueprintSurface,
                        unfocusedContainerColor = BlueprintSurface,
                        cursorColor = CopperTerracotta
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    NOTE_PRESETS.forEach { preset ->
                        val selected = description.equals(preset, ignoreCase = true)
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = if (selected) CopperSoftTint else RecessedSurface,
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (selected) CopperTerracotta.copy(alpha = 0.45f) else DividerHairline
                            ),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onDescriptionChange(if (selected) "Tapmark Sale" else preset)
                                }
                        ) {
                            Text(
                                text = preset,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) CopperTerracotta else PrimaryInk,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Prominent centered tabular monospace ₱ amount display
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = BlueprintSurface,
            border = BorderStroke(1.dp, DividerHairline)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = resolveTerminalMicroTag(gatewayConfig),
                        style = TapmarkMonoCaption.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp
                        ),
                        color = SecondaryGraphite
                    )

                    if (amountCentavos > 0L) {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = RecessedSurface,
                            border = BorderStroke(1.dp, DividerHairline),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .clickable {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    onAmountChange(0L)
                                }
                        ) {
                            Text(
                                text = "Clear",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = SecondaryGraphite,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = formattedAmount,
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = amountFontSize,
                        lineHeight = amountFontSize * 1.15f,
                        letterSpacing = (-0.5).sp,
                        fontFeatureSettings = "tnum"
                    ),
                    color = if (amountCentavos > 0L) PrimaryInk else MutedInk,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .graphicsLayer {
                            val s = amountScale.value
                            scaleX = s
                            scaleY = s
                        }
                )
            }
        }

        // Tactile 3x4 numeric keypad
        val keypadRows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("00", "0", "⌫")
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            keypadRows.forEach { rowKeys ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    rowKeys.forEach { key ->
                        TactileKeypadKey(
                            label = key,
                            isSpecial = key == "00" || key == "⌫",
                            onClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                val next = applyKeypadKey(amountCentavos, key)
                                if (next != amountCentavos) {
                                    onAmountChange(next)
                                }
                            },
                            onLongClick = if (key == "⌫") {
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAmountChange(0L)
                                }
                            } else {
                                null
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }

        // Dual Action CTAs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onTapCardNfc()
                },
                enabled = amountCentavos > 0L,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CopperTerracotta,
                    contentColor = Color.White,
                    disabledContainerColor = CopperTerracotta.copy(alpha = 0.35f),
                    disabledContentColor = Color.White.copy(alpha = 0.78f)
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                modifier = Modifier.weight(0.96f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Contactless,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "Tap Card (NFC)",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            OutlinedButton(
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onQrCheckout()
                },
                enabled = amountCentavos > 0L,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    width = 1.dp,
                    color = if (amountCentavos > 0L) PrimaryInk.copy(alpha = 0.35f) else DividerHairline
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface,
                    contentColor = PrimaryInk,
                    disabledContainerColor = CardSurface.copy(alpha = 0.65f),
                    disabledContentColor = MutedInk
                ),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 14.dp),
                modifier = Modifier.weight(1.04f)
            ) {
                Icon(
                    imageVector = Icons.Outlined.QrCode2,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(5.dp))
                Text(
                    text = "QR Ph / Checkout",
                    style = MaterialTheme.typography.labelLarge.copy(fontSize = 12.5.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TactileKeypadKey(
    label: String,
    isSpecial: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 580f),
        label = "keypad_press_scale"
    )

    Surface(
        modifier = modifier
            .height(52.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(14.dp),
        color = if (isSpecial) RecessedSurface else CardSurface,
        border = BorderStroke(1.dp, DividerHairline),
        shadowElevation = if (isPressed) 0.dp else 1.dp
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (label == "⌫") {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = "Backspace",
                    tint = PrimaryInk,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = label,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = if (isSpecial) FontWeight.SemiBold else FontWeight.Bold,
                    fontSize = if (isSpecial) 17.sp else 20.sp,
                    color = PrimaryInk
                )
            }
        }
    }
}

@Composable
private fun WaitingForNfcStage(
    nfcState: NfcHardwareState,
    amountCentavos: Long,
    description: String,
    isReading: Boolean,
    tagLostMessage: String?,
    errorMessage: String?,
    onSimulateBpiTap: () -> Unit,
    onCancel: () -> Unit
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 8.dp, bottom = 122.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Honest sandbox notice badge
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = StatusAmberTint,
            border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.35f))
        ) {
            Text(
                text = "SANDBOX NFC TERMINAL · NO REAL CHARGE",
                style = TapmarkMonoCaption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = StatusAmber,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = PaymentGatewayClient.formatPhpAmount(amountCentavos),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                color = PrimaryInk
            )
            Text(
                text = description.ifBlank { "Tapmark Sale" },
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryGraphite
            )
        }

        CopperCoilCanvas(
            modifier = Modifier.widthIn(max = 285.dp),
            isReading = isReading
        )

        Text(
            text = "Hold contactless card against the center back of your phone",
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
            color = PrimaryInk,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        val inlineMessage = when {
            isReading -> Triple("Reading contactless card...", CopperTerracotta, CopperSoftTint)
            !tagLostMessage.isNullOrBlank() -> Triple(tagLostMessage, StatusAmber, StatusAmberTint)
            !errorMessage.isNullOrBlank() -> Triple(errorMessage, StatusCrimson, StatusCrimsonTint)
            else -> null
        }

        AnimatedVisibility(
            visible = inlineMessage != null,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically()
        ) {
            inlineMessage?.let { (msg, accent, bg) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(bg)
                        .border(1.dp, accent.copy(alpha = 0.32f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isReading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = accent
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(accent)
                        )
                    }
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                        color = PrimaryInk
                    )
                }
            }
        }

        if (nfcState == NfcHardwareState.DISABLED) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = StatusAmberTint,
                border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.3f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "NFC is turned off. Enable NFC or use Simulate BPI Tap below.",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryInk,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { openNfcSettingsSafely(context) }) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.labelLarge,
                            color = CopperTerracotta
                        )
                    }
                }
            }
        } else if (nfcState == NfcHardwareState.UNAVAILABLE) {
            Text(
                text = "NFC hardware unavailable — use Simulate BPI Tap to test terminal receipt flow",
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryGraphite,
                textAlign = TextAlign.Center
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onCancel,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, DividerHairline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = CardSurface,
                    contentColor = PrimaryInk
                ),
                contentPadding = PaddingValues(vertical = 12.dp),
                modifier = Modifier.weight(0.9f)
            ) {
                Text("Cancel", style = MaterialTheme.typography.labelLarge)
            }

            Button(
                onClick = onSimulateBpiTap,
                enabled = !isReading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CopperTerracotta,
                    contentColor = Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
                modifier = Modifier.weight(1.3f)
            ) {
                Text(
                    text = "Simulate BPI Tap",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun NfcReceiptStage(
    receipt: TerminalReceipt?,
    tappedCard: ScannedCard?,
    onFeedbackMessage: (String) -> Unit,
    onNewSale: () -> Unit
) {
    val context = LocalContext.current
    val stampScale = remember(receipt?.id) { Animatable(0.84f) }

    LaunchedEffect(receipt?.id) {
        stampScale.snapTo(0.84f)
        stampScale.animateTo(
            targetValue = 1f,
            animationSpec = spring(dampingRatio = 0.62f, stiffness = Spring.StiffnessMedium)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = 122.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Animated sage-green APPROVED stamp
        Surface(
            modifier = Modifier.graphicsLayer {
                scaleX = stampScale.value
                scaleY = stampScale.value
            },
            shape = RoundedCornerShape(999.dp),
            color = StatusSageTint,
            border = BorderStroke(1.5.dp, StatusSage.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.CheckCircle,
                    contentDescription = null,
                    tint = StatusSage,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "APPROVED",
                    style = MaterialTheme.typography.titleSmall.copy(
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.3.sp
                    ),
                    color = StatusSage
                )
            }
        }

        if (tappedCard != null) {
            PhysicalCardView(
                card = tappedCard,
                modifier = Modifier.widthIn(max = 360.dp)
            )
        }

        if (receipt != null) {
            PerforatedTerminalReceiptCard(receipt = receipt)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = receipt.referenceNumber,
                            text = PaymentGatewayClient.buildReceiptShareText(receipt)
                        )
                        onFeedbackMessage("Receipt copied")
                    },
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, DividerHairline),
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = CardSurface,
                        contentColor = PrimaryInk
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = SecondaryGraphite,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Receipt", style = MaterialTheme.typography.labelLarge)
                }

                Button(
                    onClick = onNewSale,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CopperTerracotta,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(vertical = 12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("New Sale", style = MaterialTheme.typography.labelLarge)
                }
            }
        } else {
            Button(
                onClick = onNewSale,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = CopperTerracotta,
                    contentColor = Color.White
                )
            ) {
                Text("New Sale", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
private fun PerforatedTerminalReceiptCard(
    receipt: TerminalReceipt,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerHairline),
        shadowElevation = 2.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Receipt Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(BlueprintSurface)
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = receipt.merchantName,
                        style = MaterialTheme.typography.titleMedium,
                        color = PrimaryInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    if (receipt.isSandboxDemo) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = StatusAmberTint,
                            border = BorderStroke(1.dp, StatusAmber.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "SANDBOX DEMO",
                                style = TapmarkMonoCaption.copy(fontSize = 9.5.sp, fontWeight = FontWeight.Bold),
                                color = StatusAmber,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Text(
                    text = PaymentGatewayClient.formatPhpAmount(receipt.amountCentavos),
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    fontSize = 28.sp,
                    color = PrimaryInk
                )

                Text(
                    text = receipt.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGraphite,
                    textAlign = TextAlign.Center
                )
            }

            // Perforated paper tear divider
            PerforatedReceiptDivider()

            // Key/Value Receipt Lines
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                ReceiptDetailRow("Reference #", receipt.referenceNumber, mono = true)
                ReceiptDetailRow("Date / Time", formatAbsoluteTimestamp(receipt.createdAtMillis))

                val bankAndNetwork = listOfNotNull(
                    receipt.bankName?.takeIf { it.isNotBlank() },
                    receipt.cardNetwork?.takeIf { it.isNotBlank() }
                ).joinToString(" · ")
                if (bankAndNetwork.isNotBlank()) {
                    ReceiptDetailRow("Bank / Network", bankAndNetwork)
                }

                receipt.panMasked?.takeIf { it.isNotBlank() }?.let {
                    ReceiptDetailRow("Masked PAN", it, mono = true)
                }
                receipt.authCode?.takeIf { it.isNotBlank() }?.let {
                    ReceiptDetailRow("Auth Code", it, mono = true)
                }
                receipt.emvAid?.takeIf { it.isNotBlank() }?.let {
                    ReceiptDetailRow("EMV AID", it, mono = true)
                }
                receipt.emvAtc?.let {
                    ReceiptDetailRow("EMV ATC", it.toString(), mono = true)
                }
                receipt.emvCryptogram?.takeIf { it.isNotBlank() }?.let {
                    ReceiptDetailRow("Cryptogram (9F26)", it, mono = true)
                }
            }
        }
    }
}

@Composable
private fun PerforatedReceiptDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cy = size.height / 2f
            val notchRadius = 7.dp.toPx()
            drawLine(
                color = DividerHairline,
                start = Offset(notchRadius + 4.dp.toPx(), cy),
                end = Offset(size.width - notchRadius - 4.dp.toPx(), cy),
                strokeWidth = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(5.dp.toPx(), 4.dp.toPx()),
                    0f
                )
            )
            // Left and right paper punch notches matching PaperCanvas
            drawCircle(color = PaperCanvas, radius = notchRadius, center = Offset(0f, cy))
            drawCircle(color = PaperCanvas, radius = notchRadius, center = Offset(size.width, cy))
        }
    }
}

@Composable
private fun ReceiptDetailRow(
    label: String,
    value: String,
    mono: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryGraphite,
            modifier = Modifier.weight(0.42f)
        )
        Text(
            text = value,
            style = if (mono) {
                TapmarkMonoBody.copy(fontSize = 12.5.sp)
            } else {
                MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium)
            },
            color = PrimaryInk,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.58f)
        )
    }
}

@Composable
private fun QrCheckoutStage(
    gatewayConfig: GatewayConfig,
    amountCentavos: Long,
    description: String,
    session: CheckoutSessionResult?,
    activeReceipt: TerminalReceipt?,
    isCreatingSession: Boolean,
    isCheckingStatus: Boolean,
    errorMessage: String?,
    onOpenGatewaySettings: () -> Unit,
    onRetrySession: () -> Unit,
    onCheckStatus: () -> Unit,
    onMarkTestPaid: () -> Unit,
    onFeedbackMessage: (String) -> Unit,
    onNewSale: () -> Unit
) {
    val context = LocalContext.current
    val providerLabel = session?.let {
        formatProviderEnvLabel(it.provider, it.environment, it.isSimulated)
    } ?: formatProviderEnvLabel(gatewayConfig.provider, gatewayConfig.environment, isSimulated = gatewayConfig.provider == GatewayProvider.DEMO_SANDBOX)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(top = 6.dp, bottom = 122.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = if (session?.isSimulated == false) StatusSageTint else StatusAmberTint,
            border = BorderStroke(
                width = 1.dp,
                color = if (session?.isSimulated == false) StatusSage.copy(alpha = 0.35f) else StatusAmber.copy(alpha = 0.35f)
            )
        ) {
            Text(
                text = providerLabel.uppercase(),
                style = TapmarkMonoCaption.copy(fontSize = 10.sp, fontWeight = FontWeight.Bold),
                color = if (session?.isSimulated == false) StatusSage else StatusAmber,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = PaymentGatewayClient.formatPhpAmount(amountCentavos),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 32.sp,
                color = PrimaryInk
            )
            Text(
                text = description.ifBlank { "Tapmark Sale" },
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryGraphite
            )
        }

        when {
            isCreatingSession -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = CardSurface,
                    border = BorderStroke(1.dp, DividerHairline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 36.dp, horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = CopperTerracotta,
                            strokeWidth = 2.6.dp,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "Creating checkout session...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = PrimaryInk
                        )
                    }
                }
            }

            !errorMessage.isNullOrBlank() -> {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = StatusCrimsonTint,
                    border = BorderStroke(1.dp, StatusCrimson.copy(alpha = 0.32f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Checkout Session Error",
                            style = MaterialTheme.typography.titleSmall,
                            color = StatusCrimson
                        )
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = PrimaryInk
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onOpenGatewaySettings,
                                shape = RoundedCornerShape(10.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = CopperTerracotta,
                                    contentColor = Color.White
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = "Edit Gateway Keys",
                                    style = MaterialTheme.typography.labelLarge,
                                    maxLines = 1
                                )
                            }
                            OutlinedButton(
                                onClick = onRetrySession,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, DividerHairline),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = CardSurface,
                                    contentColor = PrimaryInk
                                ),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text("Retry", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                }
            }

            session != null -> {
                QrCodeCanvas(
                    payload = session.qrPayload,
                    caption = if (session.isSimulated) "QR PH · SANDBOX PREVIEW" else "QR PH · CHECKOUT",
                    modifier = Modifier.widthIn(max = 264.dp)
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(15.dp),
                    color = BlueprintSurface,
                    border = BorderStroke(1.dp, DividerHairline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Ref: ${session.referenceNumber}",
                                style = TapmarkMonoCaption.copy(fontWeight = FontWeight.SemiBold),
                                color = PrimaryInk
                            )
                            activeReceipt?.let {
                                ReceiptStatusPill(status = it.status)
                            }
                        }
                        Text(
                            text = session.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryGraphite
                        )
                    }
                }

                if (session.checkoutUrl.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            val opened = openExternalUrl(context, session.checkoutUrl)
                            if (!opened) {
                                onFeedbackMessage("No browser available to open checkout URL")
                            }
                        },
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, DividerHairline),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = CardSurface,
                            contentColor = PrimaryInk
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Checkout Page", style = MaterialTheme.typography.labelLarge)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val isAlreadyPaid = activeReceipt?.status == ReceiptStatus.PAID ||
                        activeReceipt?.status == ReceiptStatus.APPROVED

                    if (!isAlreadyPaid && session.isSimulated) {
                        OutlinedButton(
                            onClick = onMarkTestPaid,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, StatusSage.copy(alpha = 0.45f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = StatusSageTint,
                                contentColor = StatusSage
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Mark Test Paid", style = MaterialTheme.typography.labelLarge)
                        }
                    } else if (!isAlreadyPaid && session.provider == GatewayProvider.PAYMONGO) {
                        OutlinedButton(
                            onClick = onCheckStatus,
                            enabled = !isCheckingStatus,
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DividerHairline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurface,
                                contentColor = PrimaryInk
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            if (isCheckingStatus) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(15.dp),
                                    strokeWidth = 2.dp,
                                    color = CopperTerracotta
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Check Status", style = MaterialTheme.typography.labelLarge)
                        }
                    } else if (isAlreadyPaid && activeReceipt != null) {
                        OutlinedButton(
                            onClick = {
                                copyTextToClipboard(
                                    context = context,
                                    label = activeReceipt.referenceNumber,
                                    text = PaymentGatewayClient.buildReceiptShareText(activeReceipt)
                                )
                                onFeedbackMessage("Receipt copied")
                            },
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, DividerHairline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = CardSurface,
                                contentColor = PrimaryInk
                            ),
                            contentPadding = PaddingValues(vertical = 12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Copy Receipt", style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    Button(
                        onClick = onNewSale,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CopperTerracotta,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(vertical = 12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Done / New Sale", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }

        if (isCreatingSession || !errorMessage.isNullOrBlank()) {
            TextButton(onClick = onNewSale) {
                Text(
                    text = "Back to Keypad",
                    style = MaterialTheme.typography.labelLarge,
                    color = SecondaryGraphite
                )
            }
        }
    }
}

@Composable
private fun TerminalLedgerStage(
    receipts: List<TerminalReceipt>,
    todayTotalCentavos: Long,
    onBackToKeypad: () -> Unit,
    onDeleteReceipt: (String) -> Unit,
    onClearAll: () -> Unit,
    onFeedbackMessage: (String) -> Unit
) {
    val context = LocalContext.current
    var selectedReceipt by remember { mutableStateOf<TerminalReceipt?>(null) }
    var showClearAllConfirm by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp,
            end = 20.dp,
            top = 6.dp,
            bottom = 122.dp
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item(key = "ledger_summary") {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = CardSurface,
                border = BorderStroke(1.dp, DividerHairline)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "TODAY'S TOTAL",
                                style = TapmarkSectionOverline,
                                color = SecondaryGraphite
                            )
                            Text(
                                text = PaymentGatewayClient.formatPhpAmount(todayTotalCentavos),
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 26.sp,
                                color = PrimaryInk
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = RecessedSurface,
                            border = BorderStroke(1.dp, DividerHairline)
                        ) {
                            Text(
                                text = "${receipts.size} ${if (receipts.size == 1) "tx" else "txs"}",
                                style = TapmarkMonoCaption.copy(fontWeight = FontWeight.SemiBold),
                                color = PrimaryInk,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = onBackToKeypad,
                            shape = RoundedCornerShape(11.dp),
                            border = BorderStroke(1.dp, DividerHairline),
                            colors = ButtonDefaults.outlinedButtonColors(
                                containerColor = BlueprintSurface,
                                contentColor = PrimaryInk
                            ),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Back to Keypad", style = MaterialTheme.typography.labelLarge)
                        }

                        if (receipts.isNotEmpty()) {
                            TextButton(
                                onClick = { showClearAllConfirm = true },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "Clear All",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = StatusCrimson
                                )
                            }
                        }
                    }
                }
            }
        }

        if (receipts.isEmpty()) {
            item(key = "ledger_empty") {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = BlueprintSurface,
                    border = BorderStroke(1.dp, DividerHairline)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ReceiptLong,
                            contentDescription = null,
                            tint = CopperTerracotta,
                            modifier = Modifier.size(30.dp)
                        )
                        Text(
                            text = "No terminal receipts yet",
                            style = MaterialTheme.typography.titleMedium,
                            color = PrimaryInk
                        )
                        Text(
                            text = "Completed NFC tap tests and QR Ph checkout sessions appear here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = SecondaryGraphite,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(
                items = receipts,
                key = { it.id }
            ) { receipt ->
                TerminalReceiptRowCard(
                    receipt = receipt,
                    onClick = { selectedReceipt = receipt }
                )
            }
        }
    }

    selectedReceipt?.let { receipt ->
        AlertDialog(
            onDismissRequest = { selectedReceipt = null },
            containerColor = CardSurface,
            titleContentColor = PrimaryInk,
            textContentColor = SecondaryGraphite,
            shape = RoundedCornerShape(18.dp),
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = receipt.referenceNumber,
                        style = TapmarkMonoBody.copy(fontWeight = FontWeight.Bold, fontSize = 15.sp),
                        color = PrimaryInk
                    )
                    ReceiptStatusPill(status = receipt.status)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = PaymentGatewayClient.formatPhpAmount(receipt.amountCentavos),
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp,
                        color = PrimaryInk
                    )
                    HorizontalDivider(color = DividerHairline)
                    ReceiptDetailRow("Merchant", receipt.merchantName)
                    ReceiptDetailRow("Description", receipt.description)
                    ReceiptDetailRow("Date / Time", formatAbsoluteTimestamp(receipt.createdAtMillis))
                    ReceiptDetailRow(
                        "Method",
                        if (receipt.methodMode == PaymentMethodMode.NFC_TAP_DEMO) "NFC Tap Demo" else "QR Ph Checkout"
                    )
                    receipt.bankName?.takeIf { it.isNotBlank() }?.let {
                        ReceiptDetailRow("Bank", it)
                    }
                    receipt.panMasked?.takeIf { it.isNotBlank() }?.let {
                        ReceiptDetailRow("Card", it, mono = true)
                    }
                    receipt.authCode?.takeIf { it.isNotBlank() }?.let {
                        ReceiptDetailRow("Auth Code", it, mono = true)
                    }
                    receipt.emvAid?.takeIf { it.isNotBlank() }?.let {
                        ReceiptDetailRow("EMV AID", it, mono = true)
                    }
                    receipt.emvAtc?.let {
                        ReceiptDetailRow("EMV ATC", it.toString(), mono = true)
                    }
                    receipt.emvCryptogram?.takeIf { it.isNotBlank() }?.let {
                        ReceiptDetailRow("Cryptogram", it, mono = true)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        copyTextToClipboard(
                            context = context,
                            label = receipt.referenceNumber,
                            text = PaymentGatewayClient.buildReceiptShareText(receipt)
                        )
                        onFeedbackMessage("Receipt copied")
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CopperTerracotta,
                        contentColor = Color.White
                    )
                ) {
                    Text("Copy", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            val id = receipt.id
                            selectedReceipt = null
                            onDeleteReceipt(id)
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.DeleteOutline,
                            contentDescription = null,
                            tint = StatusCrimson,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Delete", style = MaterialTheme.typography.labelLarge, color = StatusCrimson)
                    }
                    TextButton(onClick = { selectedReceipt = null }) {
                        Text("Close", style = MaterialTheme.typography.labelLarge, color = SecondaryGraphite)
                    }
                }
            }
        )
    }

    if (showClearAllConfirm) {
        AlertDialog(
            onDismissRequest = { showClearAllConfirm = false },
            containerColor = CardSurface,
            titleContentColor = PrimaryInk,
            textContentColor = SecondaryGraphite,
            shape = RoundedCornerShape(18.dp),
            title = {
                Text("Clear Terminal Receipts?", style = MaterialTheme.typography.titleLarge)
            },
            text = {
                Text(
                    text = "All ${receipts.size} terminal receipt records will be removed from this device.",
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearAllConfirm = false
                        onClearAll()
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StatusCrimson,
                        contentColor = Color.White
                    )
                ) {
                    Text("Clear All", style = MaterialTheme.typography.labelLarge)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearAllConfirm = false }) {
                    Text("Cancel", style = MaterialTheme.typography.labelLarge, color = SecondaryGraphite)
                }
            }
        )
    }
}

@Composable
private fun TerminalReceiptRowCard(
    receipt: TerminalReceipt,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(15.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerHairline)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            ReceiptMethodEmblem(receipt = receipt)

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = receipt.description.ifBlank { "Tapmark Sale" },
                        style = MaterialTheme.typography.titleSmall,
                        color = PrimaryInk,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    ReceiptStatusPill(status = receipt.status)
                }

                val methodText = receipt.panMasked?.takeIf { it.isNotBlank() }
                    ?: "QR Ph · ${receipt.provider.shortDisplayName()}"
                Text(
                    text = methodText,
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGraphite,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = receipt.referenceNumber,
                        style = TapmarkMonoCaption,
                        color = MutedInk
                    )
                    Text("·", style = TapmarkMonoCaption, color = MutedInk)
                    Text(
                        text = formatRelativeTimestamp(receipt.createdAtMillis),
                        style = MaterialTheme.typography.labelSmall,
                        color = MutedInk
                    )
                }
            }

            Text(
                text = PaymentGatewayClient.formatPhpAmount(receipt.amountCentavos),
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                fontSize = 14.5.sp,
                color = PrimaryInk
            )
        }
    }
}

@Composable
private fun ReceiptMethodEmblem(receipt: TerminalReceipt) {
    if (receipt.methodMode == PaymentMethodMode.NFC_TAP_DEMO) {
        val swatchColor = resolveReceiptBankSwatchColor(receipt)
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(swatchColor)
                .border(1.dp, DividerHairline, RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .padding(start = 6.dp)
                    .size(width = 8.dp, height = 6.dp)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(Color(0xFFE0BE6E))
            )
        }
    } else {
        Box(
            modifier = Modifier
                .size(width = 42.dp, height = 30.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(CopperSoftTint)
                .border(1.dp, CopperTerracotta.copy(alpha = 0.28f), RoundedCornerShape(7.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Outlined.QrCode2,
                contentDescription = null,
                tint = CopperTerracotta,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun ReceiptStatusPill(status: ReceiptStatus) {
    val (label, bgColor, textColor) = when (status) {
        ReceiptStatus.APPROVED -> Triple("APPROVED", StatusSageTint, StatusSage)
        ReceiptStatus.PAID -> Triple("PAID", StatusSageTint, StatusSage)
        ReceiptStatus.PENDING -> Triple("PENDING", StatusAmberTint, StatusAmber)
        ReceiptStatus.FAILED -> Triple("FAILED", StatusCrimsonTint, StatusCrimson)
        ReceiptStatus.CANCELLED -> Triple("CANCELLED", RecessedSurface, SecondaryGraphite)
    }

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .padding(horizontal = 7.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = TapmarkMonoCaption.copy(fontSize = 9.sp, fontWeight = FontWeight.Bold),
            color = textColor
        )
    }
}

@Composable
private fun GatewaySettingsDialog(
    initialConfig: GatewayConfig,
    onDismiss: () -> Unit,
    onSave: (GatewayConfig) -> Unit
) {
    var provider by remember(initialConfig) { mutableStateOf(initialConfig.provider) }
    var environment by remember(initialConfig) { mutableStateOf(initialConfig.environment) }
    var merchantName by remember(initialConfig) { mutableStateOf(initialConfig.merchantName) }
    var payMongoKey by remember(initialConfig) { mutableStateOf(initialConfig.payMongoApiKey) }
    var mayaKey by remember(initialConfig) { mutableStateOf(initialConfig.mayaPublicKey) }
    var customBackendUrl by remember(initialConfig) { mutableStateOf(initialConfig.customBackendUrl) }
    var revealPayMongoKey by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            shape = RoundedCornerShape(22.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, DividerHairline),
            shadowElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Gateway Settings",
                    style = MaterialTheme.typography.titleLarge,
                    color = PrimaryInk
                )

                Text(
                    text = "PROVIDER",
                    style = TapmarkSectionOverline,
                    color = SecondaryGraphite
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        Triple(GatewayProvider.DEMO_SANDBOX, "Sandbox Demo", 1.25f),
                        Triple(GatewayProvider.PAYMONGO, "PayMongo", 1.0f),
                        Triple(GatewayProvider.MAYA, "Maya", 0.78f)
                    ).forEach { (item, label, weightValue) ->
                        val selected = provider == item
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) CopperTerracotta else RecessedSurface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) CopperTerracotta else DividerHairline
                            ),
                            modifier = Modifier
                                .weight(weightValue)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { provider = item }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color = if (selected) Color.White else PrimaryInk,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 9.dp)
                            )
                        }
                    }
                }

                Text(
                    text = "ENVIRONMENT",
                    style = TapmarkSectionOverline,
                    color = SecondaryGraphite
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        GatewayEnvironment.SANDBOX to "Sandbox / Test",
                        GatewayEnvironment.LIVE to "Live"
                    ).forEach { (env, label) ->
                        val selected = environment == env
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selected) CopperSoftTint else RecessedSurface,
                            border = BorderStroke(
                                1.dp,
                                if (selected) CopperTerracotta else DividerHairline
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { environment = env }
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                ),
                                color = if (selected) CopperTerracotta else PrimaryInk,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = merchantName,
                    onValueChange = { if (it.length <= 48) merchantName = it },
                    label = { Text("Merchant Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    colors = terminalTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = payMongoKey,
                    onValueChange = { payMongoKey = it },
                    label = { Text("PayMongo API Key (sk_test_... / sk_live_...)") },
                    singleLine = true,
                    visualTransformation = if (revealPayMongoKey) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation()
                    },
                    trailingIcon = {
                        IconButton(onClick = { revealPayMongoKey = !revealPayMongoKey }) {
                            Icon(
                                imageVector = if (revealPayMongoKey) {
                                    Icons.Outlined.VisibilityOff
                                } else {
                                    Icons.Outlined.Visibility
                                },
                                contentDescription = if (revealPayMongoKey) "Hide API Key" else "Show API Key",
                                tint = SecondaryGraphite
                            )
                        }
                    },
                    textStyle = TapmarkMonoBody.copy(color = PrimaryInk),
                    shape = RoundedCornerShape(12.dp),
                    colors = terminalTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = mayaKey,
                    onValueChange = { mayaKey = it },
                    label = { Text("Maya Public Key (pk-...)") },
                    singleLine = true,
                    textStyle = TapmarkMonoBody.copy(color = PrimaryInk),
                    shape = RoundedCornerShape(12.dp),
                    colors = terminalTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = customBackendUrl,
                    onValueChange = { customBackendUrl = it },
                    label = { Text("Custom Backend Proxy URL (optional)") },
                    singleLine = true,
                    textStyle = TapmarkMonoBody.copy(color = PrimaryInk),
                    shape = RoundedCornerShape(12.dp),
                    colors = terminalTextFieldColors(),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    color = BlueprintSurface,
                    border = BorderStroke(1.dp, DividerHairline)
                ) {
                    Text(
                        text = "PayMongo and Maya Sandbox developer accounts are ₱0/month with free test QR Ph & e-wallet checkout keys. Note: live NFC card acquiring requires a certified PCI MPoC / SoftPOS kernel.",
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryGraphite,
                        modifier = Modifier.padding(12.dp)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel", style = MaterialTheme.typography.labelLarge, color = SecondaryGraphite)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            onSave(
                                GatewayConfig(
                                    provider = provider,
                                    environment = environment,
                                    merchantName = merchantName.trim().ifBlank { "Tapmark Terminal" },
                                    payMongoApiKey = payMongoKey.trim(),
                                    mayaPublicKey = mayaKey.trim(),
                                    customBackendUrl = customBackendUrl.trim()
                                )
                            )
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CopperTerracotta,
                            contentColor = Color.White
                        )
                    ) {
                        Text("Save", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun terminalTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = CopperTerracotta,
    unfocusedBorderColor = DividerHairline,
    focusedContainerColor = BlueprintSurface,
    unfocusedContainerColor = BlueprintSurface,
    cursorColor = CopperTerracotta,
    focusedTextColor = PrimaryInk,
    unfocusedTextColor = PrimaryInk,
    focusedLabelColor = CopperTerracotta,
    unfocusedLabelColor = SecondaryGraphite
)

private fun applyKeypadKey(currentCentavos: Long, key: String): Long = when (key) {
    "⌫" -> currentCentavos / 10L
    "00" -> {
        if (currentCentavos == 0L) {
            0L
        } else {
            val next = currentCentavos * 100L
            if (next <= MAX_CENTAVOS) next else currentCentavos
        }
    }

    else -> {
        val digit = key.toLongOrNull() ?: return currentCentavos
        if (currentCentavos == 0L && digit == 0L) {
            0L
        } else {
            val next = currentCentavos * 10L + digit
            if (next <= MAX_CENTAVOS) next else currentCentavos
        }
    }
}

private data class GatewayPillStyle(
    val label: String,
    val dotColor: Color,
    val bgColor: Color,
    val borderColor: Color
)

private fun resolveGatewayPillStyle(config: GatewayConfig): GatewayPillStyle {
    return when (config.provider) {
        GatewayProvider.DEMO_SANDBOX -> GatewayPillStyle(
            label = "Sandbox Demo",
            dotColor = StatusAmber,
            bgColor = StatusAmberTint,
            borderColor = StatusAmber.copy(alpha = 0.28f)
        )

        GatewayProvider.PAYMONGO -> {
            val isLive = config.environment == GatewayEnvironment.LIVE
            val hasKey = config.payMongoApiKey.isNotBlank() || config.customBackendUrl.isNotBlank()
            GatewayPillStyle(
                label = if (isLive) "PayMongo Live" else "PayMongo Test",
                dotColor = if (isLive && hasKey) StatusSage else StatusAmber,
                bgColor = if (isLive && hasKey) StatusSageTint else StatusAmberTint,
                borderColor = if (isLive && hasKey) {
                    StatusSage.copy(alpha = 0.28f)
                } else {
                    StatusAmber.copy(alpha = 0.28f)
                }
            )
        }

        GatewayProvider.MAYA -> {
            val isLive = config.environment == GatewayEnvironment.LIVE
            val hasKey = config.mayaPublicKey.isNotBlank() || config.customBackendUrl.isNotBlank()
            GatewayPillStyle(
                label = if (isLive) "Maya Live" else "Maya Sandbox",
                dotColor = if (isLive && hasKey) StatusSage else StatusAmber,
                bgColor = if (isLive && hasKey) StatusSageTint else StatusAmberTint,
                borderColor = if (isLive && hasKey) {
                    StatusSage.copy(alpha = 0.28f)
                } else {
                    StatusAmber.copy(alpha = 0.28f)
                }
            )
        }
    }
}

private fun resolveTerminalMicroTag(config: GatewayConfig): String = when (config.provider) {
    GatewayProvider.DEMO_SANDBOX -> "SANDBOX TERMINAL"
    GatewayProvider.PAYMONGO -> if (config.environment == GatewayEnvironment.LIVE) {
        "PAYMONGO LIVE"
    } else {
        "PAYMONGO SANDBOX"
    }
    GatewayProvider.MAYA -> if (config.environment == GatewayEnvironment.LIVE) {
        "MAYA LIVE"
    } else {
        "MAYA SANDBOX"
    }
}

private fun formatProviderEnvLabel(
    provider: GatewayProvider,
    environment: GatewayEnvironment,
    isSimulated: Boolean
): String {
    if (isSimulated || provider == GatewayProvider.DEMO_SANDBOX) {
        return "Sandbox Demo · QR Ph Preview"
    }
    return when (provider) {
        GatewayProvider.PAYMONGO -> if (environment == GatewayEnvironment.LIVE) "PayMongo Live" else "PayMongo Test"
        GatewayProvider.MAYA -> if (environment == GatewayEnvironment.LIVE) "Maya Live" else "Maya Sandbox"
        GatewayProvider.DEMO_SANDBOX -> "Sandbox Demo"
    }
}

private fun GatewayProvider.shortDisplayName(): String = when (this) {
    GatewayProvider.DEMO_SANDBOX -> "Sandbox"
    GatewayProvider.PAYMONGO -> "PayMongo"
    GatewayProvider.MAYA -> "Maya"
}

private fun resolveReceiptBankSwatchColor(receipt: TerminalReceipt): Color {
    val bank = receipt.bankName.orEmpty().lowercase()
    return when {
        bank.contains("bpi") || bank.contains("philippine islands") -> Color(0xFF7A1626)
        bank.contains("bdo") -> Color(0xFF0F3570)
        bank.contains("gcash") -> Color(0xFF0057C2)
        bank.contains("maya") -> Color(0xFF141817)
        bank.contains("gotyme") -> Color(0xFF0D4A52)
        else -> (receipt.cardVisualStyle ?: CardVisualStyle.VISA_SLATE).toPalette().baseColor
    }
}

private fun openNfcSettingsSafely(context: Context) {
    val primary = Intent(Settings.ACTION_NFC_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    val fallback = Intent(Settings.ACTION_WIRELESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(primary)
    } catch (_: Throwable) {
        try {
            context.startActivity(fallback)
        } catch (_: Throwable) {
        }
    }
}
