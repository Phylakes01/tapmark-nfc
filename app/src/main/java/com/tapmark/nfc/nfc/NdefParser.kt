package com.tapmark.nfc.nfc

import android.nfc.NdefMessage
import com.tapmark.nfc.model.ParsedNdefRecord
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

/**
 * Pure-Kotlin NFC Forum NDEF binary & record decoder with an Android NdefMessage bridge.
 */
object NdefParser {

    const val TNF_EMPTY: Short = 0x00
    const val TNF_WELL_KNOWN: Short = 0x01
    const val TNF_MIME_MEDIA: Short = 0x02
    const val TNF_ABSOLUTE_URI: Short = 0x03
    const val TNF_EXTERNAL_TYPE: Short = 0x04
    const val TNF_UNKNOWN: Short = 0x05
    const val TNF_UNCHANGED: Short = 0x06

    /** Full NFC Forum URI Record Type Definition (RTD) prefix table (0x00..0x23). */
    val URI_PREFIX_TABLE: List<String> = listOf(
        "",                           // 0x00
        "http://www.",                // 0x01
        "https://www.",               // 0x02
        "http://",                    // 0x03
        "https://",                   // 0x04
        "tel:",                       // 0x05
        "mailto:",                    // 0x06
        "ftp://anonymous:anonymous@", // 0x07
        "ftp://ftp.",                 // 0x08
        "ftps://",                    // 0x09
        "sftp://",                    // 0x0A
        "smb://",                     // 0x0B
        "nfs://",                     // 0x0C
        "ftp://",                     // 0x0D
        "dav://",                     // 0x0E
        "news:",                      // 0x0F
        "telnet://",                  // 0x10
        "imap:",                      // 0x11
        "rtsp://",                    // 0x12
        "urn:",                       // 0x13
        "pop:",                       // 0x14
        "sip:",                       // 0x15
        "sips:",                      // 0x16
        "tftp:",                      // 0x17
        "btspp://",                   // 0x18
        "btl2cap://",                 // 0x19
        "btgoep://",                  // 0x1A
        "tcpobex://",                 // 0x1B
        "irdaobex://",                // 0x1C
        "file://",                    // 0x1D
        "urn:epc:id:",                // 0x1E
        "urn:epc:tag:",               // 0x1F
        "urn:epc:pat:",               // 0x20
        "urn:epc:raw:",               // 0x21
        "urn:epc:",                   // 0x22
        "urn:nfc:"                    // 0x23
    )

    fun parseAndroidMessage(message: NdefMessage?): List<ParsedNdefRecord> {
        if (message == null) return emptyList()
        return message.records.map { record ->
            parseRecord(
                tnf = record.tnf,
                type = record.type ?: ByteArray(0),
                id = record.id ?: ByteArray(0),
                payload = record.payload ?: ByteArray(0)
            )
        }
    }

    /**
     * Parses raw NDEF message bytes (pure Kotlin, no Android framework dependency).
     */
    fun parseMessageBytes(messageBytes: ByteArray): List<ParsedNdefRecord> {
        if (messageBytes.isEmpty()) return emptyList()
        val records = mutableListOf<ParsedNdefRecord>()
        var offset = 0

        var inChunk = false
        var chunkTnf: Short = TNF_EMPTY
        var chunkType = ByteArray(0)
        var chunkId = ByteArray(0)
        val chunkPayload = ByteArrayOutputStream()

        while (offset < messageBytes.size) {
            val header = messageBytes[offset++].toInt() and 0xFF
            val me = (header and 0x40) != 0
            val cf = (header and 0x20) != 0
            val sr = (header and 0x10) != 0
            val il = (header and 0x08) != 0
            val tnf = (header and 0x07).toShort()

            if (offset >= messageBytes.size) break
            val typeLen = messageBytes[offset++].toInt() and 0xFF

            val payloadLen: Int = if (sr) {
                if (offset >= messageBytes.size) break
                messageBytes[offset++].toInt() and 0xFF
            } else {
                if (offset + 4 > messageBytes.size) break
                val len = ((messageBytes[offset].toInt() and 0xFF) shl 24) or
                    ((messageBytes[offset + 1].toInt() and 0xFF) shl 16) or
                    ((messageBytes[offset + 2].toInt() and 0xFF) shl 8) or
                    (messageBytes[offset + 3].toInt() and 0xFF)
                offset += 4
                if (len < 0) break
                len
            }

            val idLen: Int = if (il) {
                if (offset >= messageBytes.size) break
                messageBytes[offset++].toInt() and 0xFF
            } else 0

            if (offset + typeLen + idLen + payloadLen > messageBytes.size) break

            val type = messageBytes.copyOfRange(offset, offset + typeLen)
            offset += typeLen
            val id = messageBytes.copyOfRange(offset, offset + idLen)
            offset += idLen
            val payload = messageBytes.copyOfRange(offset, offset + payloadLen)
            offset += payloadLen

            if (cf) {
                if (!inChunk) {
                    inChunk = true
                    chunkTnf = tnf
                    chunkType = type
                    chunkId = id
                    chunkPayload.reset()
                }
                chunkPayload.write(payload)
            } else if (inChunk) {
                chunkPayload.write(payload)
                records.add(parseRecord(chunkTnf, chunkType, chunkId, chunkPayload.toByteArray()))
                inChunk = false
                chunkPayload.reset()
            } else {
                records.add(parseRecord(tnf, type, id, payload))
            }

            if (me) break
        }

        return records
    }

    fun parseRecord(
        tnf: Short,
        type: ByteArray,
        id: ByteArray = ByteArray(0),
        payload: ByteArray
    ): ParsedNdefRecord {
        val rawHex = if (payload.isNotEmpty()) {
            payload.toHexString(" ")
        } else {
            type.toHexString(" ")
        }
        val byteSize = payload.size

        return when (tnf) {
            TNF_EMPTY -> ParsedNdefRecord(
                typeLabel = "Empty",
                title = "Empty Record",
                value = "(empty)",
                actionUrl = null,
                rawHex = rawHex,
                byteSize = byteSize
            )

            TNF_WELL_KNOWN -> parseWellKnownRecord(type, payload, rawHex, byteSize)

            TNF_MIME_MEDIA -> parseMimeRecord(type, payload, rawHex, byteSize)

            TNF_ABSOLUTE_URI -> {
                val uri = String(type, StandardCharsets.UTF_8).trim()
                val action = uri.takeIf { isActionableUrl(it) }
                ParsedNdefRecord(
                    typeLabel = "Absolute URI",
                    title = "Absolute URI",
                    value = uri.ifEmpty { rawHex },
                    actionUrl = action,
                    rawHex = rawHex,
                    byteSize = byteSize
                )
            }

            TNF_EXTERNAL_TYPE -> parseExternalRecord(type, payload, rawHex, byteSize)

            else -> {
                val typeStr = String(type, StandardCharsets.UTF_8).takeIf { isMostlyPrintable(type) }
                val fallbackValue = if (isMostlyPrintable(payload) && payload.isNotEmpty()) {
                    String(payload, StandardCharsets.UTF_8)
                } else {
                    rawHex.ifEmpty { "(no payload)" }
                }
                ParsedNdefRecord(
                    typeLabel = "Raw",
                    title = typeStr ?: "Unknown NDEF Record (TNF $tnf)",
                    value = fallbackValue,
                    actionUrl = null,
                    rawHex = rawHex,
                    byteSize = byteSize
                )
            }
        }
    }

    private fun parseWellKnownRecord(
        type: ByteArray,
        payload: ByteArray,
        rawHex: String,
        byteSize: Int
    ): ParsedNdefRecord {
        val typeStr = String(type, StandardCharsets.US_ASCII)
        return when (typeStr) {
            "T" -> decodeTextRecord(payload, rawHex, byteSize)
            "U" -> decodeUriRecord(payload, rawHex, byteSize)
            "Sp" -> decodeSmartPosterRecord(payload, rawHex, byteSize)
            else -> ParsedNdefRecord(
                typeLabel = "Well-Known",
                title = "RTD: $typeStr",
                value = if (isMostlyPrintable(payload) && payload.isNotEmpty()) {
                    String(payload, StandardCharsets.UTF_8)
                } else rawHex,
                actionUrl = null,
                rawHex = rawHex,
                byteSize = byteSize
            )
        }
    }

    private fun decodeTextRecord(
        payload: ByteArray,
        rawHex: String,
        byteSize: Int
    ): ParsedNdefRecord {
        if (payload.isEmpty()) {
            return ParsedNdefRecord("Text", "Plain Text", "", null, rawHex, byteSize)
        }
        val status = payload[0].toInt() and 0xFF
        val isUtf16 = (status and 0x80) != 0
        val langLen = (status and 0x3F).coerceAtMost((payload.size - 1).coerceAtLeast(0))
        val lang = if (langLen > 0) String(payload, 1, langLen, StandardCharsets.US_ASCII) else ""
        val textOffset = 1 + langLen
        val textBytes = if (textOffset < payload.size) {
            payload.copyOfRange(textOffset, payload.size)
        } else {
            ByteArray(0)
        }

        val decodedText = if (!isUtf16) {
            String(textBytes, StandardCharsets.UTF_8)
        } else {
            decodeUtf16Bytes(textBytes)
        }

        return ParsedNdefRecord(
            typeLabel = "Text",
            title = if (lang.isNotBlank()) "Text ($lang)" else "Plain Text",
            value = decodedText,
            actionUrl = null,
            rawHex = rawHex,
            byteSize = byteSize
        )
    }

    private fun decodeUtf16Bytes(bytes: ByteArray): String {
        if (bytes.size >= 2) {
            val b0 = bytes[0].toInt() and 0xFF
            val b1 = bytes[1].toInt() and 0xFF
            if (b0 == 0xFF && b1 == 0xFE) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16LE)
            }
            if (b0 == 0xFE && b1 == 0xFF) {
                return String(bytes, 2, bytes.size - 2, StandardCharsets.UTF_16BE)
            }
        }
        return String(bytes, StandardCharsets.UTF_16BE)
    }

    private fun decodeUriRecord(
        payload: ByteArray,
        rawHex: String,
        byteSize: Int
    ): ParsedNdefRecord {
        if (payload.isEmpty()) {
            return ParsedNdefRecord("URI", "URI", "", null, rawHex, byteSize)
        }
        val prefixCode = payload[0].toInt() and 0xFF
        val prefix = URI_PREFIX_TABLE.getOrElse(prefixCode) { "" }
        val remainder = if (payload.size > 1) {
            String(payload, 1, payload.size - 1, StandardCharsets.UTF_8)
        } else {
            ""
        }
        val fullUri = prefix + remainder
        val title = when {
            fullUri.startsWith("https://", true) || fullUri.startsWith("http://", true) -> "Web Link"
            fullUri.startsWith("tel:", true) -> "Phone Number"
            fullUri.startsWith("mailto:", true) -> "Email Address"
            else -> "URI"
        }
        val actionUrl = fullUri.takeIf { isActionableUrl(it) }
        return ParsedNdefRecord(
            typeLabel = "URI",
            title = title,
            value = fullUri,
            actionUrl = actionUrl,
            rawHex = rawHex,
            byteSize = byteSize
        )
    }

    private fun decodeSmartPosterRecord(
        payload: ByteArray,
        rawHex: String,
        byteSize: Int
    ): ParsedNdefRecord {
        val nested = parseMessageBytes(payload)
        val uriRecord = nested.firstOrNull { it.typeLabel == "URI" }
        val textRecord = nested.firstOrNull { it.typeLabel == "Text" }
        val title = textRecord?.value?.takeIf { it.isNotBlank() } ?: "Smart Poster"
        val value = listOfNotNull(
            textRecord?.value?.takeIf { it.isNotBlank() },
            uriRecord?.value?.takeIf { it.isNotBlank() }
        ).joinToString("\n").ifBlank { rawHex }

        return ParsedNdefRecord(
            typeLabel = "Smart Poster",
            title = title,
            value = value,
            actionUrl = uriRecord?.actionUrl,
            rawHex = rawHex,
            byteSize = byteSize
        )
    }

    private fun parseMimeRecord(
        type: ByteArray,
        payload: ByteArray,
        rawHex: String,
        byteSize: Int
    ): ParsedNdefRecord {
        val mime = String(type, StandardCharsets.US_ASCII).trim().lowercase()
        return when (mime) {
            "application/vnd.wfa.wsc" -> decodeWifiWscRecord(payload, rawHex, byteSize)
            "text/vcard", "text/x-vcard" -> decodeVcardRecord(payload, rawHex, byteSize)
            "text/plain" -> ParsedNdefRecord(
                typeLabel = "Text",
                title = "Plain Text (MIME)",
                value = String(payload, StandardCharsets.UTF_8),
                actionUrl = null,
                rawHex = rawHex,
                byteSize = byteSize
            )
            "application/json", "text/json" -> ParsedNdefRecord(
                typeLabel = "JSON",
                title = "JSON Data",
                value = String(payload, StandardCharsets.UTF_8),
                actionUrl = null,
                rawHex = rawHex,
                byteSize = byteSize
            )
            else -> {
                val text = if (payload.isNotEmpty() && isMostlyPrintable(payload)) {
                    String(payload, StandardCharsets.UTF_8)
                } else {
                    rawHex
                }
                ParsedNdefRecord(
                    typeLabel = "MIME",
                    title = mime.ifEmpty { "MIME Payload" },
                    value = text,
                    actionUrl = null,
                    rawHex = rawHex,
                    byteSize = byteSize
                )
            }
        }
    }

    /**
     * Decodes Wi-Fi Alliance WSC TLV payload (`application/vnd.wfa.wsc`).
     */
    fun decodeWifiWscRecord(
        payload: ByteArray,
        rawHex: String = payload.toHexString(" "),
        byteSize: Int = payload.size
    ): ParsedNdefRecord {
        var ssid: String? = null
        var auth: String? = null
        var enc: String? = null
        var password: String? = null
        var mac: String? = null

        fun parseWscTlv(bytes: ByteArray) {
            var offset = 0
            while (offset + 4 <= bytes.size) {
                val attrType = ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
                val attrLen = ((bytes[offset + 2].toInt() and 0xFF) shl 8) or (bytes[offset + 3].toInt() and 0xFF)
                offset += 4
                if (attrLen < 0 || offset + attrLen > bytes.size) break
                val value = bytes.copyOfRange(offset, offset + attrLen)
                offset += attrLen

                when (attrType) {
                    0x100E -> parseWscTlv(value) // Credential container
                    0x1045 -> ssid = String(value, StandardCharsets.UTF_8)
                    0x1027 -> password = String(value, StandardCharsets.UTF_8)
                    0x1003 -> if (value.size >= 2) {
                        val mask = ((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                        auth = decodeWscAuth(mask)
                    }
                    0x100F -> if (value.size >= 2) {
                        val mask = ((value[0].toInt() and 0xFF) shl 8) or (value[1].toInt() and 0xFF)
                        enc = decodeWscEncryption(mask)
                    }
                    0x1020 -> mac = value.toHexString(":")
                }
            }
        }

        parseWscTlv(payload)

        val lines = buildList {
            ssid?.let { add("SSID: $it") }
            val sec = listOfNotNull(auth, enc).joinToString(" / ")
            if (sec.isNotEmpty()) add("Security: $sec")
            password?.let { add("Password: $it") }
            mac?.let { add("MAC: $it") }
        }

        return ParsedNdefRecord(
            typeLabel = "Wi-Fi",
            title = if (!ssid.isNullOrBlank()) "Wi-Fi: $ssid" else "Wi-Fi Credentials",
            value = if (lines.isNotEmpty()) lines.joinToString("\n") else rawHex,
            actionUrl = null,
            rawHex = rawHex,
            byteSize = byteSize
        )
    }

    private fun decodeWscAuth(mask: Int): String {
        val parts = mutableListOf<String>()
        if ((mask and 0x0001) != 0) parts.add("Open")
        if ((mask and 0x0002) != 0) parts.add("WPA-PSK")
        if ((mask and 0x0004) != 0) parts.add("Shared")
        if ((mask and 0x0008) != 0) parts.add("WPA-Enterprise")
        if ((mask and 0x0010) != 0) parts.add("WPA2-Enterprise")
        if ((mask and 0x0020) != 0) parts.add("WPA2-PSK")
        return if (parts.isNotEmpty()) parts.joinToString(" / ") else "0x%04X".format(mask)
    }

    private fun decodeWscEncryption(mask: Int): String {
        val parts = mutableListOf<String>()
        if ((mask and 0x0001) != 0) parts.add("None")
        if ((mask and 0x0002) != 0) parts.add("WEP")
        if ((mask and 0x0004) != 0) parts.add("TKIP")
        if ((mask and 0x0008) != 0) parts.add("AES")
        return if (parts.isNotEmpty()) parts.joinToString(" / ") else "0x%04X".format(mask)
    }

    private fun decodeVcardRecord(
        payload: ByteArray,
        rawHex: String,
        byteSize: Int
    ): ParsedNdefRecord {
        val rawText = String(payload, StandardCharsets.UTF_8)
            .replace("\r\n ", "")
            .replace("\n ", "")
        val lines = rawText.lines().map { it.trim() }.filter { it.isNotEmpty() }

        var fullName: String? = null
        var org: String? = null
        val phones = mutableListOf<String>()
        val emails = mutableListOf<String>()

        for (line in lines) {
            val upper = line.uppercase()
            val colonIdx = line.indexOf(':')
            if (colonIdx < 0) continue
            val valuePart = line.substring(colonIdx + 1).trim()
            val keyPart = upper.substring(0, colonIdx)
            when {
                keyPart == "FN" -> fullName = valuePart
                keyPart == "ORG" -> org = valuePart.replace(';', ' ').trim()
                keyPart == "TEL" || keyPart.startsWith("TEL;") -> phones.add(valuePart)
                keyPart == "EMAIL" || keyPart.startsWith("EMAIL;") -> emails.add(valuePart)
            }
        }

        val summaryLines = buildList {
            fullName?.let { add("Name: $it") }
            org?.takeIf { it.isNotBlank() }?.let { add("Organization: $it") }
            phones.forEach { add("Phone: $it") }
            emails.forEach { add("Email: $it") }
        }

        return ParsedNdefRecord(
            typeLabel = "vCard",
            title = fullName ?: "Contact Card",
            value = if (summaryLines.isNotEmpty()) summaryLines.joinToString("\n") else rawText,
            actionUrl = null,
            rawHex = rawHex,
            byteSize = byteSize
        )
    }

    private fun parseExternalRecord(
        type: ByteArray,
        payload: ByteArray,
        rawHex: String,
        byteSize: Int
    ): ParsedNdefRecord {
        val extType = String(type, StandardCharsets.UTF_8).trim()
        if (extType.equals("android.com:pkg", ignoreCase = true)) {
            val pkgName = String(payload, StandardCharsets.UTF_8).trim()
            return ParsedNdefRecord(
                typeLabel = "Android App",
                title = "Android Application Record",
                value = pkgName.ifEmpty { rawHex },
                actionUrl = null,
                rawHex = rawHex,
                byteSize = byteSize
            )
        }
        val text = if (payload.isNotEmpty() && isMostlyPrintable(payload)) {
            String(payload, StandardCharsets.UTF_8)
        } else {
            rawHex
        }
        return ParsedNdefRecord(
            typeLabel = "External",
            title = extType.ifEmpty { "External Type" },
            value = text,
            actionUrl = null,
            rawHex = rawHex,
            byteSize = byteSize
        )
    }

    /**
     * Scans NFC Forum Type 2 user memory (starting at byte 16 / page 4) for an NDEF Message TLV (0x03).
     */
    fun extractNdefFromType2UserMemory(fullDumpBytes: ByteArray): ByteArray? {
        var offset = 16 // Page 4 starts at byte 16
        while (offset < fullDumpBytes.size) {
            val tag = fullDumpBytes[offset++].toInt() and 0xFF
            when (tag) {
                0x00 -> continue // NULL TLV
                0xFE -> return null // Terminator TLV
                else -> {
                    if (offset >= fullDumpBytes.size) return null
                    val lenByte = fullDumpBytes[offset++].toInt() and 0xFF
                    val len = if (lenByte == 0xFF) {
                        if (offset + 2 > fullDumpBytes.size) return null
                        val l = ((fullDumpBytes[offset].toInt() and 0xFF) shl 8) or
                            (fullDumpBytes[offset + 1].toInt() and 0xFF)
                        offset += 2
                        l
                    } else {
                        lenByte
                    }
                    if (len < 0 || offset + len > fullDumpBytes.size) return null
                    if (tag == 0x03) {
                        return fullDumpBytes.copyOfRange(offset, offset + len)
                    }
                    offset += len
                }
            }
        }
        return null
    }

    private fun isActionableUrl(uri: String): Boolean {
        val lower = uri.lowercase()
        return lower.startsWith("https://") ||
            lower.startsWith("http://") ||
            lower.startsWith("mailto:") ||
            lower.startsWith("tel:")
    }

    private fun isMostlyPrintable(bytes: ByteArray): Boolean {
        if (bytes.isEmpty()) return false
        var printable = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            if (v in 0x20..0x7E || v == 0x09 || v == 0x0A || v == 0x0D || v >= 0x80) {
                printable++
            }
        }
        return printable * 10 >= bytes.size * 8
    }
}
