package com.tapmark.nfc.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapmark.nfc.model.CardCategory
import com.tapmark.nfc.model.ScannedCard
import java.util.Locale

enum class BankArtworkMotif {
    HERITAGE_FACETS,
    GOLD_RIBBON,
    STEPPED_WEAVE,
    SUNSET_CURVE,
    LEAF_CONTOUR,
    WAVE_ARCS,
    OBSIDIAN_MINT,
    TEAL_PRISM,
    CORAL_WAVES,
    HEX_FACETS,
    SHIELD_ARCS,
    COIN_RINGS,
    TWIN_STRIPES,
    COMPASS_ARCS,
    CHEVRON_BANDS,
    CITRUS_ARC,
    OCTAGON_PRISM,
    HSBC_HEXAGON,
    MINIMAL_STRIPE,
    CLASSIC_FRAME,
    MACHINED_GRAIN
}

data class BankCardTheme(
    val palette: CardStylePalette,
    val motif: BankArtworkMotif,
    val accentColor: Color,
    val emblemCode: String,
    val displayBrand: String
)

private fun darkCardPalette(
    base: Color,
    secondary: Color,
    border: Color,
    ink: Color = Color(0xFFF7F5F0),
    secondaryInk: Color = Color(0xFFC8CED8),
    badgeBg: Color = Color(0x28FFFFFF),
    badgeInk: Color = Color(0xFFF7F5F0),
    chipGold: Color = Color(0xFFDDC078)
): CardStylePalette = CardStylePalette(
    baseColor = base,
    secondaryColor = secondary,
    borderColor = border,
    inkColor = ink,
    secondaryInkColor = secondaryInk,
    badgeBgColor = badgeBg,
    badgeInkColor = badgeInk,
    grainColor = Color(0x10FFFFFF),
    chipGoldTint = chipGold,
    isDark = true
)

/**
 * Resolves a bespoke bank card palette, geometric artwork motif, and emblem badge
 * across 19 Philippine banks & e-wallets and 15 global banks, falling back cleanly
 * to [CardVisualStyle.toPalette] + [BankArtworkMotif.MACHINED_GRAIN].
 */
fun resolveBankCardTheme(card: ScannedCard): BankCardTheme {
    val emv = card.emvInfo
    if (emv == null && card.category != CardCategory.PAYMENT) {
        val basePalette = card.visualStyle.toPalette()
        return BankCardTheme(
            palette = basePalette,
            motif = BankArtworkMotif.MACHINED_GRAIN,
            accentColor = basePalette.chipGoldTint,
            emblemCode = "",
            displayBrand = card.cardTitle
        )
    }

    val candidates = listOfNotNull(
        emv?.bankName,
        emv?.appLabel,
        card.cardTitle
    ).joinToString(" | ").lowercase(Locale.US)

    fun hasWord(word: String): Boolean = Regex("\\b${Regex.escape(word)}\\b").containsMatchIn(candidates)

    return when {
        // --- 19 Philippine Banks & E-Wallets ---
        candidates.contains("bank of the philippine islands") || hasWord("bpi") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF7A1626),
                secondary = Color(0xFF530D18),
                border = Color(0xFFA03647),
                ink = Color(0xFFFFF8F5),
                secondaryInk = Color(0xFFEAC7CC),
                badgeBg = Color(0x2CD4A843),
                badgeInk = Color(0xFFFFF4DA),
                chipGold = Color(0xFFE0BE6E)
            ),
            motif = BankArtworkMotif.HERITAGE_FACETS,
            accentColor = Color(0xFFD9AA42),
            emblemCode = "BPI",
            displayBrand = "BPI"
        )

        hasWord("bdo") || candidates.contains("banco de oro") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF0F3570),
                secondary = Color(0xFF082047),
                border = Color(0xFF2E5899),
                ink = Color(0xFFF7FAFF),
                secondaryInk = Color(0xFFB9CCE8),
                badgeBg = Color(0x33D4A12A),
                badgeInk = Color(0xFFFFF4D6),
                chipGold = Color(0xFFE3BD5E)
            ),
            motif = BankArtworkMotif.GOLD_RIBBON,
            accentColor = Color(0xFFD4A12A),
            emblemCode = "BDO",
            displayBrand = "BDO"
        )

        candidates.contains("metrobank") || candidates.contains("metropolitan bank") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF0C2957),
                secondary = Color(0xFF071A38),
                border = Color(0xFF284D85),
                ink = Color(0xFFF4F9FF),
                secondaryInk = Color(0xFFB6CDE8),
                badgeBg = Color(0x2C38B6DA),
                badgeInk = Color(0xFFE8FAFF),
                chipGold = Color(0xFFDDC177)
            ),
            motif = BankArtworkMotif.STEPPED_WEAVE,
            accentColor = Color(0xFF38B6DA),
            emblemCode = "MB",
            displayBrand = "Metrobank"
        )

        candidates.contains("unionbank") || candidates.contains("union bank") || hasWord("ubp") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF1C1F24),
                secondary = Color(0xFF111317),
                border = Color(0xFF3A404A),
                ink = Color(0xFFFAF8F5),
                secondaryInk = Color(0xFFC4C8D0),
                badgeBg = Color(0x36EA6C1A),
                badgeInk = Color(0xFFFFE7D6),
                chipGold = Color(0xFFD8BA79)
            ),
            motif = BankArtworkMotif.SUNSET_CURVE,
            accentColor = Color(0xFFEA6C1A),
            emblemCode = "UB",
            displayBrand = "UnionBank"
        )

        candidates.contains("land bank") || candidates.contains("landbank") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF124D30),
                secondary = Color(0xFF0B331F),
                border = Color(0xFF2C724F),
                ink = Color(0xFFF5FBF7),
                secondaryInk = Color(0xFFB9D9C8),
                badgeBg = Color(0x2CD6B04E),
                badgeInk = Color(0xFFFFF6DC),
                chipGold = Color(0xFFDFC072)
            ),
            motif = BankArtworkMotif.LEAF_CONTOUR,
            accentColor = Color(0xFFD6B04E),
            emblemCode = "LBP",
            displayBrand = "LandBank"
        )

        candidates.contains("gcash") || candidates.contains("g-xchange") || candidates.contains("gxchange") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF0068E8),
                secondary = Color(0xFF0049AD),
                border = Color(0xFF368BF2),
                ink = Color(0xFFFFFFFF),
                secondaryInk = Color(0xFFCDE4FF),
                badgeBg = Color(0x2CFFFFFF),
                badgeInk = Color(0xFFFFFFFF),
                chipGold = Color(0xFFE5CF8D)
            ),
            motif = BankArtworkMotif.WAVE_ARCS,
            accentColor = Color(0xFF4AD9FF),
            emblemCode = "G",
            displayBrand = "GCash"
        )

        candidates.contains("maya") || candidates.contains("paymaya") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF141918),
                secondary = Color(0xFF0B0E0E),
                border = Color(0xFF2D3B37),
                ink = Color(0xFFF5FAF8),
                secondaryInk = Color(0xFFB0C6BE),
                badgeBg = Color(0x2A2CE59E),
                badgeInk = Color(0xFFD9FFF0),
                chipGold = Color(0xFFD5C89A)
            ),
            motif = BankArtworkMotif.OBSIDIAN_MINT,
            accentColor = Color(0xFF2CE59E),
            emblemCode = "M",
            displayBrand = "Maya"
        )

        candidates.contains("gotyme") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF0B4248),
                secondary = Color(0xFF06292D),
                border = Color(0xFF256870),
                ink = Color(0xFFF2FCFD),
                secondaryInk = Color(0xFFADD6DA),
                badgeBg = Color(0x2C3BE0D3),
                badgeInk = Color(0xFFE2FFFC),
                chipGold = Color(0xFFDEC88E)
            ),
            motif = BankArtworkMotif.TEAL_PRISM,
            accentColor = Color(0xFF3BE0D3),
            emblemCode = "GT",
            displayBrand = "GoTyme"
        )

        candidates.contains("seabank") || candidates.contains("maribank") -> {
            val isMari = candidates.contains("maribank")
            BankCardTheme(
                palette = darkCardPalette(
                    base = Color(0xFFE0532C),
                    secondary = Color(0xFFB63B19),
                    border = Color(0xFFF07B59),
                    ink = Color(0xFFFFFDFB),
                    secondaryInk = Color(0xFFFFE0D4),
                    badgeBg = Color(0x2BFFFFFF),
                    badgeInk = Color(0xFFFFFFFF),
                    chipGold = Color(0xFFE8CE95)
                ),
                motif = BankArtworkMotif.CORAL_WAVES,
                accentColor = Color(0xFFFFB57A),
                emblemCode = if (isMari) "MB" else "SB",
                displayBrand = if (isMari) "MariBank" else "SeaBank"
            )
        }

        hasWord("rcbc") || candidates.contains("rizal commercial") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF233346),
                secondary = Color(0xFF16212E),
                border = Color(0xFF405873),
                ink = Color(0xFFF5F8FC),
                secondaryInk = Color(0xFFB8C7D9),
                badgeBg = Color(0x2C2FC2B3),
                badgeInk = Color(0xFFE1FCF9),
                chipGold = Color(0xFFDCC284)
            ),
            motif = BankArtworkMotif.HEX_FACETS,
            accentColor = Color(0xFF2FC2B3),
            emblemCode = "RC",
            displayBrand = "RCBC"
        )

        candidates.contains("security bank") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF0E2F5A),
                secondary = Color(0xFF081D39),
                border = Color(0xFF2A5288),
                ink = Color(0xFFF6F9FF),
                secondaryInk = Color(0xFFB8CBE6),
                badgeBg = Color(0x30E2A52E),
                badgeInk = Color(0xFFFFF3DA),
                chipGold = Color(0xFFE0BD6E)
            ),
            motif = BankArtworkMotif.SHIELD_ARCS,
            accentColor = Color(0xFFE2A52E),
            emblemCode = "SBC",
            displayBrand = "Security Bank"
        )

        candidates.contains("china bank") || candidates.contains("chinabank") || candidates.contains("china banking") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF781623),
                secondary = Color(0xFF520D16),
                border = Color(0xFFA03644),
                ink = Color(0xFFFFF8F8),
                secondaryInk = Color(0xFFE8C3C7),
                badgeBg = Color(0x2CD9B052),
                badgeInk = Color(0xFFFFF4DB),
                chipGold = Color(0xFFE1BF73)
            ),
            motif = BankArtworkMotif.COIN_RINGS,
            accentColor = Color(0xFFDFB452),
            emblemCode = "CBC",
            displayBrand = "China Bank"
        )

        candidates.contains("philippine national bank") || hasWord("pnb") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF193261),
                secondary = Color(0xFF102142),
                border = Color(0xFF36558F),
                ink = Color(0xFFF5F8FF),
                secondaryInk = Color(0xFFBAC8E6),
                badgeBg = Color(0x2CD9B45A),
                badgeInk = Color(0xFFFFF5DD),
                chipGold = Color(0xFFDFC27A)
            ),
            motif = BankArtworkMotif.TWIN_STRIPES,
            accentColor = Color(0xFFD9B45A),
            emblemCode = "PNB",
            displayBrand = "PNB"
        )

        candidates.contains("eastwest") || candidates.contains("east west") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF4E1E66),
                secondary = Color(0xFF331045),
                border = Color(0xFF723A8F),
                ink = Color(0xFFFCF7FF),
                secondaryInk = Color(0xFFD9C0E6),
                badgeBg = Color(0x2CD799EC),
                badgeInk = Color(0xFFF8EBFF),
                chipGold = Color(0xFFDEC489)
            ),
            motif = BankArtworkMotif.COMPASS_ARCS,
            accentColor = Color(0xFFD799EC),
            emblemCode = "EW",
            displayBrand = "EastWest"
        )

        hasWord("cimb") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF991122),
                secondary = Color(0xFF6B0915),
                border = Color(0xFFBD3748),
                ink = Color(0xFFFFF8F8),
                secondaryInk = Color(0xFFECC2C8),
                badgeBg = Color(0x2CFFFFFF),
                badgeInk = Color(0xFFFFFFFF),
                chipGold = Color(0xFFDFC180)
            ),
            motif = BankArtworkMotif.CHEVRON_BANDS,
            accentColor = Color(0xFFF05565),
            emblemCode = "CI",
            displayBrand = "CIMB"
        )

        candidates.contains("tonik") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF5B28B5),
                secondary = Color(0xFF3D1782),
                border = Color(0xFF7F4ED6),
                ink = Color(0xFFFAF8FF),
                secondaryInk = Color(0xFFD4C4F4),
                badgeBg = Color(0x2CB991FF),
                badgeInk = Color(0xFFF4ECFF),
                chipGold = Color(0xFFDFC994)
            ),
            motif = BankArtworkMotif.CITRUS_ARC,
            accentColor = Color(0xFFB991FF),
            emblemCode = "TK",
            displayBrand = "Tonik"
        )

        candidates.contains("psbank") || candidates.contains("philippine savings") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF123C7D),
                secondary = Color(0xFF0A2654),
                border = Color(0xFF3161AA),
                ink = Color(0xFFF6F9FF),
                secondaryInk = Color(0xFFBACCE9),
                badgeBg = Color(0x36F07424),
                badgeInk = Color(0xFFFFEBDC),
                chipGold = Color(0xFFDFC078)
            ),
            motif = BankArtworkMotif.OCTAGON_PRISM,
            accentColor = Color(0xFFF07424),
            emblemCode = "PS",
            displayBrand = "PSBank"
        )

        candidates.contains("robinsons") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF132B52),
                secondary = Color(0xFF0A1B36),
                border = Color(0xFF325080),
                ink = Color(0xFFF6F8FC),
                secondaryInk = Color(0xFFBBC8DF),
                badgeBg = Color(0x34C8333F),
                badgeInk = Color(0xFFFFE4E6),
                chipGold = Color(0xFFDCC07E)
            ),
            motif = BankArtworkMotif.SHIELD_ARCS,
            accentColor = Color(0xFFC8333F),
            emblemCode = "RB",
            displayBrand = "Robinsons"
        )

        candidates.contains("asia united") || hasWord("aub") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF114B3C),
                secondary = Color(0xFF093227),
                border = Color(0xFF2D705E),
                ink = Color(0xFFF5FBF8),
                secondaryInk = Color(0xFFB7D9CF),
                badgeBg = Color(0x2CD9B24C),
                badgeInk = Color(0xFFFFF5DA),
                chipGold = Color(0xFFDFC273)
            ),
            motif = BankArtworkMotif.GOLD_RIBBON,
            accentColor = Color(0xFFD9B24C),
            emblemCode = "AUB",
            displayBrand = "AUB"
        )

        // --- 15 Global Banks ---
        candidates.contains("hsbc") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF1E2329),
                secondary = Color(0xFF13161B),
                border = Color(0xFF3D4450),
                ink = Color(0xFFF7F8FA),
                secondaryInk = Color(0xFFC4CAD4),
                badgeBg = Color(0x32DB0011),
                badgeInk = Color(0xFFFFE5E7),
                chipGold = Color(0xFFD8BE7C)
            ),
            motif = BankArtworkMotif.HSBC_HEXAGON,
            accentColor = Color(0xFFDB0011),
            emblemCode = "HS",
            displayBrand = "HSBC"
        )

        candidates.contains("chase") || candidates.contains("jpmorgan") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF12417E),
                secondary = Color(0xFF0B2A54),
                border = Color(0xFF3363A4),
                ink = Color(0xFFF5F9FF),
                secondaryInk = Color(0xFFBACFE9),
                badgeBg = Color(0x2C5EA2E8),
                badgeInk = Color(0xFFEAF4FF),
                chipGold = Color(0xFFDCC282)
            ),
            motif = BankArtworkMotif.OCTAGON_PRISM,
            accentColor = Color(0xFF5EA2E8),
            emblemCode = "CH",
            displayBrand = "Chase"
        )

        candidates.contains("bank of america") || hasWord("bofa") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF1D355E),
                secondary = Color(0xFF132340),
                border = Color(0xFF3C588A),
                ink = Color(0xFFF7F9FD),
                secondaryInk = Color(0xFFBECBE2),
                badgeBg = Color(0x32D23242),
                badgeInk = Color(0xFFFFE6E9),
                chipGold = Color(0xFFDBC182)
            ),
            motif = BankArtworkMotif.TWIN_STRIPES,
            accentColor = Color(0xFFD23242),
            emblemCode = "BA",
            displayBrand = "BofA"
        )

        candidates.contains("wells fargo") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFFA61C28),
                secondary = Color(0xFF731019),
                border = Color(0xFFC93E4A),
                ink = Color(0xFFFFF9F6),
                secondaryInk = Color(0xFFF0C7CB),
                badgeBg = Color(0x30E5B73B),
                badgeInk = Color(0xFFFFF5DA),
                chipGold = Color(0xFFE5BF5B)
            ),
            motif = BankArtworkMotif.CLASSIC_FRAME,
            accentColor = Color(0xFFE5B73B),
            emblemCode = "WF",
            displayBrand = "Wells Fargo"
        )

        candidates.contains("citibank") || hasWord("citi") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF103A6E),
                secondary = Color(0xFF092447),
                border = Color(0xFF2E5E9B),
                ink = Color(0xFFF5F9FF),
                secondaryInk = Color(0xFFB9CCE6),
                badgeBg = Color(0x30D9363E),
                badgeInk = Color(0xFFFFE8EA),
                chipGold = Color(0xFFDCC282)
            ),
            motif = BankArtworkMotif.WAVE_ARCS,
            accentColor = Color(0xFFD9363E),
            emblemCode = "CI",
            displayBrand = "Citi"
        )

        candidates.contains("capital one") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF142742),
                secondary = Color(0xFF0B182B),
                border = Color(0xFF314C72),
                ink = Color(0xFFF6F8FC),
                secondaryInk = Color(0xFFBBC7DA),
                badgeBg = Color(0x34D84535),
                badgeInk = Color(0xFFFFE8E5),
                chipGold = Color(0xFFDCC282)
            ),
            motif = BankArtworkMotif.CITRUS_ARC,
            accentColor = Color(0xFFD84535),
            emblemCode = "C1",
            displayBrand = "Capital One"
        )

        candidates.contains("barclays") || candidates.contains("barclaycard") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF0D3A58),
                secondary = Color(0xFF08253A),
                border = Color(0xFF2B6085),
                ink = Color(0xFFF4FAFE),
                secondaryInk = Color(0xFFB6D4E8),
                badgeBg = Color(0x2C2EB5E5),
                badgeInk = Color(0xFFE6F8FF),
                chipGold = Color(0xFFDCC284)
            ),
            motif = BankArtworkMotif.SHIELD_ARCS,
            accentColor = Color(0xFF2EB5E5),
            emblemCode = "BC",
            displayBrand = "Barclays"
        )

        candidates.contains("revolut") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF1B2028),
                secondary = Color(0xFF11151B),
                border = Color(0xFF3B4452),
                ink = Color(0xFFF6F8FB),
                secondaryInk = Color(0xFFC1C8D4),
                badgeBg = Color(0x2C6794EC),
                badgeInk = Color(0xFFEAF1FF),
                chipGold = Color(0xFFD6C08A)
            ),
            motif = BankArtworkMotif.STEPPED_WEAVE,
            accentColor = Color(0xFF6794EC),
            emblemCode = "RV",
            displayBrand = "Revolut"
        )

        hasWord("wise") || candidates.contains("transferwise") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF163322),
                secondary = Color(0xFF0E2216),
                border = Color(0xFF305941),
                ink = Color(0xFFF5FCF6),
                secondaryInk = Color(0xFFBBD8C2),
                badgeBg = Color(0x2C9FE870),
                badgeInk = Color(0xFFECFFE0),
                chipGold = Color(0xFFD9C98A)
            ),
            motif = BankArtworkMotif.CHEVRON_BANDS,
            accentColor = Color(0xFF9FE870),
            emblemCode = "W",
            displayBrand = "Wise"
        )

        candidates.contains("monzo") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFFEB5A46),
                secondary = Color(0xFFC73D2A),
                border = Color(0xFFF58373),
                ink = Color(0xFFFFFDFB),
                secondaryInk = Color(0xFFFFE1DC),
                badgeBg = Color(0x2BFFFFFF),
                badgeInk = Color(0xFFFFFFFF),
                chipGold = Color(0xFFEAD29A)
            ),
            motif = BankArtworkMotif.CORAL_WAVES,
            accentColor = Color(0xFFFFD27D),
            emblemCode = "MZ",
            displayBrand = "Monzo"
        )

        hasWord("dbs") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF1A191A),
                secondary = Color(0xFF101011),
                border = Color(0xFF3D393D),
                ink = Color(0xFFF8F6F6),
                secondaryInk = Color(0xFFC9C4C6),
                badgeBg = Color(0x32E3242B),
                badgeInk = Color(0xFFFFE6E7),
                chipGold = Color(0xFFD8BE7C)
            ),
            motif = BankArtworkMotif.MINIMAL_STRIPE,
            accentColor = Color(0xFFE3242B),
            emblemCode = "DBS",
            displayBrand = "DBS"
        )

        hasWord("ocbc") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFFA11B24),
                secondary = Color(0xFF721017),
                border = Color(0xFFC53E47),
                ink = Color(0xFFFFF8F8),
                secondaryInk = Color(0xFFEDC4C7),
                badgeBg = Color(0x2CE8B857),
                badgeInk = Color(0xFFFFF4DC),
                chipGold = Color(0xFFE3C070)
            ),
            motif = BankArtworkMotif.COMPASS_ARCS,
            accentColor = Color(0xFFE8B857),
            emblemCode = "OC",
            displayBrand = "OCBC"
        )

        hasWord("uob") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF112D5E),
                secondary = Color(0xFF0A1D40),
                border = Color(0xFF30518E),
                ink = Color(0xFFF5F8FF),
                secondaryInk = Color(0xFFBBC8E4),
                badgeBg = Color(0x32D9323B),
                badgeInk = Color(0xFFFFE6E8),
                chipGold = Color(0xFFDCC282)
            ),
            motif = BankArtworkMotif.TWIN_STRIPES,
            accentColor = Color(0xFFD9323B),
            emblemCode = "UOB",
            displayBrand = "UOB"
        )

        candidates.contains("american express") || hasWord("amex") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF193B31),
                secondary = Color(0xFF0F2B23),
                border = Color(0xFF356B5B),
                ink = Color(0xFFF5F7F4),
                secondaryInk = Color(0xFFB5CEC6),
                badgeBg = Color(0x2AD6C08A),
                badgeInk = Color(0xFFFFF6DE),
                chipGold = Color(0xFFDCC48C)
            ),
            motif = BankArtworkMotif.CLASSIC_FRAME,
            accentColor = Color(0xFFD6C08A),
            emblemCode = "AE",
            displayBrand = "Amex"
        )

        candidates.contains("discover") -> BankCardTheme(
            palette = darkCardPalette(
                base = Color(0xFF212126),
                secondary = Color(0xFF141418),
                border = Color(0xFF42424C),
                ink = Color(0xFFF7F6F4),
                secondaryInk = Color(0xFFC4C3C9),
                badgeBg = Color(0x34E86F24),
                badgeInk = Color(0xFFFFEBDC),
                chipGold = Color(0xFFDCC07E)
            ),
            motif = BankArtworkMotif.SUNSET_CURVE,
            accentColor = Color(0xFFE86F24),
            emblemCode = "DC",
            displayBrand = "Discover"
        )

        // Fallback for unrecognized banks or generic cards
        else -> {
            val basePalette = card.visualStyle.toPalette()
            val brand = emv?.bankName?.takeIf { it.isNotBlank() }
                ?: emv?.network?.takeIf { it.isNotBlank() }
                ?: card.cardTitle
            val code = if (emv != null) {
                brand.split(' ', '-', '/')
                    .filter { it.isNotBlank() }
                    .take(2)
                    .joinToString("") { it.first().uppercaseChar().toString() }
                    .ifEmpty { "EMV" }
            } else {
                ""
            }
            BankCardTheme(
                palette = basePalette,
                motif = BankArtworkMotif.MACHINED_GRAIN,
                accentColor = basePalette.chipGoldTint,
                emblemCode = code,
                displayBrand = brand
            )
        }
    }
}

/**
 * Draws bespoke geometric vector bank artwork onto a physical card canvas.
 */
fun DrawScope.drawBankArtwork(theme: BankCardTheme) {
    val w = size.width
    val h = size.height
    val accent = theme.accentColor
    val ink = theme.palette.inkColor

    when (theme.motif) {
        BankArtworkMotif.HERITAGE_FACETS -> {
            val facet1 = Path().apply {
                moveTo(w * 0.48f, 0f)
                lineTo(w, 0f)
                lineTo(w, h * 0.72f)
                lineTo(w * 0.66f, h * 0.45f)
                close()
            }
            val facet2 = Path().apply {
                moveTo(w * 0.62f, h)
                lineTo(w, h * 0.34f)
                lineTo(w, h)
                close()
            }
            drawPath(facet1, color = ink.copy(alpha = 0.06f))
            drawPath(facet2, color = accent.copy(alpha = 0.16f))
            drawLine(
                color = accent.copy(alpha = 0.42f),
                start = Offset(w * 0.46f, 0f),
                end = Offset(w, h * 0.68f),
                strokeWidth = 1.6.dp.toPx()
            )
            drawLine(
                color = accent.copy(alpha = 0.25f),
                start = Offset(w * 0.54f, h),
                end = Offset(w, h * 0.26f),
                strokeWidth = 1.1.dp.toPx()
            )
        }

        BankArtworkMotif.GOLD_RIBBON -> {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.20f), Color.Transparent),
                    center = Offset(w * 0.85f, h * 0.25f),
                    radius = w * 0.55f
                )
            )
            for (i in 0..2) {
                val offset = i * 11.dp.toPx()
                val ribbon = Path().apply {
                    moveTo(w * 0.32f, h + 10.dp.toPx())
                    cubicTo(
                        w * 0.54f, h * 0.55f + offset,
                        w * 0.78f, h * 0.82f - offset,
                        w + 8.dp.toPx(), h * 0.16f + offset
                    )
                }
                drawPath(
                    path = ribbon,
                    color = accent.copy(alpha = 0.40f - i * 0.10f),
                    style = Stroke(width = (2.6f - i * 0.5f).dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        BankArtworkMotif.STEPPED_WEAVE -> {
            val stepW = 24.dp.toPx()
            for (i in 0..5) {
                val baseX = w * 0.52f + i * stepW * 0.72f
                val stepY = h * (0.82f - i * 0.12f)
                drawLine(
                    color = if (i % 2 == 0) accent.copy(alpha = 0.28f) else ink.copy(alpha = 0.10f),
                    start = Offset(baseX, h),
                    end = Offset(baseX + w * 0.25f, stepY.coerceAtLeast(0f)),
                    strokeWidth = 8.dp.toPx(),
                    cap = StrokeCap.Butt
                )
            }
            drawLine(
                color = theme.palette.chipGoldTint.copy(alpha = 0.34f),
                start = Offset(w * 0.58f, h * 0.15f),
                end = Offset(w, h * 0.15f),
                strokeWidth = 1.2.dp.toPx()
            )
        }

        BankArtworkMotif.SUNSET_CURVE -> {
            val curve = Path().apply {
                moveTo(w * 0.55f, h)
                cubicTo(w * 0.72f, h * 0.52f, w * 0.84f, h * 0.38f, w, h * 0.14f)
                lineTo(w, h)
                close()
            }
            drawPath(curve, color = accent.copy(alpha = 0.22f))
            val arcLine = Path().apply {
                moveTo(w * 0.48f, h)
                cubicTo(w * 0.67f, h * 0.48f, w * 0.81f, h * 0.32f, w, h * 0.08f)
            }
            drawPath(
                path = arcLine,
                color = accent.copy(alpha = 0.65f),
                style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        BankArtworkMotif.LEAF_CONTOUR -> {
            for (i in 0..3) {
                val spread = i * 18.dp.toPx()
                val leaf = Path().apply {
                    moveTo(w * 0.60f + spread * 0.4f, h)
                    quadraticTo(w * 0.76f, h * 0.42f + spread * 0.2f, w, h * 0.22f + spread * 0.4f)
                }
                drawPath(
                    path = leaf,
                    color = accent.copy(alpha = 0.36f - i * 0.06f),
                    style = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawCircle(
                color = ink.copy(alpha = 0.06f),
                radius = h * 0.56f,
                center = Offset(w * 0.94f, h * 0.78f)
            )
        }

        BankArtworkMotif.WAVE_ARCS -> {
            for (i in 0..3) {
                val r = h * (0.38f + i * 0.22f)
                drawCircle(
                    color = accent.copy(alpha = 0.26f - i * 0.05f),
                    radius = r,
                    center = Offset(w * 0.92f, h * 0.62f),
                    style = Stroke(width = 2.2.dp.toPx())
                )
            }
            val waveFill = Path().apply {
                moveTo(w * 0.45f, h)
                cubicTo(w * 0.65f, h * 0.70f, w * 0.82f, h * 0.84f, w, h * 0.42f)
                lineTo(w, h)
                close()
            }
            drawPath(waveFill, color = accent.copy(alpha = 0.16f))
        }

        BankArtworkMotif.OBSIDIAN_MINT -> {
            val stripe = Path().apply {
                moveTo(w * 0.70f, 0f)
                lineTo(w * 0.82f, 0f)
                lineTo(w * 0.54f, h)
                lineTo(w * 0.42f, h)
                close()
            }
            drawPath(stripe, color = accent.copy(alpha = 0.14f))
            drawLine(
                color = accent.copy(alpha = 0.55f),
                start = Offset(w * 0.70f, 0f),
                end = Offset(w * 0.42f, h),
                strokeWidth = 1.6.dp.toPx()
            )
            // Precision dot matrix on right side
            val dotRadius = 1.3.dp.toPx()
            for (row in 0..4) {
                for (col in 0..3) {
                    drawCircle(
                        color = accent.copy(alpha = 0.24f),
                        radius = dotRadius,
                        center = Offset(w * 0.76f + col * 10.dp.toPx(), h * 0.24f + row * 10.dp.toPx())
                    )
                }
            }
        }

        BankArtworkMotif.TEAL_PRISM -> {
            val p1 = Path().apply {
                moveTo(w * 0.56f, 0f)
                lineTo(w, 0f)
                lineTo(w * 0.78f, h * 0.56f)
                close()
            }
            val p2 = Path().apply {
                moveTo(w * 0.78f, h * 0.56f)
                lineTo(w, 0f)
                lineTo(w, h)
                lineTo(w * 0.56f, h)
                close()
            }
            drawPath(p1, color = accent.copy(alpha = 0.16f))
            drawPath(p2, color = ink.copy(alpha = 0.07f))
            drawLine(
                color = accent.copy(alpha = 0.45f),
                start = Offset(w * 0.56f, 0f),
                end = Offset(w * 0.78f, h * 0.56f),
                strokeWidth = 1.3.dp.toPx()
            )
            drawLine(
                color = accent.copy(alpha = 0.35f),
                start = Offset(w * 0.78f, h * 0.56f),
                end = Offset(w * 0.56f, h),
                strokeWidth = 1.3.dp.toPx()
            )
        }

        BankArtworkMotif.CORAL_WAVES -> {
            val w1 = Path().apply {
                moveTo(0f, h * 0.80f)
                cubicTo(w * 0.30f, h * 0.62f, w * 0.64f, h * 0.96f, w, h * 0.64f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            val w2 = Path().apply {
                moveTo(w * 0.25f, h)
                cubicTo(w * 0.52f, h * 0.54f, w * 0.78f, h * 0.74f, w, h * 0.32f)
                lineTo(w, h)
                close()
            }
            drawPath(w1, color = ink.copy(alpha = 0.08f))
            drawPath(w2, color = accent.copy(alpha = 0.22f))
        }

        BankArtworkMotif.HEX_FACETS -> {
            val r = 20.dp.toPx()
            val centers = listOf(
                Offset(w * 0.74f, h * 0.26f),
                Offset(w * 0.87f, h * 0.44f),
                Offset(w * 0.74f, h * 0.62f),
                Offset(w * 0.87f, h * 0.80f)
            )
            centers.forEachIndexed { idx, c ->
                val hex = Path().apply {
                    for (k in 0..5) {
                        val angle = Math.toRadians((60 * k + 30).toDouble())
                        val px = c.x + (r * kotlin.math.cos(angle)).toFloat()
                        val py = c.y + (r * kotlin.math.sin(angle)).toFloat()
                        if (k == 0) moveTo(px, py) else lineTo(px, py)
                    }
                    close()
                }
                if (idx % 2 == 0) {
                    drawPath(hex, color = accent.copy(alpha = 0.12f))
                }
                drawPath(hex, color = accent.copy(alpha = 0.32f), style = Stroke(width = 1.2.dp.toPx()))
            }
        }

        BankArtworkMotif.SHIELD_ARCS -> {
            for (i in 0..2) {
                val xOff = i * 20.dp.toPx()
                val shield = Path().apply {
                    moveTo(w * 0.68f + xOff, 0f)
                    quadraticTo(w * 0.54f + xOff, h * 0.5f, w * 0.68f + xOff, h)
                }
                drawPath(
                    path = shield,
                    color = accent.copy(alpha = 0.40f - i * 0.10f),
                    style = Stroke(width = 2.0.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawCircle(
                color = accent.copy(alpha = 0.10f),
                radius = h * 0.44f,
                center = Offset(w * 0.90f, h * 0.5f)
            )
        }

        BankArtworkMotif.COIN_RINGS -> {
            val c1 = Offset(w * 0.80f, h * 0.44f)
            val c2 = Offset(w * 0.90f, h * 0.60f)
            listOf(c1 to 34.dp.toPx(), c1 to 22.dp.toPx(), c2 to 28.dp.toPx()).forEachIndexed { idx, (c, rad) ->
                drawCircle(
                    color = accent.copy(alpha = 0.34f - idx * 0.06f),
                    radius = rad,
                    center = c,
                    style = Stroke(width = 1.8.dp.toPx())
                )
            }
        }

        BankArtworkMotif.TWIN_STRIPES -> {
            val s1 = Path().apply {
                moveTo(w * 0.66f, 0f)
                lineTo(w * 0.72f, 0f)
                lineTo(w * 0.56f, h)
                lineTo(w * 0.50f, h)
                close()
            }
            val s2 = Path().apply {
                moveTo(w * 0.76f, 0f)
                lineTo(w * 0.80f, 0f)
                lineTo(w * 0.64f, h)
                lineTo(w * 0.60f, h)
                close()
            }
            drawPath(s1, color = accent.copy(alpha = 0.24f))
            drawPath(s2, color = ink.copy(alpha = 0.14f))
        }

        BankArtworkMotif.COMPASS_ARCS -> {
            val centerPt = Offset(w * 0.84f, h * 0.52f)
            for (i in 1..3) {
                val rad = i * 24.dp.toPx()
                drawArc(
                    color = accent.copy(alpha = 0.38f - i * 0.08f),
                    startAngle = 115f,
                    sweepAngle = 155f,
                    useCenter = false,
                    topLeft = Offset(centerPt.x - rad, centerPt.y - rad),
                    size = Size(rad * 2f, rad * 2f),
                    style = Stroke(width = 1.6.dp.toPx(), cap = StrokeCap.Round)
                )
            }
            drawLine(
                color = ink.copy(alpha = 0.12f),
                start = Offset(centerPt.x - 42.dp.toPx(), centerPt.y),
                end = Offset(w, centerPt.y),
                strokeWidth = 1.dp.toPx()
            )
        }

        BankArtworkMotif.CHEVRON_BANDS -> {
            for (i in 0..2) {
                val xStart = w * 0.58f + i * 26.dp.toPx()
                val chev = Path().apply {
                    moveTo(xStart, 0f)
                    lineTo(xStart + 28.dp.toPx(), h * 0.50f)
                    lineTo(xStart, h)
                }
                drawPath(
                    path = chev,
                    color = accent.copy(alpha = 0.36f - i * 0.08f),
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
            }
        }

        BankArtworkMotif.CITRUS_ARC -> {
            val c = Offset(w * 0.96f, h * 0.88f)
            drawCircle(
                color = accent.copy(alpha = 0.14f),
                radius = h * 0.82f,
                center = c
            )
            drawCircle(
                color = accent.copy(alpha = 0.44f),
                radius = h * 0.62f,
                center = c,
                style = Stroke(width = 2.4.dp.toPx())
            )
        }

        BankArtworkMotif.OCTAGON_PRISM -> {
            val c = Offset(w * 0.84f, h * 0.48f)
            val rad = 38.dp.toPx()
            val oct = Path().apply {
                for (i in 0..7) {
                    val angle = Math.toRadians((45 * i + 22.5))
                    val px = c.x + (rad * kotlin.math.cos(angle)).toFloat()
                    val py = c.y + (rad * kotlin.math.sin(angle)).toFloat()
                    if (i == 0) moveTo(px, py) else lineTo(px, py)
                }
                close()
            }
            drawPath(oct, color = accent.copy(alpha = 0.14f))
            drawPath(oct, color = accent.copy(alpha = 0.40f), style = Stroke(width = 1.6.dp.toPx()))
            drawLine(accent.copy(alpha = 0.22f), Offset(c.x - rad, c.y), Offset(c.x + rad, c.y), 1.dp.toPx())
        }

        BankArtworkMotif.HSBC_HEXAGON -> {
            val cx = w * 0.84f
            val cy = h * 0.48f
            val s = 26.dp.toPx()
            // Classic split geometric hexagon bow-tie motif
            val topTri = Path().apply {
                moveTo(cx - s, cy - s * 0.55f)
                lineTo(cx + s, cy - s * 0.55f)
                lineTo(cx, cy)
                close()
            }
            val bottomTri = Path().apply {
                moveTo(cx - s, cy + s * 0.55f)
                lineTo(cx + s, cy + s * 0.55f)
                lineTo(cx, cy)
                close()
            }
            val leftTri = Path().apply {
                moveTo(cx - s, cy - s * 0.55f)
                lineTo(cx - s * 1.45f, cy)
                lineTo(cx - s, cy + s * 0.55f)
                close()
            }
            val rightTri = Path().apply {
                moveTo(cx + s, cy - s * 0.55f)
                lineTo(cx + s * 1.45f, cy)
                lineTo(cx + s, cy + s * 0.55f)
                close()
            }
            drawPath(topTri, color = accent.copy(alpha = 0.28f))
            drawPath(bottomTri, color = accent.copy(alpha = 0.28f))
            drawPath(leftTri, color = ink.copy(alpha = 0.16f))
            drawPath(rightTri, color = ink.copy(alpha = 0.16f))
        }

        BankArtworkMotif.MINIMAL_STRIPE -> {
            drawRect(
                color = accent.copy(alpha = 0.85f),
                topLeft = Offset(w - 14.dp.toPx(), 0f),
                size = Size(4.5.dp.toPx(), h)
            )
            drawLine(
                color = ink.copy(alpha = 0.08f),
                start = Offset(0f, h * 0.50f),
                end = Offset(w, h * 0.50f),
                strokeWidth = 0.8.dp.toPx()
            )
        }

        BankArtworkMotif.CLASSIC_FRAME -> {
            val inset = 11.dp.toPx()
            drawRoundRect(
                color = accent.copy(alpha = 0.34f),
                topLeft = Offset(inset, inset),
                size = Size(w - inset * 2f, h - inset * 2f),
                cornerRadius = CornerRadius(10.dp.toPx(), 10.dp.toPx()),
                style = Stroke(width = 1.2.dp.toPx())
            )
            val innerInset = 15.dp.toPx()
            drawRoundRect(
                color = accent.copy(alpha = 0.16f),
                topLeft = Offset(innerInset, innerInset),
                size = Size(w - innerInset * 2f, h - innerInset * 2f),
                cornerRadius = CornerRadius(7.dp.toPx(), 7.dp.toPx()),
                style = Stroke(width = 0.8.dp.toPx())
            )
        }

        BankArtworkMotif.MACHINED_GRAIN -> {
            val step = 11.dp.toPx()
            var x = -h
            while (x < w + h) {
                drawLine(
                    color = theme.palette.grainColor.copy(alpha = 0.08f),
                    start = Offset(x, h),
                    end = Offset(x + h * 0.55f, 0f),
                    strokeWidth = 0.8.dp.toPx()
                )
                x += step
            }
        }
    }
}

/**
 * Crisp geometric vector emblem badge rendered beside the bank header on the card front.
 */
@Composable
fun BankBrandEmblem(
    theme: BankCardTheme,
    modifier: Modifier = Modifier
) {
    if (theme.emblemCode.isBlank()) return
    val code = theme.emblemCode
    val fontSize = when {
        code.length >= 3 -> 9.sp
        code.length == 2 -> 10.5.sp
        else -> 12.sp
    }

    Box(
        modifier = modifier.size(28.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val corner = 7.dp.toPx()
            drawRoundRect(
                color = theme.accentColor.copy(alpha = 0.22f),
                cornerRadius = CornerRadius(corner, corner)
            )
            drawRoundRect(
                color = theme.accentColor.copy(alpha = 0.72f),
                cornerRadius = CornerRadius(corner, corner),
                style = Stroke(width = 1.2.dp.toPx())
            )
            // Small precision accent bar along bottom edge
            drawLine(
                color = theme.accentColor,
                start = Offset(size.width * 0.26f, size.height - 3.dp.toPx()),
                end = Offset(size.width * 0.74f, size.height - 3.dp.toPx()),
                strokeWidth = 1.6.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
        Text(
            text = code,
            fontFamily = FontFamily.SansSerif,
            fontWeight = FontWeight.ExtraBold,
            fontSize = fontSize,
            letterSpacing = 0.3.sp,
            color = theme.palette.inkColor
        )
    }
}

/**
 * Custom vector Canvas marks for card payment networks in the bottom-right corner.
 */
@Composable
fun NetworkBrandMark(
    network: String,
    inkColor: Color,
    isDark: Boolean,
    modifier: Modifier = Modifier
) {
    val norm = network.trim().lowercase(Locale.US)
    Canvas(
        modifier = modifier.size(width = 48.dp, height = 28.dp)
    ) {
        val w = size.width
        val h = size.height

        when {
            norm.contains("mastercard") || norm.contains("maestro") -> {
                val isMaestro = norm.contains("maestro")
                val leftColor = if (isMaestro) Color(0xFF0099DF) else Color(0xFFEB001B)
                val rightColor = if (isMaestro) Color(0xFFEB001B) else Color(0xFFF79E1B)
                val r = h * 0.40f
                val cy = h * 0.50f
                val cx1 = w * 0.38f
                val cx2 = w * 0.64f
                drawCircle(color = leftColor, radius = r, center = Offset(cx1, cy))
                drawCircle(color = rightColor.copy(alpha = 0.90f), radius = r, center = Offset(cx2, cy))
            }

            norm.contains("visa") -> {
                // Crisp italic-cant vector VISA monogram + gold wing stroke
                val stroke = 2.1.dp.toPx()
                val topY = h * 0.24f
                val botY = h * 0.76f
                // V
                val vPath = Path().apply {
                    moveTo(w * 0.08f, topY)
                    lineTo(w * 0.22f, botY)
                    lineTo(w * 0.34f, topY)
                }
                drawPath(
                    vPath,
                    color = inkColor,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // Signature gold accent wing on V
                drawLine(
                    color = Color(0xFFF7B600),
                    start = Offset(w * 0.04f, topY),
                    end = Offset(w * 0.14f, topY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                // I
                drawLine(
                    color = inkColor,
                    start = Offset(w * 0.44f, topY),
                    end = Offset(w * 0.40f, botY),
                    strokeWidth = stroke,
                    cap = StrokeCap.Round
                )
                // S
                val sPath = Path().apply {
                    moveTo(w * 0.65f, topY + 1.dp.toPx())
                    cubicTo(w * 0.50f, topY - 1.dp.toPx(), w * 0.49f, h * 0.52f, w * 0.59f, h * 0.52f)
                    cubicTo(w * 0.69f, h * 0.52f, w * 0.66f, botY + 1.dp.toPx(), w * 0.50f, botY - 1.dp.toPx())
                }
                drawPath(
                    sPath,
                    color = inkColor,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                // A
                val aPath = Path().apply {
                    moveTo(w * 0.72f, botY)
                    lineTo(w * 0.85f, topY)
                    lineTo(w * 0.93f, botY)
                }
                drawPath(
                    aPath,
                    color = inkColor,
                    style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
                )
                drawLine(
                    color = inkColor,
                    start = Offset(w * 0.77f, h * 0.60f),
                    end = Offset(w * 0.90f, h * 0.60f),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            norm.contains("amex") || norm.contains("american express") -> {
                val frameBg = if (isDark) Color(0xFF006FCF) else Color(0xFF006FCF)
                drawRoundRect(
                    color = frameBg,
                    topLeft = Offset(w * 0.16f, h * 0.10f),
                    size = Size(w * 0.76f, h * 0.80f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.75f),
                    topLeft = Offset(w * 0.16f, h * 0.10f),
                    size = Size(w * 0.76f, h * 0.80f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                    style = Stroke(width = 1.dp.toPx())
                )
                // Geometric AMEX bars inside emblem
                val stroke = 1.5.dp.toPx()
                val ax = w * 0.28f
                val midY = h * 0.50f
                // A-triangle & X cross
                val aMark = Path().apply {
                    moveTo(ax, h * 0.68f)
                    lineTo(ax + 5.dp.toPx(), h * 0.32f)
                    lineTo(ax + 10.dp.toPx(), h * 0.68f)
                }
                drawPath(aMark, color = Color.White, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
                drawLine(Color.White, Offset(w * 0.56f, h * 0.34f), Offset(w * 0.78f, h * 0.66f), stroke, StrokeCap.Round)
                drawLine(Color.White, Offset(w * 0.78f, h * 0.34f), Offset(w * 0.56f, h * 0.66f), stroke, StrokeCap.Round)
                drawLine(Color.White.copy(alpha = 0.6f), Offset(w * 0.24f, midY), Offset(w * 0.48f, midY), 1.dp.toPx(), StrokeCap.Round)
            }

            norm.contains("jcb") -> {
                val pillW = w * 0.24f
                val pillH = h * 0.78f
                val top = h * 0.11f
                val colors = listOf(Color(0xFF0E4C96), Color(0xFFCE0E2D), Color(0xFF007940))
                colors.forEachIndexed { idx, col ->
                    val left = w * 0.18f + idx * (pillW + 2.dp.toPx())
                    drawRoundRect(
                        color = col,
                        topLeft = Offset(left, top),
                        size = Size(pillW, pillH),
                        cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx())
                    )
                    drawLine(
                        color = Color.White.copy(alpha = 0.85f),
                        start = Offset(left + pillW * 0.32f, top + pillH * 0.52f),
                        end = Offset(left + pillW * 0.68f, top + pillH * 0.52f),
                        strokeWidth = 1.4.dp.toPx(),
                        cap = StrokeCap.Round
                    )
                }
            }

            norm.contains("unionpay") -> {
                val colors = listOf(Color(0xFFD4232A), Color(0xFF00447C), Color(0xFF007B85))
                val stripeW = w * 0.24f
                val skew = 4.dp.toPx()
                colors.forEachIndexed { idx, col ->
                    val x = w * 0.16f + idx * (stripeW * 0.88f)
                    val p = Path().apply {
                        moveTo(x + skew, h * 0.14f)
                        lineTo(x + stripeW + skew, h * 0.14f)
                        lineTo(x + stripeW - skew, h * 0.86f)
                        lineTo(x - skew, h * 0.86f)
                        close()
                    }
                    drawPath(p, color = col)
                }
            }

            norm.contains("discover") -> {
                // Discover signature horizon + orange sun circle
                drawRoundRect(
                    color = inkColor.copy(alpha = 0.16f),
                    topLeft = Offset(w * 0.08f, h * 0.20f),
                    size = Size(w * 0.84f, h * 0.60f),
                    cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                )
                drawCircle(
                    color = Color(0xFFFF6000),
                    radius = h * 0.22f,
                    center = Offset(w * 0.44f, h * 0.50f)
                )
                drawLine(
                    color = Color(0xFFFF6000),
                    start = Offset(w * 0.14f, h * 0.72f),
                    end = Offset(w * 0.86f, h * 0.72f),
                    strokeWidth = 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            else -> {
                val r = h * 0.30f
                drawCircle(
                    color = inkColor.copy(alpha = 0.60f),
                    radius = r,
                    center = Offset(w * 0.44f, h * 0.50f),
                    style = Stroke(width = 1.6.dp.toPx())
                )
                drawCircle(
                    color = inkColor.copy(alpha = 0.40f),
                    radius = r,
                    center = Offset(w * 0.64f, h * 0.50f),
                    style = Stroke(width = 1.6.dp.toPx())
                )
            }
        }
    }
}
