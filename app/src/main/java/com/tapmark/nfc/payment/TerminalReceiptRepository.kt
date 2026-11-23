package com.tapmark.nfc.payment

import android.content.Context
import com.tapmark.nfc.model.CardVisualStyle
import com.tapmark.nfc.model.GatewayEnvironment
import com.tapmark.nfc.model.GatewayProvider
import com.tapmark.nfc.model.PaymentMethodMode
import com.tapmark.nfc.model.ReceiptStatus
import com.tapmark.nfc.model.TerminalReceipt
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar

/**
 * Persists terminal payment receipts in app-private storage (`filesDir/tapmark_receipts.json`),
 * keeping up to 100 newest-first entries using atomic temp-file swaps.
 */
class TerminalReceiptRepository(context: Context) {

    private val receiptsFile = File(context.filesDir, FILE_NAME)
    private val lock = Any()

    fun loadAll(): List<TerminalReceipt> = synchronized(lock) {
        loadInternal()
    }

    fun saveReceipt(receipt: TerminalReceipt): List<TerminalReceipt> = synchronized(lock) {
        val current = loadInternal().filterNot { it.id == receipt.id }
        val updated = (listOf(receipt) + current).take(MAX_RECEIPTS)
        writeInternal(updated)
        updated
    }

    fun updateStatus(id: String, status: ReceiptStatus): List<TerminalReceipt> = synchronized(lock) {
        val current = loadInternal()
        var changed = false
        val updated = current.map { receipt ->
            if (receipt.id == id && receipt.status != status) {
                changed = true
                receipt.copy(status = status)
            } else {
                receipt
            }
        }
        if (changed) {
            writeInternal(updated)
        }
        updated
    }

    fun updateStatusByReference(
        referenceNumber: String,
        status: ReceiptStatus
    ): List<TerminalReceipt> = synchronized(lock) {
        val cleanRef = referenceNumber.trim()
        val current = loadInternal()
        if (cleanRef.isEmpty()) return@synchronized current

        var changed = false
        val updated = current.map { receipt ->
            if (receipt.referenceNumber.equals(cleanRef, ignoreCase = true) && receipt.status != status) {
                changed = true
                receipt.copy(status = status)
            } else {
                receipt
            }
        }
        if (changed) {
            writeInternal(updated)
        }
        updated
    }

    fun delete(id: String): List<TerminalReceipt> = synchronized(lock) {
        val current = loadInternal()
        val updated = current.filterNot { it.id == id }
        if (updated.size != current.size) {
            writeInternal(updated)
        }
        updated
    }

    fun clearAll(): List<TerminalReceipt> = synchronized(lock) {
        if (receiptsFile.exists()) {
            receiptsFile.delete()
        }
        val tempFile = File(receiptsFile.parentFile, "$FILE_NAME.tmp")
        if (tempFile.exists()) {
            tempFile.delete()
        }
        emptyList()
    }

    fun todayTotalCentavos(receipts: List<TerminalReceipt> = loadAll()): Long =
        computeTodayTotalCentavos(receipts, System.currentTimeMillis())

    private fun loadInternal(): List<TerminalReceipt> {
        if (!receiptsFile.exists()) return emptyList()
        return try {
            val raw = receiptsFile.readText(Charsets.UTF_8)
            if (raw.isBlank()) return emptyList()
            val array = JSONArray(raw)
            val result = ArrayList<TerminalReceipt>(array.length())
            for (i in 0 until array.length()) {
                val obj = array.optJSONObject(i) ?: continue
                receiptFromJson(obj)?.let { result.add(it) }
            }
            result.take(MAX_RECEIPTS)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun writeInternal(receipts: List<TerminalReceipt>) {
        val parent = receiptsFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        val array = JSONArray()
        for (receipt in receipts.take(MAX_RECEIPTS)) {
            array.put(receiptToJson(receipt))
        }
        val payload = array.toString()
        val tempFile = File(receiptsFile.parentFile, "$FILE_NAME.tmp")
        try {
            tempFile.writeText(payload, Charsets.UTF_8)
            if (receiptsFile.exists()) {
                receiptsFile.delete()
            }
            if (!tempFile.renameTo(receiptsFile)) {
                receiptsFile.writeText(payload, Charsets.UTF_8)
                tempFile.delete()
            }
        } catch (_: Exception) {
            runCatching { receiptsFile.writeText(payload, Charsets.UTF_8) }
            runCatching { if (tempFile.exists()) tempFile.delete() }
        }
    }

    private fun receiptToJson(receipt: TerminalReceipt): JSONObject = JSONObject().apply {
        put("id", receipt.id)
        put("referenceNumber", receipt.referenceNumber)
        put("createdAtMillis", receipt.createdAtMillis)
        put("amountCentavos", receipt.amountCentavos)
        put("currency", receipt.currency)
        put("description", receipt.description)
        put("merchantName", receipt.merchantName)
        put("provider", receipt.provider.name)
        put("environment", receipt.environment.name)
        put("methodMode", receipt.methodMode.name)
        put("status", receipt.status.name)
        put("isSandboxDemo", receipt.isSandboxDemo)
        put("bankName", receipt.bankName ?: JSONObject.NULL)
        put("cardNetwork", receipt.cardNetwork ?: JSONObject.NULL)
        put("panMasked", receipt.panMasked ?: JSONObject.NULL)
        put("cardVisualStyle", receipt.cardVisualStyle?.name ?: JSONObject.NULL)
        put("emvAid", receipt.emvAid ?: JSONObject.NULL)
        put("emvAtc", receipt.emvAtc ?: JSONObject.NULL)
        put("emvCryptogram", receipt.emvCryptogram ?: JSONObject.NULL)
        put("authCode", receipt.authCode ?: JSONObject.NULL)
        put("checkoutSessionId", receipt.checkoutSessionId ?: JSONObject.NULL)
        put("checkoutUrl", receipt.checkoutUrl ?: JSONObject.NULL)
    }

    private fun receiptFromJson(obj: JSONObject): TerminalReceipt? {
        val id = obj.optStringOrNull("id")?.takeIf { it.isNotBlank() } ?: return null
        val provider = obj.optStringOrNull("provider")
            ?.let { name -> GatewayProvider.entries.firstOrNull { it.name == name } }
            ?: GatewayProvider.DEMO_SANDBOX
        val environment = obj.optStringOrNull("environment")
            ?.let { name -> GatewayEnvironment.entries.firstOrNull { it.name == name } }
            ?: GatewayEnvironment.SANDBOX
        val methodMode = obj.optStringOrNull("methodMode")
            ?.let { name -> PaymentMethodMode.entries.firstOrNull { it.name == name } }
            ?: PaymentMethodMode.NFC_TAP_DEMO
        val status = obj.optStringOrNull("status")
            ?.let { name -> ReceiptStatus.entries.firstOrNull { it.name == name } }
            ?: ReceiptStatus.PENDING
        val visualStyle = obj.optStringOrNull("cardVisualStyle")
            ?.let { name -> CardVisualStyle.entries.firstOrNull { it.name == name } }

        return TerminalReceipt(
            id = id,
            referenceNumber = obj.optString("referenceNumber", ""),
            createdAtMillis = obj.optLong("createdAtMillis", 0L),
            amountCentavos = obj.optLong("amountCentavos", 0L),
            currency = obj.optString("currency", "PHP"),
            description = obj.optString("description", ""),
            merchantName = obj.optString("merchantName", "Tapmark Terminal"),
            provider = provider,
            environment = environment,
            methodMode = methodMode,
            status = status,
            isSandboxDemo = obj.optBoolean("isSandboxDemo", false),
            bankName = obj.optStringOrNull("bankName"),
            cardNetwork = obj.optStringOrNull("cardNetwork"),
            panMasked = obj.optStringOrNull("panMasked"),
            cardVisualStyle = visualStyle,
            emvAid = obj.optStringOrNull("emvAid"),
            emvAtc = obj.optIntOrNull("emvAtc"),
            emvCryptogram = obj.optStringOrNull("emvCryptogram"),
            authCode = obj.optStringOrNull("authCode"),
            checkoutSessionId = obj.optStringOrNull("checkoutSessionId"),
            checkoutUrl = obj.optStringOrNull("checkoutUrl")
        )
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)

    private fun JSONObject.optIntOrNull(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    companion object {
        private const val FILE_NAME = "tapmark_receipts.json"
        private const val MAX_RECEIPTS = 100

        internal fun computeTodayTotalCentavos(
            receipts: List<TerminalReceipt>,
            nowMillis: Long = System.currentTimeMillis()
        ): Long {
            val nowCal = Calendar.getInstance().apply { timeInMillis = nowMillis }
            val nowYear = nowCal.get(Calendar.YEAR)
            val nowDay = nowCal.get(Calendar.DAY_OF_YEAR)
            val receiptCal = Calendar.getInstance()

            var total = 0L
            for (receipt in receipts) {
                if (receipt.status != ReceiptStatus.APPROVED && receipt.status != ReceiptStatus.PAID) continue
                if (receipt.createdAtMillis <= 0L || receipt.amountCentavos <= 0L) continue
                receiptCal.timeInMillis = receipt.createdAtMillis
                if (receiptCal.get(Calendar.YEAR) == nowYear &&
                    receiptCal.get(Calendar.DAY_OF_YEAR) == nowDay
                ) {
                    total += receipt.amountCentavos
                }
            }
            return total
        }
    }
}
