package com.tapmark.nfc.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BankBinDatabaseTest {

    @Test
    fun `resolves curated Philippine banks and e-wallet BINs`() {
        val bpiDebit = BankBinDatabase.lookupOffline("453756")
        assertNotNull(bpiDebit)
        assertEquals("Bank of the Philippine Islands", bpiDebit!!.bankName)
        assertEquals("Debit", bpiDebit.cardType)
        assertEquals("Classic", bpiDebit.cardTier)
        assertEquals("Philippines", bpiDebit.country)

        assertEquals("Bank of the Philippine Islands", BankBinDatabase.lookupOffline("541572")?.bankName)
        assertEquals("Titanium", BankBinDatabase.lookupOffline("541572")?.cardTier)
        assertEquals("Bank of the Philippine Islands", BankBinDatabase.lookupOffline("542458")?.bankName)
        assertEquals("Bank of the Philippine Islands", BankBinDatabase.lookupOffline("526491")?.bankName)

        // BDO verified PH BINs (and ensure unverified international 541701 is not hardcoded as BDO)
        assertEquals("BDO Unibank", BankBinDatabase.lookupOffline("541781")?.bankName)
        assertEquals("BDO Unibank", BankBinDatabase.lookupOffline("524301")?.bankName)
        assertEquals("BDO Unibank", BankBinDatabase.lookupOffline("360999")?.bankName)
        assertNull(BankBinDatabase.lookupOffline("541701"))

        assertEquals("Metrobank", BankBinDatabase.lookupOffline("405598")?.bankName)
        assertEquals("Debit", BankBinDatabase.lookupOffline("524128")?.cardType)
        assertEquals("UnionBank of the Philippines", BankBinDatabase.lookupOffline("437315")?.bankName)
        assertEquals("UnionBank of the Philippines", BankBinDatabase.lookupOffline("544538")?.bankName)
        assertEquals("Land Bank of the Philippines", BankBinDatabase.lookupOffline("554541")?.bankName)

        assertEquals("GCash (G-Xchange, Inc.)", BankBinDatabase.lookupOffline("223540")?.bankName)
        assertEquals("GCash (G-Xchange, Inc.)", BankBinDatabase.lookupOffline("441360")?.bankName)
        assertEquals("GCash (G-Xchange, Inc.)", BankBinDatabase.lookupOffline("549627")?.bankName)
        assertEquals("Maya (Maya Philippines)", BankBinDatabase.lookupOffline("542482")?.bankName)

        assertEquals("Security Bank", BankBinDatabase.lookupOffline("515603")?.bankName)
        assertEquals("RCBC", BankBinDatabase.lookupOffline("431170")?.bankName)
        assertEquals("Philippine National Bank", BankBinDatabase.lookupOffline("521387")?.bankName)
        assertEquals("China Bank", BankBinDatabase.lookupOffline("522604")?.bankName)
        assertEquals("EastWest Bank", BankBinDatabase.lookupOffline("412611")?.bankName)
        assertEquals("GoTyme Bank", BankBinDatabase.lookupOffline("424032")?.bankName)
        assertEquals("SeaBank Philippines", BankBinDatabase.lookupOffline("529416")?.bankName)
        assertEquals("CIMB Bank Philippines", BankBinDatabase.lookupOffline("436741")?.bankName)
        assertEquals("Tonik Digital Bank", BankBinDatabase.lookupOffline("555562")?.bankName)
        assertEquals("Robinsons Bank", BankBinDatabase.lookupOffline("406159")?.bankName)
        assertEquals("Asia United Bank (AUB)", BankBinDatabase.lookupOffline("515103")?.bankName)
        assertEquals("HSBC Philippines", BankBinDatabase.lookupOffline("402893")?.bankName)
    }

    @Test
    fun `resolves curated global bank BINs`() {
        assertEquals("Chase", BankBinDatabase.lookupOffline("414720")?.bankName)
        assertEquals("Bank of America", BankBinDatabase.lookupOffline("440066")?.bankName)
        assertEquals("Wells Fargo", BankBinDatabase.lookupOffline("434256")?.bankName)
        assertEquals("Capital One", BankBinDatabase.lookupOffline("517805")?.bankName)
        assertEquals("Discover", BankBinDatabase.lookupOffline("601100")?.bankName)
        assertEquals("Barclays", BankBinDatabase.lookupOffline("465901")?.bankName)
        assertEquals("Revolut", BankBinDatabase.lookupOffline("400549")?.bankName)
        assertEquals("Wise", BankBinDatabase.lookupOffline("410558")?.bankName)
        assertEquals("Monzo", BankBinDatabase.lookupOffline("516859")?.bankName)
        assertEquals("DBS Bank", BankBinDatabase.lookupOffline("401893")?.bankName)
        assertEquals("OCBC Bank", BankBinDatabase.lookupOffline("400610")?.bankName)
        assertEquals("UOB", BankBinDatabase.lookupOffline("400682")?.bankName)
    }

    @Test
    fun `extracts bank names from Issuer URL tag 5F50 with domain fallback`() {
        assertEquals("Bank of the Philippine Islands (BPI)", BankBinDatabase.extractBankFromIssuerUrl("https://www.bpi.com.ph/cards"))
        assertEquals("BDO Unibank", BankBinDatabase.extractBankFromIssuerUrl("http://bdo.com.ph"))
        assertEquals("UnionBank of the Philippines", BankBinDatabase.extractBankFromIssuerUrl("https://www.unionbankph.com/personal"))
        assertEquals("Metrobank", BankBinDatabase.extractBankFromIssuerUrl("metrobank.com.ph"))
        assertEquals("GCash", BankBinDatabase.extractBankFromIssuerUrl("https://www.gcash.com"))
        assertEquals("Maya", BankBinDatabase.extractBankFromIssuerUrl("https://www.maya.ph"))
        assertEquals("GoTyme Bank", BankBinDatabase.extractBankFromIssuerUrl("https://gotyme.com.ph"))
        assertEquals("SeaBank Philippines", BankBinDatabase.extractBankFromIssuerUrl("https://www.seabank.ph"))
        assertEquals("Security Bank", BankBinDatabase.extractBankFromIssuerUrl("https://www.securitybank.com"))
        assertEquals("RCBC", BankBinDatabase.extractBankFromIssuerUrl("https://rcbc.com"))
        assertEquals("Philippine National Bank", BankBinDatabase.extractBankFromIssuerUrl("https://www.pnb.com.ph"))
        assertEquals("China Bank", BankBinDatabase.extractBankFromIssuerUrl("https://www.chinabank.ph"))
        assertEquals("EastWest Bank", BankBinDatabase.extractBankFromIssuerUrl("https://www.eastwestbanker.com"))
        assertEquals("Northstar Bank", BankBinDatabase.extractBankFromIssuerUrl("https://cards.northstar-bank.co.uk/support"))
        assertNull(BankBinDatabase.extractBankFromIssuerUrl("   "))
        assertNull(BankBinDatabase.extractBankFromIssuerUrl("invalid"))
    }

    @Test
    fun `extracts bank names from Application Label and ignores generic network labels`() {
        assertEquals("Bank of the Philippine Islands (BPI)", BankBinDatabase.extractBankFromAppLabel("BPI Debit Mastercard"))
        assertEquals("BDO Unibank", BankBinDatabase.extractBankFromAppLabel("BDO VISA CLASSIC"))
        assertEquals("UnionBank of the Philippines", BankBinDatabase.extractBankFromAppLabel("UnionBank Visa"))
        assertEquals("Metrobank", BankBinDatabase.extractBankFromAppLabel("METROBANK ON"))
        assertEquals("GCash", BankBinDatabase.extractBankFromAppLabel("GCASH VISA"))
        assertEquals("Maya", BankBinDatabase.extractBankFromAppLabel("MAYA DEBIT"))
        assertEquals("GoTyme Bank", BankBinDatabase.extractBankFromAppLabel("GoTyme Visa Debit"))
        assertEquals("SeaBank Philippines", BankBinDatabase.extractBankFromAppLabel("SeaBank Debit"))
        assertEquals("RCBC", BankBinDatabase.extractBankFromAppLabel("RCBC Bankard"))
        assertEquals("Security Bank", BankBinDatabase.extractBankFromAppLabel("Security Bank Gold"))
        assertEquals("China Bank", BankBinDatabase.extractBankFromAppLabel("CHINABANK PRIME"))
        assertEquals("Philippine National Bank", BankBinDatabase.extractBankFromAppLabel("PNB Mabuhay"))
        assertEquals("EastWest Bank", BankBinDatabase.extractBankFromAppLabel("EastWest Platinum"))
        assertEquals("Chase", BankBinDatabase.extractBankFromAppLabel("CHASE SAPPHIRE"))
        assertEquals("Citibank", BankBinDatabase.extractBankFromAppLabel("CITI REWARDS"))
        assertEquals("HSBC", BankBinDatabase.extractBankFromAppLabel("HSBC PREMIER"))
        assertEquals("DBS Bank", BankBinDatabase.extractBankFromAppLabel("DBS LIVE FRESH"))
        assertEquals("OCBC Bank", BankBinDatabase.extractBankFromAppLabel("OCBC 365"))
        assertEquals("UOB", BankBinDatabase.extractBankFromAppLabel("UOB ONE CARD"))

        // Generic network labels must be ignored
        assertNull(BankBinDatabase.extractBankFromAppLabel("VISA DEBIT"))
        assertNull(BankBinDatabase.extractBankFromAppLabel("Debit Mastercard"))
        assertNull(BankBinDatabase.extractBankFromAppLabel("MASTERCARD"))
    }

    @Test
    fun `normalizes issuer names and preserves acronyms cleanly`() {
        assertEquals("GCash (G-Xchange, Inc.)", BankBinDatabase.normalizeIssuerName("G-XCHANGE, INC."))
        assertEquals("Maya (Maya Philippines)", BankBinDatabase.normalizeIssuerName("PAYMAYA PHILIPPINES, INC."))
        assertEquals("Bank of the Philippine Islands", BankBinDatabase.normalizeIssuerName("BANK OF THE PHILIPPINE ISLANDS"))
        assertEquals("BDO Unibank", BankBinDatabase.normalizeIssuerName("BANCO DE ORO UNIBANK, INC."))
        assertEquals("PSBank Savings Corp.", BankBinDatabase.normalizeIssuerName("PSBANK SAVINGS CORP."))
        assertEquals("BPI Family Bank PH, Inc.", BankBinDatabase.normalizeIssuerName("BPI FAMILY BANK PH, INC."))
        assertEquals("RCBC Bankard USA", BankBinDatabase.normalizeIssuerName("RCBC BANKARD USA"))
        assertEquals("DBS Bank Ltd.", BankBinDatabase.normalizeIssuerName("DBS BANK LTD."))
    }

    @Test
    fun `lookupOffline falls back across BIN, tag 42 IIN, issuer URL, and app label`() {
        // Tag 42 IIN fallback when BIN is null
        val from42 = BankBinDatabase.lookupOffline(
            bin = null,
            iinTag42 = "542482",
            appLabel = "VISA DEBIT",
            issuerUrl = null
        )
        assertEquals("Maya (Maya Philippines)", from42?.bankName)

        // Issuer URL fallback when BIN is unmapped
        val fromUrl = BankBinDatabase.lookupOffline(
            bin = "499999",
            iinTag42 = null,
            appLabel = "VISA DEBIT",
            issuerUrl = "https://www.gotyme.com.ph"
        )
        assertEquals("GoTyme Bank", fromUrl?.bankName)
        assertEquals("Debit", fromUrl?.cardType)
        assertEquals("Philippines", fromUrl?.country)

        // App label fallback when URL is absent
        val fromLabel = BankBinDatabase.lookupOffline(
            bin = "499999",
            iinTag42 = null,
            appLabel = "BDO Debit Mastercard",
            issuerUrl = null
        )
        assertEquals("BDO Unibank", fromLabel?.bankName)
        assertEquals("Debit", fromLabel?.cardType)

        // Strict 6-digit BIN validation
        assertEquals("453756", BankBinDatabase.strictSixDigitBin("4537561044128911"))
        assertNull(BankBinDatabase.strictSixDigitBin("45375"))
    }

    @Test
    fun `parses HandyAPI and Binlist JSON responses purely in Kotlin`() {
        val handyJson = """
            {
              "Status": "SUCCESS",
              "Scheme": "VISA",
              "Type": "DEBIT",
              "Issuer": "BANK OF THE PHILIPPINE ISLANDS",
              "CardTier": "CLASSIC",
              "Country": {
                "A2": "PH",
                "Name": "PHILIPPINES"
              },
              "Luhn": true
            }
        """.trimIndent()
        val handy = BankResolver.parseHandyApiResponse(handyJson)
        assertNotNull(handy)
        assertEquals("Bank of the Philippine Islands", handy!!.bankName)
        assertEquals("Debit", handy.cardType)
        assertEquals("Classic", handy.cardTier)
        assertEquals("Philippines", handy.country)

        val handyNotFound = """{"Status":"NOT FOUND","Issuer":null}"""
        assertNull(BankResolver.parseHandyApiResponse(handyNotFound))

        val binlistJson = """
            {
              "scheme": "mastercard",
              "type": "credit",
              "brand": "Platinum",
              "prepaid": false,
              "country": {
                "alpha2": "PH",
                "name": "Philippines"
              },
              "bank": {
                "name": "G-XCHANGE, INC.",
                "url": "www.gcash.com"
              }
            }
        """.trimIndent()
        val binlist = BankResolver.parseBinlistResponse(binlistJson)
        assertNotNull(binlist)
        assertEquals("GCash (G-Xchange, Inc.)", binlist!!.bankName)
        assertEquals("Credit", binlist.cardType)
        assertEquals("Platinum", binlist.cardTier)
        assertEquals("Philippines", binlist.country)

        assertNull(BankResolver.parseHandyApiResponse("<html>502 Bad Gateway</html>"))
        assertNull(BankResolver.parseBinlistResponse(""))
        assertTrue(BankResolver.parseBinlistResponse("""{"bank":{"name":""}}""") == null)
    }
}
