package com.omaykan.seller.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * The highland palette: forest-green frames, a cream page, terracotta actions.
 *
 * The same values as :app's Color.kt, copied rather than imported (see the
 * README on why nothing is shared yet). The shopper's app was redrawn to this
 * palette first; the seller's side follows it so a shop owner who has both on
 * one phone sees one market, not two products. When a value changes in :app,
 * change it here too.
 *
 * The forest is the frame — headers, the tab bar, the welcome screen.
 * Terracotta is the thing to press. Everything a merchant reads sits on cream
 * or white.
 */

// --- light ---
internal val ForestLight = Color(0xFF1E3A2B)
internal val OnForestLight = Color(0xFFF6EFE3)
internal val OnForestMutedLight = Color(0xFFC9D2C6)
internal val CtaLight = Color(0xFFB0512E)
internal val CtaPressedLight = Color(0xFF943F20)
internal val OnCtaLight = Color(0xFFFFFFFF)
internal val PeachLight = Color(0xFFF1E0CE)
internal val OnPeachLight = Color(0xFF8A3E22)
internal val GoldLight = Color(0xFFE2A24A)
internal val SuccessLight = Color(0xFF2F7D4A)
internal val WarningLight = Color(0xFFC27A12)
internal val DangerLight = Color(0xFFC0392B)
internal val BgBaseLight = Color(0xFFF7F2EA)
internal val BgElevatedLight = Color(0xFFFFFFFF)
internal val FillLight = Color(0xFFEFE7DB)
internal val TextPrimaryLight = Color(0xFF2A2420)
internal val TextSecondaryLight = Color(0xFF6B6158)
internal val TextTertiaryLight = Color(0xFF978C81)
internal val SeparatorLight = Color(0xFFE5DBCD)
internal val ShadowLight = Color(0x553A2A1E)

// --- dark ---
internal val ForestDark = Color(0xFF1C3527)
internal val OnForestDark = Color(0xFFF1E9DC)
internal val OnForestMutedDark = Color(0xFFA9B5A8)
internal val CtaDark = Color(0xFFC4623B)
internal val CtaPressedDark = Color(0xFFD57A55)
internal val OnCtaDark = Color(0xFFFFFFFF)
internal val PeachDark = Color(0xFF3A2E25)
internal val OnPeachDark = Color(0xFFE9A27F)
internal val GoldDark = Color(0xFFE2A24A)
internal val SuccessDark = Color(0xFF6CC48A)
internal val WarningDark = Color(0xFFE9A13B)
internal val DangerDark = Color(0xFFFF6B5E)
internal val BgBaseDark = Color(0xFF121411)
internal val BgElevatedDark = Color(0xFF1E221D)
internal val FillDark = Color(0xFF2A2F28)
internal val TextPrimaryDark = Color(0xFFF2ECE3)
internal val TextSecondaryDark = Color(0xFFBAB0A4)
internal val TextTertiaryDark = Color(0xFF8E857B)
internal val SeparatorDark = Color(0xFF343930)
internal val ShadowDark = Color(0x00000000)

/**
 * The tokens Material 3's ColorScheme has no slot for.
 *
 * Rather than bend `tertiary` into meaning "success" and leave the next person
 * guessing, the surplus tokens travel in their own holder and keep their names.
 */
@Immutable
data class SellerColors(
    val accentPressed: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    val fill: Color,
    /** The strongest reading colour — headings, counts, prices. */
    val ink: Color,
    val onInk: Color,
    /**
     * An order that has arrived and that nobody has acknowledged.
     *
     * The warning amber, and deliberately not the danger red: a new order is
     * the best thing that happens to a shop all day. It is asking for
     * attention, not reporting a fault.
     */
    val attention: Color,
    /**
     * The forest: every header, the tab bar, the welcome screen.
     *
     * Kept under its old name because the order sheets already read it. It is
     * the frame and never a button — terracotta is the only thing that says
     * "press me".
     */
    val canopy: Color,
    val onCanopy: Color,
    /** Second-line type on [canopy] — the shop code, an idle tab. */
    val canopyMuted: Color,
    /**
     * The warm peach ground behind category circles, the icon on an order,
     * the panel a delivery address sits in.
     */
    val accentSoft: Color,
    /** Icons and words drawn on [accentSoft]. */
    val onAccentSoft: Color,
    /** The sun in the mark, and nothing that could be mistaken for a price. */
    val gold: Color,
    /**
     * The card shadow, tinted warm brown rather than black.
     *
     * A neutral drop shadow on a cream ground goes grey and dirty. In dark
     * theme it is fully transparent: on a near-black page a shadow draws
     * nothing, and the surface colour is what lifts a card instead.
     */
    val shadow: Color,
)

internal val LightSellerColors = SellerColors(
    accentPressed = CtaPressedLight,
    success = SuccessLight,
    warning = WarningLight,
    danger = DangerLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    separator = SeparatorLight,
    fill = FillLight,
    ink = TextPrimaryLight,
    onInk = BgBaseLight,
    attention = WarningLight,
    canopy = ForestLight,
    onCanopy = OnForestLight,
    canopyMuted = OnForestMutedLight,
    accentSoft = PeachLight,
    onAccentSoft = OnPeachLight,
    gold = GoldLight,
    shadow = ShadowLight,
)

internal val DarkSellerColors = SellerColors(
    accentPressed = CtaPressedDark,
    success = SuccessDark,
    warning = WarningDark,
    danger = DangerDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    separator = SeparatorDark,
    fill = FillDark,
    ink = TextPrimaryDark,
    onInk = BgBaseDark,
    attention = WarningDark,
    canopy = ForestDark,
    onCanopy = OnForestDark,
    canopyMuted = OnForestMutedDark,
    accentSoft = PeachDark,
    onAccentSoft = OnPeachDark,
    gold = GoldDark,
    shadow = ShadowDark,
)

internal val LocalSellerColors = staticCompositionLocalOf { LightSellerColors }
