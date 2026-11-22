package com.tapmark.nfc.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

class NdefParserTest {

    @Test
    fun `decodes Well-Known Text records in UTF-8 and UTF-16`() {
        val utf8Payload = byteArrayOf(0x02) +
            "en".toByteArray(StandardCharsets.US_ASCII) +
            "Hello Tapmark".toByteArray(StandardCharsets.UTF_8)

        val utf8Rec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_WELL_KNOWN,
            type = "T".toByteArray(StandardCharsets.US_ASCII),
            payload = utf8Payload
        )
        assertEquals("Text", utf8Rec.typeLabel)
        assertEquals("Text (en)", utf8Rec.title)
        assertEquals("Hello Tapmark", utf8Rec.value)
        assertNull(utf8Rec.actionUrl)

        val utf16BePayload = byteArrayOf((0x80 or 0x02).toByte()) +
            "en".toByteArray(StandardCharsets.US_ASCII) +
            "Tactile".toByteArray(StandardCharsets.UTF_16BE)

        val utf16BeRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_WELL_KNOWN,
            type = "T".toByteArray(StandardCharsets.US_ASCII),
            payload = utf16BePayload
        )
        assertEquals("Tactile", utf16BeRec.value)

        val utf16LeBomPayload = byteArrayOf((0x80 or 0x02).toByte()) +
            "ja".toByteArray(StandardCharsets.US_ASCII) +
            byteArrayOf(0xFF.toByte(), 0xFE.toByte()) +
            "東京".toByteArray(StandardCharsets.UTF_16LE)

        val utf16LeRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_WELL_KNOWN,
            type = "T".toByteArray(StandardCharsets.US_ASCII),
            payload = utf16LeBomPayload
        )
        assertEquals("Text (ja)", utf16LeRec.title)
        assertEquals("東京", utf16LeRec.value)
    }

    @Test
    fun `decodes URI records across RTD prefix table 0x00 to 0x23`() {
        assertEquals(36, NdefParser.URI_PREFIX_TABLE.size)
        assertEquals("", NdefParser.URI_PREFIX_TABLE[0x00])
        assertEquals("urn:nfc:", NdefParser.URI_PREFIX_TABLE[0x23])

        val httpsRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_WELL_KNOWN,
            type = "U".toByteArray(StandardCharsets.US_ASCII),
            payload = byteArrayOf(0x04) + "tapmark.dev/card".toByteArray(StandardCharsets.UTF_8)
        )
        assertEquals("URI", httpsRec.typeLabel)
        assertEquals("https://tapmark.dev/card", httpsRec.value)
        assertEquals("https://tapmark.dev/card", httpsRec.actionUrl)

        val mailtoRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_WELL_KNOWN,
            type = "U".toByteArray(StandardCharsets.US_ASCII),
            payload = byteArrayOf(0x06) + "hello@tapmark.dev".toByteArray(StandardCharsets.UTF_8)
        )
        assertEquals("mailto:hello@tapmark.dev", mailtoRec.value)
        assertEquals("mailto:hello@tapmark.dev", mailtoRec.actionUrl)

        val urnNfcRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_WELL_KNOWN,
            type = "U".toByteArray(StandardCharsets.US_ASCII),
            payload = byteArrayOf(0x23) + "ext:custom".toByteArray(StandardCharsets.UTF_8)
        )
        assertEquals("urn:nfc:ext:custom", urnNfcRec.value)
        assertNull(urnNfcRec.actionUrl)
    }

    @Test
    fun `decodes Wi-Fi WSC TLV MIME record`() {
        fun wscTlv(tag: Int, value: ByteArray): ByteArray = byteArrayOf(
            (tag ushr 8).toByte(),
            tag.toByte(),
            (value.size ushr 8).toByte(),
            value.size.toByte()
        ) + value

        val credentialBody = ByteArrayOutputStream().apply {
            write(wscTlv(0x1045, "Atelier_5G".toByteArray(StandardCharsets.UTF_8)))
            write(wscTlv(0x1003, byteArrayOf(0x00, 0x20))) // WPA2-PSK
            write(wscTlv(0x100F, byteArrayOf(0x00, 0x08))) // AES
            write(wscTlv(0x1027, "copper-99".toByteArray(StandardCharsets.UTF_8)))
        }.toByteArray()

        val wscPayload = wscTlv(0x100E, credentialBody)
        val parsed = NdefParser.parseRecord(
            tnf = NdefParser.TNF_MIME_MEDIA,
            type = "application/vnd.wfa.wsc".toByteArray(StandardCharsets.US_ASCII),
            payload = wscPayload
        )

        assertEquals("Wi-Fi", parsed.typeLabel)
        assertEquals("Wi-Fi: Atelier_5G", parsed.title)
        assertTrue(parsed.value.contains("SSID: Atelier_5G"))
        assertTrue(parsed.value.contains("WPA2-PSK"))
        assertTrue(parsed.value.contains("AES"))
        assertTrue(parsed.value.contains("Password: copper-99"))
    }

    @Test
    fun `decodes vCard, JSON, External android pkg, Absolute URI, and binary fallback`() {
        val vcard = "BEGIN:VCARD\nFN:Clara Vance\nORG:Tapmark Lab\nTEL:+15550199\nEND:VCARD"
        val vcardRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_MIME_MEDIA,
            type = "text/vcard".toByteArray(StandardCharsets.US_ASCII),
            payload = vcard.toByteArray(StandardCharsets.UTF_8)
        )
        assertEquals("vCard", vcardRec.typeLabel)
        assertEquals("Clara Vance", vcardRec.title)
        assertTrue(vcardRec.value.contains("Phone: +15550199"))

        val jsonRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_MIME_MEDIA,
            type = "application/json".toByteArray(StandardCharsets.US_ASCII),
            payload = """{"mode":"readonly"}""".toByteArray(StandardCharsets.UTF_8)
        )
        assertEquals("JSON", jsonRec.typeLabel)
        assertEquals("""{"mode":"readonly"}""", jsonRec.value)

        val pkgRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_EXTERNAL_TYPE,
            type = "android.com:pkg".toByteArray(StandardCharsets.UTF_8),
            payload = "com.tapmark.nfc".toByteArray(StandardCharsets.UTF_8)
        )
        assertEquals("Android App", pkgRec.typeLabel)
        assertEquals("com.tapmark.nfc", pkgRec.value)

        val absUriRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_ABSOLUTE_URI,
            type = "https://example.org/spec".toByteArray(StandardCharsets.UTF_8),
            payload = ByteArray(0)
        )
        assertEquals("Absolute URI", absUriRec.typeLabel)
        assertEquals("https://example.org/spec", absUriRec.actionUrl)

        val binaryRec = NdefParser.parseRecord(
            tnf = NdefParser.TNF_UNKNOWN,
            type = ByteArray(0),
            payload = byteArrayOf(0xDE.toByte(), 0xAD.toByte(), 0x00, 0x01)
        )
        assertEquals("Raw", binaryRec.typeLabel)
        assertEquals("DE AD 00 01", binaryRec.value)
    }

    @Test
    fun `parses raw binary NDEF message bytes and Type 2 user memory TLV`() {
        // Two short records:
        // Rec 1: MB=1, ME=0, SR=1, TNF=1 | typeLen=1 ('U') | payloadLen=12 | 0x04 + "tapmark.dev"
        val urlBytes = "tapmark.dev".toByteArray(StandardCharsets.UTF_8)
        val rec1 = byteArrayOf(0x91.toByte(), 0x01, (1 + urlBytes.size).toByte(), 0x55, 0x04) + urlBytes
        // Rec 2: MB=0, ME=1, SR=1, TNF=1 | typeLen=1 ('T') | payloadLen=6 | 0x02 "en" "NFC"
        val rec2 = byteArrayOf(0x51, 0x01, 0x06, 0x54, 0x02, 'e'.code.toByte(), 'n'.code.toByte(), 'N'.code.toByte(), 'F'.code.toByte(), 'C'.code.toByte())
        val ndefMsg = rec1 + rec2

        val parsed = NdefParser.parseMessageBytes(ndefMsg)
        assertEquals(2, parsed.size)
        assertEquals("https://tapmark.dev", parsed[0].value)
        assertEquals("NFC", parsed[1].value)

        // Wrap inside Type 2 dump (16-byte header + NDEF TLV 0x03 <len> <msg> 0xFE)
        val type2Memory = ByteArray(16) + byteArrayOf(0x03, ndefMsg.size.toByte()) + ndefMsg + byteArrayOf(0xFE.toByte())
        val extracted = NdefParser.extractNdefFromType2UserMemory(type2Memory)
        assertEquals(ndefMsg.toList(), extracted?.toList())
    }
}
