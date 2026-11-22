package com.tapmark.nfc.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.graphics.Color
import com.tapmark.nfc.model.CardCategory
import com.tapmark.nfc.model.CardVisualStyle
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.ui.theme.AccessStonePrimary
import com.tapmark.nfc.ui.theme.AccessStoneSecondary
import com.tapmark.nfc.ui.theme.AmexEmeraldPrimary
import com.tapmark.nfc.ui.theme.AmexEmeraldSecondary
import com.tapmark.nfc.ui.theme.CopperSoftTint
import com.tapmark.nfc.ui.theme.LinenAccent
import com.tapmark.nfc.ui.theme.LinenSurface
import com.tapmark.nfc.ui.theme.MastercardCharcoalPrimary
import com.tapmark.nfc.ui.theme.MastercardCharcoalSecondary
import com.tapmark.nfc.ui.theme.PrimaryInk
import com.tapmark.nfc.ui.theme.SecondaryGraphite
import com.tapmark.nfc.ui.theme.TagTerracottaPrimary
import com.tapmark.nfc.ui.theme.TagTerracottaSecondary
import com.tapmark.nfc.ui.theme.TransitIndigoPrimary
import com.tapmark.nfc.ui.theme.TransitIndigoSecondary
import com.tapmark.nfc.ui.theme.VisaSlatePrimary
import com.tapmark.nfc.ui.theme.VisaSlateSecondary
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Date
import java.util.Locale

data class CardStylePalette(
    val baseColor: Color,
    val secondaryColor: Color,
    val borderColor: Color,
    val inkColor: Color,
    val secondaryInkColor: Color,
    val badgeBgColor: Color,
    val badgeInkColor: Color,
    val grainColor: Color,
    val chipGoldTint: Color,
    val isDark: Boolean
)

fun CardVisualStyle.toPalette(): CardStylePalette = when (this) {
    CardVisualStyle.COPPER_LINEN -> CardStylePalette(
        baseColor = LinenSurface,
        secondaryColor = Color(0xFFE7DFCF),
        borderColor = Color(0xFFD5CCB9),
        inkColor = PrimaryInk,
        secondaryInkColor = SecondaryGraphite,
        badgeBgColor = CopperSoftTint,
        badgeInkColor = LinenAccent,
        grainColor = Color(0x0D171614),
        chipGoldTint = Color(0xFFD1B375),
        isDark = false
    )

    CardVisualStyle.VISA_SLATE -> CardStylePalette(
        baseColor = VisaSlatePrimary,
        secondaryColor = VisaSlateSecondary,
        borderColor = Color(0xFF3E4A5C),
        inkColor = Color(0xFFF6F3EC),
        secondaryInkColor = Color(0xFFB7C0CD),
        badgeBgColor = Color(0x28FFFFFF),
        badgeInkColor = Color(0xFFF6F3EC),
        grainColor = Color(0x10FFFFFF),
        chipGoldTint = Color(0xFFD8BF83),
        isDark = true
    )

    CardVisualStyle.MASTERCARD_CHARCOAL -> CardStylePalette(
        baseColor = MastercardCharcoalPrimary,
        secondaryColor = MastercardCharcoalSecondary,
        borderColor = Color(0xFF423C37),
        inkColor = Color(0xFFF5F2EB),
        secondaryInkColor = Color(0xFFC2B9AE),
        badgeBgColor = Color(0x33C8552D),
        badgeInkColor = Color(0xFFF6DDD3),
        grainColor = Color(0x0FFFFFFF),
        chipGoldTint = Color(0xFFD9B774),
        isDark = true
    )

    CardVisualStyle.AMEX_EMERALD -> CardStylePalette(
        baseColor = AmexEmeraldPrimary,
        secondaryColor = AmexEmeraldSecondary,
        borderColor = Color(0xFF346758),
        inkColor = Color(0xFFF5F2EB),
        secondaryInkColor = Color(0xFFB3CEC5),
        badgeBgColor = Color(0x24FFFFFF),
        badgeInkColor = Color(0xFFF5F2EB),
        grainColor = Color(0x0FFFFFFF),
        chipGoldTint = Color(0xFFDCC48C),
        isDark = true
    )

    CardVisualStyle.TRANSIT_INDIGO -> CardStylePalette(
        baseColor = TransitIndigoPrimary,
        secondaryColor = TransitIndigoSecondary,
        borderColor = Color(0xFF3E567C),
        inkColor = Color(0xFFF5F2EB),
        secondaryInkColor = Color(0xFFB7C5DA),
        badgeBgColor = Color(0x26FFFFFF),
        badgeInkColor = Color(0xFFF5F2EB),
        grainColor = Color(0x0EFFFFFF),
        chipGoldTint = Color(0xFFC8CDD4),
        isDark = true
    )

    CardVisualStyle.ACCESS_STONE -> CardStylePalette(
        baseColor = AccessStonePrimary,
        secondaryColor = AccessStoneSecondary,
        borderColor = Color(0xFFC5BDAD),
        inkColor = PrimaryInk,
        secondaryInkColor = SecondaryGraphite,
        badgeBgColor = Color(0x1A171614),
        badgeInkColor = PrimaryInk,
        grainColor = Color(0x0B171614),
        chipGoldTint = Color(0xFFBBA87A),
        isDark = false
    )

    CardVisualStyle.TAG_TERRACOTTA -> CardStylePalette(
        baseColor = TagTerracottaPrimary,
        secondaryColor = TagTerracottaSecondary,
        borderColor = Color(0xFF963B1C),
        inkColor = Color(0xFFFFFBF6),
        secondaryInkColor = Color(0xFFF4D5C8),
        badgeBgColor = Color(0x2BFFFFFF),
        badgeInkColor = Color(0xFFFFFBF6),
        grainColor = Color(0x11FFFFFF),
        chipGoldTint = Color(0xFFE7CE9C),
        isDark = true
    )
}

fun CardCategory.displayName(): String = when (this) {
    CardCategory.PAYMENT -> "Payment"
    CardCategory.TRANSIT -> "Transit"
    CardCategory.ACCESS -> "Access"
    CardCategory.SMART_TAG -> "Smart Tag"
    CardCategory.GENERIC -> "Generic"
}

fun formatPanGroups(rawPan: String): String {
    val digits = rawPan.filter { it.isDigit() }
    if (digits.length == 15) {
        return "${digits.substring(0, 4)} ${digits.substring(4, 10)} ${digits.substring(10, 15)}"
    }
    return digits.chunked(4).joinToString(" ")
}

fun formatShortUid(uidHex: String): String {
    val clean = uidHex.trim()
    return if (clean.length <= 14) {
        clean
    } else {
        "${clean.take(5)}..${clean.takeLast(5)}"
    }
}

fun formatByteCount(bytes: Int): String {
    return "${NumberFormat.getIntegerInstance(Locale.US).format(bytes)} B"
}

fun formatRelativeTimestamp(scannedAtMillis: Long, nowMillis: Long = System.currentTimeMillis()): String {
    val diff = (nowMillis - scannedAtMillis).coerceAtLeast(0L)
    val minutes = diff / 60_000L
    val hours = diff / 3_600_000L
    val days = diff / 86_400_000L
    return when {
        minutes < 1L -> "Just now"
        minutes < 60L -> "${minutes}m ago"
        hours < 24L -> "${hours}h ago"
        days == 1L -> "Yesterday"
        days < 7L -> "${days}d ago"
        else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(scannedAtMillis))
    }
}

fun formatAbsoluteTimestamp(millis: Long): String {
    return DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
}

fun buildCardSummaryText(card: ScannedCard): String {
    val sb = StringBuilder()
    val title = card.customLabel?.takeIf { it.isNotBlank() } ?: card.cardTitle
    sb.appendLine("Tapmark Inspection Summary")
    sb.appendLine("Title: $title")
    if (!card.customLabel.isNullOrBlank()) {
        sb.appendLine("Card: ${card.cardTitle}")
    }
    sb.appendLine("Category: ${card.category.displayName()}")
    sb.appendLine("Chip: ${card.chipGuess}")
    sb.appendLine("Manufacturer: ${card.manufacturer}")
    sb.appendLine("UID: ${card.uidHex}")
    sb.appendLine("UID (Decimal): ${card.uidDecimal}")

    card.emvInfo?.let { emv ->
        emv.bankName?.takeIf { it.isNotBlank() }?.let { sb.appendLine("Issuing Bank: $it") }
        sb.appendLine("Network: ${emv.network}")
        val typeAndTier = listOfNotNull(
            emv.cardType?.takeIf { it.isNotBlank() },
            emv.cardTier?.takeIf { it.isNotBlank() }
        ).joinToString(" · ")
        if (typeAndTier.isNotEmpty()) {
            sb.appendLine("Card Type: $typeAndTier")
        }
        emv.bin?.takeIf { it.isNotBlank() }?.let { sb.appendLine("BIN / IIN: $it") }
        emv.appLabel?.let { sb.appendLine("App Label: $it") }
        sb.appendLine("AID: ${emv.aid}")
        sb.appendLine("PAN: ${emv.panMasked}")
        emv.expiryFormatted?.let { sb.appendLine("Expiry: $it") }
        emv.cardholderName?.let { sb.appendLine("Cardholder: $it") }
        emv.atc?.let { sb.appendLine("ATC: $it") }
        emv.lastOnlineAtc?.let { sb.appendLine("Last Online ATC: $it") }

        if (emv.transactionLogs.isNotEmpty()) {
            sb.appendLine("Transaction History (${emv.transactionLogs.size}):")
            emv.transactionLogs.forEachIndexed { idx, log ->
                val txTitle = log.merchantName?.takeIf { it.isNotBlank() }
                    ?: log.txType?.takeIf { it.isNotBlank() }
                    ?: "Card Transaction"
                val whenStr = listOfNotNull(
                    log.date.takeIf { it.isNotBlank() },
                    log.time?.takeIf { it.isNotBlank() }
                ).joinToString(" ")
                val amountStr = "${log.currencySymbol.orEmpty()}${log.amount} ${log.currency}"
                val metaParts = listOfNotNull(
                    if (!log.merchantName.isNullOrBlank() && !log.txType.isNullOrBlank()) log.txType else null,
                    log.country?.takeIf { it.isNotBlank() },
                    log.cryptogramInfo?.takeIf { it.isNotBlank() },
                    log.atc?.let { "#$it" }
                )
                val metaSuffix = if (metaParts.isNotEmpty()) " (${metaParts.joinToString(" · ")})" else ""
                sb.appendLine("  ${idx + 1}. $whenStr — $txTitle: $amountStr$metaSuffix")
            }
        } else if (!emv.logStatusMessage.isNullOrBlank()) {
            sb.appendLine("Transaction History: ${emv.logStatusMessage}")
        }
    }

    if (card.ndefRecords.isNotEmpty()) {
        sb.appendLine("NDEF Records (${card.ndefRecords.size}):")
        card.ndefRecords.forEachIndexed { idx, record ->
            sb.appendLine("  ${idx + 1}. [${record.typeLabel}] ${record.title}: ${record.value}")
        }
    }

    sb.appendLine("Technologies: ${card.rfDetails.technologies.joinToString(", ")}")
    card.rfDetails.atqaHex?.let { sb.appendLine("ATQA: $it") }
    card.rfDetails.sakHex?.let { sb.appendLine("SAK: $it") }
    return sb.toString().trimEnd()
}

fun copyTextToClipboard(context: Context, label: String, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}

fun openExternalUrl(context: Context, url: String): Boolean {
    return try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    } catch (_: Throwable) {
        false
    }
}
