package com.omaykan.rider.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * The highland palette: forest-green frames, a cream page, terracotta actions.
 *
 * The same family as :app's and :seller's Color.kt, copied rather than imported
 * (see the README on why nothing is shared yet), so a rider who also shops on
 * the same phone sees one market rather than three products. When a frame, page
 * or action value changes over there, change it here too.
 *
 * Three roles, and each colour has one:
 *
 * - **Forest** is the frame — every header, the tab bar, the welcome screen.
 * - **Terracotta** is the one thing on a screen to press: Log in, Take this
 *   job, I have picked up the order.
 * - **The green accent** is state — a switch that is on, the stop a rider is
 *   heading to, the step of a delivery they are on. It is the forest lifted
 *   just enough to read as a line on white, which the frame colour does not.
 *
 * Gold is still never decoration on a job. [RiderColors.cash] is money the
 * rider is holding that is not theirs; the sun in the mark is [RiderColors.gold]
 * and appears nowhere a number does.
 */

// --- light ---
internal val ForestLight = Color(0xFF1E3A2B)
internal val OnForestLight = Color(0xFFF6EFE3)
internal val OnForestMutedLight = Color(0xFFC9D2C6)
internal val AccentLight = Color(0xFF2E5E43)
internal val AccentPressedLight = Color(0xFF3F7A57)
internal val OnAccentLight = Color(0xFFFFFFFF)
internal val CtaLight = Color(0xFFB0512E)
internal val CtaPressedLight = Color(0xFF943F20)
internal val OnCtaLight = Color(0xFFFFFFFF)
internal val PeachLight = Color(0xFFF1E0CE)
internal val OnPeachLight = Color(0xFF8A3E22)
internal val GoldLight = Color(0xFFE2A24A)
internal val SuccessLight = Color(0xFF2F7D4A)
internal val WarningLight = Color(0xFFB26F10)
// Deeper than the warning amber: this one is set as words on its own soft
// ground ("Collect ₱540.00"), and the lighter amber is 3:1 there.
internal val CashLight = Color(0xFF9A6212)
internal val DangerLight = Color(0xFFC0392B)
internal val BgBaseLight = Color(0xFFF7F2EA)
internal val BgElevatedLight = Color(0xFFFFFFFF)
internal val FillLight = Color(0xFFEFE7DB)
internal val TextPrimaryLight = Color(0xFF2A2420)
internal val TextSecondaryLight = Color(0xFF6B6158)
internal val TextTertiaryLight = Color(0xFF8A7F74)
internal val SeparatorLight = Color(0xFFE5DBCD)
internal val HairlineLight = Color(0xFFE9E0D2)
internal val AccentSoftLight = Color(0xFFE4EDE5)
internal val CashSoftLight = Color(0xFFF8EBD6)
internal val SuccessSoftLight = Color(0xFFE1EFE4)
internal val DangerSoftLight = Color(0xFFF8E1DC)
internal val CanopyFillLight = Color(0x1FFFFFFF)

// --- dark ---
internal val ForestDark = Color(0xFF1C3527)
internal val OnForestDark = Color(0xFFF1E9DC)
internal val OnForestMutedDark = Color(0xFFA9B5A8)
internal val AccentDark = Color(0xFF9CCBA8)
internal val AccentPressedDark = Color(0xFFB5DBBE)
internal val OnAccentDark = Color(0xFF10251A)
internal val CtaDark = Color(0xFFC4623B)
internal val CtaPressedDark = Color(0xFFD57A55)
internal val OnCtaDark = Color(0xFFFFFFFF)
internal val PeachDark = Color(0xFF3A2E25)
internal val OnPeachDark = Color(0xFFE9A27F)
internal val GoldDark = Color(0xFFE2A24A)
internal val SuccessDark = Color(0xFF6CC48A)
internal val WarningDark = Color(0xFFE9A13B)
internal val CashDark = Color(0xFFE9A13B)
internal val DangerDark = Color(0xFFFF6B5E)
internal val BgBaseDark = Color(0xFF121411)
internal val BgElevatedDark = Color(0xFF1E221D)
internal val FillDark = Color(0xFF2A2F28)
internal val TextPrimaryDark = Color(0xFFF2ECE3)
internal val TextSecondaryDark = Color(0xFFBAB0A4)
internal val TextTertiaryDark = Color(0xFF948A7F)
internal val SeparatorDark = Color(0xFF343930)
internal val HairlineDark = Color(0xFF2C312A)
internal val AccentSoftDark = Color(0xFF22352A)
internal val CashSoftDark = Color(0xFF3A2E17)
internal val SuccessSoftDark = Color(0xFF1D3324)
internal val DangerSoftDark = Color(0xFF3A1F1B)
internal val CanopyFillDark = Color(0x1AFFFFFF)

/**
 * The tokens Material 3's ColorScheme has no slot for.
 *
 * Rather than bend `tertiary` into meaning "success" and leave the next person
 * guessing, the surplus tokens travel in their own holder and keep their names.
 */
@Immutable
data class RiderColors(
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
     * Money the rider has to physically collect at the door.
     *
     * Amber, and deliberately not the danger red: an unpaid order is a normal
     * job with one extra step, not a fault. But it is the single fact on a card
     * that costs a rider real money to miss, so it is the one thing on the
     * board allowed to shout.
     */
    val cash: Color,
    /**
     * The forest every screen hangs from: headers, the tab bar, the welcome.
     *
     * The frame and never a button — terracotta is the only thing that says
     * "press me". Kept under its old name because every screen already reads it.
     */
    val canopy: Color,
    val onCanopy: Color,
    val onCanopyMuted: Color,
    /** The translucent white an icon or a stat sits on, over [canopy]. */
    val canopyFill: Color,
    /** The one action a screen wants pressed. */
    val cta: Color,
    val ctaPressed: Color,
    val onCta: Color,
    /** The warm ground behind a figure's icon on the home screen. */
    val peach: Color,
    /** Icons drawn on [peach]. */
    val onPeach: Color,
    /** The sun in the mark, and nothing that could be mistaken for a price. */
    val gold: Color,
    val accentSoft: Color,
    val cashSoft: Color,
    val successSoft: Color,
    val dangerSoft: Color,
    /** A card's edge. Barely there in light, doing the real work in dark. */
    val hairline: Color,
)

internal val LightRiderColors = RiderColors(
    accentPressed = AccentPressedLight,
    success = SuccessLight,
    warning = WarningLight,
    danger = DangerLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    separator = SeparatorLight,
    fill = FillLight,
    ink = TextPrimaryLight,
    onInk = BgBaseLight,
    cash = CashLight,
    canopy = ForestLight,
    onCanopy = OnForestLight,
    onCanopyMuted = OnForestMutedLight,
    canopyFill = CanopyFillLight,
    cta = CtaLight,
    ctaPressed = CtaPressedLight,
    onCta = OnCtaLight,
    peach = PeachLight,
    onPeach = OnPeachLight,
    gold = GoldLight,
    accentSoft = AccentSoftLight,
    cashSoft = CashSoftLight,
    successSoft = SuccessSoftLight,
    dangerSoft = DangerSoftLight,
    hairline = HairlineLight,
)

internal val DarkRiderColors = RiderColors(
    accentPressed = AccentPressedDark,
    success = SuccessDark,
    warning = WarningDark,
    danger = DangerDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    separator = SeparatorDark,
    fill = FillDark,
    ink = TextPrimaryDark,
    onInk = BgBaseDark,
    cash = CashDark,
    canopy = ForestDark,
    onCanopy = OnForestDark,
    onCanopyMuted = OnForestMutedDark,
    canopyFill = CanopyFillDark,
    cta = CtaDark,
    ctaPressed = CtaPressedDark,
    onCta = OnCtaDark,
    peach = PeachDark,
    onPeach = OnPeachDark,
    gold = GoldDark,
    accentSoft = AccentSoftDark,
    cashSoft = CashSoftDark,
    successSoft = SuccessSoftDark,
    dangerSoft = DangerSoftDark,
    hairline = HairlineDark,
)

internal val LocalRiderColors = staticCompositionLocalOf<RiderColors> {
    error("RiderColors requested outside RiderTheme")
}
