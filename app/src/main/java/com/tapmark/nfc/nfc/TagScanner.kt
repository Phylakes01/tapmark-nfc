package com.tapmark.nfc.nfc

import android.nfc.Tag
import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.nfc.tech.NfcA
import android.nfc.tech.NfcB
import android.nfc.tech.NfcF
import android.nfc.tech.NfcV
import com.tapmark.nfc.model.CardCategory
import com.tapmark.nfc.model.CardVisualStyle
import com.tapmark.nfc.model.EmvCardInfo
import com.tapmark.nfc.model.MemoryDumpInfo
import com.tapmark.nfc.model.ParsedNdefRecord
import com.tapmark.nfc.model.RfTechDetails
import com.tapmark.nfc.model.ScanResult
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.model.TlvNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigInteger
import java.util.UUID

/**
 * Coordinates safe, read-only NFC tag inspection on Dispatchers.IO across ISO-DEP/EMV,
 * NDEF, NfcA/B/F/V, MIFARE Classic, and MIFARE Ultralight / NTAG chips.
 */
object TagScanner {

    private const val TECH_TIMEOUT_MS = 4000

    suspend fun readTag(tag: Tag): ScanResult = withContext(Dispatchers.IO) {
        try {
            val uid = tag.id ?: ByteArray(0)
            val uidHex = if (uid.isEmpty()) "N/A" else uid.toHexString(":")
            val uidReversedHex = if (uid.isEmpty()) "N/A" else uid.reversedArray().toHexString(":")
            val uidDecimal = if (uid.isEmpty()) "0" else BigInteger(1, uid).toString()

            val techSimpleNames = tag.techList.map { it.substringAfterLast('.') }.distinct()
            val readNotes = mutableListOf<String>()

            // Non-blocking discovery metadata (no connect() required)
            val nfcA = NfcA.get(tag)
            val nfcB = NfcB.get(tag)
            val nfcF = NfcF.get(tag)
            val nfcV = NfcV.get(tag)
            val isoDep = IsoDep.get(tag)
            val ndef = Ndef.get(tag)
            val mifareClassic = MifareClassic.get(tag)
            val mifareUltralight = MifareUltralight.get(tag)

            val atqa = nfcA?.atqa
            val sak = nfcA?.sak
            val atqaHex = atqa?.toHexString(" ")
            val sakHex = sak?.let { "%02X".format(it.toInt() and 0xFF) }

            val historicalBytes = isoDep?.historicalBytes ?: isoDep?.hiLayerResponse
            val historicalBytesHex = historicalBytes?.takeIf { it.isNotEmpty() }?.toHexString(" ")

            val maxTransceiveBytes = listOfNotNull(
                isoDep?.maxTransceiveLength,
                nfcA?.maxTransceiveLength,
                nfcB?.maxTransceiveLength,
                nfcF?.maxTransceiveLength,
                nfcV?.maxTransceiveLength,
                mifareClassic?.maxTransceiveLength,
                mifareUltralight?.maxTransceiveLength
            ).maxOrNull()

            nfcB?.let { b ->
                val appData = b.applicationData?.toHexString(" ")
                val protoInfo = b.protocolInfo?.toHexString(" ")
                if (!appData.isNullOrBlank() || !protoInfo.isNullOrBlank()) {
                    readNotes.add("ISO 14443-3B AppData=${appData ?: "-"}, ProtocolInfo=${protoInfo ?: "-"}")
                }
            }

            nfcF?.let { f ->
                val sysCode = f.systemCode?.toHexString() ?: "-"
                val pmm = f.manufacturer?.toHexString(" ") ?: "-"
                readNotes.add("FeliCa SystemCode=$sysCode, PMm=$pmm")
            }

            nfcV?.let { v ->
                readNotes.add(
                    "ISO 15693 DSFID=0x%02X, ResponseFlags=0x%02X".format(
                        v.dsfId.toInt() and 0xFF,
                        v.responseFlags.toInt() and 0xFF
                    )
                )
            }

            // 1. NDEF inspection
            var ndefRecords: List<ParsedNdefRecord> = emptyList()
            var ndefCapacityBytes: Int? = ndef?.maxSize?.takeIf { it > 0 }
            var ndefUsedBytes: Int? = null
            var isWritable: Boolean? = ndef?.isWritable

            if (ndef != null) {
                try {
                    ndef.connect()
                    val msg = runCatching { ndef.ndefMessage }.getOrNull() ?: ndef.cachedNdefMessage
                    if (msg != null) {
                        ndefRecords = NdefParser.parseAndroidMessage(msg)
                        ndefUsedBytes = runCatching { msg.toByteArray().size }.getOrNull()
                        readNotes.add("Decoded ${ndefRecords.size} NDEF record(s) (${ndef.type})")
                    } else {
                        readNotes.add("NDEF formatted (${ndef.type}), empty message")
                    }
                } catch (tle: TagLostException) {
                    throw tle
                } catch (e: IOException) {
                    val cached = ndef.cachedNdefMessage
                    if (cached != null) {
                        ndefRecords = NdefParser.parseAndroidMessage(cached)
                        ndefUsedBytes = runCatching { cached.toByteArray().size }.getOrNull()
                        readNotes.add("Used cached NDEF message (${e.message ?: "I/O fallback"})")
                    } else {
                        readNotes.add("Could not read NDEF message: ${e.message ?: "I/O error"}")
                    }
                } finally {
                    runCatching { ndef.close() }
                }
            }

            // 2. ISO-DEP / EMV inspection
            var emvInfo: EmvCardInfo? = null
            var rawTlvEntries: List<TlvNode> = emptyList()

            if (isoDep != null) {
                try {
                    isoDep.connect()
                    runCatching { isoDep.timeout = TECH_TIMEOUT_MS }
                    val channel = object : IsoDepChannel {
                        override fun transceive(command: ByteArray): ByteArray = isoDep.transceive(command)
                    }
                    val emvResult = EmvReader.readCard(channel)
                    emvInfo = emvResult.emvInfo
                    rawTlvEntries = emvResult.rawTlvEntries
                    readNotes.addAll(emvResult.notes)
                } catch (tle: TagLostException) {
                    throw tle
                } catch (e: IOException) {
                    readNotes.add("ISO-DEP session ended: ${e.message ?: "I/O error"}")
                } finally {
                    runCatching { isoDep.close() }
                }
            }

            // 3. Memory peek for MIFARE Ultralight / NTAG21x or MIFARE Classic or NfcV
            var memoryInfo: MemoryDumpInfo? = null
            var inferredMemoryBytes: Int? = mifareClassic?.size?.takeIf { it > 0 }
            val sectorCount: Int? = mifareClassic?.sectorCount?.takeIf { it > 0 }
            val blockCount: Int? = mifareClassic?.blockCount?.takeIf { it > 0 }

            if (mifareUltralight != null && emvInfo == null) {
                val ulResult = readUltralightSafe(mifareUltralight, readNotes)
                memoryInfo = ulResult.memoryInfo
                if (ulResult.detectedMemoryBytes != null) {
                    inferredMemoryBytes = ulResult.detectedMemoryBytes
                }
                if (ulResult.detectedUserNdefCapacity != null && ndefCapacityBytes == null) {
                    ndefCapacityBytes = ulResult.detectedUserNdefCapacity
                }
                if (ndefRecords.isEmpty() && ulResult.rawDump.isNotEmpty()) {
                    val extractedNdef = NdefParser.extractNdefFromType2UserMemory(ulResult.rawDump)
                    if (extractedNdef != null && extractedNdef.isNotEmpty()) {
                        ndefRecords = NdefParser.parseMessageBytes(extractedNdef)
                        ndefUsedBytes = extractedNdef.size
                    }
                }
            } else if (mifareClassic != null && emvInfo == null) {
                memoryInfo = readMifareClassicSafe(mifareClassic, readNotes)
            } else if (nfcV != null && emvInfo == null) {
                val nfcVResult = readNfcVSafe(nfcV, uid, readNotes)
                memoryInfo = nfcVResult.memoryInfo
                if (nfcVResult.memoryBytes != null) {
                    inferredMemoryBytes = nfcVResult.memoryBytes
                }
            }

            // Enrich EMV issuer info via BankResolver strictly after all NFC handles are closed
            if (emvInfo != null) {
                val currentEmv = emvInfo
                val issuerUrl = rawTlvEntries.firstOrNull { it.tagHex == "5F50" }?.displayValue
                    ?.takeIf { it.isNotBlank() }
                val iinTag42 = rawTlvEntries.firstOrNull { it.tagHex == "42" }?.valueHex
                    ?.takeIf { it.isNotBlank() }
                val resolvedBank = BankResolver.resolve(
                    bin = currentEmv.bin ?: currentEmv.panFull?.take(6),
                    iinTag42 = iinTag42,
                    appLabel = currentEmv.appLabel,
                    issuerUrl = issuerUrl
                )
                if (resolvedBank != null) {
                    emvInfo = currentEmv.copy(
                        bankName = resolvedBank.bankName.takeIf { it.isNotBlank() } ?: currentEmv.bankName,
                        cardType = currentEmv.cardType ?: resolvedBank.cardType,
                        cardTier = currentEmv.cardTier ?: resolvedBank.cardTier,
                        issuerCountry = currentEmv.issuerCountry ?: resolvedBank.country
                    )
                    readNotes.add("Issuer bank resolved: ${emvInfo?.bankName}")
                }
            }

            // 4. Identify chip & category
            val chipId = ChipIdentifier.identifyChip(
                uid = uid,
                technologies = techSimpleNames,
                atqa = atqa,
                sak = sak,
                historicalBytes = historicalBytes,
                ndefMaxSize = ndefCapacityBytes,
                memoryBytes = inferredMemoryBytes,
                ultralightType = mifareUltralight?.type,
                classicType = mifareClassic?.type,
                classicSize = mifareClassic?.size,
                feliCaSystemCode = nfcF?.systemCode,
                hasEmv = emvInfo != null,
                hasNdefRecords = ndefRecords.isNotEmpty()
            )

            val category = when {
                emvInfo != null -> CardCategory.PAYMENT
                chipId.suggestedCategory == CardCategory.TRANSIT -> CardCategory.TRANSIT
                ndefRecords.isNotEmpty() -> CardCategory.SMART_TAG
                else -> chipId.suggestedCategory
            }

            val visualStyle = resolveVisualStyle(category, emvInfo)

            val (cardTitle, cardSubtitle) = buildCardHeadlines(
                emvInfo = emvInfo,
                ndefRecords = ndefRecords,
                chipId = chipId,
                uidHex = uidHex
            )

            val rfDetails = RfTechDetails(
                technologies = techSimpleNames,
                atqaHex = atqaHex,
                sakHex = sakHex,
                historicalBytesHex = historicalBytesHex,
                maxTransceiveBytes = maxTransceiveBytes,
                ndefCapacityBytes = ndefCapacityBytes,
                ndefUsedBytes = ndefUsedBytes,
                isWritable = isWritable,
                sectorCount = sectorCount,
                blockCount = blockCount,
                memoryBytes = inferredMemoryBytes
            )

            val card = ScannedCard(
                id = UUID.randomUUID().toString(),
                scannedAtMillis = System.currentTimeMillis(),
                customLabel = null,
                isSample = false,
                category = category,
                visualStyle = visualStyle,
                cardTitle = cardTitle,
                cardSubtitle = cardSubtitle,
                uidHex = uidHex,
                uidReversedHex = uidReversedHex,
                uidDecimal = uidDecimal,
                manufacturer = chipId.manufacturer,
                chipGuess = chipId.chipGuess,
                emvInfo = emvInfo,
                ndefRecords = ndefRecords,
                rfDetails = rfDetails,
                memoryInfo = memoryInfo,
                rawTlvEntries = rawTlvEntries,
                readNotes = readNotes
            )

            ScanResult.Success(card)
        } catch (_: TagLostException) {
            ScanResult.TagLost
        } catch (e: Exception) {
            ScanResult.Error(e.message ?: "Unable to read NFC tag")
        }
    }

    private data class UltralightPeekResult(
        val memoryInfo: MemoryDumpInfo?,
        val rawDump: ByteArray,
        val detectedUserNdefCapacity: Int?,
        val detectedMemoryBytes: Int?
    )

    private fun readUltralightSafe(
        ul: MifareUltralight,
        readNotes: MutableList<String>
    ): UltralightPeekResult {
        val pagesHex = mutableListOf<String>()
        val rawStream = ByteArrayOutputStream()
        var userCapacity: Int? = null
        var totalBytesEstimate: Int? = null

        try {
            ul.connect()
            runCatching { ul.timeout = TECH_TIMEOUT_MS }

            // Read first 4 pages (pages 0..3) including UID and Capability Container (page 3)
            val headerChunk = ul.readPages(0)
            var maxPages = when (ul.type) {
                MifareUltralight.TYPE_ULTRALIGHT_C -> 44
                else -> 40
            }

            if (headerChunk.size >= 16) {
                // Page 3 is CC (bytes 12..15)
                if ((headerChunk[12].toInt() and 0xFF) == 0xE1) {
                    val ccSize = headerChunk[14].toInt() and 0xFF
                    if (ccSize > 0) {
                        userCapacity = ccSize * 8
                        when (userCapacity) {
                            48 -> {
                                maxPages = 16
                                totalBytesEstimate = 64
                            }
                            in 136..160 -> {
                                maxPages = 40
                                totalBytesEstimate = 180
                            }
                            in 480..520 -> {
                                maxPages = 130
                                totalBytesEstimate = 540
                            }
                            in 860..900 -> {
                                maxPages = 226
                                totalBytesEstimate = 924
                            }
                        }
                    }
                }
                appendPages(headerChunk, startPage = 0, maxPages = maxPages, pagesHex = pagesHex, rawStream = rawStream)
            }

            // Cap single-tap read loop at 136 pages (~34 transceives) for crisp responsiveness
            val readLimit = maxPages.coerceAtMost(136)
            var page = 4
            while (page < readLimit) {
                try {
                    val chunk = ul.readPages(page)
                    if (chunk.isEmpty()) break
                    appendPages(chunk, startPage = page, maxPages = readLimit, pagesHex = pagesHex, rawStream = rawStream)
                    page += 4
                } catch (tle: TagLostException) {
                    if (pagesHex.isEmpty()) throw tle
                    readNotes.add("Tag moved after reading ${pagesHex.size} page(s)")
                    break
                } catch (_: IOException) {
                    // End of readable or protected pages (e.g. PWD/PACK config pages)
                    break
                }
            }

            val rawBytes = rawStream.toByteArray()
            val userBytes = if (rawBytes.size > 16) rawBytes.copyOfRange(16, rawBytes.size) else rawBytes
            val ascii = buildAsciiPreview(userBytes)
            val truncatedNote = if (maxPages > readLimit) " (showing first $readLimit of $maxPages pages)" else ""
            val summary = "Read ${pagesHex.size} pages (${rawBytes.size} bytes)$truncatedNote"
            readNotes.add("MIFARE Ultralight/NTAG peek: $summary")

            return UltralightPeekResult(
                memoryInfo = if (pagesHex.isNotEmpty()) MemoryDumpInfo(summary, ascii, pagesHex) else null,
                rawDump = rawBytes,
                detectedUserNdefCapacity = userCapacity,
                detectedMemoryBytes = totalBytesEstimate
            )
        } catch (tle: TagLostException) {
            if (pagesHex.isEmpty()) throw tle
            val rawBytes = rawStream.toByteArray()
            return UltralightPeekResult(
                memoryInfo = MemoryDumpInfo("Partial read (${pagesHex.size} pages)", buildAsciiPreview(rawBytes), pagesHex),
                rawDump = rawBytes,
                detectedUserNdefCapacity = userCapacity,
                detectedMemoryBytes = totalBytesEstimate
            )
        } catch (e: IOException) {
            readNotes.add("Ultralight memory peek skipped: ${e.message ?: "I/O error"}")
            return UltralightPeekResult(null, ByteArray(0), userCapacity, totalBytesEstimate)
        } finally {
            runCatching { ul.close() }
        }
    }

    private fun appendPages(
        chunk: ByteArray,
        startPage: Int,
        maxPages: Int,
        pagesHex: MutableList<String>,
        rawStream: ByteArrayOutputStream
    ) {
        var offset = 0
        var pageIndex = startPage
        while (offset + 4 <= chunk.size && pageIndex < maxPages) {
            val pageBytes = chunk.copyOfRange(offset, offset + 4)
            pagesHex.add("%02X: %s".format(pageIndex, pageBytes.toHexString(" ")))
            rawStream.write(pageBytes)
            offset += 4
            pageIndex++
        }
    }

    private fun readMifareClassicSafe(
        mc: MifareClassic,
        readNotes: MutableList<String>
    ): MemoryDumpInfo? {
        val pagesHex = mutableListOf<String>()
        val rawStream = ByteArrayOutputStream()
        var authenticatedSectors = 0
        val totalSectors = mc.sectorCount
        val keysToTry = listOf(
            MifareClassic.KEY_DEFAULT,
            MifareClassic.KEY_MIFARE_APPLICATION_DIRECTORY,
            MifareClassic.KEY_NFC_FORUM
        )

        try {
            mc.connect()
            runCatching { mc.timeout = TECH_TIMEOUT_MS }

            for (sector in 0 until totalSectors) {
                var authed = false
                for (key in keysToTry) {
                    authed = runCatching { mc.authenticateSectorWithKeyA(sector, key) }.getOrDefault(false)
                    if (authed) break
                }
                if (!authed) {
                    authed = runCatching {
                        mc.authenticateSectorWithKeyB(sector, MifareClassic.KEY_DEFAULT)
                    }.getOrDefault(false)
                }

                if (!authed) continue
                authenticatedSectors++

                val firstBlock = mc.sectorToBlock(sector)
                val blockCountInSector = mc.getBlockCountInSector(sector)
                for (b in 0 until blockCountInSector) {
                    val blockIndex = firstBlock + b
                    val data = runCatching { mc.readBlock(blockIndex) }.getOrNull() ?: break
                    pagesHex.add("S%02d B%02d: %s".format(sector, blockIndex, data.toHexString(" ")))
                    rawStream.write(data)
                }
            }

            readNotes.add(
                "MIFARE Classic read-only check: $authenticatedSectors/$totalSectors sector(s) accessible with standard keys"
            )

            if (pagesHex.isEmpty()) return null
            val rawBytes = rawStream.toByteArray()
            val summary = "$authenticatedSectors/$totalSectors sectors unlocked · ${pagesHex.size} blocks read"
            return MemoryDumpInfo(
                summary = summary,
                asciiPreview = buildAsciiPreview(rawBytes),
                pagesHex = pagesHex
            )
        } catch (tle: TagLostException) {
            if (pagesHex.isEmpty()) throw tle
            val rawBytes = rawStream.toByteArray()
            readNotes.add("MIFARE Classic partial read: $authenticatedSectors/$totalSectors sectors before lift")
            return MemoryDumpInfo(
                summary = "Partial: $authenticatedSectors/$totalSectors sectors (${pagesHex.size} blocks)",
                asciiPreview = buildAsciiPreview(rawBytes),
                pagesHex = pagesHex
            )
        } catch (e: IOException) {
            readNotes.add("MIFARE Classic read skipped: ${e.message ?: "authentication unavailable"}")
            return null
        } finally {
            runCatching { mc.close() }
        }
    }

    private data class NfcVPeekResult(
        val memoryInfo: MemoryDumpInfo?,
        val memoryBytes: Int?
    )

    private fun readNfcVSafe(
        nfcV: NfcV,
        uid: ByteArray,
        readNotes: MutableList<String>
    ): NfcVPeekResult {
        val pagesHex = mutableListOf<String>()
        val rawStream = ByteArrayOutputStream()
        var totalMemoryBytes: Int? = null

        try {
            nfcV.connect()

            // Optional addressed GET SYSTEM INFORMATION (command 0x2B)
            if (uid.size == 8) {
                val sysInfoCmd = byteArrayOf(0x22, 0x2B) + uid
                val sysResp = runCatching { nfcV.transceive(sysInfoCmd) }.getOrNull()
                if (sysResp != null && sysResp.size >= 13 && sysResp[0].toInt() == 0x00) {
                    val infoFlags = sysResp[1].toInt() and 0xFF
                    var idx = 10 // flags(1) + infoFlags(1) + uid(8)
                    if ((infoFlags and 0x01) != 0) idx++ // DSFID
                    if ((infoFlags and 0x02) != 0) idx++ // AFI
                    if ((infoFlags and 0x04) != 0 && idx + 1 < sysResp.size) {
                        val numBlocks = (sysResp[idx].toInt() and 0xFF) + 1
                        val blockSize = (sysResp[idx + 1].toInt() and 0x1F) + 1
                        totalMemoryBytes = numBlocks * blockSize
                        readNotes.add("ISO 15693 memory: $numBlocks blocks × $blockSize bytes ($totalMemoryBytes bytes)")
                    }
                }
            }

            // Safe read-only peek of first 8 blocks (unaddressed READ SINGLE BLOCK 0x02 0x20 <blk>)
            for (blk in 0 until 8) {
                val cmd = if (uid.size == 8) {
                    byteArrayOf(0x22, 0x20) + uid + byteArrayOf(blk.toByte())
                } else {
                    byteArrayOf(0x02, 0x20, blk.toByte())
                }
                val resp = runCatching { nfcV.transceive(cmd) }.getOrNull() ?: break
                if (resp.size < 2 || resp[0].toInt() != 0x00) break
                val blockBytes = resp.copyOfRange(1, resp.size)
                pagesHex.add("B%02d: %s".format(blk, blockBytes.toHexString(" ")))
                rawStream.write(blockBytes)
            }

            val rawBytes = rawStream.toByteArray()
            val memoryInfo = if (pagesHex.isNotEmpty()) {
                MemoryDumpInfo(
                    summary = "Read ${pagesHex.size} ISO 15693 block(s) (${rawBytes.size} bytes)",
                    asciiPreview = buildAsciiPreview(rawBytes),
                    pagesHex = pagesHex
                )
            } else null

            return NfcVPeekResult(memoryInfo, totalMemoryBytes)
        } catch (tle: TagLostException) {
            if (pagesHex.isEmpty()) throw tle
            return NfcVPeekResult(null, totalMemoryBytes)
        } catch (e: IOException) {
            readNotes.add("ISO 15693 peek skipped: ${e.message ?: "I/O error"}")
            return NfcVPeekResult(null, totalMemoryBytes)
        } finally {
            runCatching { nfcV.close() }
        }
    }

    private fun resolveVisualStyle(
        category: CardCategory,
        emvInfo: EmvCardInfo?
    ): CardVisualStyle {
        if (emvInfo != null) {
            return when (emvInfo.network) {
                "Visa" -> CardVisualStyle.VISA_SLATE
                "Mastercard", "Maestro" -> CardVisualStyle.MASTERCARD_CHARCOAL
                "Amex" -> CardVisualStyle.AMEX_EMERALD
                else -> CardVisualStyle.COPPER_LINEN
            }
        }
        return when (category) {
            CardCategory.PAYMENT -> CardVisualStyle.COPPER_LINEN
            CardCategory.TRANSIT -> CardVisualStyle.TRANSIT_INDIGO
            CardCategory.ACCESS -> CardVisualStyle.ACCESS_STONE
            CardCategory.SMART_TAG -> CardVisualStyle.TAG_TERRACOTTA
            CardCategory.GENERIC -> CardVisualStyle.COPPER_LINEN
        }
    }

    private fun buildCardHeadlines(
        emvInfo: EmvCardInfo?,
        ndefRecords: List<ParsedNdefRecord>,
        chipId: ChipIdentification,
        uidHex: String
    ): Pair<String, String> {
        if (emvInfo != null) {
            val title = emvInfo.bankName?.takeIf { it.isNotBlank() }
                ?: emvInfo.appLabel?.takeIf { it.isNotBlank() }
                ?: "${emvInfo.network} Card"
            val tail = emvInfo.panFull?.takeLast(4)?.let { "•••• $it" } ?: emvInfo.aid
            val subtitle = "${emvInfo.appLabel?.takeIf { it.isNotBlank() } ?: emvInfo.network} · $tail"
            return title to subtitle
        }
        if (ndefRecords.isNotEmpty()) {
            val first = ndefRecords.first()
            val title = first.title.ifBlank { chipId.chipGuess }
            val countLabel = if (ndefRecords.size == 1) "1 NDEF record" else "${ndefRecords.size} NDEF records"
            return title to "${chipId.chipGuess} · $countLabel"
        }
        return chipId.chipGuess to "${chipId.manufacturer} · $uidHex"
    }

    private fun buildAsciiPreview(bytes: ByteArray): String? {
        if (bytes.isEmpty()) return null
        val sb = StringBuilder()
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            sb.append(if (v in 0x20..0x7E) v.toChar() else '.')
        }
        val preview = sb.toString().trim('.')
        return preview.takeIf { it.any { ch -> ch.isLetterOrDigit() } }?.take(240)
    }
}
