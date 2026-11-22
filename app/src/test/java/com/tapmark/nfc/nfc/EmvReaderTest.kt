package com.tapmark.nfc.nfc

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.StandardCharsets

class EmvReaderTest {

    private fun tlv(tagHex: String, value: ByteArray): ByteArray {
        val tag = tagHex.hexToByteArray()
        return tag + BerTlvParser.encodeBerLength(value.size) + value
    }

    private fun sw9000(payload: ByteArray): ByteArray =
        payload + byteArrayOf(0x90.toByte(), 0x00)

    @Test
    fun `executes full Visa PPSE priority sorting, PDOL, GPO Format 2, AFL records, GET DATA, and Tx Logs`() {
        var capturedGpoApdu: ByteArray? = null

        val visaAid = "A0000000031010".hexToByteArray()
        val mcAid = "A0000000041010".hexToByteArray()

        // PPSE lists Mastercard (priority 2) before Visa (priority 1) to verify sorting by tag 87
        val mcTemplate = tlv(
            "61",
            tlv("4F", mcAid) + tlv("50", "Mastercard".toByteArray()) + tlv("87", byteArrayOf(0x02))
        )
        val visaTemplate = tlv(
            "61",
            tlv("4F", visaAid) + tlv("50", "Visa Debit".toByteArray()) + tlv("87", byteArrayOf(0x01))
        )
        val ppseResp = tlv("6F", tlv("84", "2PAY.SYS.DDF01".toByteArray()) + tlv("A5", tlv("BF0C", mcTemplate + visaTemplate)))

        // Visa SELECT response with PDOL (9F38) and Log Entry (9F4D -> SFI 11, max 10)
        val pdolValue = "9F66049F02069F03069F1A0295055F2A029A039C019F3704".hexToByteArray()
        val visaSelectResp = tlv(
            "6F",
            tlv("84", visaAid) +
                tlv(
                    "A5",
                    tlv("50", "Visa Debit".toByteArray(StandardCharsets.US_ASCII)) +
                        tlv("9F38", pdolValue) +
                        tlv("BF0C", tlv("9F4D", byteArrayOf(0x0B, 0x0A)))
                )
        )

        // GPO Format 2 (77) with AIP (82), AFL (94: SFI 1 rec 1..1), and inline Track 2 (57)
        val track2Bytes = "4532891044128917D2908201123456789F".hexToByteArray()
        val gpoResp = tlv(
            "77",
            tlv("82", byteArrayOf(0x20, 0x00)) +
                tlv("94", byteArrayOf(0x08, 0x01, 0x01, 0x00)) +
                tlv("57", track2Bytes)
        )

        // READ RECORD SFI 1 Rec 1 (70)
        val rec1Resp = tlv(
            "70",
            tlv("5A", "4532891044128917".hexToByteArray()) +
                tlv("5F20", "VANCE/CLARA     ".toByteArray(StandardCharsets.US_ASCII)) +
                tlv("5F24", "290831".hexToByteArray()) +
                tlv("5F25", "240801".hexToByteArray()) +
                tlv("5F28", "0840".hexToByteArray()) +
                tlv("9F42", "0840".hexToByteArray()) +
                tlv("5F30", "0201".hexToByteArray())
        )

        val logFormatDol = "9A039F02065F2A029F1A02".hexToByteArray()
        val logRec1 = "26111800000000148008400840".hexToByteArray()
        val logRec2 = "26111400000000624508400840".hexToByteArray()

        val channel = object : IsoDepChannel {
            override fun transceive(command: ByteArray): ByteArray {
                val hex = command.toHexString()
                return when {
                    hex == "00A404000E325041592E5359532E444446303100" -> sw9000(ppseResp)
                    hex == "00A4040007A000000003101000" -> sw9000(visaSelectResp)
                    hex.startsWith("80A80000") -> {
                        capturedGpoApdu = command
                        sw9000(gpoResp)
                    }
                    hex == "00B2010C00" -> sw9000(rec1Resp) // SFI 1: (1 shl 3) or 4 = 0x0C
                    hex == "80CA9F3600" -> sw9000(tlv("9F36", byteArrayOf(0x00, 0x8E.toByte())))
                    hex == "80CA9F1700" -> sw9000(tlv("9F17", byteArrayOf(0x03)))
                    hex == "80CA9F4F00" -> sw9000(tlv("9F4F", logFormatDol))
                    hex == "00B2015C00" -> sw9000(logRec1) // SFI 11: (11 shl 3) or 4 = 0x5C
                    hex == "00B2025C00" -> sw9000(logRec2)
                    hex == "00B2035C00" -> byteArrayOf(0x6A, 0x83.toByte())
                    else -> byteArrayOf(0x6A, 0x82.toByte())
                }
            }
        }

        val result = EmvReader.readCard(channel)
        val emv = result.emvInfo
        assertNotNull(emv)
        emv!!

        // Verify Visa priority 01 beat Mastercard priority 02
        assertEquals("Visa", emv.network)
        assertEquals("Visa Debit", emv.appLabel)
        assertEquals("A0000000031010", emv.aid)

        // Verify PDOL payload length (33 bytes) and TTQ inside GPO APDU
        assertNotNull(capturedGpoApdu)
        val gpoBytes = capturedGpoApdu!!
        // 80 A8 00 00 <Lc=35> 83 <len=33> <33 bytes PDOL> 00
        assertEquals(35, gpoBytes[4].toInt() and 0xFF)
        assertEquals(0x83, gpoBytes[5].toInt() and 0xFF)
        assertEquals(33, gpoBytes[6].toInt() and 0xFF)
        assertEquals("36000000", gpoBytes.copyOfRange(7, 11).toHexString())

        // Verify parsed card fields
        assertEquals("4532891044128917", emv.panFull)
        assertEquals("4532 •••• •••• 8917", emv.panMasked)
        assertEquals("08/29", emv.expiryFormatted)
        assertEquals("08/24", emv.effectiveFormatted)
        assertEquals("CLARA VANCE", emv.cardholderName)
        assertEquals("201", emv.serviceCode)
        assertEquals("United States (840)", emv.issuerCountry)
        assertEquals("USD (840)", emv.currency)
        assertEquals(142, emv.atc)
        assertEquals(3, emv.pinTryCounter)
        assertEquals(true, emv.luhnValid)

        // Verify transaction logs
        assertEquals(2, emv.transactionLogs.size)
        assertEquals("2026-11-18", emv.transactionLogs[0].date)
        assertEquals("14.80", emv.transactionLogs[0].amount)
        assertEquals("USD", emv.transactionLogs[0].currency)
        assertEquals("United States", emv.transactionLogs[0].country)
        assertEquals("62.45", emv.transactionLogs[1].amount)
    }

    @Test
    fun `executes Mastercard PPSE, SELECT, Format 1 GPO, and READ RECORD flow`() {
        val mcAid = "A0000000041010".hexToByteArray()
        val ppseResp = tlv(
            "6F",
            tlv("A5", tlv("BF0C", tlv("61", tlv("4F", mcAid) + tlv("50", "Debit Mastercard".toByteArray()))))
        )
        val selectResp = tlv(
            "6F",
            tlv("84", mcAid) + tlv("A5", tlv("9F12", "Mastercard".toByteArray()))
        )
        // Format 1 (80): 2-byte AIP (19 80) + 4-byte AFL (SFI 2, rec 1..2 -> 10 01 02 00)
        val gpoFmt1Resp = tlv("80", "198010010200".hexToByteArray())
        val rec1 = tlv("70", tlv("57", "5413330089012349D281220100000F".hexToByteArray()))
        val rec2 = tlv("70", tlv("5F20", "DOE/JANE".toByteArray()) + tlv("5F28", "0826".hexToByteArray()))

        val channel = object : IsoDepChannel {
            override fun transceive(command: ByteArray): ByteArray {
                return when (command.toHexString()) {
                    "00A404000E325041592E5359532E444446303100" -> sw9000(ppseResp)
                    "00A4040007A000000004101000" -> sw9000(selectResp)
                    "80A8000002830000" -> sw9000(gpoFmt1Resp)
                    "00B2011400" -> sw9000(rec1) // SFI 2: (2 shl 3) or 4 = 0x14
                    "00B2021400" -> sw9000(rec2)
                    else -> byteArrayOf(0x6A, 0x82.toByte())
                }
            }
        }

        val result = EmvReader.readCard(channel)
        val emv = result.emvInfo
        assertNotNull(emv)
        emv!!

        assertEquals("Mastercard", emv.network)
        assertEquals("Mastercard", emv.appLabel)
        assertEquals("5413330089012349", emv.panFull)
        assertEquals("5413 •••• •••• 2349", emv.panMasked)
        assertEquals("12/28", emv.expiryFormatted)
        assertEquals("201", emv.serviceCode)
        assertEquals("JANE DOE", emv.cardholderName)
        assertEquals("United Kingdom (826)", emv.issuerCountry)
        assertEquals(true, emv.luhnValid)
        assertTrue(result.rawTlvEntries.any { it.tagHex == "82" && it.valueHex == "1980" })
        assertTrue(result.rawTlvEntries.any { it.tagHex == "94" && it.valueHex == "10010200" })
    }

    @Test
    fun `falls back to AID probing when PPSE fails and supports 61XX GET RESPONSE`() {
        val amexAid = "A000000025010801".hexToByteArray()
        val selectFull = tlv("6F", tlv("84", amexAid) + tlv("A5", tlv("50", "American Express".toByteArray())))
        val part1 = selectFull.copyOfRange(0, 10)
        val part2 = selectFull.copyOfRange(10, selectFull.size)

        val rec1 = tlv("70", tlv("5A", "378282246310005F".hexToByteArray()) + tlv("5F24", "300531".hexToByteArray()))

        val channel = object : IsoDepChannel {
            override fun transceive(command: ByteArray): ByteArray {
                val hex = command.toHexString()
                return when {
                    hex == "00A404000E325041592E5359532E444446303100" -> byteArrayOf(0x6A, 0x82.toByte())
                    hex == "00A4040008A00000002501080100" -> part1 + byteArrayOf(0x61, part2.size.toByte())
                    hex.startsWith("00C00000") -> sw9000(part2)
                    hex == "80A8000002830000" -> byteArrayOf(0x69, 0x85.toByte()) // fallback to SFI 1..2 read
                    hex == "00B2010C00" -> sw9000(rec1)
                    else -> byteArrayOf(0x6A, 0x82.toByte())
                }
            }
        }

        val result = EmvReader.readCard(channel)
        val emv = result.emvInfo
        assertNotNull(emv)
        emv!!
        assertEquals("Amex", emv.network)
        assertEquals("378282246310005", emv.panFull)
        assertEquals("3782 •••••• •0005", emv.panMasked)
        assertEquals("05/30", emv.expiryFormatted)
        assertEquals(true, emv.luhnValid)
    }

    @Test
    fun `returns null emvInfo on non-EMV IsoDep card`() {
        val channel = object : IsoDepChannel {
            override fun transceive(command: ByteArray): ByteArray = byteArrayOf(0x6A, 0x82.toByte())
        }
        val result = EmvReader.readCard(channel)
        assertNull(result.emvInfo)
    }

    @Test
    fun `dynamic PDOL builder populates exact byte sizes and zero-pads unknown tags`() {
        val pdol = "9F66049F02069F03069F1A0295055F2A029A039C019F37049F6E03".hexToByteArray()
        val un = byteArrayOf(0x12, 0x34, 0x56, 0x78)
        val data = EmvReader.buildPdolData(pdol, dateYyMmDd = "261122", unpredictableNumber = un)

        assertEquals(36, data.size)
        // 9F66 (4) -> 36000000
        assertEquals("36000000", data.copyOfRange(0, 4).toHexString())
        // 9F02 (6) + 9F03 (6) -> zeros
        assertEquals("000000000000", data.copyOfRange(4, 10).toHexString())
        assertEquals("000000000000", data.copyOfRange(10, 16).toHexString())
        // 9F1A (2) -> 0840
        assertEquals("0840", data.copyOfRange(16, 18).toHexString())
        // 95 (5) -> zeros
        assertEquals("0000000000", data.copyOfRange(18, 23).toHexString())
        // 5F2A (2) -> 0840
        assertEquals("0840", data.copyOfRange(23, 25).toHexString())
        // 9A (3) -> 261122
        assertEquals("261122", data.copyOfRange(25, 28).toHexString())
        // 9C (1) -> 00
        assertEquals("00", data.copyOfRange(28, 29).toHexString())
        // 9F37 (4) -> 12345678
        assertEquals("12345678", data.copyOfRange(29, 33).toHexString())
        // Unknown 9F6E (3) -> 000000
        assertEquals("000000", data.copyOfRange(33, 36).toHexString())
    }

    @Test
    fun `validates Luhn, Track 2 parsing, PAN masking, and network detection`() {
        assertTrue(EmvReader.isLuhnValid("4532891044128917"))
        assertFalse(EmvReader.isLuhnValid("4532891044128910"))
        assertFalse(EmvReader.isLuhnValid("1234"))

        val t2 = EmvReader.parseTrack2("4532891044128917D2908201999F")
        assertNotNull(t2)
        assertEquals("4532891044128917", t2!!.pan)
        assertEquals("08/29", t2.expiryFormatted)
        assertEquals("201", t2.serviceCode)

        assertEquals("Visa", EmvReader.detectNetwork("A0000000031010", null))
        assertEquals("Mastercard", EmvReader.detectNetwork("A0000000041010", null))
        assertEquals("Maestro", EmvReader.detectNetwork("A0000000043060", null))
        assertEquals("Amex", EmvReader.detectNetwork("A000000025010801", null))
        assertEquals("Discover", EmvReader.detectNetwork("A0000001523010", null))
        assertEquals("JCB", EmvReader.detectNetwork("A0000000651010", null))
        assertEquals("UnionPay", EmvReader.detectNetwork("A000000333010101", null))
    }

    @Test
    fun `unwraps 70 log records, skips 00 and FF cyclic slots, rejects invalid dates, and probes SFI via GET DATA`() {
        val visaAid = "A0000000031010".hexToByteArray()
        val ppseResp = tlv(
            "6F",
            tlv("A5", tlv("BF0C", tlv("61", tlv("4F", visaAid) + tlv("50", "BPI Visa Debit".toByteArray()))))
        )
        val selectResp = tlv(
            "6F",
            tlv("84", visaAid) + tlv("A5", tlv("50", "BPI Visa Debit".toByteArray()))
        )
        val gpoResp = tlv(
            "77",
            tlv("82", byteArrayOf(0x20, 0x00)) +
                tlv("94", byteArrayOf(0x08, 0x01, 0x01, 0x00))
        )
        val rec1 = tlv(
            "70",
            tlv("5A", "4537561044128911".hexToByteArray()) +
                tlv("5F24", "290831".hexToByteArray()) +
                tlv("5F28", "0608".hexToByteArray()) +
                tlv("9F42", "0608".hexToByteArray())
        )

        // 9A(3) 9F21(3) 9F02(6) 5F2A(2) 9C(1) 9F4E(12) 9F36(2) 9F27(1) -> 30 bytes
        val logFormatDol = "9A039F21039F02065F2A029C019F4E0C9F36029F2701".hexToByteArray()

        val slot1Zero = ByteArray(30) { 0x00 }
        val slot2Wrapped = tlv(
            "70",
            "261122".hexToByteArray() +
                "184215".hexToByteArray() +
                "000000125000".hexToByteArray() +
                "0608".hexToByteArray() +
                "00".hexToByteArray() +
                "MERCURY DRUG".toByteArray(StandardCharsets.US_ASCII) +
                "008E".hexToByteArray() +
                "40".hexToByteArray()
        )
        val slot3Ff = ByteArray(30) { 0xFF.toByte() }
        val slot4CorruptZeroDate = "000000".hexToByteArray() +
            "000000".hexToByteArray() +
            "000000000000".hexToByteArray() +
            "0608".hexToByteArray() +
            "00".hexToByteArray() +
            "            ".toByteArray(StandardCharsets.US_ASCII) +
            "0000".hexToByteArray() +
            "00".hexToByteArray()
        val slot5InvalidMonth = "261301".hexToByteArray() +
            "000000".hexToByteArray() +
            "000000000000".hexToByteArray() +
            "0608".hexToByteArray() +
            "00".hexToByteArray() +
            "BAD DATE    ".toByteArray(StandardCharsets.US_ASCII) +
            "0000".hexToByteArray() +
            "00".hexToByteArray()
        val slot6Refund = "261120".hexToByteArray() +
            "090530".hexToByteArray() +
            "000000049950".hexToByteArray() +
            "0608".hexToByteArray() +
            "20".hexToByteArray() +
            "SM STORE BGC".toByteArray(StandardCharsets.US_ASCII) +
            "008D".hexToByteArray() +
            "80".hexToByteArray()
        val slot7CashDeclined = "261119".hexToByteArray() +
            "235959".hexToByteArray() +
            "000000200000".hexToByteArray() +
            "0608".hexToByteArray() +
            "01".hexToByteArray() +
            "BPI ATM AYAL".toByteArray(StandardCharsets.US_ASCII) +
            "008C".hexToByteArray() +
            "00".hexToByteArray()

        // No 9F4D returned; chip returns 9F4F via GET DATA and stores logs on SFI 12 (P2 = (12<<3)|4 = 0x64)
        val channel = object : IsoDepChannel {
            override fun transceive(command: ByteArray): ByteArray {
                return when (command.toHexString()) {
                    "00A404000E325041592E5359532E444446303100" -> sw9000(ppseResp)
                    "00A4040007A000000003101000" -> sw9000(selectResp)
                    "80A8000002830000" -> sw9000(gpoResp)
                    "00B2010C00" -> sw9000(rec1)
                    "80CA9F3600" -> sw9000(tlv("9F36", byteArrayOf(0x00, 0x8E.toByte())))
                    "80CA9F1300" -> sw9000(tlv("9F13", byteArrayOf(0x00, 0x8C.toByte())))
                    "80CA9F1700" -> sw9000(tlv("9F17", byteArrayOf(0x03)))
                    "80CA9F4F00" -> sw9000(tlv("9F4F", logFormatDol))
                    "00B2016400" -> sw9000(slot1Zero)
                    "00B2026400" -> sw9000(slot2Wrapped)
                    "00B2036400" -> sw9000(slot3Ff)
                    "00B2046400" -> sw9000(slot4CorruptZeroDate)
                    "00B2056400" -> sw9000(slot5InvalidMonth)
                    "00B2066400" -> sw9000(slot6Refund)
                    "00B2076400" -> sw9000(slot7CashDeclined)
                    "00B2086400" -> byteArrayOf(0x6A, 0x83.toByte())
                    else -> byteArrayOf(0x6A, 0x82.toByte())
                }
            }
        }

        val result = EmvReader.readCard(channel)
        val emv = result.emvInfo
        assertNotNull(emv)
        emv!!

        assertEquals("Bank of the Philippine Islands", emv.bankName)
        assertEquals("Debit", emv.cardType)
        assertEquals("Classic", emv.cardTier)
        assertEquals("453756", emv.bin)
        assertEquals(142, emv.atc)
        assertEquals(140, emv.lastOnlineAtc)
        assertEquals("Read 3 transaction(s) from chip log (SFI 12)", emv.logStatusMessage)

        assertEquals(3, emv.transactionLogs.size)

        val tx1 = emv.transactionLogs[0]
        assertEquals("2026-11-22", tx1.date)
        assertEquals("18:42:15", tx1.time)
        assertEquals("1,250.00", tx1.amount)
        assertEquals("PHP", tx1.currency)
        assertEquals("₱", tx1.currencySymbol)
        assertEquals("Purchase", tx1.txType)
        assertEquals("MERCURY DRUG", tx1.merchantName)
        assertEquals(142, tx1.atc)
        assertEquals("Offline Approved", tx1.cryptogramInfo)
        assertFalse(tx1.rawHex.isNullOrBlank())

        val tx2 = emv.transactionLogs[1]
        assertEquals("2026-11-20", tx2.date)
        assertEquals("09:05:30", tx2.time)
        assertEquals("499.50", tx2.amount)
        assertEquals("Refund", tx2.txType)
        assertEquals("SM STORE BGC", tx2.merchantName)
        assertEquals(141, tx2.atc)
        assertEquals("Online Auth", tx2.cryptogramInfo)

        val tx3 = emv.transactionLogs[2]
        assertEquals("2026-11-19", tx3.date)
        assertEquals("23:59:59", tx3.time)
        assertEquals("2,000.00", tx3.amount)
        assertEquals("Cash Advance", tx3.txType)
        assertEquals("Declined", tx3.cryptogramInfo)
    }

    @Test
    fun `supports DF4D DF4F Mastercard logs, 4-byte binary amounts, 9F03 fallback, and 9F42 fallback currency`() {
        val mcAid = "A0000000041010".hexToByteArray()
        val selectResp = tlv("6F", tlv("84", mcAid) + tlv("A5", tlv("50", "Mastercard".toByteArray())))
        val gpoResp = tlv("80", "198008010100".hexToByteArray())
        val rec1 = tlv(
            "70",
            tlv("5A", "5415721122334455".hexToByteArray()) +
                tlv("5F24", "291031".hexToByteArray()) +
                tlv("9F42", "0608".hexToByteArray())
        )
        // 9A(3) 9F02(4 binary) 9F03(4 binary) 9C(1) 9F36(2) -> 14 bytes (no 5F2A in DOL -> falls back to 9F42 PHP)
        val df4fDol = "9A039F02049F03049C019F3602".hexToByteArray()
        val logRec1 = "261121".hexToByteArray() + "0001E848".hexToByteArray() + "00000000".hexToByteArray() + "09".hexToByteArray() + "000A".hexToByteArray()
        val logRec2 = "261120".hexToByteArray() + "00000000".hexToByteArray() + "00002710".hexToByteArray() + "40".hexToByteArray() + "0009".hexToByteArray()
        val logRec3 = "261119".hexToByteArray() + "00000000".hexToByteArray() + "00000000".hexToByteArray() + "31".hexToByteArray() + "0008".hexToByteArray()

        val channel = object : IsoDepChannel {
            override fun transceive(command: ByteArray): ByteArray {
                return when (command.toHexString()) {
                    "00A404000E325041592E5359532E444446303100" -> byteArrayOf(0x6A, 0x82.toByte())
                    "00A4040007A000000004101000" -> sw9000(selectResp)
                    "80A8000002830000" -> sw9000(gpoResp)
                    "00B2010C00" -> sw9000(rec1)
                    "80CADF4D00" -> sw9000(tlv("DF4D", byteArrayOf(0x0B, 0x0A)))
                    "80CADF4F00" -> sw9000(tlv("DF4F", df4fDol))
                    "00B2015C00" -> sw9000(logRec1)
                    "00B2025C00" -> sw9000(logRec2)
                    "00B2035C00" -> sw9000(logRec3)
                    "00B2045C00" -> byteArrayOf(0x6A, 0x83.toByte())
                    else -> byteArrayOf(0x6A, 0x82.toByte())
                }
            }
        }

        val emv = EmvReader.readCard(channel).emvInfo
        assertNotNull(emv)
        emv!!
        assertEquals("Bank of the Philippine Islands", emv.bankName)
        assertEquals("Titanium", emv.cardTier)
        assertEquals(3, emv.transactionLogs.size)

        assertEquals("1,250.00", emv.transactionLogs[0].amount)
        assertEquals("PHP", emv.transactionLogs[0].currency)
        assertEquals("₱", emv.transactionLogs[0].currencySymbol)
        assertEquals("Cashback", emv.transactionLogs[0].txType)

        assertEquals("100.00", emv.transactionLogs[1].amount)
        assertEquals("Transfer", emv.transactionLogs[1].txType)

        assertEquals("0.00", emv.transactionLogs[2].amount)
        assertEquals("Balance Inquiry", emv.transactionLogs[2].txType)
    }

    @Test
    fun `retries GPO with non-zero amount and updated TTQs and reports honest log status`() {
        val gpoAttempts = mutableListOf<ByteArray>()
        val visaAid = "A0000000031010".hexToByteArray()
        val pdol = "9F66049F02069F1A025F2A029A039C019F3704".hexToByteArray()
        val selectResp = tlv(
            "6F",
            tlv("84", visaAid) + tlv(
                "A5",
                tlv("50", "VISA DEBIT".toByteArray()) +
                    tlv("9F38", pdol) +
                    tlv("BF0C", tlv("5F50", "https://www.gotyme.com.ph".toByteArray(StandardCharsets.US_ASCII)))
            )
        )
        val gpoSuccessResp = tlv(
            "77",
            tlv("82", byteArrayOf(0x20, 0x00)) +
                tlv("94", byteArrayOf(0x08, 0x01, 0x01, 0x00))
        )
        val rec1 = tlv("70", tlv("5A", "4999990011223344".hexToByteArray()) + tlv("5F24", "300131".hexToByteArray()))

        val channel = object : IsoDepChannel {
            override fun transceive(command: ByteArray): ByteArray {
                val hex = command.toHexString()
                return when {
                    hex == "00A404000E325041592E5359532E444446303100" -> byteArrayOf(0x6A, 0x82.toByte())
                    hex == "00A4040007A000000003101000" -> sw9000(selectResp)
                    hex.startsWith("80A80000") -> {
                        gpoAttempts.add(command)
                        when (gpoAttempts.size) {
                            1, 2 -> byteArrayOf(0x69, 0x85.toByte())
                            else -> sw9000(gpoSuccessResp)
                        }
                    }
                    hex == "00B2010C00" -> sw9000(rec1)
                    hex == "80CA9F3600" -> sw9000(tlv("9F36", byteArrayOf(0x00, 0x2A)))
                    else -> byteArrayOf(0x6A, 0x82.toByte())
                }
            }
        }

        val result = EmvReader.readCard(channel)
        val emv = result.emvInfo
        assertNotNull(emv)
        emv!!

        assertEquals(3, gpoAttempts.size)
        // Attempt 1: TTQ 36000000, Amount 000000000000
        assertEquals("36000000", gpoAttempts[0].copyOfRange(7, 11).toHexString())
        assertEquals("000000000000", gpoAttempts[0].copyOfRange(11, 17).toHexString())
        // Attempt 2: TTQ 36204000, Amount 000000000100
        assertEquals("36204000", gpoAttempts[1].copyOfRange(7, 11).toHexString())
        assertEquals("000000000100", gpoAttempts[1].copyOfRange(11, 17).toHexString())
        // Attempt 3: TTQ 26000000, Amount 000000000100
        assertEquals("26000000", gpoAttempts[2].copyOfRange(7, 11).toHexString())
        assertEquals("000000000100", gpoAttempts[2].copyOfRange(11, 17).toHexString())

        assertEquals("GoTyme Bank", emv.bankName)
        assertEquals(42, emv.atc)
        assertTrue(emv.transactionLogs.isEmpty())
        assertEquals(
            "This bank chip does not expose itemized transaction logs over contactless NFC · Chip ATC: 42 lifetime tap(s)",
            emv.logStatusMessage
        )
    }

    @Test
    fun `reports honest empty chip log status when 9F4D and 9F4F exist with zero stored records`() {
        val visaAid = "A0000000031010".hexToByteArray()
        val selectResp = tlv(
            "6F",
            tlv("84", visaAid) + tlv(
                "A5",
                tlv("50", "Visa Debit".toByteArray()) +
                    tlv("BF0C", tlv("9F4D", byteArrayOf(0x0B, 0x03)))
            )
        )
        val gpoResp = tlv("77", tlv("82", byteArrayOf(0x20, 0x00)) + tlv("94", byteArrayOf(0x08, 0x01, 0x01, 0x00)))
        val rec1 = tlv("70", tlv("5A", "4537561044128911".hexToByteArray()) + tlv("5F24", "290831".hexToByteArray()))
        val logFormatDol = "9A039F0206".hexToByteArray()

        val channel = object : IsoDepChannel {
            override fun transceive(command: ByteArray): ByteArray {
                return when (command.toHexString()) {
                    "00A404000E325041592E5359532E444446303100" -> byteArrayOf(0x6A, 0x82.toByte())
                    "00A4040007A000000003101000" -> sw9000(selectResp)
                    "80A8000002830000" -> sw9000(gpoResp)
                    "00B2010C00" -> sw9000(rec1)
                    "80CA9F4F00" -> sw9000(tlv("9F4F", logFormatDol))
                    "00B2015C00" -> sw9000(ByteArray(9) { 0x00 })
                    "00B2025C00" -> byteArrayOf(0x6A, 0x83.toByte())
                    else -> byteArrayOf(0x6A, 0x82.toByte())
                }
            }
        }

        val emv = EmvReader.readCard(channel).emvInfo
        assertNotNull(emv)
        assertTrue(emv!!.transactionLogs.isEmpty())
        assertEquals("Chip transaction log is empty (0 records stored)", emv.logStatusMessage)
    }
}
