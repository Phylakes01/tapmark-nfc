package com.tapmark.nfc.data

import android.content.Context
import com.tapmark.nfc.model.CardCategory
import com.tapmark.nfc.model.CardVisualStyle
import com.tapmark.nfc.model.EmvCardInfo
import com.tapmark.nfc.model.EmvTxLog
import com.tapmark.nfc.model.MemoryDumpInfo
import com.tapmark.nfc.model.ParsedNdefRecord
import com.tapmark.nfc.model.RfTechDetails
import com.tapmark.nfc.model.ScannedCard
import com.tapmark.nfc.model.TlvNode
import com.tapmark.nfc.nfc.BankBinDatabase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists scanned NFC cards as JSON in app-private storage (`filesDir/tapmark_history.json`),
 * stripping full PANs and chip CVC/iCVV data before disk persistence and capping history at 50 newest-first entries.
 */
class CardHistoryRepository(context: Context) {

    private val historyFile = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    init {
        runCatching { BankBinDatabase.registerContext(context) }
    }

    fun loadAll(): List<ScannedCard> = synchronized(lock) {
        loadInternal()
    }

    fun saveScan(card: ScannedCard): List<ScannedCard> = synchronized(lock) {
        val sanitized = card.copy(
            isSample = false,
            emvInfo = card.emvInfo?.copy(
                panFull = null,
                chipCvcData = null
            )
        )
        val current = loadInternal().filterNot { it.id == sanitized.id }
        val updated = (listOf(sanitized) + current).take(MAX_HISTORY_ENTRIES)
        writeInternal(updated)
        updated
    }

    fun updateLabel(id: String, label: String?): List<ScannedCard> = synchronized(lock) {
        val cleanedLabel = label?.trim()?.takeIf { it.isNotEmpty() }
        val current = loadInternal()
        val updated = current.map { card ->
            if (card.id == id) card.copy(customLabel = cleanedLabel) else card
        }
        writeInternal(updated)
        updated
    }

    fun delete(id: String): List<ScannedCard> = synchronized(lock) {
        val updated = loadInternal().filterNot { it.id == id }
        writeInternal(updated)
        updated
    }

    fun clearAll(): List<ScannedCard> = synchronized(lock) {
        if (historyFile.exists()) {
            historyFile.delete()
        }
        emptyList()
    }

    private fun loadInternal(): List<ScannedCard> {
        if (!historyFile.exists()) return emptyList()
        return try {
            val raw = historyFile.readText(Charsets.UTF_8)
            if (raw.isBlank()) return emptyList()
            val array = JSONArray(raw)
            val result = ArrayList<ScannedCard>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                cardFromJson(obj)?.let { result.add(it) }
            }
            result.take(MAX_HISTORY_ENTRIES)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeInternal(cards: List<ScannedCard>) {
        val parent = historyFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val array = JSONArray()
        for (card in cards.take(MAX_HISTORY_ENTRIES)) {
            val sanitized = card.copy(
                emvInfo = card.emvInfo?.copy(
                    panFull = null,
                    chipCvcData = null
                )
            )
            array.put(cardToJson(sanitized))
        }
        val payload = array.toString()
        val tempFile = File(historyFile.parentFile, "$FILE_NAME.tmp")
        try {
            tempFile.writeText(payload, Charsets.UTF_8)
            if (historyFile.exists()) {
                historyFile.delete()
            }
            if (!tempFile.renameTo(historyFile)) {
                historyFile.writeText(payload, Charsets.UTF_8)
                tempFile.delete()
            }
        } catch (_: Exception) {
            historyFile.writeText(payload, Charsets.UTF_8)
        }
    }

    private fun cardToJson(card: ScannedCard): JSONObject = JSONObject().apply {
        put("id", card.id)
        put("scannedAtMillis", card.scannedAtMillis)
        put("customLabel", card.customLabel ?: JSONObject.NULL)
        put("isSample", card.isSample)
        put("category", card.category.name)
        put("visualStyle", card.visualStyle.name)
        put("cardTitle", card.cardTitle)
        put("cardSubtitle", card.cardSubtitle)
        put("uidHex", card.uidHex)
        put("uidReversedHex", card.uidReversedHex)
        put("uidDecimal", card.uidDecimal)
        put("manufacturer", card.manufacturer)
        put("chipGuess", card.chipGuess)
        put("emvInfo", card.emvInfo?.let { emvToJson(it) } ?: JSONObject.NULL)
        put("ndefRecords", JSONArray().apply {
            card.ndefRecords.forEach { put(ndefToJson(it)) }
        })
        put("rfDetails", rfToJson(card.rfDetails))
        put("memoryInfo", card.memoryInfo?.let { memoryToJson(it) } ?: JSONObject.NULL)
        put("rawTlvEntries", JSONArray().apply {
            card.rawTlvEntries.forEach { put(tlvToJson(it)) }
        })
        put("readNotes", JSONArray(card.readNotes))
    }

    private fun cardFromJson(obj: JSONObject): ScannedCard? {
        val id = obj.optStringOrNull("id") ?: return null
        val category = obj.optStringOrNull("category")
            ?.let { name -> CardCategory.entries.firstOrNull { it.name == name } }
            ?: CardCategory.GENERIC
        val visualStyle = obj.optStringOrNull("visualStyle")
            ?.let { name -> CardVisualStyle.entries.firstOrNull { it.name == name } }
            ?: CardVisualStyle.COPPER_LINEN

        val ndefArray = obj.optJSONArray("ndefRecords")
        val ndefList = buildList {
            if (ndefArray != null) {
                for (i in 0 until ndefArray.length()) {
                    ndefArray.optJSONObject(i)?.let { ndefFromJson(it) }?.let { add(it) }
                }
            }
        }

        val tlvArray = obj.optJSONArray("rawTlvEntries")
        val tlvList = buildList {
            if (tlvArray != null) {
                for (i in 0 until tlvArray.length()) {
                    tlvArray.optJSONObject(i)?.let { tlvFromJson(it) }?.let { add(it) }
                }
            }
        }

        val notesArray = obj.optJSONArray("readNotes")
        val notes = buildList {
            if (notesArray != null) {
                for (i in 0 until notesArray.length()) {
                    notesArray.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }

        return ScannedCard(
            id = id,
            scannedAtMillis = obj.optLong("scannedAtMillis", 0L),
            customLabel = obj.optStringOrNull("customLabel"),
            isSample = obj.optBoolean("isSample", false),
            category = category,
            visualStyle = visualStyle,
            cardTitle = obj.optString("cardTitle", "NFC Card"),
            cardSubtitle = obj.optString("cardSubtitle", ""),
            uidHex = obj.optString("uidHex", "N/A"),
            uidReversedHex = obj.optString("uidReversedHex", "N/A"),
            uidDecimal = obj.optString("uidDecimal", "0"),
            manufacturer = obj.optString("manufacturer", "Unknown"),
            chipGuess = obj.optString("chipGuess", "Generic NFC Tag"),
            emvInfo = obj.optJSONObject("emvInfo")?.let { emvFromJson(it) },
            ndefRecords = ndefList,
            rfDetails = obj.optJSONObject("rfDetails")?.let { rfFromJson(it) }
                ?: RfTechDetails(emptyList(), null, null, null, null, null, null, null, null, null, null),
            memoryInfo = obj.optJSONObject("memoryInfo")?.let { memoryFromJson(it) },
            rawTlvEntries = tlvList,
            readNotes = notes
        )
    }

    private fun emvToJson(emv: EmvCardInfo): JSONObject = JSONObject().apply {
        put("network", emv.network)
        put("bankName", emv.bankName ?: JSONObject.NULL)
        put("cardType", emv.cardType ?: JSONObject.NULL)
        put("cardTier", emv.cardTier ?: JSONObject.NULL)
        put("bin", emv.bin ?: JSONObject.NULL)
        put("appLabel", emv.appLabel ?: JSONObject.NULL)
        put("aid", emv.aid)
        put("panMasked", emv.panMasked)
        put("panFull", JSONObject.NULL) // Never persist full PAN to disk
        put("expiryFormatted", emv.expiryFormatted ?: JSONObject.NULL)
        put("effectiveFormatted", emv.effectiveFormatted ?: JSONObject.NULL)
        put("cardholderName", emv.cardholderName ?: JSONObject.NULL)
        put("serviceCode", emv.serviceCode ?: JSONObject.NULL)
        put("issuerCountry", emv.issuerCountry ?: JSONObject.NULL)
        put("currency", emv.currency ?: JSONObject.NULL)
        put("atc", emv.atc ?: JSONObject.NULL)
        put("lastOnlineAtc", emv.lastOnlineAtc ?: JSONObject.NULL)
        put("pinTryCounter", emv.pinTryCounter ?: JSONObject.NULL)
        put("luhnValid", emv.luhnValid ?: JSONObject.NULL)
        put("cvcNote", emv.cvcNote ?: JSONObject.NULL)
        put("chipCvcLabel", emv.chipCvcLabel ?: JSONObject.NULL)
        put("chipCvcData", JSONObject.NULL) // Never persist chip CVC/iCVV data to disk
        put("cryptogramHex", emv.cryptogramHex ?: JSONObject.NULL)
        put("logStatusMessage", emv.logStatusMessage ?: JSONObject.NULL)
        put("transactionLogs", JSONArray().apply {
            emv.transactionLogs.forEach { log ->
                put(JSONObject().apply {
                    put("date", log.date)
                    put("time", log.time ?: JSONObject.NULL)
                    put("amount", log.amount)
                    put("currency", log.currency)
                    put("currencySymbol", log.currencySymbol ?: JSONObject.NULL)
                    put("country", log.country ?: JSONObject.NULL)
                    put("txType", log.txType ?: JSONObject.NULL)
                    put("merchantName", log.merchantName ?: JSONObject.NULL)
                    put("atc", log.atc ?: JSONObject.NULL)
                    put("cryptogramInfo", log.cryptogramInfo ?: JSONObject.NULL)
                    put("rawHex", log.rawHex ?: JSONObject.NULL)
                })
            }
        })
    }

    private fun emvFromJson(obj: JSONObject): EmvCardInfo {
        val logsArray = obj.optJSONArray("transactionLogs")
        val logs = buildList {
            if (logsArray != null) {
                for (i in 0 until logsArray.length()) {
                    val item = logsArray.optJSONObject(i) ?: continue
                    add(
                        EmvTxLog(
                            date = item.optString("date", ""),
                            time = item.optStringOrNull("time"),
                            amount = item.optString("amount", ""),
                            currency = item.optString("currency", ""),
                            currencySymbol = item.optStringOrNull("currencySymbol"),
                            country = item.optStringOrNull("country"),
                            txType = item.optStringOrNull("txType"),
                            merchantName = item.optStringOrNull("merchantName"),
                            atc = item.optIntOrNull("atc"),
                            cryptogramInfo = item.optStringOrNull("cryptogramInfo"),
                            rawHex = item.optStringOrNull("rawHex")
                        )
                    )
                }
            }
        }
        return EmvCardInfo(
            network = obj.optString("network", "EMV Payment"),
            bankName = obj.optStringOrNull("bankName"),
            cardType = obj.optStringOrNull("cardType"),
            cardTier = obj.optStringOrNull("cardTier"),
            bin = obj.optStringOrNull("bin"),
            appLabel = obj.optStringOrNull("appLabel"),
            aid = obj.optString("aid", ""),
            panMasked = obj.optString("panMasked", "•••• •••• •••• ••••"),
            panFull = null,
            expiryFormatted = obj.optStringOrNull("expiryFormatted"),
            effectiveFormatted = obj.optStringOrNull("effectiveFormatted"),
            cardholderName = obj.optStringOrNull("cardholderName"),
            serviceCode = obj.optStringOrNull("serviceCode"),
            issuerCountry = obj.optStringOrNull("issuerCountry"),
            currency = obj.optStringOrNull("currency"),
            atc = obj.optIntOrNull("atc"),
            lastOnlineAtc = obj.optIntOrNull("lastOnlineAtc"),
            pinTryCounter = obj.optIntOrNull("pinTryCounter"),
            luhnValid = obj.optBooleanOrNull("luhnValid"),
            cvcNote = obj.optStringOrNull("cvcNote"),
            chipCvcLabel = obj.optStringOrNull("chipCvcLabel"),
            chipCvcData = null,
            cryptogramHex = obj.optStringOrNull("cryptogramHex"),
            logStatusMessage = obj.optStringOrNull("logStatusMessage"),
            transactionLogs = logs
        )
    }

    private fun ndefToJson(rec: ParsedNdefRecord): JSONObject = JSONObject().apply {
        put("typeLabel", rec.typeLabel)
        put("title", rec.title)
        put("value", rec.value)
        put("actionUrl", rec.actionUrl ?: JSONObject.NULL)
        put("rawHex", rec.rawHex)
        put("byteSize", rec.byteSize)
    }

    private fun ndefFromJson(obj: JSONObject): ParsedNdefRecord = ParsedNdefRecord(
        typeLabel = obj.optString("typeLabel", "Raw"),
        title = obj.optString("title", "Record"),
        value = obj.optString("value", ""),
        actionUrl = obj.optStringOrNull("actionUrl"),
        rawHex = obj.optString("rawHex", ""),
        byteSize = obj.optInt("byteSize", 0)
    )

    private fun rfToJson(rf: RfTechDetails): JSONObject = JSONObject().apply {
        put("technologies", JSONArray(rf.technologies))
        put("atqaHex", rf.atqaHex ?: JSONObject.NULL)
        put("sakHex", rf.sakHex ?: JSONObject.NULL)
        put("historicalBytesHex", rf.historicalBytesHex ?: JSONObject.NULL)
        put("maxTransceiveBytes", rf.maxTransceiveBytes ?: JSONObject.NULL)
        put("ndefCapacityBytes", rf.ndefCapacityBytes ?: JSONObject.NULL)
        put("ndefUsedBytes", rf.ndefUsedBytes ?: JSONObject.NULL)
        put("isWritable", rf.isWritable ?: JSONObject.NULL)
        put("sectorCount", rf.sectorCount ?: JSONObject.NULL)
        put("blockCount", rf.blockCount ?: JSONObject.NULL)
        put("memoryBytes", rf.memoryBytes ?: JSONObject.NULL)
    }

    private fun rfFromJson(obj: JSONObject): RfTechDetails {
        val techArray = obj.optJSONArray("technologies")
        val techs = buildList {
            if (techArray != null) {
                for (i in 0 until techArray.length()) {
                    techArray.optString(i)?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
            }
        }
        return RfTechDetails(
            technologies = techs,
            atqaHex = obj.optStringOrNull("atqaHex"),
            sakHex = obj.optStringOrNull("sakHex"),
            historicalBytesHex = obj.optStringOrNull("historicalBytesHex"),
            maxTransceiveBytes = obj.optIntOrNull("maxTransceiveBytes"),
            ndefCapacityBytes = obj.optIntOrNull("ndefCapacityBytes"),
            ndefUsedBytes = obj.optIntOrNull("ndefUsedBytes"),
            isWritable = obj.optBooleanOrNull("isWritable"),
            sectorCount = obj.optIntOrNull("sectorCount"),
            blockCount = obj.optIntOrNull("blockCount"),
            memoryBytes = obj.optIntOrNull("memoryBytes")
        )
    }

    private fun memoryToJson(mem: MemoryDumpInfo): JSONObject = JSONObject().apply {
        put("summary", mem.summary)
        put("asciiPreview", mem.asciiPreview ?: JSONObject.NULL)
        put("pagesHex", JSONArray(mem.pagesHex))
    }

    private fun memoryFromJson(obj: JSONObject): MemoryDumpInfo {
        val pagesArray = obj.optJSONArray("pagesHex")
        val pages = buildList {
            if (pagesArray != null) {
                for (i in 0 until pagesArray.length()) {
                    pagesArray.optString(i)?.let { add(it) }
                }
            }
        }
        return MemoryDumpInfo(
            summary = obj.optString("summary", ""),
            asciiPreview = obj.optStringOrNull("asciiPreview"),
            pagesHex = pages
        )
    }

    private fun tlvToJson(node: TlvNode): JSONObject = JSONObject().apply {
        put("tagHex", node.tagHex)
        put("name", node.name)
        put("valueHex", node.valueHex)
        put("displayValue", node.displayValue)
    }

    private fun tlvFromJson(obj: JSONObject): TlvNode = TlvNode(
        tagHex = obj.optString("tagHex", ""),
        name = obj.optString("name", ""),
        valueHex = obj.optString("valueHex", ""),
        displayValue = obj.optString("displayValue", "")
    )

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optBooleanOrNull(key: String): Boolean? =
        if (!has(key) || isNull(key)) null else optBoolean(key)

    companion object {
        private const val FILE_NAME = "tapmark_history.json"
        private const val MAX_HISTORY_ENTRIES = 50
    }
}
