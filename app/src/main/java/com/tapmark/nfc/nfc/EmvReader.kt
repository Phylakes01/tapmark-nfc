package com.tapmark.nfc.nfc

import com.tapmark.nfc.model.EmvCardInfo
import com.tapmark.nfc.model.EmvTxLog
import com.tapmark.nfc.model.TlvNode
import java.security.SecureRandom
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Transport abstraction for ISO 14443-4 APDUs so EMV logic is pure Kotlin and unit-testable.
 */
interface IsoDepChannel {
    fun transceive(command: ByteArray): ByteArray
}

data class EmvReadResult(
    val emvInfo: EmvCardInfo?,
    val rawTlvEntries: List<TlvNode>,
    val notes: List<String>
)

data class Track2Data(
    val pan: String,
    val expiryFormatted: String?,
    val serviceCode: String?,
    val discretionaryData: String? = null
)

/**
 * Pure-Kotlin contactless EMV reader implementing PPSE -> SELECT AID -> Dynamic PDOL -> GPO (with retry) ->
 * READ RECORD (AFL + fallback) -> GET DATA & Transaction Logs + offline bank/BIN enrichment.
 */
object EmvReader {

    private val PPSE_AID = "2PAY.SYS.DDF01".toByteArray(Charsets.US_ASCII)
    private const val CVC_EMVCO_NOTE =
        "Printed CVV2/CVC2 is never stored on EMV chips (EMVCo security)"
    private val DEFAULT_TTQ = byteArrayOf(0x36, 0x00, 0x00, 0x00)
    private val RETRY_AMOUNT_BYTES = byteArrayOf(0x00, 0x00, 0x00, 0x00, 0x01, 0x00)
    private val RETRY_TTQ_1 = byteArrayOf(0x36, 0x20, 0x40, 0x00)
    private val RETRY_TTQ_2 = byteArrayOf(0x26, 0x00, 0x00, 0x00)
    private val COMMON_LOG_SFIS = listOf(11, 12, 13, 20, 21)

    private val FALLBACK_AIDS: List<Pair<String, String>> = listOf(
        "A0000000031010" to "Visa",
        "A0000000032010" to "Visa Electron",
        "A0000000032020" to "V PAY",
        "A0000000041010" to "Mastercard",
        "A0000000043060" to "Maestro",
        "A0000000046000" to "Cirrus",
        "A000000025010801" to "Amex",
        "A000000025010402" to "Amex",
        "A0000000651010" to "JCB",
        "A0000001523010" to "Discover",
        "A000000333010101" to "UnionPay",
        "A000000333010102" to "UnionPay",
        "A0000002771010" to "Interac",
        "A0000005241010" to "RuPay"
    )

    private val random = SecureRandom()

    private data class CandidateApp(
        val aidHex: String,
        val priority: Int,
        val ppseLabel: String?
    )

    private data class ApduResponse(
        val data: ByteArray,
        val sw1: Int,
        val sw2: Int
    ) {
        val isSuccess: Boolean get() = sw1 == 0x90 && sw2 == 0x00
        val swHex: String get() = "%02X%02X".format(sw1, sw2)
    }

    private data class AflEntry(
        val sfi: Int,
        val startRecord: Int,
        val endRecord: Int
    )

    fun readCard(channel: IsoDepChannel): EmvReadResult {
        val notes = mutableListOf<String>()

        // Step 1: SELECT PPSE (2PAY.SYS.DDF01)
        val candidates = mutableListOf<CandidateApp>()
        val ppseResponse = transceiveApdu(channel, buildSelectApdu(PPSE_AID))
        if (ppseResponse.isSuccess && ppseResponse.data.isNotEmpty()) {
            val ppseTlv = BerTlvParser.parse(ppseResponse.data)
            val appTemplates = ppseTlv.flatMap { it.findAll("61") }
            for (tpl in appTemplates) {
                val aidEl = tpl.findFirst("4F") ?: continue
                val aidHex = aidEl.valueHex
                if (aidHex.isBlank()) continue
                val rawPriority = tpl.findFirst("87")?.value?.firstOrNull()?.let { it.toInt() and 0x0F } ?: 0
                val sortPriority = if (rawPriority in 1..15) rawPriority else 99
                val label = tpl.findFirst("9F12")?.value?.let { BerTlvParser.decodePrintableAscii(it) }?.ifBlank { null }
                    ?: tpl.findFirst("50")?.value?.let { BerTlvParser.decodePrintableAscii(it) }?.ifBlank { null }
                candidates.add(CandidateApp(aidHex = aidHex, priority = sortPriority, ppseLabel = label))
            }
            if (candidates.isNotEmpty()) {
                candidates.sortBy { it.priority }
                notes.add("PPSE listed ${candidates.size} application(s): ${candidates.joinToString { it.aidHex }}")
            } else {
                notes.add("PPSE responded cleanly but listed no AID entries; probing common AIDs")
            }
        } else {
            notes.add("PPSE unavailable (${ppseResponse.swHex}); probing common payment AIDs")
        }

        val appsToTry: List<CandidateApp> = if (candidates.isNotEmpty()) {
            candidates
        } else {
            FALLBACK_AIDS.mapIndexed { idx, (aid, label) ->
                CandidateApp(aidHex = aid, priority = idx + 1, ppseLabel = label)
            }
        }

        // Step 2..6: Try candidates in priority order
        var bestFallbackInfo: EmvCardInfo? = null
        var bestFallbackNodes: List<TlvNode> = emptyList()

        for (candidate in appsToTry) {
            val selectResp = transceiveApdu(channel, buildSelectApdu(candidate.aidHex.hexToByteArray()))
            if (!selectResp.isSuccess || selectResp.data.isEmpty()) {
                continue
            }

            val rawNodes = mutableListOf<TlvNode>()
            val seenNodes = HashSet<Pair<String, String>>()
            fun recordTlv(elements: List<BerTlvParser.BerTlvElement>) {
                for (node in BerTlvParser.toTlvNodes(elements)) {
                    val key = node.tagHex to node.valueHex
                    if (seenNodes.add(key)) {
                        rawNodes.add(node)
                    }
                }
            }

            val selectTlv = BerTlvParser.parse(selectResp.data)
            recordTlv(selectTlv)

            val collectedTags = mutableMapOf<String, ByteArray>()
            collectLeafValues(selectTlv, collectedTags)

            val selectedAidHex = collectedTags["84"]?.toHexString()?.takeIf { it.isNotEmpty() } ?: candidate.aidHex
            val appLabel = collectedTags["9F12"]?.let { BerTlvParser.decodePrintableAscii(it) }?.ifBlank { null }
                ?: collectedTags["50"]?.let { BerTlvParser.decodePrintableAscii(it) }?.ifBlank { null }
                ?: candidate.ppseLabel

            // Step 3 & 4: Build Dynamic PDOL + GET PROCESSING OPTIONS (with non-zero amount & TTQ retry)
            val pdolBytes = collectedTags["9F38"]
            val pdolEntries = pdolBytes?.let { BerTlvParser.parseDol(it) }.orEmpty()
            val hasAmountOrTtqInPdol = pdolEntries.any { it.tagHex == "9F02" || it.tagHex == "9F66" }
            val hasTtqInPdol = pdolEntries.any { it.tagHex == "9F66" }
            val initialPdolData = buildPdolData(pdolBytes)
            var gpoResp = transceiveApdu(channel, buildGpoApdu(initialPdolData))

            if ((!gpoResp.isSuccess || gpoResp.data.isEmpty()) && hasAmountOrTtqInPdol) {
                val retry1Pdol = buildPdolData(
                    pdolBytes = pdolBytes,
                    amountBytes = RETRY_AMOUNT_BYTES,
                    ttqBytes = RETRY_TTQ_1
                )
                val retry1Resp = transceiveApdu(channel, buildGpoApdu(retry1Pdol))
                if (retry1Resp.isSuccess && retry1Resp.data.isNotEmpty()) {
                    gpoResp = retry1Resp
                    notes.add("GPO succeeded on retry with non-zero amount (1.00) and TTQ 36204000")
                } else if (hasTtqInPdol) {
                    val retry2Pdol = buildPdolData(
                        pdolBytes = pdolBytes,
                        amountBytes = RETRY_AMOUNT_BYTES,
                        ttqBytes = RETRY_TTQ_2
                    )
                    val retry2Resp = transceiveApdu(channel, buildGpoApdu(retry2Pdol))
                    if (retry2Resp.isSuccess && retry2Resp.data.isNotEmpty()) {
                        gpoResp = retry2Resp
                        notes.add("GPO succeeded on retry with non-zero amount (1.00) and TTQ 26000000")
                    } else {
                        gpoResp = retry2Resp
                    }
                } else {
                    gpoResp = retry1Resp
                }
            }

            val aflEntries = mutableListOf<AflEntry>()
            if (gpoResp.isSuccess && gpoResp.data.isNotEmpty()) {
                val gpoTlv = BerTlvParser.parse(gpoResp.data)
                recordTlv(gpoTlv)
                collectLeafValues(gpoTlv, collectedTags)

                // Handle Format 1 (Tag 80: 2-byte AIP followed by AFL bytes)
                val fmt1 = gpoTlv.firstNotNullOfOrNull { it.findFirst("80") }
                if (fmt1 != null && fmt1.value.size >= 2) {
                    val aipBytes = fmt1.value.copyOfRange(0, 2)
                    collectedTags["82"] = aipBytes
                    addSyntheticNode(rawNodes, seenNodes, "82", aipBytes)
                    if (fmt1.value.size > 2) {
                        val aflBytes = fmt1.value.copyOfRange(2, fmt1.value.size)
                        collectedTags["94"] = aflBytes
                        addSyntheticNode(rawNodes, seenNodes, "94", aflBytes)
                    }
                }

                collectedTags["94"]?.let { aflBytes ->
                    aflEntries.addAll(parseAfl(aflBytes))
                }
            } else {
                notes.add("GPO for $selectedAidHex returned ${gpoResp.swHex}; attempting record read fallback")
            }

            // Step 5: READ RECORD over AFL or safe fallback SFI 1..2
            if (aflEntries.isNotEmpty()) {
                for (entry in aflEntries) {
                    val maxRec = entry.endRecord.coerceAtMost(entry.startRecord + 15)
                    for (rec in entry.startRecord..maxRec) {
                        val recResp = readRecord(channel, entry.sfi, rec)
                        if (recResp.isSuccess && recResp.data.isNotEmpty()) {
                            val recTlv = BerTlvParser.parse(recResp.data)
                            recordTlv(recTlv)
                            collectLeafValues(recTlv, collectedTags)
                        } else if (recResp.sw1 == 0x6A && (recResp.sw2 == 0x82 || recResp.sw2 == 0x83)) {
                            break
                        }
                    }
                }
            } else {
                for (sfi in 1..2) {
                    for (rec in 1..4) {
                        val recResp = readRecord(channel, sfi, rec)
                        if (recResp.isSuccess && recResp.data.isNotEmpty()) {
                            val recTlv = BerTlvParser.parse(recResp.data)
                            recordTlv(recTlv)
                            collectLeafValues(recTlv, collectedTags)
                        } else if (recResp.sw1 == 0x6A && (recResp.sw2 == 0x82 || recResp.sw2 == 0x83)) {
                            break
                        }
                    }
                }
            }

            // Additional GET DATA queries: 9F36 (ATC), 9F13 (Last Online ATC), 9F17 (PIN Try Counter),
            // 9F4D/DF4D (Log Entry), and 9F4F/DF4F (Log Format)
            queryGetDataIfMissing(channel, collectedTags, rawNodes, seenNodes, "9F36", 0x9F, 0x36)
            queryGetDataIfMissing(channel, collectedTags, rawNodes, seenNodes, "9F13", 0x9F, 0x13)
            queryGetDataIfMissing(channel, collectedTags, rawNodes, seenNodes, "9F17", 0x9F, 0x17)

            if (!collectedTags.containsKey("9F4D")) {
                queryGetDataIfMissing(channel, collectedTags, rawNodes, seenNodes, "9F4D", 0x9F, 0x4D)
            }
            if (!collectedTags.containsKey("9F4D") && !collectedTags.containsKey("DF4D")) {
                queryGetDataIfMissing(channel, collectedTags, rawNodes, seenNodes, "DF4D", 0xDF, 0x4D)
            }
            if (!collectedTags.containsKey("9F4F")) {
                queryGetDataIfMissing(channel, collectedTags, rawNodes, seenNodes, "9F4F", 0x9F, 0x4F)
            }
            if (!collectedTags.containsKey("9F4F") && !collectedTags.containsKey("DF4F")) {
                queryGetDataIfMissing(channel, collectedTags, rawNodes, seenNodes, "DF4F", 0xDF, 0x4F)
            }

            // Transaction logs via 9F4D/DF4D or SFI probe fallback when 9F4F/DF4F is present
            val fallbackCurrencyCode = collectedTags["9F42"]?.let { BerTlvParser.decodeBcdInt(it) }
            val logEntryBytes = collectedTags["9F4D"] ?: collectedTags["DF4D"]
            val logFormatBytes = collectedTags["9F4F"] ?: collectedTags["DF4F"]
            val logDol = logFormatBytes?.takeIf { it.isNotEmpty() }?.let { BerTlvParser.parseDol(it) }.orEmpty()

            val txLogs = mutableListOf<EmvTxLog>()
            var usedLogSfi: Int? = null
            var logSupportedByChip = false

            if (logEntryBytes != null && logEntryBytes.size >= 2) {
                val logSfi = logEntryBytes[0].toInt() and 0xFF
                val maxLogCount = (logEntryBytes[1].toInt() and 0xFF).coerceIn(0, 30)
                if (logSfi in 1..30 && maxLogCount > 0 && logDol.isNotEmpty()) {
                    logSupportedByChip = true
                    usedLogSfi = logSfi
                    readLogsFromSfi(
                        channel = channel,
                        sfi = logSfi,
                        maxCount = maxLogCount,
                        logDol = logDol,
                        fallbackCurrencyCode = fallbackCurrencyCode,
                        outLogs = txLogs
                    )
                }
            } else if (logDol.isNotEmpty()) {
                val aflSfis = aflEntries.map { it.sfi }.toSet()
                for (probeSfi in COMMON_LOG_SFIS) {
                    if (probeSfi in aflSfis) continue
                    val probeResp = readRecord(channel, probeSfi, 1)
                    if (probeResp.isSuccess && probeResp.data.isNotEmpty()) {
                        logSupportedByChip = true
                        usedLogSfi = probeSfi
                        readLogsFromSfi(
                            channel = channel,
                            sfi = probeSfi,
                            maxCount = 20,
                            logDol = logDol,
                            fallbackCurrencyCode = fallbackCurrencyCode,
                            firstRecordResponse = probeResp,
                            outLogs = txLogs
                        )
                        break
                    }
                }
            }

            // Step 6: Extract Track 2, PAN, dates, Luhn, metadata, offline Bank/BIN
            val track2 = collectedTags["57"]?.let { parseTrack2(it.toHexString()) }
            val panFrom5A = collectedTags["5A"]?.toHexString()?.trimEnd('F', 'f')?.filter { it.isDigit() }
                ?.takeIf { it.length >= 12 }
            val panFull = panFrom5A ?: track2?.pan
            val expiryFormatted = collectedTags["5F24"]?.let { parseEmvDateMmYy(it.toHexString()) }
                ?: track2?.expiryFormatted
            val effectiveFormatted = collectedTags["5F25"]?.let { parseEmvDateMmYy(it.toHexString()) }
            val serviceCode = collectedTags["5F30"]?.toHexString()?.takeLast(3)
                ?: track2?.serviceCode
            val cardholderName = collectedTags["5F20"]?.let {
                cleanCardholderName(BerTlvParser.decodePrintableAscii(it))
            }
            val issuerCountry = collectedTags["5F28"]?.let { bytes ->
                val code = BerTlvParser.decodeBcdInt(bytes)
                val name = code?.let { BerTlvParser.countryName(it) }
                if (name != null) "$name ($code)" else code?.toString()
            }
            val currency = collectedTags["9F42"]?.let { bytes ->
                val code = BerTlvParser.decodeBcdInt(bytes)
                val info = code?.let { BerTlvParser.currencyInfo(it) }
                if (info != null) "${info.code} ($code)" else code?.toString()
            }
            val atc = collectedTags["9F36"]?.let { parseUnsignedInt(it) }
            val lastOnlineAtc = collectedTags["9F13"]?.let { parseUnsignedInt(it) }
            val pinTryCounter = collectedTags["9F17"]?.firstOrNull()?.let { it.toInt() and 0xFF }

            val network = detectNetwork(selectedAidHex, panFull)
            val luhnValid = panFull?.let { isLuhnValid(it) }
            val panMasked = panFull?.let { maskPan(it) } ?: "•••• •••• •••• ••••"

            val iinTag42 = collectedTags["42"]?.toHexString()?.let { BankBinDatabase.strictSixDigitBin(it) }
            val bin = BankBinDatabase.strictSixDigitBin(panFull) ?: iinTag42
            val issuerUrl = collectedTags["5F50"]?.let { BerTlvParser.decodePrintableAscii(it) }?.ifBlank { null }
            val bankInfo = BankBinDatabase.lookupOffline(
                bin = bin,
                iinTag42 = iinTag42,
                appLabel = appLabel,
                issuerUrl = issuerUrl
            )

            val logStatusMessage = when {
                txLogs.isNotEmpty() -> {
                    val sfiText = usedLogSfi?.let { " (SFI $it)" } ?: ""
                    "Read ${txLogs.size} transaction(s) from chip log$sfiText"
                }
                logSupportedByChip -> "Chip transaction log is empty (0 records stored)"
                else -> {
                    val base = "This bank chip does not expose itemized transaction logs over contactless NFC"
                    if (atc != null) "$base · Chip ATC: $atc lifetime tap(s)" else base
                }
            }

            // EMVCo security fields: static printed CVV2/CVC2 is never stored on EMV chips;
            // expose dynamic CVC3 or Track 1/2 discretionary data + Application Cryptogram (9F26) when present.
            val cvc9F61 = collectedTags["9F61"]?.toHexString()?.takeIf { it.isNotBlank() }
            val cvc9F60 = collectedTags["9F60"]?.toHexString()?.takeIf { it.isNotBlank() }
            val track2Disc = track2?.discretionaryData?.takeIf { it.isNotBlank() }
            val track1Disc = collectedTags["9F1F"]?.let { decodeTrack1Discretionary(it) }
            val (chipCvcLabel, chipCvcData) = when {
                cvc9F61 != null -> "Dynamic CVC3 (9F61)" to cvc9F61
                cvc9F60 != null -> "Dynamic CVC3 (9F60)" to cvc9F60
                track2Disc != null -> "Track 2 Discretionary (iCVV)" to track2Disc
                track1Disc != null -> "Track 1 Discretionary (9F1F)" to track1Disc
                else -> null to null
            }
            val cryptogramHex = collectedTags["9F26"]?.toHexString()?.takeIf { it.isNotBlank() }

            val info = EmvCardInfo(
                network = network,
                bankName = bankInfo?.bankName,
                cardType = bankInfo?.cardType,
                cardTier = bankInfo?.cardTier,
                bin = bin,
                appLabel = appLabel,
                aid = selectedAidHex,
                panMasked = panMasked,
                panFull = panFull,
                expiryFormatted = expiryFormatted,
                effectiveFormatted = effectiveFormatted,
                cardholderName = cardholderName,
                serviceCode = serviceCode,
                issuerCountry = issuerCountry ?: bankInfo?.country,
                currency = currency,
                atc = atc,
                lastOnlineAtc = lastOnlineAtc,
                pinTryCounter = pinTryCounter,
                luhnValid = luhnValid,
                cvcNote = CVC_EMVCO_NOTE,
                chipCvcLabel = chipCvcLabel,
                chipCvcData = chipCvcData,
                cryptogramHex = cryptogramHex,
                logStatusMessage = logStatusMessage,
                transactionLogs = txLogs
            )

            if (panFull != null) {
                notes.add("Selected AID $selectedAidHex (${appLabel ?: network})")
                if (txLogs.isNotEmpty()) {
                    notes.add(logStatusMessage)
                }
                return EmvReadResult(emvInfo = info, rawTlvEntries = rawNodes, notes = notes)
            } else if (bestFallbackInfo == null) {
                bestFallbackInfo = info
                bestFallbackNodes = rawNodes
            }
        }

        if (bestFallbackInfo != null) {
            notes.add("EMV application ${bestFallbackInfo.aid} selected, but PAN was not exposed over contactless")
        } else {
            notes.add("No readable EMV payment application found")
        }

        return EmvReadResult(emvInfo = bestFallbackInfo, rawTlvEntries = bestFallbackNodes, notes = notes)
    }

    /**
     * Dynamically populates a PDOL byte payload matching the exact requested tag lengths.
     */
    fun buildPdolData(
        pdolBytes: ByteArray?,
        dateYyMmDd: String = currentDateYyMmDd(),
        unpredictableNumber: ByteArray? = null,
        amountBytes: ByteArray = ByteArray(6),
        ttqBytes: ByteArray = DEFAULT_TTQ
    ): ByteArray {
        if (pdolBytes == null || pdolBytes.isEmpty()) return ByteArray(0)
        val entries = BerTlvParser.parseDol(pdolBytes)
        if (entries.isEmpty()) return ByteArray(0)

        val unBytes = unpredictableNumber ?: ByteArray(4).also { random.nextBytes(it) }
        val dateBcd = runCatching {
            val clean = dateYyMmDd.filter { it.isDigit() }.padStart(6, '0').take(6)
            clean.hexToByteArray()
        }.getOrElse { byteArrayOf(0x26, 0x01, 0x01) }

        val out = mutableListOf<Byte>()
        for (entry in entries) {
            if (entry.length <= 0) continue
            val chunk = when (entry.tagHex) {
                "9F66" -> fitBytes(ttqBytes, entry.length, leftPadZero = false)
                "9F02" -> fitBytes(amountBytes, entry.length, leftPadZero = true)
                "9F03" -> ByteArray(entry.length) // 0.00 Other Amount
                "9F1A" -> fitBytes(byteArrayOf(0x08, 0x40), entry.length, leftPadZero = true) // US (840)
                "95" -> ByteArray(entry.length) // TVR all zeros
                "5F2A" -> fitBytes(byteArrayOf(0x08, 0x40), entry.length, leftPadZero = true) // USD (840)
                "9A" -> fitBytes(dateBcd, entry.length, leftPadZero = true) // YYMMDD
                "9C" -> fitBytes(byteArrayOf(0x00), entry.length, leftPadZero = true) // Purchase
                "9F37" -> {
                    if (entry.length <= unBytes.size) {
                        unBytes.copyOf(entry.length)
                    } else {
                        val expanded = ByteArray(entry.length)
                        random.nextBytes(expanded)
                        unBytes.copyInto(expanded)
                        expanded
                    }
                }
                else -> ByteArray(entry.length)
            }
            for (b in chunk) out.add(b)
        }
        return out.toByteArray()
    }

    fun parseTrack2(track2Hex: String): Track2Data? {
        val clean = track2Hex.uppercase().replace(" ", "")
        val dIdx = clean.indexOf('D')
        if (dIdx <= 0) return null
        val pan = clean.substring(0, dIdx).filter { it.isDigit() }
        if (pan.length < 12) return null

        val afterD = clean.substring(dIdx + 1)
        val expiryFormatted = if (afterD.length >= 4 && afterD.take(4).all { it.isDigit() }) {
            val yy = afterD.substring(0, 2)
            val mm = afterD.substring(2, 4)
            if (mm.toIntOrNull() in 1..12) "$mm/$yy" else null
        } else null

        val serviceCode = if (afterD.length >= 7 && afterD.substring(4, 7).all { it.isDigit() }) {
            afterD.substring(4, 7)
        } else null

        val discretionaryData = if (afterD.length > 7) {
            afterD.substring(7)
                .trimEnd('F', 'f')
                .filter { it.isDigit() || it in 'A'..'F' || it in 'a'..'f' }
                .trimEnd('F', 'f')
                .takeIf { it.isNotBlank() }
        } else null

        return Track2Data(
            pan = pan,
            expiryFormatted = expiryFormatted,
            serviceCode = serviceCode,
            discretionaryData = discretionaryData
        )
    }

    private fun decodeTrack1Discretionary(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val isPrintableAscii = bytes.all { (it.toInt() and 0xFF) in 0x20..0x7E }
        val decoded = if (isPrintableAscii) {
            BerTlvParser.decodePrintableAscii(bytes)
        } else {
            bytes.toHexString()
        }
        return decoded.takeIf { it.isNotBlank() }
    }

    fun isLuhnValid(pan: String): Boolean {
        val digits = pan.filter { it.isDigit() }
        if (digits.length !in 12..19) return false
        var sum = 0
        var doubleNext = false
        for (i in digits.length - 1 downTo 0) {
            var digit = digits[i] - '0'
            if (doubleNext) {
                digit *= 2
                if (digit > 9) digit -= 9
            }
            sum += digit
            doubleNext = !doubleNext
        }
        return sum % 10 == 0
    }

    fun maskPan(pan: String): String {
        val digits = pan.filter { it.isDigit() }
        if (digits.length < 8) return digits
        val first4 = digits.take(4)
        val last4 = digits.takeLast(4)
        return when (digits.length) {
            15 -> "$first4 •••••• •$last4"
            16 -> "$first4 •••• •••• $last4"
            else -> {
                val middleCount = (digits.length - 8).coerceAtLeast(4)
                val middleGrouped = "•".repeat(middleCount).chunked(4).joinToString(" ")
                "$first4 $middleGrouped $last4"
            }
        }
    }

    fun detectNetwork(aidHex: String?, pan: String?): String {
        val aid = aidHex?.uppercase().orEmpty()
        when {
            aid.startsWith("A000000003") -> return "Visa"
            aid.startsWith("A0000000043060") -> return "Maestro"
            aid.startsWith("A000000004") -> return "Mastercard"
            aid.startsWith("A000000025") -> return "Amex"
            aid.startsWith("A000000152") -> return "Discover"
            aid.startsWith("A000000065") -> return "JCB"
            aid.startsWith("A000000333") -> return "UnionPay"
            aid.startsWith("A000000277") -> return "Interac"
            aid.startsWith("A000000524") -> return "RuPay"
        }

        val digits = pan?.filter { it.isDigit() }.orEmpty()
        if (digits.isEmpty()) return "EMV Payment"

        val prefix2 = digits.take(2).toIntOrNull()
        val prefix4 = digits.take(4).toIntOrNull()
        return when {
            digits.startsWith("4") -> "Visa"
            prefix2 in 51..55 || prefix4 in 2221..2720 -> "Mastercard"
            digits.startsWith("34") || digits.startsWith("37") -> "Amex"
            prefix4 in 3528..3589 -> "JCB"
            digits.startsWith("62") -> "UnionPay"
            digits.startsWith("6011") || digits.startsWith("65") ||
                digits.take(3).toIntOrNull() in 644..649 -> "Discover"
            prefix2 == 50 || prefix2 in 56..58 || digits.startsWith("6304") ||
                digits.startsWith("6759") || prefix4 in 6761..6763 -> "Maestro"
            else -> "EMV Payment"
        }
    }

    fun cleanCardholderName(rawName: String): String? {
        val trimmed = rawName.trim()
        if (trimmed.isBlank() || trimmed == "/") return null
        val slashParts = trimmed.split('/')
        val normalized = if (slashParts.size == 2) {
            val surname = slashParts[0].trim()
            val given = slashParts[1].trim()
            if (surname.isNotEmpty() && given.isNotEmpty()) {
                "$given $surname"
            } else {
                (surname + " " + given).trim()
            }
        } else {
            trimmed.replace('/', ' ')
        }
        return normalized.replace(Regex("\\s+"), " ").trim().takeIf { it.isNotEmpty() }
    }

    private fun readLogsFromSfi(
        channel: IsoDepChannel,
        sfi: Int,
        maxCount: Int,
        logDol: List<BerTlvParser.DolEntry>,
        fallbackCurrencyCode: Int?,
        firstRecordResponse: ApduResponse? = null,
        outLogs: MutableList<EmvTxLog>
    ) {
        for (rec in 1..maxCount) {
            val logResp = if (rec == 1 && firstRecordResponse != null) {
                firstRecordResponse
            } else {
                readRecord(channel, sfi, rec)
            }

            if (logResp.sw1 == 0x6A && (logResp.sw2 == 0x82 || logResp.sw2 == 0x83)) {
                break
            }
            if ((logResp.sw1 == 0x6A && logResp.sw2 == 0x81) || logResp.sw1 == 0x6D || logResp.sw1 == 0x6E) {
                break
            }
            if (!logResp.isSuccess || logResp.data.isEmpty()) {
                continue
            }

            parseTransactionLogRecord(
                rawResponse = logResp.data,
                logDol = logDol,
                fallbackCurrencyCode = fallbackCurrencyCode
            )?.let { outLogs.add(it) }
        }
    }

    private fun parseTransactionLogRecord(
        rawResponse: ByteArray,
        logDol: List<BerTlvParser.DolEntry>,
        fallbackCurrencyCode: Int?
    ): EmvTxLog? {
        if (rawResponse.isEmpty() || logDol.isEmpty()) return null
        val dolTotalLen = logDol.sumOf { it.length.coerceAtLeast(0) }
        if (dolTotalLen <= 0) return null

        // Unwrap 0x70 record wrapper when present and larger than raw DOL length
        val recordBytes: ByteArray = if ((rawResponse[0].toInt() and 0xFF) == 0x70 && rawResponse.size > dolTotalLen) {
            val parsed = BerTlvParser.parse(rawResponse)
            parsed.firstOrNull { it.tagHex == "70" }?.value?.takeIf { it.isNotEmpty() } ?: rawResponse
        } else {
            rawResponse
        }

        // Skip uninitialized cyclic slots (all 0x00 or all 0xFF)
        if (recordBytes.all { it == 0x00.toByte() } || recordBytes.all { (it.toInt() and 0xFF) == 0xFF }) {
            return null
        }

        var offset = 0
        var dateStr: String? = null
        var timeStr: String? = null
        var amount9F02: ByteArray? = null
        var amount9F03: ByteArray? = null
        var currencyCode: Int? = null
        var countryName: String? = null
        var txType: String? = null
        var merchantName: String? = null
        var logAtc: Int? = null
        var cryptogramInfo: String? = null

        for (entry in logDol) {
            if (entry.length <= 0 || offset + entry.length > recordBytes.size) break
            val slice = recordBytes.copyOfRange(offset, offset + entry.length)
            offset += entry.length
            when (entry.tagHex) {
                "9A" -> dateStr = formatLogDate(slice.toHexString())
                "9F21" -> timeStr = formatLogTime(slice.toHexString())
                "9F02" -> amount9F02 = slice
                "9F03" -> amount9F03 = slice
                "5F2A" -> currencyCode = BerTlvParser.decodeBcdInt(slice)?.takeIf { it > 0 }
                "9F1A" -> {
                    val c = BerTlvParser.decodeBcdInt(slice)?.takeIf { it > 0 }
                    countryName = c?.let { BerTlvParser.countryName(it) ?: it.toString() }
                }
                "9C" -> txType = parseTxType(slice)
                "9F4E" -> merchantName = BerTlvParser.decodePrintableAscii(slice).takeIf { it.isNotBlank() }
                "9F36" -> logAtc = parseUnsignedInt(slice)
                "9F27" -> cryptogramInfo = parseCryptogramInfo(slice)
            }
        }

        val minor9F02 = amount9F02?.let { decodeLogAmountMinorUnits(it) }
        val minor9F03 = amount9F03?.let { decodeLogAmountMinorUnits(it) }
        val amountMinorUnits: Long? = when {
            minor9F02 != null && minor9F02 > 0L -> minor9F02
            minor9F03 != null && minor9F03 > 0L -> minor9F03
            else -> minor9F02 ?: minor9F03
        }

        val hasValidDate = dateStr != null
        val hasNonZeroAmount = amountMinorUnits != null && amountMinorUnits > 0L
        val hasValidAtc = logAtc != null && logAtc > 0
        if (!hasValidDate && !hasNonZeroAmount && !hasValidAtc) {
            return null
        }

        val effectiveCurrencyCode = currencyCode ?: fallbackCurrencyCode
        val currencyInfo = effectiveCurrencyCode?.let { BerTlvParser.currencyInfo(it) }
        val minorUnits = currencyInfo?.minorUnits ?: 2
        val formattedAmount = amountMinorUnits?.let { formatAmountWithCommas(it, minorUnits) } ?: "0.00"
        val currencyLabel = currencyInfo?.code ?: effectiveCurrencyCode?.toString() ?: "UNK"

        return EmvTxLog(
            date = dateStr ?: "Unknown",
            time = timeStr,
            amount = formattedAmount,
            currency = currencyLabel,
            currencySymbol = currencyInfo?.symbol,
            country = countryName,
            txType = txType,
            merchantName = merchantName,
            atc = logAtc,
            cryptogramInfo = cryptogramInfo,
            rawHex = recordBytes.toHexString(" ")
        )
    }

    private fun decodeLogAmountMinorUnits(bytes: ByteArray): Long? {
        if (bytes.isEmpty()) return null
        // 4-byte unsigned binary format
        if (bytes.size == 4) {
            var acc = 0L
            for (b in bytes) {
                acc = (acc shl 8) or (b.toInt() and 0xFF).toLong()
            }
            return acc
        }
        // Standard 6-byte (or variable-length) packed BCD format
        val hex = bytes.toHexString()
        if (hex.all { it.isDigit() }) {
            return hex.toLongOrNull()
        }
        if (bytes.size <= 8) {
            var acc = 0L
            for (b in bytes) {
                acc = (acc shl 8) or (b.toInt() and 0xFF).toLong()
            }
            return acc
        }
        return null
    }

    private fun formatAmountWithCommas(value: Long, minorUnits: Int): String {
        val intFormatter = NumberFormat.getIntegerInstance(Locale.US)
        if (minorUnits <= 0) return intFormatter.format(value)
        var divisor = 1L
        repeat(minorUnits) { divisor *= 10L }
        val whole = value / divisor
        val frac = (value % divisor).toString().padStart(minorUnits, '0')
        return "${intFormatter.format(whole)}.$frac"
    }

    private fun formatLogDate(hex: String): String? {
        if (hex.length < 6) return null
        val yymmdd = hex.substring(0, 6)
        if (yymmdd == "000000" || !yymmdd.all { it.isDigit() }) return null
        val yy = yymmdd.substring(0, 2).toIntOrNull() ?: return null
        val mm = yymmdd.substring(2, 4).toIntOrNull() ?: return null
        val dd = yymmdd.substring(4, 6).toIntOrNull() ?: return null
        if (mm !in 1..12 || dd !in 1..31) return null
        val century = if (yy <= 79) "20" else "19"
        return "%s%02d-%02d-%02d".format(Locale.US, century, yy, mm, dd)
    }

    private fun formatLogTime(hex: String): String? {
        if (hex.length < 6) return null
        val hhmmss = hex.substring(0, 6)
        if (!hhmmss.all { it.isDigit() }) return null
        val hh = hhmmss.substring(0, 2).toIntOrNull() ?: return null
        val mm = hhmmss.substring(2, 4).toIntOrNull() ?: return null
        val ss = hhmmss.substring(4, 6).toIntOrNull() ?: return null
        if (hh !in 0..23 || mm !in 0..59 || ss !in 0..59) return null
        return "%02d:%02d:%02d".format(Locale.US, hh, mm, ss)
    }

    private fun parseTxType(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return when (bytes[0].toInt() and 0xFF) {
            0x00 -> "Purchase"
            0x01 -> "Cash Advance"
            0x09 -> "Cashback"
            0x20 -> "Refund"
            0x31 -> "Balance Inquiry"
            0x40 -> "Transfer"
            else -> null
        }
    }

    private fun parseCryptogramInfo(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        return when (bytes[0].toInt() and 0xC0) {
            0x00 -> "Declined"
            0x40 -> "Offline Approved"
            0x80 -> "Online Auth"
            else -> null
        }
    }

    private fun parseUnsignedInt(bytes: ByteArray): Int? {
        if (bytes.isEmpty() || bytes.size > 4) return null
        var acc = 0
        for (b in bytes) {
            acc = (acc shl 8) or (b.toInt() and 0xFF)
        }
        return acc
    }

    private fun parseEmvDateMmYy(hex: String): String? {
        if (hex.length < 4 || !hex.take(4).all { it.isDigit() }) return null
        val yy = hex.substring(0, 2)
        val mm = hex.substring(2, 4)
        if (mm.toIntOrNull() !in 1..12) return null
        return "$mm/$yy"
    }

    private fun parseAfl(aflBytes: ByteArray): List<AflEntry> {
        val entries = mutableListOf<AflEntry>()
        var i = 0
        while (i + 3 < aflBytes.size) {
            val sfi = (aflBytes[i].toInt() and 0xFF) ushr 3
            val startRec = aflBytes[i + 1].toInt() and 0xFF
            val endRec = aflBytes[i + 2].toInt() and 0xFF
            if (sfi in 1..30 && startRec >= 1 && endRec >= startRec) {
                entries.add(AflEntry(sfi = sfi, startRecord = startRec, endRecord = endRec))
            }
            i += 4
        }
        return entries
    }

    private fun buildSelectApdu(aid: ByteArray): ByteArray =
        byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aid.size.toByte()) + aid + byteArrayOf(0x00)

    private fun buildGpoApdu(pdolData: ByteArray): ByteArray {
        val gpoData = byteArrayOf(0x83.toByte()) + BerTlvParser.encodeBerLength(pdolData.size) + pdolData
        return byteArrayOf(
            0x80.toByte(),
            0xA8.toByte(),
            0x00,
            0x00,
            gpoData.size.toByte()
        ) + gpoData + byteArrayOf(0x00)
    }

    private fun readRecord(channel: IsoDepChannel, sfi: Int, record: Int): ApduResponse {
        val p2 = ((sfi shl 3) or 0x04).toByte()
        val apdu = byteArrayOf(0x00, 0xB2.toByte(), record.toByte(), p2, 0x00)
        return transceiveApdu(channel, apdu)
    }

    private fun queryGetDataIfMissing(
        channel: IsoDepChannel,
        collectedTags: MutableMap<String, ByteArray>,
        rawNodes: MutableList<TlvNode>,
        seenNodes: MutableSet<Pair<String, String>>,
        tagHex: String,
        tagByte1: Int,
        tagByte2: Int
    ) {
        if (collectedTags.containsKey(tagHex)) return
        getDataTag(channel, tagByte1, tagByte2)?.let { value ->
            collectedTags[tagHex] = value
            addSyntheticNode(rawNodes, seenNodes, tagHex, value)
        }
    }

    private fun getDataTag(channel: IsoDepChannel, tagByte1: Int, tagByte2: Int): ByteArray? {
        val apdu = byteArrayOf(0x80.toByte(), 0xCA.toByte(), tagByte1.toByte(), tagByte2.toByte(), 0x00)
        val resp = transceiveApdu(channel, apdu)
        if (!resp.isSuccess || resp.data.isEmpty()) return null
        val parsed = BerTlvParser.parse(resp.data)
        val expectedHex = "%02X%02X".format(tagByte1 and 0xFF, tagByte2 and 0xFF)
        val match = parsed.firstNotNullOfOrNull { it.findFirst(expectedHex) }
        return match?.value ?: resp.data
    }

    private fun transceiveApdu(channel: IsoDepChannel, command: ByteArray): ApduResponse {
        var raw = channel.transceive(command)
        if (raw.size < 2) {
            return ApduResponse(ByteArray(0), 0x6F, 0x00)
        }
        var sw1 = raw[raw.size - 2].toInt() and 0xFF
        var sw2 = raw[raw.size - 1].toInt() and 0xFF

        if (sw1 == 0x6C && command.size >= 5) {
            val retried = command.copyOf()
            retried[retried.size - 1] = sw2.toByte()
            raw = channel.transceive(retried)
            if (raw.size < 2) return ApduResponse(ByteArray(0), 0x6F, 0x00)
            sw1 = raw[raw.size - 2].toInt() and 0xFF
            sw2 = raw[raw.size - 1].toInt() and 0xFF
        }

        val buffer = raw.copyOfRange(0, raw.size - 2).toMutableList()

        var hops = 0
        while (sw1 == 0x61 && hops < 6) {
            hops++
            val getResp = byteArrayOf(0x00, 0xC0.toByte(), 0x00, 0x00, sw2.toByte())
            val next = channel.transceive(getResp)
            if (next.size < 2) {
                sw1 = 0x6F
                sw2 = 0x00
                break
            }
            for (i in 0 until next.size - 2) {
                buffer.add(next[i])
            }
            sw1 = next[next.size - 2].toInt() and 0xFF
            sw2 = next[next.size - 1].toInt() and 0xFF
        }

        return ApduResponse(data = buffer.toByteArray(), sw1 = sw1, sw2 = sw2)
    }

    private fun collectLeafValues(
        elements: List<BerTlvParser.BerTlvElement>,
        out: MutableMap<String, ByteArray>
    ) {
        for (el in elements) {
            if (!el.isConstructed || el.children.isEmpty()) {
                out.putIfAbsent(el.tagHex, el.value)
            }
            if (el.children.isNotEmpty()) {
                collectLeafValues(el.children, out)
            }
        }
    }

    private fun addSyntheticNode(
        rawNodes: MutableList<TlvNode>,
        seen: MutableSet<Pair<String, String>>,
        tagHex: String,
        value: ByteArray
    ) {
        val el = BerTlvParser.BerTlvElement(
            tagHex = tagHex,
            tagBytes = tagHex.hexToByteArray(),
            isConstructed = false,
            value = value,
            children = emptyList()
        )
        val node = TlvNode(
            tagHex = tagHex,
            name = BerTlvParser.tagName(tagHex),
            valueHex = el.valueHex,
            displayValue = BerTlvParser.formatDisplayValue(el)
        )
        val key = node.tagHex to node.valueHex
        if (seen.add(key)) {
            rawNodes.add(node)
        }
    }

    private fun fitBytes(source: ByteArray, targetLen: Int, leftPadZero: Boolean): ByteArray {
        if (source.size == targetLen) return source.copyOf()
        if (source.size > targetLen) {
            return if (leftPadZero) {
                source.copyOfRange(source.size - targetLen, source.size)
            } else {
                source.copyOf(targetLen)
            }
        }
        val out = ByteArray(targetLen)
        if (leftPadZero) {
            source.copyInto(out, destinationOffset = targetLen - source.size)
        } else {
            source.copyInto(out, destinationOffset = 0)
        }
        return out
    }

    private fun currentDateYyMmDd(): String =
        LocalDate.now().format(DateTimeFormatter.ofPattern("yyMMdd"))
}
