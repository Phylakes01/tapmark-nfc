package com.tapmark.nfc.nfc

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URI
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * Privacy-preserving BIN/IIN bank issuer resolver.
 * Checks in-memory cache -> offline curated DB / bundled asset DB / URL & label matching ->
 * optional live 6-digit-BIN lookup (HandyAPI -> Binlist fallback) on Dispatchers.IO.
 * Never sends more than 6 digits of a PAN over the network.
 */
object BankResolver {

    private const val CONNECT_TIMEOUT_MS = 2500
    private const val READ_TIMEOUT_MS = 2500
    private const val USER_AGENT = "Tapmark/1.1"

    private val cache = ConcurrentHashMap<String, BankIssuerInfo>()

    suspend fun resolve(
        bin: String?,
        iinTag42: String?,
        appLabel: String?,
        issuerUrl: String?
    ): BankIssuerInfo? {
        val bin6 = BankBinDatabase.strictSixDigitBin(bin)
            ?: BankBinDatabase.strictSixDigitBin(iinTag42)

        if (bin6 != null) {
            cache[bin6]?.let { return it }
        }

        // 1. Instant curated offline BIN table
        if (bin6 != null) {
            BankBinDatabase.lookupCuratedBin(bin6)?.let { curated ->
                cache[bin6] = curated
                return curated
            }
        }

        // 2. Bundled offline asset DB (on IO dispatcher) + URL / app-label offline heuristics
        return withContext(Dispatchers.IO) {
            if (bin6 != null) {
                BankBinDatabase.lookupAssetBin(bin6)?.let { assetHit ->
                    cache[bin6] = assetHit
                    return@withContext assetHit
                }
            }

            BankBinDatabase.lookupOffline(
                bin = bin,
                iinTag42 = iinTag42,
                appLabel = appLabel,
                issuerUrl = issuerUrl
            )?.let { offlineHit ->
                return@withContext offlineHit
            }

            // 3. Live 6-digit BIN API lookup (strictly 6 digits only)
            if (bin6 == null) return@withContext null

            val handyInfo = fetchJson("https://data.handyapi.com/bin/$bin6")
                ?.let { parseHandyApiResponse(it) }
            if (handyInfo != null) {
                cache[bin6] = handyInfo
                return@withContext handyInfo
            }

            val binlistInfo = fetchJson(
                urlString = "https://lookup.binlist.net/$bin6",
                headers = mapOf("Accept-Version" to "3")
            )?.let { parseBinlistResponse(it) }
            if (binlistInfo != null) {
                cache[bin6] = binlistInfo
                return@withContext binlistInfo
            }

            null
        }
    }

    fun clearCache() {
        cache.clear()
    }

    fun parseHandyApiResponse(json: String): BankIssuerInfo? {
        val root = parseJsonObject(json) ?: return null
        val status = root.string("Status") ?: root.string("status")
        if (status != null && !status.equals("SUCCESS", ignoreCase = true)) {
            return null
        }

        val rawIssuer = (root.string("Issuer") ?: root.string("issuer") ?: root.string("Bank"))
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) && !it.equals("N/A", ignoreCase = true) }
            ?: return null

        val cardType = formatCardType(root.string("Type") ?: root.string("type"))
        val cardTier = formatCardTier(root.string("CardTier") ?: root.string("cardTier"))

        val countryObj = root.obj("Country") ?: root.obj("country")
        val country = (countryObj?.string("Name") ?: countryObj?.string("name") ?: root.string("Country"))
            ?.let { BankBinDatabase.normalizeCountryName(it) }
            ?: BankBinDatabase.countryFromAlpha2(countryObj?.string("A2") ?: countryObj?.string("a2"))

        return BankIssuerInfo(
            bankName = BankBinDatabase.normalizeIssuerName(rawIssuer),
            cardType = cardType,
            cardTier = cardTier,
            country = country
        )
    }

    fun parseBinlistResponse(json: String): BankIssuerInfo? {
        val root = parseJsonObject(json) ?: return null
        val bankObj = root.obj("bank")
        val rawBank = bankObj?.string("name")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }

        val bankName = if (rawBank != null) {
            BankBinDatabase.normalizeIssuerName(rawBank)
        } else {
            bankObj?.string("url")?.let { BankBinDatabase.extractBankFromIssuerUrl(it) } ?: return null
        }

        val typeFromStr = formatCardType(root.string("type"))
        val cardType = typeFromStr ?: if (root.bool("prepaid") == true) "Prepaid" else null
        val cardTier = formatCardTier(root.string("brand"))

        val countryObj = root.obj("country")
        val country = countryObj?.string("name")
            ?.let { BankBinDatabase.normalizeCountryName(it) }
            ?: BankBinDatabase.countryFromAlpha2(countryObj?.string("alpha2"))

        return BankIssuerInfo(
            bankName = bankName,
            cardType = cardType,
            cardTier = cardTier,
            country = country
        )
    }

    private fun fetchJson(
        urlString: String,
        headers: Map<String, String> = emptyMap()
    ): String? {
        var conn: HttpURLConnection? = null
        return try {
            conn = (URI(urlString).toURL().openConnection() as? HttpURLConnection) ?: return null
            conn.requestMethod = "GET"
            conn.connectTimeout = CONNECT_TIMEOUT_MS
            conn.readTimeout = READ_TIMEOUT_MS
            conn.instanceFollowRedirects = true
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            for ((k, v) in headers) {
                conn.setRequestProperty(k, v)
            }
            val code = conn.responseCode
            if (code !in 200..299) return null
            val stream = conn.inputStream ?: return null
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { reader ->
                val sb = StringBuilder()
                val buf = CharArray(1024)
                var total = 0
                while (true) {
                    val n = reader.read(buf)
                    if (n <= 0) break
                    total += n
                    if (total > 64 * 1024) break
                    sb.append(buf, 0, n)
                }
                sb.toString()
            }
        } catch (_: Exception) {
            null
        } finally {
            runCatching { conn?.disconnect() }
        }
    }

    private fun formatCardType(raw: String?): String? {
        val cleaned = raw?.trim()?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) } ?: return null
        return when (cleaned.uppercase(Locale.US)) {
            "DEBIT" -> "Debit"
            "CREDIT" -> "Credit"
            "PREPAID" -> "Prepaid"
            "CHARGE", "CHARGE CARD" -> "Charge"
            else -> cleaned.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }
    }

    private fun formatCardTier(raw: String?): String? {
        val cleaned = raw?.trim()?.takeIf {
            it.isNotEmpty() &&
                !it.equals("null", ignoreCase = true) &&
                !it.equals("none", ignoreCase = true) &&
                !it.equals("n/a", ignoreCase = true)
        } ?: return null
        return cleaned.split(Regex("\\s+")).joinToString(" ") { w ->
            when (w.uppercase(Locale.US)) {
                "VIP", "ATM" -> w.uppercase(Locale.US)
                else -> w.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
            }
        }
    }

    // ---- Pure-Kotlin minimal JSON object parser (no android.jar org.json stub dependency) ----

    private class JsonObj(val map: Map<String, Any?>) {
        fun string(key: String): String? = map[key] as? String
        fun bool(key: String): Boolean? = map[key] as? Boolean
        @Suppress("UNCHECKED_CAST")
        fun obj(key: String): JsonObj? = (map[key] as? Map<String, Any?>)?.let { JsonObj(it) }
    }

    private fun parseJsonObject(json: String): JsonObj? {
        val parser = MiniJsonParser(json)
        val parsed = parser.parseRoot() ?: return null
        @Suppress("UNCHECKED_CAST")
        return (parsed as? Map<String, Any?>)?.let { JsonObj(it) }
    }

    private class MiniJsonParser(private val src: String) {
        private var pos = 0

        fun parseRoot(): Any? {
            skipWhitespace()
            if (pos >= src.length || src[pos] != '{') return null
            return runCatching { readValue() }.getOrNull()
        }

        private fun readValue(): Any? {
            skipWhitespace()
            if (pos >= src.length) throw IllegalStateException("EOF")
            return when (val ch = src[pos]) {
                '{' -> readObject()
                '[' -> readArray()
                '"' -> readString()
                't', 'f' -> readBoolean()
                'n' -> readNull()
                '-', in '0'..'9' -> readNumber()
                else -> throw IllegalStateException("Unexpected char $ch")
            }
        }

        private fun readObject(): Map<String, Any?> {
            pos++ // '{'
            val map = LinkedHashMap<String, Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == '}') {
                pos++
                return map
            }
            while (pos < src.length) {
                skipWhitespace()
                if (pos >= src.length || src[pos] != '"') throw IllegalStateException("Expected key")
                val key = readString()
                skipWhitespace()
                if (pos >= src.length || src[pos] != ':') throw IllegalStateException("Expected ':'")
                pos++
                val value = readValue()
                map[key] = value
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == '}') {
                    pos++
                    return map
                } else {
                    throw IllegalStateException("Expected ',' or '}'")
                }
            }
            throw IllegalStateException("Unterminated object")
        }

        private fun readArray(): List<Any?> {
            pos++ // '['
            val list = ArrayList<Any?>()
            skipWhitespace()
            if (pos < src.length && src[pos] == ']') {
                pos++
                return list
            }
            while (pos < src.length) {
                list.add(readValue())
                skipWhitespace()
                if (pos < src.length && src[pos] == ',') {
                    pos++
                } else if (pos < src.length && src[pos] == ']') {
                    pos++
                    return list
                } else {
                    throw IllegalStateException("Expected ',' or ']'")
                }
            }
            throw IllegalStateException("Unterminated array")
        }

        private fun readString(): String {
            pos++ // opening '"'
            val sb = StringBuilder()
            while (pos < src.length) {
                val ch = src[pos++]
                when (ch) {
                    '"' -> return sb.toString()
                    '\\' -> {
                        if (pos >= src.length) throw IllegalStateException("Dangling escape")
                        when (val esc = src[pos++]) {
                            '"', '\\', '/' -> sb.append(esc)
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            'n' -> sb.append('\n')
                            'r' -> sb.append('\r')
                            't' -> sb.append('\t')
                            'u' -> {
                                if (pos + 4 > src.length) throw IllegalStateException("Bad unicode")
                                val code = src.substring(pos, pos + 4).toInt(16)
                                sb.append(code.toChar())
                                pos += 4
                            }
                            else -> sb.append(esc)
                        }
                    }
                    else -> sb.append(ch)
                }
            }
            throw IllegalStateException("Unterminated string")
        }

        private fun readBoolean(): Boolean {
            return if (src.startsWith("true", pos)) {
                pos += 4
                true
            } else if (src.startsWith("false", pos)) {
                pos += 5
                false
            } else {
                throw IllegalStateException("Bad boolean")
            }
        }

        private fun readNull(): Any? {
            if (src.startsWith("null", pos)) {
                pos += 4
                return null
            }
            throw IllegalStateException("Bad null")
        }

        private fun readNumber(): String {
            val start = pos
            while (pos < src.length && src[pos] in "0123456789+-.eE") {
                pos++
            }
            return src.substring(start, pos)
        }

        private fun skipWhitespace() {
            while (pos < src.length && src[pos].isWhitespace()) {
                pos++
            }
        }
    }
}
