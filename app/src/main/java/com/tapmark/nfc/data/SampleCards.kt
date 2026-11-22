package com.tapmark.nfc.data

import com.tapmark.nfc.model.CardCategory
import com.tapmark.nfc.model.CardVisualStyle
import com.tapmark.nfc.model.EmvCardInfo
import com.tapmark.nfc.model.EmvTxLog
import com.tapmark.nfc.model.MemoryDumpInfo
import com.tapmark.nfc.model.RfTechDetails
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.model.TlvNode
import com.tapmark.nfc.nfc.NdefParser
import java.io.ByteArrayOutputStream

/**
 * Curated sample cards (`isSample = true`) demonstrating EMV payment, transit,
 * and NDEF smart tag inspections without requiring physical hardware.
 */
object SampleCards {

    private val sampleScannedTimestamp = 1763800000000L

    private fun buildSampleWifiWscBytes(): ByteArray {
        fun tlv(tag: Int, value: ByteArray): ByteArray {
            val out = ByteArray(4 + value.size)
            out[0] = (tag ushr 8).toByte()
            out[1] = tag.toByte()
            out[2] = (value.size ushr 8).toByte()
            out[3] = value.size.toByte()
            value.copyInto(out, 4)
            return out
        }

        val inner = ByteArrayOutputStream().apply {
            write(tlv(0x1045, "Studio_Guest_5G".toByteArray(Charsets.UTF_8)))
            write(tlv(0x1003, byteArrayOf(0x00, 0x20))) // WPA2-PSK
            write(tlv(0x100F, byteArrayOf(0x00, 0x08))) // AES
            write(tlv(0x1027, "copper-coil-26".toByteArray(Charsets.UTF_8)))
        }.toByteArray()

        return tlv(0x100E, inner)
    }

    val all: List<ScannedCard> by lazy {
        val wifiRecord = NdefParser.decodeWifiWscRecord(buildSampleWifiWscBytes())
        val urlPayload = byteArrayOf(0x04) + "tapmark.dev/guide".toByteArray(Charsets.UTF_8)
        val urlRecord = NdefParser.parseRecord(
            tnf = NdefParser.TNF_WELL_KNOWN,
            type = "U".toByteArray(Charsets.US_ASCII),
            payload = urlPayload
        )

        listOf(
            // [0] BPI Visa Debit EMV Payment Card (Philippines)
            ScannedCard(
                id = "sample-visa-debit",
                scannedAtMillis = sampleScannedTimestamp,
                customLabel = null,
                isSample = true,
                category = CardCategory.PAYMENT,
                visualStyle = CardVisualStyle.VISA_SLATE,
                cardTitle = "Bank of the Philippine Islands",
                cardSubtitle = "BPI Visa Debit · •••• 8911",
                uidHex = "04:5A:3B:C2:19:70:80",
                uidReversedHex = "80:70:19:C2:3B:5A:04",
                uidDecimal = "1225112612860032",
                manufacturer = "NXP Semiconductors",
                chipGuess = "SmartMX / EMV Contactless (ISO 14443-4)",
                emvInfo = EmvCardInfo(
                    network = "Visa",
                    bankName = "Bank of the Philippine Islands",
                    cardType = "Debit",
                    cardTier = "Classic",
                    bin = "453756",
                    appLabel = "BPI Visa Debit",
                    aid = "A0000000031010",
                    panMasked = "4537 •••• •••• 8911",
                    panFull = "4537561044128911",
                    expiryFormatted = "08/29",
                    effectiveFormatted = "08/24",
                    cardholderName = "CLARA REYES",
                    serviceCode = "201",
                    issuerCountry = "Philippines (608)",
                    currency = "PHP (608)",
                    atc = 142,
                    lastOnlineAtc = 140,
                    pinTryCounter = 3,
                    luhnValid = true,
                    logStatusMessage = "Read 4 transaction(s) from chip log (SFI 11)",
                    transactionLogs = listOf(
                        EmvTxLog(
                            date = "2026-11-22",
                            time = "18:42:15",
                            amount = "1,250.00",
                            currency = "PHP",
                            currencySymbol = "₱",
                            country = "Philippines",
                            txType = "Purchase",
                            merchantName = "SM SUPERMARKET BGC",
                            atc = 142,
                            cryptogramInfo = "Offline Approved",
                            rawHex = "26 11 22 18 42 15 00 00 00 12 50 00 06 08 06 08 00 53 4D 20 53 55 50 45 52 4D 41 52 4B 45 54 20 42 47 43 20 20 00 8E 40"
                        ),
                        EmvTxLog(
                            date = "2026-11-20",
                            time = "12:15:08",
                            amount = "385.50",
                            currency = "PHP",
                            currencySymbol = "₱",
                            country = "Philippines",
                            txType = "Purchase",
                            merchantName = "MERCURY DRUG MAKATI",
                            atc = 141,
                            cryptogramInfo = "Offline Approved",
                            rawHex = "26 11 20 12 15 08 00 00 00 03 85 50 06 08 06 08 00 4D 45 52 43 55 52 59 20 44 52 55 47 20 4D 41 4B 41 54 49 20 00 8D 40"
                        ),
                        EmvTxLog(
                            date = "2026-11-18",
                            time = "09:04:33",
                            amount = "2,500.00",
                            currency = "PHP",
                            currencySymbol = "₱",
                            country = "Philippines",
                            txType = "Cash Advance",
                            merchantName = "BPI ATM AYALA AVE",
                            atc = 140,
                            cryptogramInfo = "Online Auth",
                            rawHex = "26 11 18 09 04 33 00 00 00 25 00 00 06 08 06 08 01 42 50 49 20 41 54 4D 20 41 59 41 4C 41 20 41 56 45 20 20 20 00 8C 80"
                        ),
                        EmvTxLog(
                            date = "2026-11-15",
                            time = "16:28:50",
                            amount = "499.00",
                            currency = "PHP",
                            currencySymbol = "₱",
                            country = "Philippines",
                            txType = "Refund",
                            merchantName = "UNIQLO GLORIETTA",
                            atc = 139,
                            cryptogramInfo = "Online Auth",
                            rawHex = "26 11 15 16 28 50 00 00 00 04 99 00 06 08 06 08 20 55 4E 49 51 4C 4F 20 47 4C 4F 52 49 45 54 54 41 20 20 20 20 00 8B 80"
                        )
                    )
                ),
                ndefRecords = emptyList(),
                rfDetails = RfTechDetails(
                    technologies = listOf("IsoDep", "NfcA"),
                    atqaHex = "04 00",
                    sakHex = "20",
                    historicalBytesHex = "80 65 A2 08 01 01 52",
                    maxTransceiveBytes = 261,
                    ndefCapacityBytes = null,
                    ndefUsedBytes = null,
                    isWritable = null,
                    sectorCount = null,
                    blockCount = null,
                    memoryBytes = null
                ),
                memoryInfo = null,
                rawTlvEntries = listOf(
                    TlvNode("84", "Dedicated File (DF) Name", "A0000000031010", "A0000000031010"),
                    TlvNode("50", "Application Label", "4250492056697361204465626974", "BPI Visa Debit"),
                    TlvNode("5F50", "Issuer URL", "68747470733A2F2F7777772E6270692E636F6D2E7068", "https://www.bpi.com.ph"),
                    TlvNode("9F38", "Processing Options Data Object List (PDOL)", "9F66049F02069F03069F1A0295055F2A029A039C019F3704", "9F66(4), 9F02(6), 9F03(6), 9F1A(2), 95(5), 5F2A(2), 9A(3), 9C(1), 9F37(4)"),
                    TlvNode("82", "Application Interchange Profile (AIP)", "2000", "2000"),
                    TlvNode("94", "Application File Locator (AFL)", "1001030018010200", "1001030018010200"),
                    TlvNode("5A", "Application PAN", "4537561044128911", "4537 •••• 8911"),
                    TlvNode("5F20", "Cardholder Name", "52455945532F434C415241", "REYES/CLARA"),
                    TlvNode("5F24", "Application Expiration Date", "290831", "08/29"),
                    TlvNode("5F25", "Application Effective Date", "240801", "08/24"),
                    TlvNode("5F28", "Issuer Country Code", "0608", "Philippines (608)"),
                    TlvNode("5F30", "Service Code", "0201", "0201"),
                    TlvNode("9F42", "Application Currency Code", "0608", "PHP (608)"),
                    TlvNode("9F36", "Application Transaction Counter (ATC)", "008E", "142 (0x008E)"),
                    TlvNode("9F13", "Last Online ATC Register", "008C", "140 (0x008C)"),
                    TlvNode("9F17", "PIN Try Counter", "03", "3 tries remaining"),
                    TlvNode("9F4D", "Log Entry", "0B0A", "0B0A"),
                    TlvNode("9F4F", "Log Format", "9A039F21039F02065F2A029F1A029C019F4E149F36029F2701", "9A(3), 9F21(3), 9F02(6), 5F2A(2), 9F1A(2), 9C(1), 9F4E(20), 9F36(2), 9F27(1)")
                ),
                readNotes = listOf(
                    "Sample EMV payment card preview — tap a physical contactless card to scan live",
                    "PPSE listed 1 application: A0000000031010 (BPI Visa Debit)",
                    "Issuer bank resolved: Bank of the Philippine Islands (BIN 453756)",
                    "Read 4 transaction(s) from chip log (SFI 11)"
                )
            ),

            // [1] MIFARE Classic / Transit Card
            ScannedCard(
                id = "sample-transit-classic",
                scannedAtMillis = sampleScannedTimestamp - 3_600_000L,
                customLabel = null,
                isSample = true,
                category = CardCategory.TRANSIT,
                visualStyle = CardVisualStyle.TRANSIT_INDIGO,
                cardTitle = "MetroLink Transit Pass",
                cardSubtitle = "MIFARE Classic 1K · 16 Sectors",
                uidHex = "04:91:E4:2A:73:48:81",
                uidReversedHex = "81:48:73:2A:E4:91:04",
                uidDecimal = "1286309057611905",
                manufacturer = "NXP Semiconductors",
                chipGuess = "MIFARE Classic 1K (EV1)",
                emvInfo = null,
                ndefRecords = emptyList(),
                rfDetails = RfTechDetails(
                    technologies = listOf("NfcA", "MifareClassic"),
                    atqaHex = "04 00",
                    sakHex = "08",
                    historicalBytesHex = null,
                    maxTransceiveBytes = 253,
                    ndefCapacityBytes = null,
                    ndefUsedBytes = null,
                    isWritable = false,
                    sectorCount = 16,
                    blockCount = 64,
                    memoryBytes = 1024
                ),
                memoryInfo = MemoryDumpInfo(
                    summary = "2/16 sectors unlocked · 8 blocks read",
                    asciiPreview = "METROLINK-ZONE2",
                    pagesHex = listOf(
                        "S00 B00: 04 91 E4 2A 73 48 81 08 04 00 02 00 4D 4C 31 00",
                        "S00 B01: 01 03 4D 45 54 52 4F 4C 49 4E 4B 2D 5A 32 00 00",
                        "S00 B02: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
                        "S00 B03: A0 A1 A2 A3 A4 A5 78 77 88 C1 FF FF FF FF FF FF",
                        "S01 B04: 4D 45 54 52 4F 4C 49 4E 4B 2D 5A 4F 4E 45 32 00",
                        "S01 B05: 00 00 04 B0 00 00 00 00 26 11 18 01 00 00 00 00",
                        "S01 B06: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00",
                        "S01 B07: D3 F7 D3 F7 D3 F7 7F 07 88 40 FF FF FF FF FF FF"
                    )
                ),
                rawTlvEntries = emptyList(),
                readNotes = listOf(
                    "Sample transit card preview — MIFARE Classic 1K EV1",
                    "MIFARE Classic read-only check: 2/16 sectors accessible with MAD/NFC Forum keys",
                    "Sectors 2–15 protected by operator Crypto1 keys (left unread safely)"
                )
            ),

            // [2] NTAG215 Smart Tag
            ScannedCard(
                id = "sample-ntag215-smart",
                scannedAtMillis = sampleScannedTimestamp - 7_200_000L,
                customLabel = null,
                isSample = true,
                category = CardCategory.SMART_TAG,
                visualStyle = CardVisualStyle.TAG_TERRACOTTA,
                cardTitle = "Studio Guest Wi-Fi & Guide",
                cardSubtitle = "NTAG215 · 2 NDEF records",
                uidHex = "04:7C:19:8A:32:5D:80",
                uidReversedHex = "80:5D:32:8A:19:7C:04",
                uidDecimal = "1262349041425792",
                manufacturer = "NXP Semiconductors",
                chipGuess = "NTAG215 (504 bytes user)",
                emvInfo = null,
                ndefRecords = listOf(wifiRecord, urlRecord),
                rfDetails = RfTechDetails(
                    technologies = listOf("NfcA", "MifareUltralight", "Ndef"),
                    atqaHex = "44 00",
                    sakHex = "00",
                    historicalBytesHex = null,
                    maxTransceiveBytes = 253,
                    ndefCapacityBytes = 504,
                    ndefUsedBytes = 94,
                    isWritable = true,
                    sectorCount = null,
                    blockCount = null,
                    memoryBytes = 540
                ),
                memoryInfo = MemoryDumpInfo(
                    summary = "Read 24 pages (96 bytes preview of 540 bytes)",
                    asciiPreview = "application/vnd.wfa.wsc Studio_Guest_5G copper-coil-26 tapmark.dev/guide",
                    pagesHex = listOf(
                        "00: 04 7C 19 2E",
                        "01: 8A 32 5D 80",
                        "02: 49 48 00 00",
                        "03: E1 10 3E 00",
                        "04: 03 5E 92 15",
                        "05: 43 61 70 70",
                        "06: 6C 69 63 61",
                        "07: 74 69 6F 6E",
                        "08: 2F 76 6E 64",
                        "09: 2E 77 66 61",
                        "0A: 2E 77 73 63",
                        "0B: 10 0E 00 3F",
                        "0C: 10 45 00 0F",
                        "0D: 53 74 75 64",
                        "0E: 69 6F 5F 47",
                        "0F: 75 65 73 74"
                    )
                ),
                rawTlvEntries = emptyList(),
                readNotes = listOf(
                    "Sample smart tag preview — NTAG215 NFC Forum Type 2 Tag",
                    "Decoded 2 NDEF records (Wi-Fi WSC credentials + HTTPS URI)",
                    "Capability Container E1 10 3E 00 indicates 504 bytes writable user memory"
                )
            )
        )
    }
}
