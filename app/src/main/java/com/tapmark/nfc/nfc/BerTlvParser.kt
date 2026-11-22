package com.tapmark.nfc.nfc

import com.tapmark.nfc.model.TlvNode
import java.nio.charset.StandardCharsets

/**
 * Recursive ISO/IEC 7816-4 / EMV BER-TLV parser, DOL parser, and EMV dictionary.
 */
object BerTlvParser {

    data class BerTlvElement(
        val tagHex: String,
        val tagBytes: ByteArray,
        val isConstructed: Boolean,
        val value: ByteArray,
        val children: List<BerTlvElement>
    ) {
        val valueHex: String
            get() = value.toHexString()

        fun findFirst(targetTagHex: String): BerTlvElement? {
            val normalized = targetTagHex.uppercase()
            if (tagHex == normalized) return this
            for (child in children) {
                val found = child.findFirst(normalized)
                if (found != null) return found
            }
            return null
        }

        fun findAll(targetTagHex: String): List<BerTlvElement> {
            val normalized = targetTagHex.uppercase()
            val results = mutableListOf<BerTlvElement>()
            collectMatching(normalized, results)
            return results
        }

        private fun collectMatching(targetTagHex: String, out: MutableList<BerTlvElement>) {
            if (tagHex == targetTagHex) {
                out.add(this)
            }
            for (child in children) {
                child.collectMatching(targetTagHex, out)
            }
        }

        fun flatten(): List<BerTlvElement> {
            val out = mutableListOf<BerTlvElement>()
            collectAll(out)
            return out
        }

        private fun collectAll(out: MutableList<BerTlvElement>) {
            out.add(this)
            for (child in children) {
                child.collectAll(out)
            }
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is BerTlvElement) return false
            return tagHex == other.tagHex &&
                tagBytes.contentEquals(other.tagBytes) &&
                isConstructed == other.isConstructed &&
                value.contentEquals(other.value) &&
                children == other.children
        }

        override fun hashCode(): Int {
            var result = tagHex.hashCode()
            result = 31 * result + tagBytes.contentHashCode()
            result = 31 * result + isConstructed.hashCode()
            result = 31 * result + value.contentHashCode()
            result = 31 * result + children.hashCode()
            return result
        }
    }

    data class DolEntry(
        val tagHex: String,
        val length: Int
    )

    data class CurrencyInfo(
        val code: String,
        val symbol: String,
        val name: String,
        val minorUnits: Int = 2
    )

    val EMV_TAG_NAMES: Map<String, String> = mapOf(
        "42" to "Issuer Identification Number (IIN)",
        "4F" to "Application Identifier (AID)",
        "50" to "Application Label",
        "57" to "Track 2 Equivalent Data",
        "5A" to "Application PAN",
        "5F20" to "Cardholder Name",
        "5F24" to "Application Expiration Date",
        "5F25" to "Application Effective Date",
        "5F28" to "Issuer Country Code",
        "5F2A" to "Transaction Currency Code",
        "5F2D" to "Language Preference",
        "5F30" to "Service Code",
        "5F34" to "Application PAN Sequence Number",
        "5F50" to "Issuer URL",
        "5F55" to "Issuer Country Code (alpha2)",
        "61" to "Application Template",
        "6F" to "File Control Information (FCI) Template",
        "70" to "EMV Proprietary Record Template",
        "77" to "Response Message Template Format 2",
        "80" to "Response Message Template Format 1",
        "82" to "Application Interchange Profile (AIP)",
        "84" to "Dedicated File (DF) Name",
        "87" to "Application Priority Indicator",
        "88" to "Short File Identifier (SFI)",
        "8C" to "Card Risk Management DOL 1 (CDOL1)",
        "8D" to "Card Risk Management DOL 2 (CDOL2)",
        "8E" to "Cardholder Verification Method (CVM) List",
        "8F" to "Certification Authority Public Key Index",
        "90" to "Issuer Public Key Certificate",
        "92" to "Issuer Public Key Remainder",
        "93" to "Signed Static Application Data",
        "94" to "Application File Locator (AFL)",
        "95" to "Terminal Verification Results (TVR)",
        "9A" to "Transaction Date",
        "9C" to "Transaction Type",
        "9F02" to "Amount, Authorised",
        "9F03" to "Amount, Other",
        "9F07" to "Application Usage Control",
        "9F08" to "Application Version Number",
        "9F0D" to "Issuer Action Code - Default",
        "9F0E" to "Issuer Action Code - Denial",
        "9F0F" to "Issuer Action Code - Online",
        "9F10" to "Issuer Application Data",
        "9F11" to "Issuer Code Table Index",
        "9F12" to "Application Preferred Name",
        "9F13" to "Last Online ATC Register",
        "9F17" to "PIN Try Counter",
        "9F1A" to "Terminal Country Code",
        "9F1F" to "Track 1 Discretionary Data",
        "9F21" to "Transaction Time",
        "9F26" to "Application Cryptogram",
        "9F27" to "Cryptogram Information Data",
        "9F32" to "Issuer Public Key Exponent",
        "9F36" to "Application Transaction Counter (ATC)",
        "9F37" to "Unpredictable Number",
        "9F38" to "Processing Options Data Object List (PDOL)",
        "9F42" to "Application Currency Code",
        "9F44" to "Application Currency Exponent",
        "9F46" to "ICC Public Key Certificate",
        "9F47" to "ICC Public Key Exponent",
        "9F48" to "ICC Public Key Remainder",
        "9F49" to "Dynamic Data Authentication DOL (DDOL)",
        "9F4A" to "Static Data Authentication Tag List",
        "9F4C" to "ICC Dynamic Number",
        "9F4D" to "Log Entry",
        "9F4E" to "Merchant Name and Location",
        "9F4F" to "Log Format",
        "9F5D" to "Available Offline Spending Amount",
        "9F60" to "CVC3 (Track 1)",
        "9F61" to "CVC3 (Track 2)",
        "9F66" to "Terminal Transaction Qualifiers (TTQ)",
        "9F6C" to "Card Transaction Qualifiers (CTQ)",
        "9F6E" to "Form Factor Indicator / Third Party Data",
        "A5" to "FCI Proprietary Template",
        "BF0C" to "FCI Issuer Discretionary Data",
        "DF4D" to "Mastercard Log Entry",
        "DF4F" to "Mastercard Log Format"
    )

    /** ISO 3166-1 numeric country codes mapped to country names. */
    val ISO_COUNTRY_MAP: Map<Int, String> = mapOf(
        36 to "Australia",
        40 to "Austria",
        56 to "Belgium",
        76 to "Brazil",
        100 to "Bulgaria",
        124 to "Canada",
        152 to "Chile",
        156 to "China",
        158 to "Taiwan",
        170 to "Colombia",
        191 to "Croatia",
        196 to "Cyprus",
        203 to "Czechia",
        208 to "Denmark",
        233 to "Estonia",
        246 to "Finland",
        250 to "France",
        276 to "Germany",
        300 to "Greece",
        344 to "Hong Kong",
        348 to "Hungary",
        352 to "Iceland",
        356 to "India",
        360 to "Indonesia",
        372 to "Ireland",
        376 to "Israel",
        380 to "Italy",
        392 to "Japan",
        398 to "Kazakhstan",
        410 to "South Korea",
        414 to "Kuwait",
        428 to "Latvia",
        440 to "Lithuania",
        442 to "Luxembourg",
        446 to "Macau",
        458 to "Malaysia",
        484 to "Mexico",
        504 to "Morocco",
        528 to "Netherlands",
        554 to "New Zealand",
        566 to "Nigeria",
        578 to "Norway",
        604 to "Peru",
        608 to "Philippines",
        616 to "Poland",
        620 to "Portugal",
        634 to "Qatar",
        642 to "Romania",
        643 to "Russia",
        682 to "Saudi Arabia",
        702 to "Singapore",
        703 to "Slovakia",
        704 to "Vietnam",
        705 to "Slovenia",
        710 to "South Africa",
        724 to "Spain",
        752 to "Sweden",
        756 to "Switzerland",
        764 to "Thailand",
        784 to "United Arab Emirates",
        792 to "Turkey",
        804 to "Ukraine",
        818 to "Egypt",
        826 to "United Kingdom",
        840 to "United States"
    )

    /** ISO 4217 numeric currency codes mapped to CurrencyInfo. */
    val ISO_CURRENCY_MAP: Map<Int, CurrencyInfo> = mapOf(
        36 to CurrencyInfo("AUD", "A$", "Australian Dollar", 2),
        124 to CurrencyInfo("CAD", "C$", "Canadian Dollar", 2),
        156 to CurrencyInfo("CNY", "¥", "Chinese Yuan", 2),
        203 to CurrencyInfo("CZK", "Kč", "Czech Koruna", 2),
        208 to CurrencyInfo("DKK", "kr", "Danish Krone", 2),
        344 to CurrencyInfo("HKD", "HK$", "Hong Kong Dollar", 2),
        348 to CurrencyInfo("HUF", "Ft", "Hungarian Forint", 2),
        356 to CurrencyInfo("INR", "₹", "Indian Rupee", 2),
        360 to CurrencyInfo("IDR", "Rp", "Indonesian Rupiah", 2),
        376 to CurrencyInfo("ILS", "₪", "Israeli New Shekel", 2),
        392 to CurrencyInfo("JPY", "¥", "Japanese Yen", 0),
        410 to CurrencyInfo("KRW", "₩", "South Korean Won", 0),
        458 to CurrencyInfo("MYR", "RM", "Malaysian Ringgit", 2),
        484 to CurrencyInfo("MXN", "MX$", "Mexican Peso", 2),
        554 to CurrencyInfo("NZD", "NZ$", "New Zealand Dollar", 2),
        578 to CurrencyInfo("NOK", "kr", "Norwegian Krone", 2),
        608 to CurrencyInfo("PHP", "₱", "Philippine Peso", 2),
        643 to CurrencyInfo("RUB", "₽", "Russian Ruble", 2),
        682 to CurrencyInfo("SAR", "SAR", "Saudi Riyal", 2),
        702 to CurrencyInfo("SGD", "S$", "Singapore Dollar", 2),
        704 to CurrencyInfo("VND", "₫", "Vietnamese Dong", 0),
        710 to CurrencyInfo("ZAR", "R", "South African Rand", 2),
        752 to CurrencyInfo("SEK", "kr", "Swedish Krona", 2),
        756 to CurrencyInfo("CHF", "CHF", "Swiss Franc", 2),
        764 to CurrencyInfo("THB", "฿", "Thai Baht", 2),
        784 to CurrencyInfo("AED", "AED", "UAE Dirham", 2),
        826 to CurrencyInfo("GBP", "£", "British Pound", 2),
        840 to CurrencyInfo("USD", "$", "US Dollar", 2),
        901 to CurrencyInfo("TWD", "NT$", "New Taiwan Dollar", 2),
        949 to CurrencyInfo("TRY", "₺", "Turkish Lira", 2),
        978 to CurrencyInfo("EUR", "€", "Euro", 2),
        985 to CurrencyInfo("PLN", "zł", "Polish Zloty", 2),
        986 to CurrencyInfo("BRL", "R$", "Brazilian Real", 2)
    )

    fun parse(data: ByteArray, offset: Int = 0, end: Int = data.size): List<BerTlvElement> {
        val result = mutableListOf<BerTlvElement>()
        var cursor = offset.coerceAtLeast(0)
        val limit = end.coerceAtMost(data.size)

        while (cursor < limit) {
            // Skip standard ISO 7816-4 / BER padding bytes (0x00, 0xFF)
            val peek = data[cursor].toInt() and 0xFF
            if (peek == 0x00 || peek == 0xFF) {
                cursor++
                continue
            }

            val tagStart = cursor
            val firstTagByte = data[cursor++].toInt() and 0xFF
            val isConstructed = (firstTagByte and 0x20) != 0

            if ((firstTagByte and 0x1F) == 0x1F) {
                while (cursor < limit) {
                    val nextTagByte = data[cursor++].toInt() and 0xFF
                    if ((nextTagByte and 0x80) == 0) {
                        break
                    }
                }
            }
            if (cursor > limit) break

            val tagBytes = data.copyOfRange(tagStart, cursor)
            val tagHex = tagBytes.toHexString()

            if (cursor >= limit) break

            val lengthFirst = data[cursor++].toInt() and 0xFF
            val valueLength: Int = if ((lengthFirst and 0x80) == 0) {
                lengthFirst
            } else {
                val numLengthBytes = lengthFirst and 0x7F
                if (numLengthBytes == 0 || numLengthBytes > 3 || cursor + numLengthBytes > limit) {
                    break
                }
                var acc = 0
                repeat(numLengthBytes) {
                    acc = (acc shl 8) or (data[cursor++].toInt() and 0xFF)
                }
                acc
            }

            if (valueLength < 0 || cursor + valueLength > limit) {
                break
            }

            val value = data.copyOfRange(cursor, cursor + valueLength)
            cursor += valueLength

            val children = if (isConstructed && value.isNotEmpty()) {
                parse(value, 0, value.size)
            } else {
                emptyList()
            }

            result.add(
                BerTlvElement(
                    tagHex = tagHex,
                    tagBytes = tagBytes,
                    isConstructed = isConstructed,
                    value = value,
                    children = children
                )
            )
        }
        return result
    }

    /** Parses a Data Object List (DOL) such as 9F38 (PDOL) or 9F4F (Log Format). */
    fun parseDol(dolBytes: ByteArray): List<DolEntry> {
        val entries = mutableListOf<DolEntry>()
        var cursor = 0
        while (cursor < dolBytes.size) {
            val tagStart = cursor
            val firstTagByte = dolBytes[cursor++].toInt() and 0xFF
            if (firstTagByte == 0x00 || firstTagByte == 0xFF) continue
            if ((firstTagByte and 0x1F) == 0x1F) {
                while (cursor < dolBytes.size) {
                    val next = dolBytes[cursor++].toInt() and 0xFF
                    if ((next and 0x80) == 0) break
                }
            }
            if (cursor >= dolBytes.size) break
            val tagHex = dolBytes.copyOfRange(tagStart, cursor).toHexString()
            val lenFirst = dolBytes[cursor++].toInt() and 0xFF
            val length = if ((lenFirst and 0x80) == 0) {
                lenFirst
            } else {
                val count = lenFirst and 0x7F
                if (count == 0 || count > 2 || cursor + count > dolBytes.size) break
                var acc = 0
                repeat(count) {
                    acc = (acc shl 8) or (dolBytes[cursor++].toInt() and 0xFF)
                }
                acc
            }
            entries.add(DolEntry(tagHex = tagHex, length = length))
        }
        return entries
    }

    fun tagName(tagHex: String): String =
        EMV_TAG_NAMES[tagHex.uppercase()] ?: "EMV Tag ${tagHex.uppercase()}"

    fun countryName(numericCode: Int): String? = ISO_COUNTRY_MAP[numericCode]

    fun currencyInfo(numericCode: Int): CurrencyInfo? = ISO_CURRENCY_MAP[numericCode]

    fun decodeBcdInt(bytes: ByteArray): Int? {
        if (bytes.isEmpty()) return null
        val hex = bytes.toHexString()
        if (!hex.all { it in '0'..'9' }) return null
        return hex.toIntOrNull()
    }

    fun toTlvNodes(elements: List<BerTlvElement>, includeConstructed: Boolean = false): List<TlvNode> {
        val nodes = mutableListOf<TlvNode>()
        fun walk(list: List<BerTlvElement>) {
            for (el in list) {
                if (!el.isConstructed || includeConstructed || el.children.isEmpty()) {
                    nodes.add(
                        TlvNode(
                            tagHex = el.tagHex,
                            name = tagName(el.tagHex),
                            valueHex = el.valueHex,
                            displayValue = formatDisplayValue(el)
                        )
                    )
                }
                if (el.children.isNotEmpty()) {
                    walk(el.children)
                }
            }
        }
        walk(elements)
        return nodes
    }

    fun formatDisplayValue(element: BerTlvElement): String {
        val tag = element.tagHex
        val bytes = element.value
        val hex = element.valueHex
        return when (tag) {
            "50", "9F12", "5F20", "5F2D", "5F50", "5F55", "9F4E" -> {
                val ascii = decodePrintableAscii(bytes)
                if (ascii.isNotBlank()) ascii else hex
            }
            "9F1F" -> {
                if (bytes.isNotEmpty() && bytes.all { (it.toInt() and 0xFF) in 0x20..0x7E }) {
                    decodePrintableAscii(bytes).ifBlank { hex }
                } else {
                    hex
                }
            }
            "84" -> {
                val ascii = decodePrintableAscii(bytes)
                if (ascii.length >= 4 && ascii.all { it in ' '..'~' }) "$ascii ($hex)" else hex
            }
            "5F24", "5F25" -> formatEmvDate(hex) ?: hex
            "5F28", "9F1A" -> {
                val code = decodeBcdInt(bytes)
                val name = code?.let { countryName(it) }
                if (name != null) "$name ($code)" else hex
            }
            "9F42", "5F2A" -> {
                val code = decodeBcdInt(bytes)
                val info = code?.let { currencyInfo(it) }
                if (info != null) "${info.code} ($code)" else hex
            }
            "9F36", "9F13" -> {
                if (bytes.size == 2) {
                    val atc = ((bytes[0].toInt() and 0xFF) shl 8) or (bytes[1].toInt() and 0xFF)
                    "$atc (0x$hex)"
                } else hex
            }
            "9F17" -> {
                if (bytes.size == 1) "${bytes[0].toInt() and 0xFF} tries remaining" else hex
            }
            "87" -> {
                if (bytes.size == 1) "Priority ${bytes[0].toInt() and 0x0F}" else hex
            }
            "5A" -> {
                val pan = hex.trimEnd('F', 'f')
                if (pan.length >= 8) maskHexDigits(pan) else hex
            }
            "57" -> {
                val dIdx = hex.indexOf('D', ignoreCase = true)
                if (dIdx > 4) {
                    val panPart = hex.substring(0, dIdx)
                    val expPart = if (hex.length >= dIdx + 5) hex.substring(dIdx + 1, dIdx + 5) else ""
                    val expFormatted = if (expPart.length == 4) " Exp ${expPart.substring(2, 4)}/${expPart.substring(0, 2)}" else ""
                    "${maskHexDigits(panPart)}$expFormatted"
                } else hex
            }
            "9F38", "9F4F", "DF4F" -> {
                val dol = parseDol(bytes)
                if (dol.isNotEmpty()) dol.joinToString(", ") { "${it.tagHex}(${it.length})" } else hex
            }
            else -> {
                if (element.isConstructed && element.children.isNotEmpty()) {
                    "${element.children.size} nested element(s)"
                } else {
                    hex
                }
            }
        }
    }

    private fun maskHexDigits(digits: String): String {
        if (digits.length <= 8) return digits
        return "${digits.take(4)} •••• ${digits.takeLast(4)}"
    }

    private fun formatEmvDate(hex: String): String? {
        if (hex.length < 4 || !hex.take(4).all { it.isDigit() }) return null
        val yy = hex.substring(0, 2)
        val mm = hex.substring(2, 4)
        return "$mm/$yy"
    }

    fun decodePrintableAscii(bytes: ByteArray): String {
        val raw = String(bytes, StandardCharsets.ISO_8859_1)
        return buildString(raw.length) {
            for (ch in raw) {
                if (ch.code in 0x20..0x7E) append(ch)
            }
        }.trim()
    }

    fun encodeBerLength(length: Int): ByteArray {
        return when {
            length < 0x80 -> byteArrayOf(length.toByte())
            length <= 0xFF -> byteArrayOf(0x81.toByte(), length.toByte())
            else -> byteArrayOf(0x82.toByte(), (length ushr 8).toByte(), length.toByte())
        }
    }
}

fun ByteArray.toHexString(separator: String = ""): String {
    if (isEmpty()) return ""
    val hexChars = "0123456789ABCDEF"
    val sb = StringBuilder(size * (2 + separator.length))
    for (i in indices) {
        if (i > 0 && separator.isNotEmpty()) sb.append(separator)
        val v = this[i].toInt() and 0xFF
        sb.append(hexChars[v ushr 4])
        sb.append(hexChars[v and 0x0F])
    }
    return sb.toString()
}

fun String.hexToByteArray(): ByteArray {
    val clean = replace(" ", "").replace(":", "").trim()
    require(clean.length % 2 == 0) { "Hex string must have even length: $this" }
    return ByteArray(clean.length / 2) { i ->
        clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
    }
}
