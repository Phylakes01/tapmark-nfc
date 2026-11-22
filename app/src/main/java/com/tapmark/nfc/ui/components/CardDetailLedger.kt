package com.tapmark.nfc.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tapmark.nfc.model.EmvCardInfo
import com.tapmark.nfc.model.EmvTxLog
import com.tapmark.nfc.model.ParsedNdefRecord
import com.tapmark.nfc.model.RfTechDetails
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.model.TlvNode
import com.tapmark.nfc.ui.theme.CardSurface
import com.tapmark.nfc.ui.theme.CopperSoftTint
import com.tapmark.nfc.ui.theme.CopperTerracotta
import com.tapmark.nfc.ui.theme.DividerHairline
import com.tapmark.nfc.ui.theme.MutedInk
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.RecessedSurface
import com.tapmark.nfc.ui.theme.SecondaryGraphite
import com.tapmark.nfc.ui.theme.StatusAmber
import com.tapmark.nfc.ui.theme.StatusSage
import com.tapmark.nfc.ui.theme.TapmarkMonoBody
import com.tapmark.nfc.ui.theme.TapmarkMonoCaption
import com.tapmark.nfc.ui.theme.TapmarkSectionOverline
import kotlinx.coroutines.delay

@Composable
fun CardDetailLedger(
    card: ScannedCard,
    revealPan: Boolean,
    onFeedbackMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var sectionIndex = 0

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // 1. Card Details
        StaggeredLedgerEntrance(cardId = card.id, index = sectionIndex++) {
            LedgerSection(title = "Card Details") {
                LedgerKeyValueRow(label = "Category", value = card.category.displayName())
                LedgerDivider()
                LedgerKeyValueRow(label = "Chip / Protocol", value = card.chipGuess)
                LedgerDivider()
                LedgerKeyValueRow(label = "Manufacturer", value = card.manufacturer)
                LedgerDivider()
                LedgerKeyValueRow(
                    label = if (card.isSample) "Sample Timestamp" else "Captured",
                    value = formatAbsoluteTimestamp(card.scannedAtMillis)
                )
            }
        }

        // 2. Payment / EMV Info + 3. Dedicated Transaction History
        card.emvInfo?.let { emv ->
            StaggeredLedgerEntrance(cardId = card.id, index = sectionIndex++) {
                EmvLedgerSection(
                    emv = emv,
                    revealPan = revealPan
                )
            }

            StaggeredLedgerEntrance(cardId = card.id, index = sectionIndex++) {
                TransactionHistorySection(emv = emv)
            }
        }

        // 4. NDEF Records
        if (card.ndefRecords.isNotEmpty()) {
            StaggeredLedgerEntrance(cardId = card.id, index = sectionIndex++) {
                LedgerSection(
                    title = "NDEF Records",
                    trailingMeta = "${card.ndefRecords.size}"
                ) {
                    card.ndefRecords.forEachIndexed { index, record ->
                        if (index > 0) {
                            LedgerDivider()
                        }
                        NdefRecordRow(
                            record = record,
                            onCopy = {
                                copyTextToClipboard(context, record.title, record.value)
                                onFeedbackMessage("Copied NDEF value")
                            },
                            onOpenUrl = { url ->
                                val opened = openExternalUrl(context, url)
                                if (!opened) {
                                    onFeedbackMessage("No app available to open this URL")
                                }
                            }
                        )
                    }
                }
            }
        }

        // 5. Hardware & RF Specs
        StaggeredLedgerEntrance(cardId = card.id, index = sectionIndex++) {
            RfSpecsLedgerSection(
                card = card,
                rf = card.rfDetails,
                onCopyUid = {
                    copyTextToClipboard(context, "NFC UID", card.uidHex)
                    onFeedbackMessage("UID copied to clipboard")
                }
            )
        }

        // 6. Memory ASCII Preview
        card.memoryInfo?.asciiPreview?.takeIf { it.isNotBlank() }?.let { ascii ->
            StaggeredLedgerEntrance(cardId = card.id, index = sectionIndex++) {
                LedgerSection(title = "Memory ASCII Preview") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(RecessedSurface)
                            .border(1.dp, DividerHairline, RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = ascii,
                            style = TapmarkMonoBody,
                            color = PrimaryInk
                        )
                    }
                }
            }
        }

        // 7. Collapsed-by-default Raw Technical Dump
        val pages = card.memoryInfo?.pagesHex.orEmpty()
        if (card.rawTlvEntries.isNotEmpty() || pages.isNotEmpty()) {
            StaggeredLedgerEntrance(cardId = card.id, index = sectionIndex) {
                RawTechnicalDumpSection(
                    tlvEntries = card.rawTlvEntries,
                    pagesHex = pages
                )
            }
        }
    }
}

@Composable
private fun StaggeredLedgerEntrance(
    cardId: String,
    index: Int,
    content: @Composable () -> Unit
) {
    val progress = remember(cardId, index) { Animatable(0f) }

    LaunchedEffect(cardId, index) {
        progress.snapTo(0f)
        val staggerDelayMs = (index * 45L).coerceAtMost(270L)
        if (staggerDelayMs > 0L) {
            delay(staggerDelayMs)
        }
        progress.animateTo(
            targetValue = 1f,
            animationSpec = tween(
                durationMillis = 270,
                easing = FastOutSlowInEasing
            )
        )
    }

    val p = progress.value
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = p
                translationY = (1f - p) * 18.dp.toPx()
            }
    ) {
        content()
    }
}

@Composable
private fun EmvLedgerSection(
    emv: EmvCardInfo,
    revealPan: Boolean
) {
    val typeTier = listOfNotNull(
        emv.cardType?.takeIf { it.isNotBlank() },
        emv.cardTier?.takeIf { it.isNotBlank() }
    ).joinToString(" · ")

    val panText = if (revealPan && !emv.panFull.isNullOrBlank()) {
        formatPanGroups(emv.panFull)
    } else {
        emv.panMasked
    }

    val rows = buildList {
        emv.bankName?.takeIf { it.isNotBlank() }?.let {
            add(LedgerRowSpec("Issuing Bank", it, isMono = false, valueColor = PrimaryInk))
        }
        add(LedgerRowSpec("Network", emv.network, isMono = false, valueColor = PrimaryInk))
        if (typeTier.isNotEmpty()) {
            add(LedgerRowSpec("Card Type / Tier", typeTier, isMono = false, valueColor = PrimaryInk))
        }
        emv.bin?.takeIf { it.isNotBlank() }?.let {
            add(LedgerRowSpec("BIN / IIN", it, isMono = true, valueColor = PrimaryInk))
        }
        emv.appLabel?.takeIf { it.isNotBlank() }?.let {
            add(LedgerRowSpec("App Label", it, isMono = false, valueColor = PrimaryInk))
        }
        add(LedgerRowSpec("AID", emv.aid, isMono = true, valueColor = PrimaryInk))
        add(LedgerRowSpec("Card Number", panText, isMono = true, valueColor = PrimaryInk, animateValue = true))

        emv.expiryFormatted?.let {
            add(LedgerRowSpec("Expiry", it, isMono = true, valueColor = PrimaryInk))
        }
        emv.effectiveFormatted?.let {
            add(LedgerRowSpec("Effective", it, isMono = true, valueColor = PrimaryInk))
        }
        emv.cardholderName?.takeIf { it.isNotBlank() }?.let {
            add(LedgerRowSpec("Cardholder", it, isMono = false, valueColor = PrimaryInk))
        }
        emv.serviceCode?.let {
            add(LedgerRowSpec("Service Code", it, isMono = true, valueColor = PrimaryInk))
        }
        emv.issuerCountry?.let {
            add(LedgerRowSpec("Issuer Country", it, isMono = false, valueColor = PrimaryInk))
        }
        emv.currency?.let {
            add(LedgerRowSpec("Currency", it, isMono = false, valueColor = PrimaryInk))
        }
        emv.atc?.let {
            add(LedgerRowSpec("App Tx Counter", it.toString(), isMono = true, valueColor = PrimaryInk))
        }
        emv.lastOnlineAtc?.let {
            add(LedgerRowSpec("Last Online ATC", it.toString(), isMono = true, valueColor = PrimaryInk))
        }
        emv.pinTryCounter?.let {
            add(LedgerRowSpec("PIN Tries Left", it.toString(), isMono = true, valueColor = PrimaryInk))
        }
        emv.luhnValid?.let { valid ->
            add(
                LedgerRowSpec(
                    label = "Luhn Check",
                    value = if (valid) "Valid" else "Check Failed",
                    isMono = false,
                    valueColor = if (valid) StatusSage else StatusAmber
                )
            )
        }
    }

    LedgerSection(title = "Payment / EMV Info") {
        rows.forEachIndexed { index, spec ->
            if (index > 0) LedgerDivider()
            LedgerKeyValueRow(
                label = spec.label,
                value = spec.value,
                isMonospace = spec.isMono,
                valueColor = spec.valueColor,
                animateValue = spec.animateValue
            )
        }
    }
}

@Composable
private fun TransactionHistorySection(emv: EmvCardInfo) {
    val hasLogs = emv.transactionLogs.isNotEmpty()
    val badgeText = when (emv.transactionLogs.size) {
        0 -> "0 ON CHIP"
        1 -> "1 RECORD"
        else -> "${emv.transactionLogs.size} RECORDS"
    }

    LedgerSection(
        title = "Transaction History",
        trailingMeta = badgeText,
        accentTrailingMeta = hasLogs
    ) {
        if (hasLogs) {
            emv.transactionLogs.forEachIndexed { idx, log ->
                if (idx > 0) {
                    LedgerDivider()
                }
                TransactionLogRow(log = log)
            }

            emv.logStatusMessage?.takeIf { it.isNotBlank() }?.let { statusMsg ->
                LedgerDivider()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(RecessedSurface.copy(alpha = 0.55f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(CopperTerracotta)
                    )
                    Text(
                        text = statusMsg,
                        style = MaterialTheme.typography.bodySmall,
                        color = SecondaryGraphite
                    )
                }
            }
        } else {
            val diagnosticMessage = emv.logStatusMessage?.takeIf { it.isNotBlank() }
                ?: "This bank chip does not expose itemized transaction logs over contactless NFC"

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(StatusAmber)
                    )
                    Text(
                        text = diagnosticMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = PrimaryInk
                    )
                }

                if (emv.atc != null || emv.lastOnlineAtc != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(RecessedSurface)
                            .border(1.dp, DividerHairline, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        emv.atc?.let { atc ->
                            Column {
                                Text(
                                    text = "LIFETIME CHIP TAPS (ATC)",
                                    style = TapmarkSectionOverline,
                                    color = MutedInk
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$atc transaction(s)",
                                    style = TapmarkMonoBody.copy(fontWeight = FontWeight.Medium),
                                    color = PrimaryInk
                                )
                            }
                        }

                        emv.lastOnlineAtc?.let { lastOnline ->
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "LAST ONLINE",
                                    style = TapmarkSectionOverline,
                                    color = MutedInk
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "#$lastOnline",
                                    style = TapmarkMonoBody.copy(fontWeight = FontWeight.Medium),
                                    color = SecondaryGraphite
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TransactionLogRow(log: EmvTxLog) {
    val statusDotColor = resolveTxStatusColor(log)
    val primaryTitle = log.merchantName?.takeIf { it.isNotBlank() }
        ?: log.txType?.takeIf { it.isNotBlank() }
        ?: "Card Transaction"

    val metaParts = buildList {
        if (log.date.isNotBlank()) add(log.date)
        log.time?.takeIf { it.isNotBlank() }?.let { add(it) }
        log.country?.takeIf { it.isNotBlank() }?.let { add(it) }
        if (!log.merchantName.isNullOrBlank() && !log.txType.isNullOrBlank()) {
            add(log.txType)
        }
        log.cryptogramInfo?.takeIf { it.isNotBlank() }?.let { add(it) }
        log.atc?.let { add("#$it") }
    }

    val amountText = "${log.currencySymbol.orEmpty()}${log.amount}"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 6.dp)
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(statusDotColor)
            )

            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = primaryTitle,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = PrimaryInk,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (metaParts.isNotEmpty()) {
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = TapmarkMonoCaption,
                        color = SecondaryGraphite
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = amountText,
                style = TapmarkMonoBody.copy(fontWeight = FontWeight.SemiBold),
                color = PrimaryInk,
                textAlign = TextAlign.End
            )
            if (log.currency.isNotBlank()) {
                Text(
                    text = log.currency,
                    style = TapmarkMonoCaption,
                    color = MutedInk,
                    textAlign = TextAlign.End
                )
            }
        }
    }
}

private fun resolveTxStatusColor(log: EmvTxLog): Color {
    val crypto = log.cryptogramInfo.orEmpty()
    val txType = log.txType.orEmpty()
    return when {
        crypto.contains("Declined", ignoreCase = true) ||
            txType.equals("Refund", ignoreCase = true) -> StatusAmber

        crypto.contains("Online", ignoreCase = true) ||
            txType.contains("Cash", ignoreCase = true) ||
            txType.equals("Transfer", ignoreCase = true) -> CopperTerracotta

        crypto.contains("Offline", ignoreCase = true) ||
            crypto.contains("Approved", ignoreCase = true) ||
            txType.equals("Purchase", ignoreCase = true) -> StatusSage

        else -> SecondaryGraphite
    }
}

@Composable
private fun RfSpecsLedgerSection(
    card: ScannedCard,
    rf: RfTechDetails,
    onCopyUid: () -> Unit
) {
    LedgerSection(title = "Hardware & RF Specs") {
        // UID row with 1-tap Copy UID action
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "UID (Hex)",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGraphite
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = card.uidHex,
                    style = TapmarkMonoBody.copy(fontWeight = FontWeight.Medium),
                    color = PrimaryInk
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            CompactPillButton(
                label = "Copy UID",
                onClick = onCopyUid,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = CopperTerracotta,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )
        }

        LedgerDivider()
        LedgerKeyValueRow(
            label = "Reversed UID",
            value = card.uidReversedHex,
            isMonospace = true
        )
        LedgerDivider()
        LedgerKeyValueRow(
            label = "Decimal UID",
            value = card.uidDecimal,
            isMonospace = true
        )

        if (rf.technologies.isNotEmpty()) {
            LedgerDivider()
            LedgerKeyValueRow(
                label = "Technologies",
                value = rf.technologies.joinToString(", ")
            )
        }

        rf.atqaHex?.let {
            LedgerDivider()
            LedgerKeyValueRow(label = "ATQA", value = it, isMonospace = true)
        }
        rf.sakHex?.let {
            LedgerDivider()
            LedgerKeyValueRow(label = "SAK", value = it, isMonospace = true)
        }
        rf.historicalBytesHex?.let {
            LedgerDivider()
            LedgerKeyValueRow(label = "Historical Bytes", value = it, isMonospace = true)
        }
        rf.maxTransceiveBytes?.let {
            LedgerDivider()
            LedgerKeyValueRow(
                label = "Max Transceive",
                value = formatByteCount(it),
                isMonospace = true
            )
        }

        val memoryLayout = buildMemoryLayoutSummary(card, rf)
        if (memoryLayout != null) {
            LedgerDivider()
            LedgerKeyValueRow(
                label = "Memory",
                value = memoryLayout,
                isMonospace = false
            )
        }

        if (rf.ndefCapacityBytes != null) {
            LedgerDivider()
            val used = rf.ndefUsedBytes ?: 0
            val writableLabel = when (rf.isWritable) {
                true -> " · Writable"
                false -> " · Read-Only"
                null -> ""
            }
            LedgerKeyValueRow(
                label = "NDEF Capacity",
                value = "$used / ${rf.ndefCapacityBytes} B$writableLabel",
                isMonospace = true
            )
        }

        if (card.readNotes.isNotEmpty()) {
            LedgerDivider()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = "Read Notes",
                    style = MaterialTheme.typography.bodySmall,
                    color = SecondaryGraphite
                )
                card.readNotes.forEach { note ->
                    Text(
                        text = "• $note",
                        style = MaterialTheme.typography.bodySmall,
                        color = PrimaryInk
                    )
                }
            }
        }
    }
}

private fun buildMemoryLayoutSummary(card: ScannedCard, rf: RfTechDetails): String? {
    val parts = mutableListOf<String>()
    rf.memoryBytes?.let { parts.add(formatByteCount(it)) }
    val sectors = rf.sectorCount
    val blocks = rf.blockCount
    if (sectors != null && blocks != null) {
        parts.add("$sectors sectors · $blocks blocks")
    } else if (sectors != null) {
        parts.add("$sectors sectors")
    } else if (blocks != null) {
        parts.add("$blocks blocks")
    }
    val detail = when (parts.size) {
        0 -> null
        1 -> parts[0]
        else -> "${parts[0]} (${parts[1]})"
    }
    val summary = card.memoryInfo?.summary?.takeIf { it.isNotBlank() }
    return when {
        detail != null && summary != null && !summary.equals(detail, ignoreCase = true) -> "$detail · $summary"
        detail != null -> detail
        else -> summary
    }
}

@Composable
private fun NdefRecordRow(
    record: ParsedNdefRecord,
    onCopy: () -> Unit,
    onOpenUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(5.dp))
                        .background(CopperSoftTint)
                        .padding(horizontal = 7.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = record.typeLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = CopperTerracotta
                    )
                }
                Text(
                    text = record.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = PrimaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${record.byteSize} B",
                style = TapmarkMonoCaption,
                color = MutedInk
            )
        }

        Text(
            text = record.value,
            style = MaterialTheme.typography.bodyMedium,
            color = PrimaryInk
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CompactPillButton(
                label = "Copy",
                onClick = onCopy,
                icon = {
                    Icon(
                        imageVector = Icons.Outlined.ContentCopy,
                        contentDescription = null,
                        tint = CopperTerracotta,
                        modifier = Modifier.size(14.dp)
                    )
                }
            )

            record.actionUrl?.takeIf { it.isNotBlank() }?.let { url ->
                CompactPillButton(
                    label = "Open URL",
                    onClick = { onOpenUrl(url) },
                    filledAccent = true,
                    icon = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.OpenInNew,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun RawTechnicalDumpSection(
    tlvEntries: List<TlvNode>,
    pagesHex: List<String>
) {
    var expanded by remember { mutableStateOf(false) }

    val metaParts = buildList {
        if (tlvEntries.isNotEmpty()) add("${tlvEntries.size} TLV")
        if (pagesHex.isNotEmpty()) add("${pagesHex.size} pages")
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = CardSurface,
        border = BorderStroke(1.dp, DividerHairline)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RAW TECHNICAL DUMP",
                        style = TapmarkSectionOverline,
                        color = SecondaryGraphite
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = metaParts.joinToString(" · "),
                        style = TapmarkMonoCaption,
                        color = MutedInk
                    )
                }

                Icon(
                    imageVector = if (expanded) {
                        Icons.Outlined.KeyboardArrowUp
                    } else {
                        Icons.Outlined.KeyboardArrowDown
                    },
                    contentDescription = if (expanded) "Collapse raw technical dump" else "Expand raw technical dump",
                    tint = SecondaryGraphite
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    if (tlvEntries.isNotEmpty()) {
                        LedgerDivider()
                        Column(modifier = Modifier.fillMaxWidth()) {
                            tlvEntries.forEachIndexed { idx, node ->
                                if (idx > 0) LedgerDivider()
                                TlvRow(node = node)
                            }
                        }
                    }

                    if (pagesHex.isNotEmpty()) {
                        LedgerDivider()
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "MEMORY PAGES (HEX)",
                                style = TapmarkSectionOverline,
                                color = MutedInk
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(RecessedSurface)
                                    .border(1.dp, DividerHairline, RoundedCornerShape(10.dp))
                                    .horizontalScroll(rememberScrollState())
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = pagesHex.joinToString("\n"),
                                    style = TapmarkMonoBody,
                                    color = PrimaryInk
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TlvRow(node: TlvNode) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = node.tagHex,
                    style = TapmarkMonoCaption.copy(fontWeight = FontWeight.SemiBold),
                    color = CopperTerracotta
                )
                Text(
                    text = node.name,
                    style = MaterialTheme.typography.labelLarge,
                    color = PrimaryInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (node.displayValue.isNotBlank()) {
            Text(
                text = node.displayValue,
                style = MaterialTheme.typography.bodySmall,
                color = SecondaryGraphite
            )
        }
        if (node.valueHex.isNotBlank()) {
            Text(
                text = node.valueHex,
                style = TapmarkMonoCaption,
                color = MutedInk
            )
        }
    }
}

@Composable
private fun LedgerSection(
    title: String,
    trailingMeta: String? = null,
    accentTrailingMeta: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                style = TapmarkSectionOverline,
                color = SecondaryGraphite
            )
            if (trailingMeta != null) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(if (accentTrailingMeta) CopperSoftTint else RecessedSurface)
                        .border(
                            width = 0.8.dp,
                            color = if (accentTrailingMeta) {
                                CopperTerracotta.copy(alpha = 0.25f)
                            } else {
                                DividerHairline
                            },
                            shape = RoundedCornerShape(999.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = trailingMeta,
                        style = TapmarkMonoCaption.copy(fontWeight = FontWeight.SemiBold),
                        color = if (accentTrailingMeta) CopperTerracotta else MutedInk
                    )
                }
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = CardSurface,
            border = BorderStroke(1.dp, DividerHairline)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                content = content
            )
        }
    }
}

@Composable
private fun LedgerKeyValueRow(
    label: String,
    value: String,
    isMonospace: Boolean = false,
    valueColor: Color = PrimaryInk,
    animateValue: Boolean = false
) {
    val valueStyle = if (isMonospace) {
        TapmarkMonoBody
    } else {
        MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = SecondaryGraphite,
            modifier = Modifier
                .padding(top = 2.dp, end = 12.dp)
                .weight(0.36f, fill = false)
        )
        if (animateValue) {
            AnimatedContent(
                targetState = value,
                transitionSpec = { fadeIn(tween(180)).togetherWith(fadeOut(tween(140))) },
                label = "ledger_value_transition",
                modifier = Modifier.weight(0.64f)
            ) { text ->
                Text(
                    text = text,
                    style = valueStyle,
                    color = valueColor,
                    textAlign = TextAlign.End,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else {
            Text(
                text = value,
                style = valueStyle,
                color = valueColor,
                textAlign = TextAlign.End,
                modifier = Modifier.weight(0.64f)
            )
        }
    }
}

@Composable
private fun LedgerDivider() {
    HorizontalDivider(
        thickness = 1.dp,
        color = DividerHairline
    )
}

@Composable
private fun CompactPillButton(
    label: String,
    onClick: () -> Unit,
    filledAccent: Boolean = false,
    icon: (@Composable () -> Unit)? = null
) {
    val bgColor = if (filledAccent) CopperTerracotta else RecessedSurface
    val textColor = if (filledAccent) Color.White else PrimaryInk
    val borderColor = if (filledAccent) CopperTerracotta else DividerHairline

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(bgColor)
            .border(1.dp, borderColor, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 11.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        if (icon != null) {
            icon()
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor
        )
    }
}

private data class LedgerRowSpec(
    val label: String,
    val value: String,
    val isMono: Boolean,
    val valueColor: Color,
    val animateValue: Boolean = false
)
