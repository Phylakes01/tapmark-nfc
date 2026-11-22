package com.tapmark.nfc.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BerTlvParserTest {

    @Test
    fun `parses 1-byte and 2-byte tags recursively with constructed bit`() {
        // 6F (FCI Template)
        //   84 07 A0000000031010
        //   A5 0F
        //     50 0A "Visa Debit"
        //     5F28 02 0840
        val hex = "6F1C8407A0000000031010A511500A566973612044656269745F28020840"
        val elements = BerTlvParser.parse(hex.hexToByteArray())

        assertEquals(1, elements.size)
        val root = elements[0]
        assertEquals("6F", root.tagHex)
        assertTrue(root.isConstructed)
        assertEquals(2, root.children.size)

        val dfName = root.findFirst("84")
        assertNotNull(dfName)
        assertEquals("A0000000031010", dfName!!.valueHex)

        val appLabel = root.findFirst("50")
        assertNotNull(appLabel)
        assertEquals("Visa Debit", BerTlvParser.decodePrintableAscii(appLabel!!.value))

        val country = root.findFirst("5F28")
        assertNotNull(country)
        assertEquals("0840", country!!.valueHex)
        assertEquals("United States (840)", BerTlvParser.formatDisplayValue(country))
    }

    @Test
    fun `parses multi-byte 0x81 and 0x82 lengths and skips padding`() {
        val payload130 = ByteArray(130) { 0x41 }
        val payload260 = ByteArray(260) { 0x42 }

        val encoded = byteArrayOf(0x00, 0xFF.toByte()) +
            byteArrayOf(0x50, 0x81.toByte(), 0x82.toByte()) + payload130 +
            byteArrayOf(0x00) +
            byteArrayOf(0x9F.toByte(), 0x10, 0x82.toByte(), 0x01, 0x04) + payload260 +
            byteArrayOf(0xFF.toByte())

        val parsed = BerTlvParser.parse(encoded)
        assertEquals(2, parsed.size)
        assertEquals("50", parsed[0].tagHex)
        assertEquals(130, parsed[0].value.size)
        assertEquals("9F10", parsed[1].tagHex)
        assertEquals(260, parsed[1].value.size)
    }

    @Test
    fun `stops gracefully on truncated length or value`() {
        val truncated = "500A56697361".hexToByteArray() // claims 10 bytes, only 4 present
        val parsed = BerTlvParser.parse(truncated)
        assertTrue(parsed.isEmpty())

        val validThenTruncated = "4F07A0000000031010500A5669".hexToByteArray()
        val partial = BerTlvParser.parse(validThenTruncated)
        assertEquals(1, partial.size)
        assertEquals("4F", partial[0].tagHex)
    }

    @Test
    fun `parses PDOL entries accurately`() {
        val pdolHex = "9F66049F02069F03069F1A0295055F2A029A039C019F3704"
        val entries = BerTlvParser.parseDol(pdolHex.hexToByteArray())

        assertEquals(9, entries.size)
        assertEquals(BerTlvParser.DolEntry("9F66", 4), entries[0])
        assertEquals(BerTlvParser.DolEntry("9F02", 6), entries[1])
        assertEquals(BerTlvParser.DolEntry("9F03", 6), entries[2])
        assertEquals(BerTlvParser.DolEntry("9F1A", 2), entries[3])
        assertEquals(BerTlvParser.DolEntry("95", 5), entries[4])
        assertEquals(BerTlvParser.DolEntry("5F2A", 2), entries[5])
        assertEquals(BerTlvParser.DolEntry("9A", 3), entries[6])
        assertEquals(BerTlvParser.DolEntry("9C", 1), entries[7])
        assertEquals(BerTlvParser.DolEntry("9F37", 4), entries[8])
    }

    @Test
    fun `contains required EMV tags, ISO 3166 countries, and ISO 4217 currencies`() {
        val requiredTags = listOf(
            "42", "4F", "50", "57", "5A", "5F20", "5F24", "5F25", "5F28", "5F30", "5F50",
            "82", "84", "87", "94", "9F12", "9F17", "9F21", "9F36", "9F38", "9F42", "9F4D", "9F4E", "9F4F",
            "DF4D", "DF4F"
        )
        for (tag in requiredTags) {
            assertTrue("Missing EMV tag $tag", BerTlvParser.EMV_TAG_NAMES.containsKey(tag))
        }

        assertEquals("United States", BerTlvParser.countryName(840))
        assertEquals("United Kingdom", BerTlvParser.countryName(826))
        assertEquals("Japan", BerTlvParser.countryName(392))
        assertEquals("Germany", BerTlvParser.countryName(276))

        assertEquals("USD", BerTlvParser.currencyInfo(840)?.code)
        assertEquals("EUR", BerTlvParser.currencyInfo(978)?.code)
        assertEquals("GBP", BerTlvParser.currencyInfo(826)?.code)
        assertEquals(0, BerTlvParser.currencyInfo(392)?.minorUnits)
    }

    @Test
    fun `formats TLV display values for dates, ATC, PIN counter, currency, URL, and merchant`() {
        val raw = "70145F24032908319F420209789F3602002A9F170103".hexToByteArray()
        val nodes = BerTlvParser.toTlvNodes(BerTlvParser.parse(raw))

        val byTag = nodes.associateBy { it.tagHex }
        assertEquals("08/29", byTag["5F24"]?.displayValue)
        assertEquals("EUR (978)", byTag["9F42"]?.displayValue)
        assertEquals("42 (0x002A)", byTag["9F36"]?.displayValue)
        assertEquals("3 tries remaining", byTag["9F17"]?.displayValue)

        val urlAndMerchant = "70195F500A6270692E636F6D2E70689F4E094D455243555259204D".hexToByteArray()
        val extraByTag = BerTlvParser.toTlvNodes(BerTlvParser.parse(urlAndMerchant)).associateBy { it.tagHex }
        assertEquals("bpi.com.ph", extraByTag["5F50"]?.displayValue)
        assertEquals("MERCURY M", extraByTag["9F4E"]?.displayValue)
    }
}
