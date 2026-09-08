package com.omaykan.seller.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Ported from packages/core/src/styles/tokens.css, by way of :app's Color.kt.
 *
 * The values are copied, not re-picked. The register, the storefront, the
 * shopper's app and this one are four faces of one product, and a merchant who
 * has the till open on the counter and this app in their hand must not see two
 * different greens. When a token changes in tokens.css, change it here too.
 *
 * A deliberate subset of :app's palette: no promo grounds and no categorical
 * tag hues, because nothing here is merchandising. What is added instead is
 * [SellerColors.attention] — the one colour this app has that the others do
 * not, for an order nobody has touched yet.
 */

// --- light ---
internal val AccentLight = Color(0xFF1A6B3C)
internal val AccentPressedLight = Color(0xFF16A34A)
internal val AccentTextOnLight = Color(0xFFFFFFFF)
internal val SuccessLight = Color(0xFF34C759)
internal val WarningLight = Color(0xFFFF9500)
internal val DangerLight = Color(0xFFFF3B30)
internal val BgBaseLight = Color(0xFFF2F2F7)
internal val BgElevatedLight = Color(0xFFFFFFFF)
internal val FillLight = Color(0x1F787880)
internal val TextPrimaryLight = Color(0xFF1D1D1F)
internal val TextSecondaryLight = Color(0xFF6E6E73)
internal val TextTertiaryLight = Color(0xFF8E8E93)
internal val SeparatorLight = Color(0x2E3C3C43)
internal val InkLight = Color(0xFF1D1D1F)
internal val OnInkLight = Color(0xFFFFFFFF)
internal val CanopyLight = Color(0xFF0F3B2C)
internal val OnCanopyLight = Color(0xFFF2FAF5)
internal val CanopyMutedLight = Color(0xFF9CC4B0)
internal val AccentSoftLight = Color(0xFFE3F1E9)
internal val OnAccentSoftLight = Color(0xFF14523A)
internal val ShadowLight = Color(0x330F3B2C)

// --- dark ---
internal val AccentDark = Color(0xFF92DD73)
internal val AccentPressedDark = Color(0xFF7DCB60)
internal val AccentTextOnDark = Color(0xFF08200F)
internal val SuccessDark = Color(0xFF30D158)
internal val WarningDark = Color(0xFFFF9F0A)
internal val DangerDark = Color(0xFFFF453A)
internal val BgBaseDark = Color(0xFF000000)
internal val BgElevatedDark = Color(0xFF2C2C2E)
internal val FillDark = Color(0x3D787880)
internal val TextPrimaryDark = Color(0xFFF5F5F7)
internal val TextSecondaryDark = Color(0xFFAEAEB2)
internal val TextTertiaryDark = Color(0xFF8E8E93)
internal val SeparatorDark = Color(0x8C545458)
internal val InkDark = Color(0xFFF5F5F7)
internal val OnInkDark = Color(0xFF1D1D1F)
internal val CanopyDark = Color(0xFF11251B)
internal val OnCanopyDark = Color(0xFFF5F5F7)
internal val CanopyMutedDark = Color(0xFF89A697)
internal val AccentSoftDark = Color(0xFF16301F)
internal val OnAccentSoftDark = Color(0xFF92DD73)
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
    /** The near-black of the primary action button — not the brand green. */
    val ink: Color,
    val onInk: Color,
    /**
     * An order that has arrived and that nobody has acknowledged.
     *
     * The warning orange, and deliberately not the danger red: a new order is
     * the best thing that happens to a shop all day. It is asking for
     * attention, not reporting a fault.
     */
    val attention: Color,
    /**
     * The dark green the header sits on.
     *
     * Not the accent. The accent is a button colour — a thing you press —
     * and a whole header painted in it would make the least interactive
     * region of the screen the loudest. This is the same green taken down
     * to a ground: dark enough to carry white type, close enough to the
     * brand that the header and the buttons under it read as one family.
     */
    val canopy: Color,
    val onCanopy: Color,
    /** Second-line type on [canopy] — the shop code, the greeting. */
    val canopyMuted: Color,
    /**
     * A tinted ground for the things that are neither surface nor accent:
     * the icon behind an order, a chip that is not selected, the panel a
     * delivery address sits in.
     */
    val accentSoft: Color,
    val onAccentSoft: Color,
    /**
     * The card shadow, tinted green rather than black.
     *
     * A neutral drop shadow on a warm white ground goes grey and dirty. In
     * dark theme it is fully transparent: on a black background a shadow
     * draws nothing, and the surface colour is what lifts a card instead.
     */
    val shadow: Color,
)

internal val LocalSellerColors = staticCompositionLocalOf<SellerColors> {
    error("SellerColors requested outside SellerTheme")
}
