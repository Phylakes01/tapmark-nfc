package com.tapmark.nfc.model

/**
 * Shared immutable data models and contracts across NFC scanning, persistence, and UI layers.
 */
enum class CardCategory {
    PAYMENT,
    TRANSIT,
    ACCESS,
    SMART_TAG,
    GENERIC
}

enum class CardVisualStyle {
    COPPER_LINEN,
    VISA_SLATE,
    MASTERCARD_CHARCOAL,
    AMEX_EMERALD,
    TRANSIT_INDIGO,
    ACCESS_STONE,
    TAG_TERRACOTTA
}

enum class NfcHardwareState {
    READY,
    DISABLED,
    UNAVAILABLE
}

data class EmvTxLog(
    val date: String,
    val time: String? = null,
    val amount: String,
    val currency: String,
    val currencySymbol: String? = null,
    val country: String? = null,
    val txType: String? = null,
    val merchantName: String? = null,
    val atc: Int? = null,
    val cryptogramInfo: String? = null,
    val rawHex: String? = null
)

data class EmvCardInfo(
    val network: String,
    val bankName: String? = null,
    val cardType: String? = null,
    val cardTier: String? = null,
    val bin: String? = null,
    val appLabel: String?,
    val aid: String,
    val panMasked: String,
    val panFull: String?,
    val expiryFormatted: String?,
    val effectiveFormatted: String?,
    val cardholderName: String?,
    val serviceCode: String?,
    val issuerCountry: String?,
    val currency: String?,
    val atc: Int?,
    val lastOnlineAtc: Int? = null,
    val pinTryCounter: Int?,
    val luhnValid: Boolean?,
    val cvcNote: String? = null,
    val chipCvcLabel: String? = null,
    val chipCvcData: String? = null,
    val cryptogramHex: String? = null,
    val logStatusMessage: String? = null,
    val transactionLogs: List<EmvTxLog>
)

data class ParsedNdefRecord(
    val typeLabel: String,
    val title: String,
    val value: String,
    val actionUrl: String?,
    val rawHex: String,
    val byteSize: Int
)

data class RfTechDetails(
    val technologies: List<String>,
    val atqaHex: String?,
    val sakHex: String?,
    val historicalBytesHex: String?,
    val maxTransceiveBytes: Int?,
    val ndefCapacityBytes: Int?,
    val ndefUsedBytes: Int?,
    val isWritable: Boolean?,
    val sectorCount: Int?,
    val blockCount: Int?,
    val memoryBytes: Int?
)

data class TlvNode(
    val tagHex: String,
    val name: String,
    val valueHex: String,
    val displayValue: String
)

data class MemoryDumpInfo(
    val summary: String,
    val asciiPreview: String?,
    val pagesHex: List<String>
)

data class ScannedCard(
    val id: String,
    val scannedAtMillis: Long,
    val customLabel: String?,
    val isSample: Boolean,
    val category: CardCategory,
    val visualStyle: CardVisualStyle,
    val cardTitle: String,
    val cardSubtitle: String,
    val uidHex: String,
    val uidReversedHex: String,
    val uidDecimal: String,
    val manufacturer: String,
    val chipGuess: String,
    val emvInfo: EmvCardInfo?,
    val ndefRecords: List<ParsedNdefRecord>,
    val rfDetails: RfTechDetails,
    val memoryInfo: MemoryDumpInfo?,
    val rawTlvEntries: List<TlvNode>,
    val readNotes: List<String>
)

sealed class ScanResult {
    data class Success(val card: ScannedCard) : ScanResult()
    data object TagLost : ScanResult()
    data class Error(val message: String) : ScanResult()
}
