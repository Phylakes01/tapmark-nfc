package com.tapmark.nfc.nfc

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import java.util.zip.GZIPInputStream

data class BankIssuerInfo(
    val bankName: String,
    val cardType: String? = null,
    val cardTier: String? = null,
    val country: String? = null
)

/**
 * Offline BIN/IIN issuer database, EMV Issuer URL (5F50) & Application Label (50/9F12)
 * matcher, and issuer-name normalizer with optional bundled asset DB fallback.
 */
object BankBinDatabase {

    @Volatile
    private var appContext: Context? = null

    fun registerContext(context: Context?) {
        if (context != null && appContext == null) {
            appContext = context.applicationContext ?: context
        }
    }

    private val ACRONYMS_UPPER = setOf(
        "BPI", "BDO", "RCBC", "PNB", "HSBC", "DBS", "UOB", "OCBC",
        "AUB", "CIMB", "USA", "UK", "PH", "N.A.", "NA", "PLC", "S.A.", "SA", "LLC", "LLP", "GMBH", "AG"
    )

    private val PROPER_MIXED_WORDS = mapOf(
        "PSBANK" to "PSBank",
        "UNIONBANK" to "UnionBank",
        "GOTYME" to "GoTyme",
        "SEABANK" to "SeaBank",
        "MARIBANK" to "MariBank",
        "GCASH" to "GCash",
        "PAYMAYA" to "PayMaya",
        "EASTWEST" to "EastWest",
        "LANDBANK" to "LandBank",
        "CHINABANK" to "ChinaBank",
        "JPMORGAN" to "JPMorgan",
        "CITIBANK" to "Citibank",
        "INC." to "Inc.",
        "INC" to "Inc.",
        "CORP." to "Corp.",
        "CORP" to "Corp.",
        "LTD." to "Ltd.",
        "LTD" to "Ltd.",
        "CO." to "Co."
    )

    private val MINOR_CONNECTORS = setOf("of", "the", "and", "for", "de", "del", "la", "in", "on", "at", "by")

    private val COUNTRY_ALPHA2_MAP = mapOf(
        "PH" to "Philippines",
        "US" to "United States",
        "GB" to "United Kingdom",
        "UK" to "United Kingdom",
        "SG" to "Singapore",
        "JP" to "Japan",
        "AU" to "Australia",
        "CA" to "Canada",
        "DE" to "Germany",
        "FR" to "France",
        "HK" to "Hong Kong",
        "TW" to "Taiwan",
        "MY" to "Malaysia",
        "ID" to "Indonesia",
        "TH" to "Thailand",
        "VN" to "Vietnam",
        "KR" to "South Korea",
        "CN" to "China",
        "IN" to "India",
        "AE" to "United Arab Emirates",
        "NL" to "Netherlands",
        "ES" to "Spain",
        "IT" to "Italy",
        "CH" to "Switzerland",
        "IE" to "Ireland",
        "NZ" to "New Zealand",
        "BR" to "Brazil",
        "MX" to "Mexico",
        "UA" to "Ukraine"
    )

    fun countryFromAlpha2(alpha2: String?): String? {
        val code = alpha2?.trim()?.uppercase(Locale.US)?.takeIf { it.length == 2 } ?: return null
        return COUNTRY_ALPHA2_MAP[code] ?: runCatching {
            Locale.Builder().setRegion(code).build().getDisplayCountry(Locale.ENGLISH)
        }.getOrNull()?.takeIf { it.isNotBlank() && it != code }
    }

    fun normalizeCountryName(raw: String?): String? {
        val cleaned = raw?.trim()?.replace(Regex("\\s+"), " ")?.takeIf { it.isNotEmpty() } ?: return null
        return cleaned.split(' ').mapIndexed { idx, w ->
            val lower = w.lowercase(Locale.US)
            if (idx > 0 && lower in MINOR_CONNECTORS) lower
            else if (w.uppercase(Locale.US) in setOf("USA", "UK", "UAE")) w.uppercase(Locale.US)
            else lower.replaceFirstChar { it.titlecase(Locale.US) }
        }.joinToString(" ")
    }

    private fun ph(bank: String, type: String? = null, tier: String? = null): BankIssuerInfo =
        BankIssuerInfo(bankName = bank, cardType = type, cardTier = tier, country = "Philippines")

    /**
     * Curated offline 6-digit BIN/IIN table covering Philippine banks/e-wallets and major global issuers.
     */
    val CURATED_BINS: Map<String, BankIssuerInfo> = buildMap {
        // Bank of the Philippine Islands (BPI)
        val bpi = "Bank of the Philippine Islands"
        put("453756", ph(bpi, "Debit", "Classic"))
        put("526491", ph(bpi, "Debit", "Prepaid"))
        put("541572", ph(bpi, "Credit", "Titanium"))
        put("542458", ph(bpi, "Credit", "Standard"))
        put("548809", ph(bpi, "Credit", "Standard"))
        put("413100", ph(bpi, "Credit"))
        put("413101", ph(bpi, "Credit"))
        put("418898", ph(bpi, "Credit", "Classic"))
        put("431574", ph(bpi, "Debit"))
        put("460888", ph(bpi, "Credit", "Platinum"))
        put("467408", ph(bpi, "Debit"))
        put("513213", ph(bpi, "Credit"))
        put("518027", ph(bpi, "Credit", "World"))
        put("518324", ph(bpi, "Debit", "Standard"))
        put("520315", ph(bpi, "Credit", "Standard"))
        put("520365", ph(bpi, "Credit", "Standard"))
        put("523091", ph(bpi, "Credit", "Platinum"))
        put("523550", ph(bpi, "Credit", "Standard"))
        put("536347", ph(bpi, "Debit", "Standard"))
        put("536348", ph(bpi, "Debit", "Standard"))
        put("542450", ph(bpi, "Debit"))
        put("545512", ph(bpi, "Credit", "Platinum"))
        put("557753", ph(bpi, "Debit", "Prepaid Reloadable"))
        put("558769", ph(bpi, "Credit", "Corporate"))
        put("559169", ph(bpi, "Credit", "Corporate"))
        put("628082", ph(bpi, "Debit", "Standard"))

        // BDO Unibank (verified PH BINs)
        val bdo = "BDO Unibank"
        put("541781", ph(bdo, "Credit", "Mixed Product"))
        put("524301", ph(bdo, "Credit", "Platinum"))
        put("524326", ph(bdo, "Credit", "Titanium"))
        put("526727", ph(bdo, "Debit", "Prepaid"))
        put("548774", ph(bdo, "Debit", "Prepaid"))
        put("439197", ph(bdo, "Credit", "Corporate"))
        put("448458", ph(bdo, "Credit", "Purchasing"))
        put("455448", ph(bdo, "Debit", "Platinum"))
        put("360999", ph(bdo, "Credit", "Business"))
        put("361000", ph(bdo, "Credit", "Business"))
        put("361001", ph(bdo, "Credit", "Personal"))
        put("361002", ph(bdo, "Credit", "Personal"))
        put("375188", ph(bdo, "Credit", "Personal"))
        put("377670", ph(bdo, "Credit", "Personal"))
        put("377671", ph(bdo, "Credit", "Personal"))
        put("377672", ph(bdo, "Credit", "Personal"))
        put("377678", ph(bdo, "Credit", "Personal"))
        put("377679", ph(bdo, "Credit", "Personal"))
        put("377693", ph(bdo, "Credit", "Personal"))
        put("400438", ph(bdo, "Credit", "Signature"))
        put("412708", ph(bdo, "Debit"))
        put("418358", ph(bdo, "Credit", "Classic"))
        put("418359", ph(bdo, "Credit", "Gold"))
        put("451194", ph(bdo, "Credit"))
        put("453216", ph(bdo, "Debit"))
        put("456752", ph(bdo, "Credit", "Classic"))
        put("456753", ph(bdo, "Credit"))
        put("462408", ph(bdo, "Debit", "Prepaid"))
        put("464995", ph(bdo, "Credit", "Platinum"))
        put("470913", ph(bdo, "Debit", "Prepaid Business"))
        put("486290", ph(bdo, "Debit", "Classic"))
        put("488957", ph(bdo, "Credit", "Classic"))
        put("489504", ph(bdo, "Debit", "Classic"))
        put("512571", ph(bdo, "Credit", "Standard"))
        put("518869", ph(bdo, "Credit", "Gold"))
        put("519463", ph(bdo, "Debit", "Standard"))
        put("519963", ph(bdo, "Credit", "World Elite"))
        put("521069", ph(bdo, "Debit", "Standard"))
        put("522452", ph(bdo, "Credit", "Electronic"))
        put("523994", ph(bdo, "Credit", "Titanium"))
        put("524388", ph(bdo, "Credit"))
        put("528822", ph(bdo, "Debit"))
        put("529967", ph(bdo, "Debit", "Prepaid"))
        put("531228", ph(bdo, "Credit", "Corporate"))
        put("531832", ph(bdo, "Debit", "Standard"))
        put("536334", ph(bdo, "Debit", "Standard"))
        put("541702", ph(bdo, "Credit", "Standard"))
        put("541788", ph(bdo, "Credit", "Standard"))
        put("541990", ph(bdo, "Credit"))
        put("545275", ph(bdo, "Credit", "Standard"))
        put("545635", ph(bdo, "Credit", "Standard"))
        put("545636", ph(bdo, "Credit", "Gold"))
        put("548095", ph(bdo, "Credit", "Standard"))
        put("625033", ph(bdo, "Credit", "Classic"))
        put("625035", ph(bdo, "Credit", "Classic"))

        // Metrobank (Metropolitan Bank and Trust Company)
        val metro = "Metrobank"
        put("405598", ph(metro, "Credit", "Signature"))
        put("405599", ph(metro, "Credit", "Platinum"))
        put("449377", ph(metro, "Credit"))
        put("524005", ph(metro, "Credit", "Platinum"))
        put("524128", ph(metro, "Debit", "Platinum"))
        put("524736", ph(metro, "Credit", "Gold"))
        put("544651", ph(metro, "Credit"))
        put("545235", ph(metro, "Credit", "World"))
        put("400195", ph(metro, "Debit"))
        put("409461", ph(metro, "Debit", "Prepaid Classic"))
        put("416200", ph(metro, "Debit", "Prepaid"))
        put("421358", ph(metro, "Debit"))
        put("431179", ph(metro, "Credit", "Gold"))
        put("465164", ph(metro, "Debit", "Prepaid"))
        put("492966", ph(metro, "Credit", "Signature"))
        put("497225", ph(metro, "Credit", "Platinum"))
        put("511736", ph(metro, "Debit", "Prepaid"))
        put("512872", ph(metro, "Credit", "World Elite"))
        put("519597", ph(metro, "Debit", "Standard"))
        put("520683", ph(metro, "Debit", "Prepaid"))
        put("521822", ph(metro, "Credit", "Platinum"))
        put("522839", ph(metro, "Debit", "Prepaid Corporate"))
        put("524163", ph(metro, "Credit"))
        put("527643", ph(metro, "Debit", "Prepaid"))
        put("528667", ph(metro, "Debit", "Standard"))
        put("529978", ph(metro, "Debit", "Prepaid"))
        put("531038", ph(metro, "Credit"))
        put("532310", ph(metro, "Debit", "Prepaid Corporate"))
        put("534507", ph(metro, "Credit", "Standard"))
        put("536343", ph(metro, "Debit", "Standard"))
        put("536344", ph(metro, "Debit", "Standard"))
        put("536345", ph(metro, "Debit", "Standard"))
        put("536499", ph(metro, "Debit", "Prepaid"))
        put("538752", ph(metro, "Debit", "Prepaid Corporate"))
        put("540396", ph(metro, "Debit", "Prepaid Corporate"))
        put("546497", ph(metro, "Credit", "Titanium"))
        put("546498", ph(metro, "Credit", "Standard"))
        put("547152", ph(metro, "Debit", "Prepaid"))
        put("556628", ph(metro, "Credit", "Corporate"))
        put("556658", ph(metro, "Credit", "Corporate"))
        put("557315", ph(metro, "Debit", "Prepaid"))
        put("557317", ph(metro, "Debit", "Prepaid"))

        // UnionBank of the Philippines
        val ubp = "UnionBank of the Philippines"
        put("437315", ph(ubp, "Debit", "Prepaid Classic"))
        put("438627", ph(ubp, "Credit", "Platinum"))
        put("439196", ph(ubp, "Credit", "Gold"))
        put("440452", ph(ubp, "Debit", "Classic"))
        put("440453", ph(ubp, "Credit", "Gold"))
        put("447381", ph(ubp, "Debit", "Prepaid Classic"))
        put("447779", ph(ubp, "Credit", "Platinum"))
        put("450852", ph(ubp, "Debit", "Prepaid Classic"))
        put("453248", ph(ubp, "Credit", "Platinum"))
        put("453971", ph(ubp, "Credit", "Platinum"))
        put("453972", ph(ubp, "Credit", "Signature"))
        put("454494", ph(ubp, "Credit", "Gold"))
        put("524067", ph(ubp, "Credit", "Platinum"))
        put("524202", ph(ubp, "Credit", "Titanium"))
        put("524260", ph(ubp, "Credit", "Titanium"))
        put("542339", ph(ubp, "Credit", "Standard"))
        put("544538", ph(ubp, "Credit", "World Elite"))
        put("544635", ph(ubp, "Credit", "Gold"))
        put("548786", ph(ubp, "Credit", "World Elite"))
        put("549419", ph(ubp, "Credit", "Platinum"))
        put("552097", ph(ubp, "Credit", "World"))
        put("552829", ph(ubp, "Credit", "Titanium"))
        put("403418", ph(ubp, "Credit", "Platinum"))
        put("403419", ph(ubp, "Credit", "Signature"))
        put("405191", ph(ubp, "Credit", "Infinite"))
        put("406959", ph(ubp, "Debit", "Classic"))
        put("407170", ph(ubp, "Debit", "Classic"))
        put("410705", ph(ubp, "Debit", "Prepaid Classic"))
        put("415764", ph(ubp, "Credit", "Platinum"))
        put("416295", ph(ubp, "Debit", "Classic"))
        put("416296", ph(ubp, "Debit", "Classic"))
        put("416297", ph(ubp, "Debit", "Prepaid Classic"))
        put("416298", ph(ubp, "Debit", "Classic"))
        put("428357", ph(ubp, "Debit"))
        put("429253", ph(ubp, "Debit", "Classic"))
        put("431237", ph(ubp, "Debit", "Prepaid Classic"))
        put("433699", ph(ubp, "Credit", "Platinum"))
        put("434348", ph(ubp, "Credit", "Platinum"))
        put("435725", ph(ubp, "Credit", "Infinite"))
        put("436512", ph(ubp, "Debit", "Classic"))
        put("440454", ph(ubp, "Credit", "Classic"))
        put("451060", ph(ubp, "Debit"))
        put("455968", ph(ubp, "Credit", "Platinum"))
        put("458824", ph(ubp, "Credit", "Corporate"))
        put("462267", ph(ubp, "Credit", "Platinum"))
        put("462464", ph(ubp, "Debit", "Prepaid"))
        put("465117", ph(ubp, "Debit", "Classic"))
        put("465160", ph(ubp, "Debit", "Classic"))
        put("468767", ph(ubp, "Credit", "Platinum"))
        put("474137", ph(ubp, "Credit", "Signature"))
        put("476407", ph(ubp, "Credit", "Platinum"))
        put("484875", ph(ubp, "Credit", "Platinum"))
        put("486038", ph(ubp, "Debit", "Prepaid Classic"))
        put("489012", ph(ubp, "Debit", "Classic"))
        put("489013", ph(ubp, "Debit", "Platinum"))
        put("492164", ph(ubp, "Credit", "Gold"))
        put("510631", ph(ubp, "Debit", "Standard"))
        put("521456", ph(ubp, "Debit"))
        put("529247", ph(ubp, "Credit", "World Elite"))
        put("529763", ph(ubp, "Debit", "Gold"))
        put("530220", ph(ubp, "Credit", "Platinum"))
        put("536301", ph(ubp, "Debit", "Platinum"))
        put("537726", ph(ubp, "Credit", "Platinum"))
        put("540127", ph(ubp, "Credit", "Platinum"))
        put("545420", ph(ubp, "Credit", "Gold"))
        put("545595", ph(ubp, "Credit", "Gold"))
        put("546069", ph(ubp, "Credit", "Standard"))
        put("547322", ph(ubp, "Debit", "Prepaid"))
        put("548430", ph(ubp, "Credit"))
        put("559744", ph(ubp, "Debit", "Platinum"))
        put("559919", ph(ubp, "Debit", "World"))
        put("589721", ph(ubp, "Debit"))
        put("590141", ph(ubp, "Debit", "Standard"))

        // Land Bank of the Philippines
        val landbank = "Land Bank of the Philippines"
        put("554541", ph(landbank, "Credit", "Gold"))
        put("457453", ph(landbank, "Debit", "Classic"))
        put("474844", ph(landbank, "Debit", "Classic"))
        put("531272", ph(landbank, "Credit", "Corporate"))
        put("533830", ph(landbank, "Debit", "Prepaid"))
        put("535888", ph(landbank, "Credit", "Standard"))

        // GCash (G-Xchange, Inc.)
        val gcash = "GCash (G-Xchange, Inc.)"
        put("223540", ph(gcash, "Debit"))
        put("441360", ph(gcash, "Debit", "Prepaid Classic"))
        put("549627", ph(gcash, "Debit", "Prepaid"))
        put("375190", ph(gcash, "Debit", "Prepaid"))
        put("503785", ph(gcash, "Debit", "Standard"))
        put("521447", ph(gcash, "Prepaid", "Prepaid"))
        put("536595", ph(gcash, "Debit", "Prepaid"))
        put("541377", ph(gcash, "Prepaid", "Prepaid"))

        // Maya (Maya Philippines)
        val maya = "Maya (Maya Philippines)"
        put("542482", ph(maya, "Debit", "Prepaid"))
        put("405160", ph(maya, "Prepaid", "Prepaid"))
        put("479260", ph(maya, "Prepaid", "Prepaid"))
        put("483442", ph(maya, "Debit", "Prepaid Classic"))
        put("486468", ph(maya, "Credit", "Platinum"))
        put("540295", ph(maya, "Debit", "Prepaid"))

        // Security Bank
        val secBank = "Security Bank"
        put("510186", ph(secBank, "Credit", "Titanium"))
        put("515603", ph(secBank, "Credit", "Platinum"))
        put("518178", ph(secBank, "Credit", "Gold"))
        put("518217", ph(secBank, "Credit", "Standard"))
        put("522465", ph(secBank, "Debit", "Prepaid Reloadable"))
        put("525617", ph(secBank, "Credit", "Standard"))
        put("526737", ph(secBank, "Debit", "Prepaid"))
        put("527366", ph(secBank, "Debit", "Business"))
        put("531210", ph(secBank, "Credit", "Corporate"))
        put("531826", ph(secBank, "Debit", "Standard"))
        put("532910", ph(secBank, "Credit", "Business"))
        put("533924", ph(secBank, "Credit", "Platinum"))
        put("542551", ph(secBank, "Credit", "Standard"))
        put("542594", ph(secBank, "Credit", "Standard"))
        put("549832", ph(secBank, "Credit", "World"))

        // RCBC (Rizal Commercial Banking Corporation)
        val rcbc = "RCBC"
        put("431170", ph(rcbc, "Credit", "Classic"))
        put("421585", ph(rcbc, "Debit", "Prepaid Classic"))
        put("427934", ph(rcbc, "Credit", "Platinum"))
        put("429382", ph(rcbc, "Credit", "Gold"))
        put("436861", ph(rcbc, "Credit", "Classic"))
        put("449915", ph(rcbc, "Debit", "Platinum"))
        put("457358", ph(rcbc, "Credit", "Infinite"))
        put("476683", ph(rcbc, "Debit", "Prepaid Classic"))
        put("484063", ph(rcbc, "Credit", "Classic"))
        put("517528", ph(rcbc, "Debit"))
        put("517968", ph(rcbc, "Credit", "Standard"))
        put("517982", ph(rcbc, "Credit", "Gold"))
        put("520352", ph(rcbc, "Credit", "Standard"))
        put("524302", ph(rcbc, "Credit", "Titanium"))
        put("524318", ph(rcbc, "Credit", "Platinum"))
        put("530063", ph(rcbc, "Debit", "Prepaid"))
        put("530526", ph(rcbc, "Debit", "Platinum"))
        put("534399", ph(rcbc, "Debit", "Standard"))
        put("536151", ph(rcbc, "Debit", "Standard"))
        put("539599", ph(rcbc, "Debit", "Standard"))
        put("552366", ph(rcbc, "Credit", "World"))
        put("625007", ph(rcbc, "Credit", "Classic"))

        // Philippine National Bank (PNB)
        val pnb = "Philippine National Bank"
        put("488907", ph(pnb, "Credit", "Classic"))
        put("488908", ph(pnb, "Credit", "Gold"))
        put("510266", ph(pnb, "Debit", "Standard"))
        put("512634", ph(pnb, "Credit", "Standard"))
        put("520353", ph(pnb, "Debit", "Standard"))
        put("520924", ph(pnb, "Debit", "Prepaid"))
        put("521387", ph(pnb, "Credit", "Platinum"))
        put("523759", ph(pnb, "Debit", "Standard"))
        put("524046", ph(pnb, "Credit", "Platinum"))
        put("528204", ph(pnb, "Debit", "Prepaid Reloadable"))
        put("530800", ph(pnb, "Credit", "World Elite"))
        put("536306", ph(pnb, "Credit", "World"))
        put("536307", ph(pnb, "Credit", "World"))
        put("537007", ph(pnb, "Credit", "World Elite"))
        put("540076", ph(pnb, "Credit", "Platinum"))
        put("545143", ph(pnb, "Credit", "World"))
        put("548509", ph(pnb, "Credit", "Standard"))
        put("552280", ph(pnb, "Credit", "World"))

        // China Bank
        val chinaBank = "China Bank"
        put("400126", ph(chinaBank, "Debit", "Signature"))
        put("400676", ph(chinaBank, "Debit", "Classic"))
        put("522604", ph(chinaBank, "Credit", "Platinum"))
        put("522644", ph(chinaBank, "Credit", "Standard"))
        put("528557", ph(chinaBank, "Credit", "World"))
        put("541280", ph(chinaBank, "Debit", "Standard"))
        put("541436", ph(chinaBank, "Credit", "World"))
        put("558569", ph(chinaBank, "Credit", "Business"))
        put("589558", ph(chinaBank, "Debit"))

        // EastWest Bank
        val eastWest = "EastWest Bank"
        put("412611", ph(eastWest, "Credit", "Classic"))
        put("412612", ph(eastWest, "Credit", "Gold"))
        put("400992", ph(eastWest, "Credit", "Classic"))
        put("400993", ph(eastWest, "Credit", "Gold"))
        put("402530", ph(eastWest, "Debit", "Classic"))
        put("418542", ph(eastWest, "Debit", "Platinum"))
        put("437507", ph(eastWest, "Debit", "Classic"))
        put("437550", ph(eastWest, "Credit", "Classic"))
        put("445086", ph(eastWest, "Credit", "Platinum"))
        put("450177", ph(eastWest, "Debit", "Prepaid Classic"))
        put("483892", ph(eastWest, "Debit", "Classic"))
        put("514921", ph(eastWest, "Credit", "Platinum"))
        put("515499", ph(eastWest, "Credit", "World"))
        put("517988", ph(eastWest, "Credit", "Gold"))
        put("518096", ph(eastWest, "Credit", "Standard"))
        put("520954", ph(eastWest, "Credit", "World"))
        put("524224", ph(eastWest, "Credit", "Titanium"))
        put("548352", ph(eastWest, "Credit", "Standard"))
        put("548924", ph(eastWest, "Credit", "Standard"))
        put("558999", ph(eastWest, "Credit", "World"))

        // GoTyme Bank
        put("424032", ph("GoTyme Bank", "Debit", "Debit"))
        put("433277", ph("GoTyme Bank", "Debit", "Debit"))
        put("454090", ph("GoTyme Bank", "Debit", "Classic"))
        put("460186", ph("GoTyme Bank", "Debit", "Classic"))

        // SeaBank / MariBank
        put("529416", ph("SeaBank Philippines", "Debit", "Standard"))
        put("548021", BankIssuerInfo("MariBank", "Credit", "Platinum", "Singapore"))

        // CIMB Bank PH
        put("436741", ph("CIMB Bank Philippines", "Debit", "Classic"))
        put("478267", ph("CIMB Bank Philippines", "Debit", "Classic"))
        put("484315", ph("CIMB Bank Philippines", "Debit", "Classic"))

        // Tonik Digital Bank
        put("555562", ph("Tonik Digital Bank", "Debit", "Standard"))

        // Robinsons Bank
        put("406159", ph("Robinsons Bank", "Debit", "Classic"))
        put("428946", ph("Robinsons Bank", "Credit", "Classic"))
        put("456434", ph("Robinsons Bank", "Debit", "Corporate"))
        put("553233", ph("Robinsons Bank", "Credit", "Fleet"))

        // Asia United Bank (AUB)
        val aub = "Asia United Bank (AUB)"
        put("515103", ph(aub, "Credit", "Standard"))
        put("515105", ph(aub, "Credit", "Standard"))
        put("520894", ph(aub, "Debit", "Business"))
        put("524405", ph(aub, "Debit", "Prepaid"))
        put("533847", ph(aub, "Debit", "Prepaid"))
        put("535892", ph(aub, "Credit", "Gold"))
        put("536257", ph(aub, "Debit", "Prepaid"))
        put("539229", ph(aub, "Credit", "Platinum"))
        put("554045", ph(aub, "Debit", "Prepaid"))
        put("558208", ph(aub, "Credit", "Corporate"))

        // HSBC Philippines
        val hsbcPh = "HSBC Philippines"
        put("402843", ph(hsbcPh, "Credit"))
        put("402892", ph(hsbcPh, "Credit", "Classic"))
        put("402893", ph(hsbcPh, "Credit", "Platinum"))
        put("436367", ph(hsbcPh, "Credit", "Gold"))
        put("436368", ph(hsbcPh, "Debit", "Classic"))
        put("436446", ph(hsbcPh, "Debit", "Classic"))
        put("458535", ph(hsbcPh, "Debit", "Platinum"))
        put("461984", ph(hsbcPh, "Credit", "Signature"))
        put("464943", ph(hsbcPh, "Debit", "Classic"))
        put("464944", ph(hsbcPh, "Debit", "Classic"))
        put("464945", ph(hsbcPh, "Debit", "Platinum"))
        put("486009", ph(hsbcPh, "Credit", "Platinum"))
        put("513476", ph(hsbcPh, "Credit", "Standard"))
        put("517790", ph(hsbcPh, "Credit", "World"))
        put("544757", ph(hsbcPh, "Credit", "Gold"))
        put("544758", ph(hsbcPh, "Credit", "Platinum"))
        put("548487", ph(hsbcPh, "Credit", "Gold"))

        // Major Global Banks
        // Chase (JPMorgan Chase)
        put("414720", BankIssuerInfo("Chase", "Credit", "Traditional", "United States"))
        put("426684", BankIssuerInfo("Chase", "Credit", "Traditional", "United States"))
        put("438857", BankIssuerInfo("Chase", "Credit", "Traditional", "United States"))
        put("401135", BankIssuerInfo("Chase", "Credit", "Traditional", "United States"))

        // Bank of America
        put("440066", BankIssuerInfo("Bank of America", "Credit", "Traditional", "United States"))
        put("374314", BankIssuerInfo("Bank of America", "Credit", "Personal", "United States"))
        put("400275", BankIssuerInfo("Bank of America", "Credit", "Business", "United States"))

        // Wells Fargo
        put("434256", BankIssuerInfo("Wells Fargo", "Debit", "Classic", "United States"))
        put("370034", BankIssuerInfo("Wells Fargo", "Credit", "Personal", "United States"))
        put("400151", BankIssuerInfo("Wells Fargo", "Credit", "Traditional", "United States"))

        // Citibank
        put("230050", BankIssuerInfo("Citibank", "Credit", "Platinum", "United States"))
        put("400996", BankIssuerInfo("Citibank", "Credit", "Purchasing", "United States"))
        put("453913", ph("Citibank Philippines", "Credit", "Classic"))
        put("541720", ph("Citibank Philippines", "Credit"))

        // Capital One
        put("517805", BankIssuerInfo("Capital One", "Credit", "World", "United States"))
        put("400229", BankIssuerInfo("Capital One", "Credit", "Business", "United States"))
        put("400344", BankIssuerInfo("Capital One", "Credit", "Traditional", "United States"))

        // American Express
        put("340000", BankIssuerInfo("American Express", "Credit", null, "United States"))
        put("378282", BankIssuerInfo("American Express", "Credit", null, "United States"))

        // Discover
        put("601100", BankIssuerInfo("Discover", "Credit", "Platinum", "United States"))
        put("369989", BankIssuerInfo("Discover", "Credit", "Personal", "United States"))

        // Barclays
        put("465901", BankIssuerInfo("Barclays", "Debit", "Classic", "United Kingdom"))
        put("401315", BankIssuerInfo("Barclays", "Credit", "Business", "United States"))
        put("401343", BankIssuerInfo("Barclays", "Credit", "Classic", "United Kingdom"))

        // HSBC (Global)
        put("400700", BankIssuerInfo("HSBC", "Debit", "Classic", "United States"))
        put("401039", BankIssuerInfo("HSBC", "Credit", "Corporate", "United Kingdom"))

        // Revolut
        put("400549", BankIssuerInfo("Revolut", "Debit", "Prepaid Platinum", "Australia"))
        put("400891", BankIssuerInfo("Revolut", "Debit", "Signature", "Singapore"))

        // Wise
        put("410558", BankIssuerInfo("Wise", "Debit", "Infinite Business", "United Kingdom"))
        put("407704", BankIssuerInfo("Wise", "Debit", "Prepaid Business", "Malaysia"))
        put("421122", ph("Wise", "Debit", "Prepaid Business"))
        put("431168", ph("Wise", "Debit", "Prepaid Classic"))

        // Monzo
        put("516859", BankIssuerInfo("Monzo", "Debit", "World", "United Kingdom"))
        put("535199", BankIssuerInfo("Monzo", "Debit", "World Business", "United Kingdom"))

        // Singapore Trio: DBS, OCBC, UOB
        put("401893", BankIssuerInfo("DBS Bank", "Debit", "Prepaid", "Singapore"))
        put("341344", BankIssuerInfo("DBS Bank", "Credit", "Personal", "Singapore"))
        put("400610", BankIssuerInfo("OCBC Bank", "Credit", "Infinite", "Singapore"))
        put("403618", BankIssuerInfo("OCBC Bank", "Debit", "Classic", "Singapore"))
        put("400682", BankIssuerInfo("UOB", "Credit", "Signature", "Singapore"))
        put("445051", ph("UOB Philippines", "Credit"))
        put("464008", ph("UOB Philippines", "Credit", "Gold"))
    }

    private val DOMAIN_BANK_MAP: List<Pair<String, String>> = listOf(
        "bpi.com.ph" to "Bank of the Philippine Islands (BPI)",
        "bpiexpressonline.com" to "Bank of the Philippine Islands (BPI)",
        "bdo.com.ph" to "BDO Unibank",
        "unionbankph.com" to "UnionBank of the Philippines",
        "metrobank.com.ph" to "Metrobank",
        "metrobankcard.com" to "Metrobank",
        "gcash.com" to "GCash",
        "mynt.xyz" to "GCash",
        "maya.ph" to "Maya",
        "paymaya.com" to "Maya",
        "gotyme.com.ph" to "GoTyme Bank",
        "seabank.ph" to "SeaBank Philippines",
        "maribank.ph" to "MariBank",
        "securitybank.com" to "Security Bank",
        "rcbc.com" to "RCBC",
        "rcbcbankard.com" to "RCBC",
        "pnb.com.ph" to "Philippine National Bank",
        "chinabank.ph" to "China Bank",
        "eastwestbanker.com" to "EastWest Bank",
        "landbank.com" to "Land Bank of the Philippines",
        "cimbbank.com.ph" to "CIMB Bank Philippines",
        "tonikbank.com" to "Tonik Digital Bank",
        "psbank.com.ph" to "PSBank",
        "robinsonsbank.com.ph" to "Robinsons Bank",
        "aub.com.ph" to "Asia United Bank (AUB)",
        "hsbc.com.ph" to "HSBC Philippines",
        "hsbc.co.uk" to "HSBC",
        "hsbc.com" to "HSBC",
        "chase.com" to "Chase",
        "bankofamerica.com" to "Bank of America",
        "wellsfargo.com" to "Wells Fargo",
        "citibank.com" to "Citibank",
        "citi.com" to "Citibank",
        "capitalone.com" to "Capital One",
        "americanexpress.com" to "American Express",
        "discover.com" to "Discover",
        "barclays.co.uk" to "Barclays",
        "barclaycard.co.uk" to "Barclays",
        "revolut.com" to "Revolut",
        "wise.com" to "Wise",
        "monzo.com" to "Monzo",
        "dbs.com.sg" to "DBS Bank",
        "dbs.com" to "DBS Bank",
        "ocbc.com" to "OCBC Bank",
        "uob.com.sg" to "UOB",
        "uobgroup.com" to "UOB"
    )

    private val APP_LABEL_RULES: List<Pair<Regex, String>> = listOf(
        Regex("\\bBPI\\b|BANK OF THE PHILIPPINE ISLANDS") to "Bank of the Philippine Islands (BPI)",
        Regex("\\bBDO\\b|BANCO DE ORO") to "BDO Unibank",
        Regex("\\bUNIONBANK\\b|\\bUBP\\b") to "UnionBank of the Philippines",
        Regex("\\bMETROBANK\\b") to "Metrobank",
        Regex("\\bLANDBANK\\b|LAND BANK") to "Land Bank of the Philippines",
        Regex("\\bGCASH\\b|G-XCHANGE") to "GCash",
        Regex("\\bPAYMAYA\\b|\\bMAYA\\b") to "Maya",
        Regex("\\bGOTYME\\b") to "GoTyme Bank",
        Regex("\\bSEABANK\\b") to "SeaBank Philippines",
        Regex("\\bMARIBANK\\b") to "MariBank",
        Regex("\\bSECURITY\\s*BANK\\b") to "Security Bank",
        Regex("\\bRCBC\\b") to "RCBC",
        Regex("\\bCHINABANK\\b|CHINA\\s*BANK") to "China Bank",
        Regex("\\bPNB\\b|PHILIPPINE NATIONAL BANK") to "Philippine National Bank",
        Regex("\\bEASTWEST\\b|EAST\\s*WEST") to "EastWest Bank",
        Regex("\\bCIMB\\b") to "CIMB Bank",
        Regex("\\bTONIK\\b") to "Tonik Digital Bank",
        Regex("\\bPSBANK\\b|PHILIPPINE SAVINGS") to "PSBank",
        Regex("\\bROBINSONS\\s*BANK\\b") to "Robinsons Bank",
        Regex("\\bASIA UNITED\\b|\\bAUB\\b") to "Asia United Bank (AUB)",
        Regex("\\bCHASE\\b") to "Chase",
        Regex("\\bBANK OF AMERICA\\b|\\bBOFA\\b") to "Bank of America",
        Regex("\\bWELLS FARGO\\b") to "Wells Fargo",
        Regex("\\bCITIBANK\\b|\\bCITI\\b") to "Citibank",
        Regex("\\bCAPITAL ONE\\b") to "Capital One",
        Regex("\\bBARCLAYS\\b|\\bBARCLAYCARD\\b") to "Barclays",
        Regex("\\bHSBC\\b") to "HSBC",
        Regex("\\bREVOLUT\\b") to "Revolut",
        Regex("\\bWISE\\b") to "Wise",
        Regex("\\bMONZO\\b") to "Monzo",
        Regex("\\bDBS\\b") to "DBS Bank",
        Regex("\\bOCBC\\b") to "OCBC Bank",
        Regex("\\bUOB\\b") to "UOB"
    )

    private val GENERIC_NETWORK_LABELS = setOf(
        "VISA", "VISA DEBIT", "VISA CREDIT", "VISA ELECTRON", "V PAY", "VPAY",
        "MASTERCARD", "DEBIT MASTERCARD", "CREDIT MASTERCARD", "MASTERCARD DEBIT", "MASTERCARD CREDIT",
        "MAESTRO", "CIRRUS", "AMEX", "AMERICAN EXPRESS", "JCB", "DISCOVER",
        "UNIONPAY", "CHINA UNIONPAY", "RUPAY", "INTERAC", "EMV", "PAYMENT"
    )

    fun extractBankFromIssuerUrl(url: String?): String? {
        val raw = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val withoutScheme = raw.substringAfter("://", raw)
        val hostPart = withoutScheme
            .substringBefore('/')
            .substringBefore('?')
            .substringBefore('#')
            .substringBefore(':')
            .lowercase(Locale.US)
            .trim('.')
        if (hostPart.isEmpty() || !hostPart.contains('.')) return null

        for ((domain, bankName) in DOMAIN_BANK_MAP) {
            if (hostPart == domain || hostPart.endsWith(".$domain")) {
                return bankName
            }
        }

        // Clean capitalized domain fallback
        var cleaned = hostPart
            .removePrefix("www.")
            .removePrefix("m.")
            .removePrefix("cards.")
            .removePrefix("mobile.")
            .removePrefix("online.")
            .removePrefix("ib.")
        val compoundSuffixes = listOf(
            ".com.ph", ".co.uk", ".com.sg", ".com.au", ".com.my", ".co.jp", ".co.id", ".com.hk", ".com.tw"
        )
        var stripped = false
        for (suffix in compoundSuffixes) {
            if (cleaned.endsWith(suffix)) {
                cleaned = cleaned.removeSuffix(suffix)
                stripped = true
                break
            }
        }
        if (!stripped) {
            val lastDot = cleaned.lastIndexOf('.')
            if (lastDot > 0) {
                cleaned = cleaned.substring(0, lastDot)
            }
        }
        val words = cleaned.split('.', '-', '_')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        return words.joinToString(" ") { w ->
            val upper = w.uppercase(Locale.US)
            when {
                upper in ACRONYMS_UPPER -> upper
                PROPER_MIXED_WORDS.containsKey(upper) -> PROPER_MIXED_WORDS.getValue(upper)
                else -> w.replaceFirstChar { ch -> ch.titlecase(Locale.US) }
            }
        }
    }

    fun extractBankFromAppLabel(appLabel: String?): String? {
        val raw = appLabel?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val normalizedSpace = raw.replace(Regex("\\s+"), " ").uppercase(Locale.US)
        if (normalizedSpace in GENERIC_NETWORK_LABELS) return null

        for ((regex, bankName) in APP_LABEL_RULES) {
            if (regex.containsMatchIn(normalizedSpace)) {
                return bankName
            }
        }
        return null
    }

    fun normalizeIssuerName(raw: String): String {
        val collapsed = raw.trim().replace(Regex("\\s+"), " ")
        if (collapsed.isEmpty()) return ""
        val upper = collapsed.uppercase(Locale.US)

        when {
            upper.contains("G-XCHANGE") || upper.contains("GXCHANGE") -> return "GCash (G-Xchange, Inc.)"
            upper.contains("PAYMAYA") || upper.contains("MAYA PHILIPPINES") || upper.startsWith("MAYA BANK") ->
                return "Maya (Maya Philippines)"
            upper.startsWith("BANK OF THE PHILIPPINE ISLANDS") -> return "Bank of the Philippine Islands"
            upper.startsWith("BANCO DE ORO") || upper.startsWith("BDO UNIBANK") || upper.startsWith("BANC DE ORO") || upper.startsWith("BANK DE ORO") || upper == "PHILIPPINE BDO" ->
                return "BDO Unibank"
            upper.startsWith("METROPOLITAN BANK AND TRUST") || upper.startsWith("METROBANK") ->
                return "Metrobank"
            upper.startsWith("UNION BANK OF THE PHILIPPINES") || upper.startsWith("UNIONBANK OF THE PHILIPPINES") ->
                return "UnionBank of the Philippines"
            upper.startsWith("LAND BANK OF THE PHILIPPINES") ->
                return "Land Bank of the Philippines"
            upper.startsWith("RIZAL COMMERCIAL BANKING") ->
                return "RCBC"
            upper.startsWith("EAST WEST BANKING") ->
                return "EastWest Bank"
            upper.startsWith("SEABANK PHILIPPINES") ->
                return "SeaBank Philippines"
            upper.startsWith("SECURITY BANK CORP") || upper == "SECURITY BANK" ->
                return "Security Bank"
            upper.startsWith("CHINA BANKING CORP") ->
                return "China Bank"
            upper.startsWith("PHILIPPINE NATIONAL BANK") ->
                return "Philippine National Bank"
            upper.startsWith("ASIA UNITED BANK") ->
                return "Asia United Bank (AUB)"
            upper.startsWith("TONIK DIGITAL BANK") ->
                return "Tonik Digital Bank"
            upper.startsWith("GOTYME BANK") ->
                return "GoTyme Bank"
            upper.startsWith("ROBINSONS BANK") ->
                return "Robinsons Bank"
            upper.startsWith("JPMORGAN CHASE") ->
                return "JPMorgan Chase"
        }

        val tokens = collapsed.split(' ')
        return tokens.mapIndexed { idx, token ->
            normalizeToken(token, isFirst = idx == 0)
        }.joinToString(" ")
    }

    private fun normalizeToken(token: String, isFirst: Boolean): String {
        if (token.isEmpty()) return token
        // Preserve punctuation-delimited words such as "G-Xchange," or "(BPI)"
        val leading = token.takeWhile { it == '(' || it == '[' || it == '"' }
        val trailing = token.takeLastWhile { it == ',' || it == ')' || it == ']' || it == '"' }
        val core = token.substring(leading.length, token.length - trailing.length)
        if (core.isEmpty()) return token

        val coreUpper = core.uppercase(Locale.US)
        val formattedCore = when {
            coreUpper in ACRONYMS_UPPER -> coreUpper
            PROPER_MIXED_WORDS.containsKey(coreUpper) -> PROPER_MIXED_WORDS.getValue(coreUpper)
            core.contains('-') -> core.split('-').joinToString("-") { part ->
                val pUpper = part.uppercase(Locale.US)
                when {
                    pUpper in ACRONYMS_UPPER -> pUpper
                    PROPER_MIXED_WORDS.containsKey(pUpper) -> PROPER_MIXED_WORDS.getValue(pUpper)
                    else -> part.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
                }
            }
            !isFirst && core.lowercase(Locale.US) in MINOR_CONNECTORS -> core.lowercase(Locale.US)
            else -> core.lowercase(Locale.US).replaceFirstChar { it.titlecase(Locale.US) }
        }
        return "$leading$formattedCore$trailing"
    }

    fun strictSixDigitBin(raw: String?): String? {
        val digits = raw?.filter { it.isDigit() } ?: return null
        if (digits.length < 6) return null
        return digits.take(6)
    }

    fun lookupCuratedBin(bin: String?): BankIssuerInfo? {
        val bin6 = strictSixDigitBin(bin) ?: return null
        return CURATED_BINS[bin6]
    }

    fun lookupOffline(
        bin: String?,
        iinTag42: String? = null,
        appLabel: String? = null,
        issuerUrl: String? = null
    ): BankIssuerInfo? {
        val bin6 = strictSixDigitBin(bin)
        if (bin6 != null) {
            CURATED_BINS[bin6]?.let { return it }
        }

        val iin6 = strictSixDigitBin(iinTag42)
        if (iin6 != null) {
            CURATED_BINS[iin6]?.let { return it }
        }

        val inferredType = inferCardTypeFromLabel(appLabel)

        val fromUrl = extractBankFromIssuerUrl(issuerUrl)
        if (fromUrl != null) {
            val isPhDomain = issuerUrl?.lowercase(Locale.US)?.contains(".ph") == true
            return BankIssuerInfo(
                bankName = fromUrl,
                cardType = inferredType,
                country = if (isPhDomain) "Philippines" else null
            )
        }

        val fromLabel = extractBankFromAppLabel(appLabel)
        if (fromLabel != null) {
            return BankIssuerInfo(
                bankName = fromLabel,
                cardType = inferredType
            )
        }

        return null
    }

    /**
     * Streams the optional bundled `assets/bin_db.tsv.gz` offline BIN database when Android Context
     * is available, keeping zero persistent heap memory after resolving.
     */
    fun lookupAssetBin(bin: String?): BankIssuerInfo? {
        val ctx = appContext ?: return null
        val bin6 = strictSixDigitBin(bin) ?: return null
        val target = bin6.toIntOrNull() ?: return null

        return runCatching {
            ctx.assets.open("bin_db.tsv.gz").use { rawIn ->
                GZIPInputStream(rawIn).use { gzIn ->
                    BufferedReader(InputStreamReader(gzIn, Charsets.UTF_8)).use { reader ->
                        val banks = HashMap<Int, String>(20_000)
                        val tiers = HashMap<Int, String>(512)
                        var line = reader.readLine()
                        while (line != null) {
                            when {
                                line.startsWith("B\t") -> {
                                    val tab2 = line.indexOf('\t', 2)
                                    if (tab2 > 2) {
                                        val id = line.substring(2, tab2).toIntOrNull()
                                        if (id != null) {
                                            banks[id] = line.substring(tab2 + 1)
                                        }
                                    }
                                }
                                line.startsWith("C\t") -> {
                                    val tab2 = line.indexOf('\t', 2)
                                    if (tab2 > 2) {
                                        val id = line.substring(2, tab2).toIntOrNull()
                                        if (id != null) {
                                            tiers[id] = line.substring(tab2 + 1)
                                        }
                                    }
                                }
                                line.startsWith("R6\t") -> {
                                    val parts = line.split('\t')
                                    if (parts.size >= 7) {
                                        val start = parts[1].toIntOrNull()
                                        val end = parts[2].toIntOrNull()
                                        if (start != null && end != null && target in start..end) {
                                            val bankId = parts[3].toIntOrNull()
                                            val rawBank = bankId?.let { banks[it] }?.trim()
                                            if (!rawBank.isNullOrEmpty()) {
                                                val type = when (parts[4].uppercase(Locale.US)) {
                                                    "D" -> "Debit"
                                                    "C" -> "Credit"
                                                    "P" -> "Prepaid"
                                                    else -> null
                                                }
                                                val tierId = parts[5].toIntOrNull()
                                                val rawTier = tierId?.let { tiers[it] }?.trim()?.takeIf { it.isNotEmpty() }
                                                val alpha2 = parts[6].trim().uppercase(Locale.US)
                                                val country = COUNTRY_ALPHA2_MAP[alpha2] ?: alpha2.takeIf { it.isNotEmpty() }
                                                return@runCatching BankIssuerInfo(
                                                    bankName = normalizeIssuerName(rawBank),
                                                    cardType = type,
                                                    cardTier = rawTier,
                                                    country = country
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                            line = reader.readLine()
                        }
                        null
                    }
                }
            }
        }.getOrNull()
    }

    private fun inferCardTypeFromLabel(appLabel: String?): String? {
        val upper = appLabel?.uppercase(Locale.US) ?: return null
        return when {
            upper.contains("DEBIT") -> "Debit"
            upper.contains("PREPAID") -> "Prepaid"
            upper.contains("CREDIT") -> "Credit"
            else -> null
        }
    }
}
