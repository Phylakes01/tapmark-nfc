package com.tapmark.nfc.nfc

import com.tapmark.nfc.model.CardCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ChipIdentifierTest {

    @Test
    fun `looks up ISO 7816-6 manufacturers across NfcA, NfcV, and random UIDs`() {
        assertEquals("NXP Semiconductors", ChipIdentifier.manufacturerName(0x04))
        assertEquals("STMicroelectronics", ChipIdentifier.manufacturerName(0x02))
        assertEquals("Infineon Technologies", ChipIdentifier.manufacturerName(0x05))
        assertEquals("Texas Instruments", ChipIdentifier.manufacturerName(0x07))
        assertEquals("Sony", ChipIdentifier.manufacturerName(0x2A))

        val iso15693Uid = "01020304050604E0".hexToByteArray()
        assertEquals(
            "NXP Semiconductors",
            ChipIdentifier.identifyManufacturer(iso15693Uid, listOf("NfcV"))
        )

        val randomUid = "08112233".hexToByteArray()
        assertEquals(
            "Dynamic Random UID",
            ChipIdentifier.identifyManufacturer(randomUid, listOf("NfcA"))
        )
    }

    @Test
    fun `identifies MIFARE Classic 1K and 4K from ATQA and SAK`() {
        val mifare1k = ChipIdentifier.identifyChip(
            uid = "04112233445566".hexToByteArray(),
            technologies = listOf("NfcA", "MifareClassic"),
            atqa = byteArrayOf(0x04, 0x00),
            sak = 0x08
        )
        assertEquals("MIFARE Classic 1K", mifare1k.chipGuess)
        assertEquals("NXP Semiconductors", mifare1k.manufacturer)
        assertEquals(CardCategory.ACCESS, mifare1k.suggestedCategory)

        val mifare4k = ChipIdentifier.identifyChip(
            uid = "04112233445566".hexToByteArray(),
            technologies = listOf("NfcA", "MifareClassic"),
            atqa = byteArrayOf(0x02, 0x00),
            sak = 0x18,
            classicSize = 4096
        )
        assertEquals("MIFARE Classic 4K", mifare4k.chipGuess)
    }

    @Test
    fun `distinguishes MIFARE Ultralight, Ultralight C, NTAG213, NTAG215, and NTAG216`() {
        val ul = ChipIdentifier.identifyChip(
            uid = "04112233445566".hexToByteArray(),
            technologies = listOf("NfcA", "MifareUltralight", "Ndef"),
            atqa = byteArrayOf(0x44, 0x00),
            sak = 0x00,
            ndefMaxSize = 48
        )
        assertEquals("MIFARE Ultralight", ul.chipGuess)
        assertEquals(CardCategory.SMART_TAG, ul.suggestedCategory)

        val ulC = ChipIdentifier.identifyChip(
            uid = "04112233445566".hexToByteArray(),
            technologies = listOf("NfcA", "MifareUltralight"),
            atqa = byteArrayOf(0x44, 0x00),
            sak = 0x00,
            ultralightType = 2
        )
        assertEquals("MIFARE Ultralight C", ulC.chipGuess)

        val ntag213 = ChipIdentifier.identifyChip(
            uid = "04112233445566".hexToByteArray(),
            technologies = listOf("NfcA", "MifareUltralight", "Ndef"),
            atqa = byteArrayOf(0x44, 0x00),
            sak = 0x00,
            ndefMaxSize = 144
        )
        assertTrue(ntag213.chipGuess.contains("NTAG213"))

        val ntag215 = ChipIdentifier.identifyChip(
            uid = "04112233445566".hexToByteArray(),
            technologies = listOf("NfcA", "MifareUltralight", "Ndef"),
            atqa = byteArrayOf(0x44, 0x00),
            sak = 0x00,
            ndefMaxSize = 504
        )
        assertTrue(ntag215.chipGuess.contains("NTAG215"))

        val ntag216 = ChipIdentifier.identifyChip(
            uid = "04112233445566".hexToByteArray(),
            technologies = listOf("NfcA", "MifareUltralight", "Ndef"),
            atqa = byteArrayOf(0x44, 0x00),
            sak = 0x00,
            ndefMaxSize = 888
        )
        assertTrue(ntag216.chipGuess.contains("NTAG216"))
    }

    @Test
    fun `identifies MIFARE DESFire, SmartMX EMV, FeliCa, and ISO 15693 ICODE SLIX`() {
        val desfire = ChipIdentifier.identifyChip(
            uid = "04112233445566".hexToByteArray(),
            technologies = listOf("NfcA", "IsoDep"),
            atqa = byteArrayOf(0x44, 0x03),
            sak = 0x20,
            hasEmv = false
        )
        assertTrue(desfire.chipGuess.contains("DESFire"))
        assertEquals(CardCategory.ACCESS, desfire.suggestedCategory)

        val smartMxEmv = ChipIdentifier.identifyChip(
            uid = "04112233445566".hexToByteArray(),
            technologies = listOf("NfcA", "IsoDep"),
            atqa = byteArrayOf(0x04, 0x00),
            sak = 0x20,
            hasEmv = true
        )
        assertTrue(smartMxEmv.chipGuess.contains("EMV"))
        assertEquals(CardCategory.PAYMENT, smartMxEmv.suggestedCategory)

        val suica = ChipIdentifier.identifyChip(
            uid = "0102030405060708".hexToByteArray(),
            technologies = listOf("NfcF"),
            feliCaSystemCode = byteArrayOf(0x00, 0x03)
        )
        assertTrue(suica.chipGuess.contains("Suica"))
        assertEquals(CardCategory.TRANSIT, suica.suggestedCategory)

        val octopus = ChipIdentifier.identifyChip(
            uid = "0102030405060708".hexToByteArray(),
            technologies = listOf("NfcF"),
            feliCaSystemCode = byteArrayOf(0x80.toByte(), 0x08)
        )
        assertTrue(octopus.chipGuess.contains("Octopus"))

        val felicaLite = ChipIdentifier.identifyChip(
            uid = "0102030405060708".hexToByteArray(),
            technologies = listOf("NfcF"),
            feliCaSystemCode = byteArrayOf(0x88.toByte(), 0xB4.toByte())
        )
        assertTrue(felicaLite.chipGuess.contains("FeliCa Lite"))

        val slix = ChipIdentifier.identifyChip(
            uid = "11223344556604E0".hexToByteArray(),
            technologies = listOf("NfcV")
        )
        assertTrue(slix.chipGuess.contains("ICODE SLIX"))
    }
}
