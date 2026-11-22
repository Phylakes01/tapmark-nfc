package com.tapmark.nfc.data

import com.tapmark.nfc.model.CardCategory
import com.tapmark.nfc.model.CardVisualStyle
import com.tapmark.nfc.nfc.BankBinDatabase
import com.tapmark.nfc.nfc.EmvReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SampleCardsTest {

    @Test
    fun `provides 7 curated sample cards with valid Luhn PANs and EMVCo security fields`() {
        val samples = SampleCards.all
        assertEquals(7, samples.size)
        assertTrue(samples.all { it.isSample })

        val expectedNote = "Printed CVV2/CVC2 is never stored on EMV chips (EMVCo security)"

        // [0] BPI
        val bpi = samples[0]
        assertEquals(CardVisualStyle.VISA_SLATE, bpi.visualStyle)
        assertEquals("Bank of the Philippine Islands", bpi.emvInfo?.bankName)
        assertEquals("453756", bpi.emvInfo?.bin)
        assertEquals(4, bpi.emvInfo?.transactionLogs?.size)
        assertEquals("Track 2 Discretionary (iCVV)", bpi.emvInfo?.chipCvcLabel)
        assertEquals("4190821100", bpi.emvInfo?.chipCvcData)
        assertEquals("7A3C19F4028E5B11", bpi.emvInfo?.cryptogramHex)
        assertEquals(expectedNote, bpi.emvInfo?.cvcNote)

        // [1] BDO
        val bdo = samples[1]
        assertEquals(CardVisualStyle.MASTERCARD_CHARCOAL, bdo.visualStyle)
        assertEquals("BDO Unibank", bdo.emvInfo?.bankName)
        assertEquals("548774", bdo.emvInfo?.bin)
        assertEquals(2, bdo.emvInfo?.transactionLogs?.size)
        assertEquals("Track 2 Discretionary (iCVV)", bdo.emvInfo?.chipCvcLabel)
        assertEquals("8831049200", bdo.emvInfo?.chipCvcData)
        assertEquals("4D91E20B71AC3908", bdo.emvInfo?.cryptogramHex)
        assertEquals(expectedNote, bdo.emvInfo?.cvcNote)

        // [2] GCash
        val gcash = samples[2]
        assertEquals(CardVisualStyle.VISA_SLATE, gcash.visualStyle)
        assertEquals("GCash (G-Xchange, Inc.)", gcash.emvInfo?.bankName)
        assertEquals("441360", gcash.emvInfo?.bin)
        assertEquals(2, gcash.emvInfo?.transactionLogs?.size)
        assertEquals("Track 2 Discretionary (iCVV)", gcash.emvInfo?.chipCvcLabel)
        assertEquals("5501928301", gcash.emvInfo?.chipCvcData)
        assertEquals("9B12C488E031A4D2", gcash.emvInfo?.cryptogramHex)
        assertEquals(expectedNote, gcash.emvInfo?.cvcNote)

        // [3] Maya
        val maya = samples[3]
        assertEquals(CardVisualStyle.MASTERCARD_CHARCOAL, maya.visualStyle)
        assertEquals("Maya (Maya Philippines)", maya.emvInfo?.bankName)
        assertEquals("542482", maya.emvInfo?.bin)
        assertEquals(2, maya.emvInfo?.transactionLogs?.size)
        assertEquals("Dynamic CVC3 (9F61)", maya.emvInfo?.chipCvcLabel)
        assertEquals("0384", maya.emvInfo?.chipCvcData)
        assertEquals("6C41B9A2F10847E5", maya.emvInfo?.cryptogramHex)
        assertEquals(expectedNote, maya.emvInfo?.cvcNote)

        // [4] GoTyme
        val goTyme = samples[4]
        assertEquals(CardVisualStyle.VISA_SLATE, goTyme.visualStyle)
        assertEquals("GoTyme Bank", goTyme.emvInfo?.bankName)
        assertEquals("454090", goTyme.emvInfo?.bin)
        assertEquals(2, goTyme.emvInfo?.transactionLogs?.size)
        assertEquals("Track 2 Discretionary (iCVV)", goTyme.emvInfo?.chipCvcLabel)
        assertEquals("7742091502", goTyme.emvInfo?.chipCvcData)
        assertEquals("2E77F1A0C3819D04", goTyme.emvInfo?.cryptogramHex)
        assertEquals(expectedNote, goTyme.emvInfo?.cvcNote)

        // Verify all 5 payment cards pass Luhn and offline BIN lookup
        for (i in 0..4) {
            val emv = samples[i].emvInfo
            assertNotNull(emv)
            assertTrue("Sample $i PAN failed Luhn", EmvReader.isLuhnValid(emv!!.panFull!!))
            assertEquals(emv.bankName, BankBinDatabase.lookupOffline(emv.bin)?.bankName)
        }

        // [5] Transit & [6] Smart Tag
        assertEquals(CardCategory.TRANSIT, samples[5].category)
        assertEquals("MetroLink Transit Pass", samples[5].cardTitle)
        assertEquals(CardCategory.SMART_TAG, samples[6].category)
        assertEquals("Studio Guest Wi-Fi & Guide", samples[6].cardTitle)
    }
}
