package com.tapmark.nfc.nfc

import com.tapmark.nfc.model.CardCategory

data class ChipIdentification(
    val manufacturer: String,
    val chipGuess: String,
    val suggestedCategory: CardCategory
)

/**
 * Identifies NFC IC manufacturers (ISO/IEC 7816-6) and chip families from ATQA/SAK,
 * Android NFC tech lists, FeliCa system codes, and memory geometry heuristics.
 */
object ChipIdentifier {

    /** ISO/IEC 7816-6 IC Manufacturer Registration Codes. */
    val MANUFACTURER_MAP: Map<Int, String> = mapOf(
        0x01 to "Motorola",
        0x02 to "STMicroelectronics",
        0x03 to "Hitachi",
        0x04 to "NXP Semiconductors",
        0x05 to "Infineon Technologies",
        0x06 to "Cylink",
        0x07 to "Texas Instruments",
        0x08 to "Fujitsu",
        0x09 to "Matsushita / Panasonic",
        0x0A to "NEC",
        0x0B to "Oki Electric",
        0x0C to "Toshiba",
        0x0D to "Mitsubishi Electric",
        0x0E to "Samsung Electronics",
        0x0F to "Hynix",
        0x10 to "LG Semiconductors",
        0x11 to "Emosyn-EM Microelectronics",
        0x12 to "INSIDE Secure",
        0x13 to "ORGA Kartensysteme",
        0x14 to "SHARP",
        0x15 to "ATMEL",
        0x16 to "EM Microelectronic-Marin",
        0x17 to "KSW Microtec",
        0x18 to "ZMD",
        0x19 to "XICOR",
        0x1A to "Sony",
        0x1B to "Malaysia Microelectronic Solutions",
        0x1C to "Emosyn",
        0x1D to "Shanghai Fudan Microelectronics",
        0x1E to "Magellan Technology",
        0x1F to "Melexis",
        0x20 to "Renesas Technology",
        0x21 to "TAGSYS",
        0x22 to "Transcore",
        0x23 to "Shanghai Belling",
        0x24 to "Masktech",
        0x25 to "Innovision R&T",
        0x26 to "Hitachi ULSI Systems",
        0x27 to "Cypak",
        0x28 to "Ricoh",
        0x29 to "ASK",
        0x2A to "Sony",
        0x2B to "Dallas Semiconductor (Maxim)",
        0x2C to "Impinj",
        0x2D to "RightPlug Alliance",
        0x2E to "Broadcom",
        0x2F to "MStar Semiconductor",
        0x30 to "BeeDar",
        0x31 to "RFIDsec",
        0x32 to "Schweizer Electronic",
        0x33 to "AMIC Technology",
        0x34 to "Mikron JSC",
        0x48 to "INSIDE Secure",
        0x6E to "Fudan Microelectronics",
        0x7A to "QuanRay Electronics"
    )

    fun manufacturerName(code: Int): String? = MANUFACTURER_MAP[code and 0xFF]

    fun identifyManufacturer(
        uid: ByteArray,
        technologies: List<String> = emptyList()
    ): String {
        if (uid.isEmpty()) return "Unknown"

        val hasNfcV = technologies.any { it.equals("NfcV", ignoreCase = true) }
        val hasNfcF = technologies.any { it.equals("NfcF", ignoreCase = true) }

        // ISO 15693 (NfcV): 8-byte UID has 0xE0 in byte 7 (MSB) and IC manufacturer in byte 6
        if (hasNfcV && uid.size == 8 && (uid[7].toInt() and 0xFF) == 0xE0) {
            return manufacturerName(uid[6].toInt() and 0xFF) ?: "Unknown (0x%02X)".format(uid[6])
        }

        // FeliCa (NfcF): 8-byte IDm starts with 0x01 or 0x02 for Sony FeliCa chips
        if (hasNfcF && uid.size == 8) {
            val b0 = uid[0].toInt() and 0xFF
            if (b0 == 0x01 || b0 == 0x02) return "Sony (FeliCa)"
            return manufacturerName(b0) ?: "Sony / FeliCa"
        }

        // ISO 14443-3A 4-byte UID: 0x08 indicates a dynamic random UID
        if (uid.size == 4 && (uid[0].toInt() and 0xFF) == 0x08) {
            return "Dynamic Random UID"
        }

        val firstByte = uid[0].toInt() and 0xFF
        val mapped = manufacturerName(firstByte)
        if (uid.size >= 7) {
            return mapped ?: "Unknown (0x%02X)".format(firstByte)
        }
        return mapped ?: "Generic / 4-byte NUID"
    }

    fun identifyChip(
        uid: ByteArray,
        technologies: List<String>,
        atqa: ByteArray? = null,
        sak: Short? = null,
        historicalBytes: ByteArray? = null,
        ndefMaxSize: Int? = null,
        memoryBytes: Int? = null,
        ultralightType: Int? = null,
        classicType: Int? = null,
        classicSize: Int? = null,
        feliCaSystemCode: ByteArray? = null,
        hasEmv: Boolean = false,
        hasNdefRecords: Boolean = false
    ): ChipIdentification {
        val manufacturer = identifyManufacturer(uid, technologies)
        val hasIsoDep = technologies.any { it.equals("IsoDep", ignoreCase = true) }
        val hasNfcA = technologies.any { it.equals("NfcA", ignoreCase = true) }
        val hasNfcB = technologies.any { it.equals("NfcB", ignoreCase = true) }
        val hasNfcF = technologies.any { it.equals("NfcF", ignoreCase = true) }
        val hasNfcV = technologies.any { it.equals("NfcV", ignoreCase = true) }
        val hasClassic = technologies.any { it.equals("MifareClassic", ignoreCase = true) }
        val hasUltralight = technologies.any { it.equals("MifareUltralight", ignoreCase = true) }
        val hasNdef = technologies.any { it.equals("Ndef", ignoreCase = true) }

        val sakInt = sak?.toInt()?.and(0xFF)
        val isAtqaDesfire = atqa != null && atqa.size >= 2 && (
            ((atqa[0].toInt() and 0xFF) == 0x44 && (atqa[1].toInt() and 0xFF) == 0x03) ||
                ((atqa[0].toInt() and 0xFF) == 0x03 && (atqa[1].toInt() and 0xFF) == 0x44)
            )
        val isAtqaUltralight = atqa != null && atqa.size >= 2 && (
            ((atqa[0].toInt() and 0xFF) == 0x44 && (atqa[1].toInt() and 0xFF) == 0x00) ||
                ((atqa[0].toInt() and 0xFF) == 0x00 && (atqa[1].toInt() and 0xFF) == 0x44)
            )

        // 1. EMV Payment Cards
        if (hasEmv) {
            val guess = when {
                hasNfcB -> "EMV Contactless Card (ISO 14443-4 Type B)"
                sakInt == 0x20 || sakInt == 0x28 || sakInt == 0x38 ->
                    "SmartMX / EMV Contactless (ISO 14443-4)"
                else -> "EMV Contactless Payment Chip (ISO 14443-4)"
            }
            return ChipIdentification(manufacturer, guess, CardCategory.PAYMENT)
        }

        // 2. Sony FeliCa (NfcF)
        if (hasNfcF) {
            val sysHex = feliCaSystemCode?.toHexString()?.uppercase()
            return when (sysHex) {
                "0003" -> ChipIdentification(manufacturer, "FeliCa (Suica / PASMO Transit)", CardCategory.TRANSIT)
                "8008" -> ChipIdentification(manufacturer, "FeliCa (Octopus Transit)", CardCategory.TRANSIT)
                "FE00" -> ChipIdentification(manufacturer, "FeliCa Common Area (Edy / Mobile)", CardCategory.TRANSIT)
                "88B4" -> ChipIdentification(manufacturer, "Sony FeliCa Lite / Lite-S", CardCategory.SMART_TAG)
                "12FC" -> ChipIdentification(manufacturer, "Sony FeliCa (NFC Forum Type 3 Tag)", CardCategory.SMART_TAG)
                else -> ChipIdentification(manufacturer, "Sony FeliCa (JIS X 6319-4)", CardCategory.TRANSIT)
            }
        }

        // 3. ISO 15693 (NfcV)
        if (hasNfcV) {
            val mfgCode = if (uid.size == 8 && (uid[7].toInt() and 0xFF) == 0xE0) {
                uid[6].toInt() and 0xFF
            } else -1
            val guess = when (mfgCode) {
                0x04 -> when (memoryBytes) {
                    256 -> "NXP ICODE SLIX2 (ISO 15693)"
                    320 -> "NXP ICODE SLIX-L / SLI-L (ISO 15693)"
                    else -> "NXP ICODE SLIX / SLI (ISO 15693)"
                }
                0x02 -> "STMicroelectronics ST25TV / LRI (ISO 15693)"
                0x07 -> "Texas Instruments Tag-it HF-I (ISO 15693)"
                0x05 -> "Infineon my-d vicinity (ISO 15693)"
                else -> "ISO 15693 Vicinity Tag"
            }
            val category = if (hasNdef || hasNdefRecords) CardCategory.SMART_TAG else CardCategory.GENERIC
            return ChipIdentification(manufacturer, guess, category)
        }

        // 4. MIFARE DESFire / SmartMX / ISO 14443-4 (non-EMV)
        if (hasIsoDep) {
            if (isAtqaDesfire || (sakInt == 0x20 && historicalBytes?.let { it.size >= 2 && (it[0].toInt() and 0xFF) == 0x80 } == true)) {
                return ChipIdentification(
                    manufacturer = manufacturer,
                    chipGuess = "MIFARE DESFire (EV1 / EV2 / EV3)",
                    suggestedCategory = CardCategory.ACCESS
                )
            }
            if (sakInt == 0x28 || sakInt == 0x38) {
                return ChipIdentification(
                    manufacturer = manufacturer,
                    chipGuess = "SmartMX / JCOP Dual-Interface Card",
                    suggestedCategory = CardCategory.ACCESS
                )
            }
            if (hasNfcB) {
                return ChipIdentification(
                    manufacturer = manufacturer,
                    chipGuess = "ISO 14443-4 Type B Smart Card",
                    suggestedCategory = CardCategory.ACCESS
                )
            }
            return ChipIdentification(
                manufacturer = manufacturer,
                chipGuess = if (sakInt == 0x20) "SmartMX / ISO 14443-4 Smart Card" else "ISO 14443-4 Contactless Card",
                suggestedCategory = if (hasNdefRecords) CardCategory.SMART_TAG else CardCategory.ACCESS
            )
        }

        // 5. MIFARE Classic / Plus
        if (hasClassic || sakInt == 0x08 || sakInt == 0x18 || sakInt == 0x09 || sakInt == 0x88) {
            val guess = when {
                classicSize == 4096 || sakInt == 0x18 -> "MIFARE Classic 4K"
                classicSize == 320 || sakInt == 0x09 -> "MIFARE Mini (0.3K)"
                classicType == 1 -> "MIFARE Classic (Plus Emulation)"
                sakInt == 0x88 -> "Infineon MIFARE Classic 1K (SLE66)"
                else -> "MIFARE Classic 1K"
            }
            val category = if (hasNdefRecords) CardCategory.SMART_TAG else CardCategory.ACCESS
            return ChipIdentification(manufacturer, guess, category)
        }

        // 6. MIFARE Ultralight / NTAG213 / NTAG215 / NTAG216
        if (hasUltralight || (hasNfcA && (sakInt == 0x00 || isAtqaUltralight))) {
            val effectiveUserBytes = ndefMaxSize ?: memoryBytes?.let { total ->
                when {
                    total >= 880 -> 888
                    total >= 500 -> 504
                    total >= 140 -> 144
                    else -> 48
                }
            }
            val guess = when {
                ultralightType == 2 -> "MIFARE Ultralight C"
                effectiveUserBytes != null && effectiveUserBytes >= 850 -> "NTAG216 (888 bytes user)"
                effectiveUserBytes != null && effectiveUserBytes in 480..540 -> "NTAG215 (504 bytes user)"
                effectiveUserBytes != null && effectiveUserBytes in 130..180 -> "NTAG213 (144 bytes user)"
                effectiveUserBytes != null && effectiveUserBytes in 40..64 -> "MIFARE Ultralight"
                memoryBytes != null && memoryBytes >= 880 -> "NTAG216 (888 bytes user)"
                memoryBytes != null && memoryBytes in 500..600 -> "NTAG215 (504 bytes user)"
                memoryBytes != null && memoryBytes in 160..220 -> "NTAG213 (144 bytes user)"
                ultralightType == 1 -> "MIFARE Ultralight / NTAG21x"
                else -> "MIFARE Ultralight / NTAG"
            }
            return ChipIdentification(manufacturer, guess, CardCategory.SMART_TAG)
        }

        // 7. Generic NfcB / NfcA / Ndef
        if (hasNfcB) {
            return ChipIdentification(manufacturer, "ISO 14443-3B Tag", CardCategory.GENERIC)
        }
        if (hasNdef || hasNdefRecords) {
            return ChipIdentification(manufacturer, "NFC Forum Tag (NDEF)", CardCategory.SMART_TAG)
        }
        if (hasNfcA) {
            return ChipIdentification(manufacturer, "ISO 14443-3A Tag", CardCategory.GENERIC)
        }

        return ChipIdentification(manufacturer, "Generic NFC Tag", CardCategory.GENERIC)
    }
}
