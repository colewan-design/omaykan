package com.omaykan.rider.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Ported from packages/core/src/styles/tokens.css, by way of :seller's
 * Color.kt.
 *
 * The values are copied, not re-picked. The register, the storefront, the
 * shopper's app, the seller's app and this one are five faces of one product.
 * When a token changes in tokens.css, change it here too.
 *
 * A deliberate subset, like :seller's: no promo grounds and no categorical tag
 * hues, because nothing here is merchandising. What is added instead is
 * [RiderColors.cash] — the one colour this app has that none of the others do.
 *
 * ## The surplus below the shared tokens
 *
 * Everything from [CanopyLight] down is this app's own and has no counterpart
 * in tokens.css. None of it is a new *brand* colour: the canopy is the brand
 * accent taken down to a near-black green, and every `…Soft` is one of the
 * existing semantic hues at card weight. They exist because the redesign leans
 * on two things a flat token list has no name for — a dark header block that
 * carries identity and earnings, and tinted badges that let a rider find the
 * money row on a card by colour before they have read a word of it.
 *
 * Deriving rather than picking is the point: if the accent in tokens.css moves,
 * these move with it and the app still looks like one product.
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

// --- the canopy: the green block at the top of every screen ---
//
// Two stops rather than one, because the canopy is a gradient now: the darker
// end at the status bar and the brighter one at the rounded bottom edge, so the
// block has a direction and the white text at the top of it is on the half with
// the most contrast to spare.
//
// The brightness moved on purpose. It used to be a near-black green, which read
// as a chrome bar; the redesign wants the header to read as the *brand*, the
// way the green header on a delivery app does. What did not move is the floor:
// both stops in both themes hold white text above 5:1, which is what a rider
// reading a phone in direct sun actually needs. That is why the bright end is
// #1E7F45 and not the mid-green a mock would pick — anything lighter drops the
// small print under a name to a contrast a bright pavement erases.
internal val CanopyLight = Color(0xFF14603A)
internal val CanopyBrightLight = Color(0xFF1E7F45)
internal val CanopyDark = Color(0xFF0E3A24)
internal val CanopyBrightDark = Color(0xFF16542F)
internal val OnCanopyLight = Color(0xFFFFFFFF)
internal val OnCanopyDark = Color(0xFFF1F6F0)
internal val OnCanopyMutedLight = Color(0xFFBFDCC9)
internal val OnCanopyMutedDark = Color(0xFF9EBCAA)

// --- the two money cards ---
//
// The only two saturated surfaces in the app that are not the brand green, and
// they exist to be told apart at a glance: one is money the rider has made and
// one is money in their pocket that is somebody else's. Reading them as the
// same colour is the mistake that costs a rider real money at the end of a
// shift, so they are opposite ends of the wheel rather than two greens.
//
// Both are deeper than the obvious teal and amber, and for the usual reason:
// white on a bright teal is about 2.6:1, which is a mock's colour, not a
// screen's. These sit at 5.2 and 4.9.
internal val PayoutLight = Color(0xFF0B7A70)
internal val PayoutDark = Color(0xFF0A5F58)
internal val OwedLight = Color(0xFFA2620C)
internal val OwedDark = Color(0xFF7C4A09)

/** The translucent white a stat pill or an icon button sits on, over canopy. */
internal val CanopyFillLight = Color(0x1FFFFFFF)
internal val CanopyFillDark = Color(0x1AFFFFFF)

// --- tinted grounds for badges and icon pills ---
internal val AccentSoftLight = Color(0xFFE3EFE7)
internal val AccentSoftDark = Color(0xFF1C3325)
internal val CashSoftLight = Color(0xFFFFF1DE)
internal val CashSoftDark = Color(0xFF3A2C12)
internal val SuccessSoftLight = Color(0xFFE3F5E7)
internal val SuccessSoftDark = Color(0xFF16321D)
internal val DangerSoftLight = Color(0xFFFDE7E5)
internal val DangerSoftDark = Color(0xFF3A1B18)

/** A card's edge. Barely there in light, doing the real work in dark. */
internal val HairlineLight = Color(0x14000000)
internal val HairlineDark = Color(0x1FFFFFFF)

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
    /** The near-black of the primary action button — not the brand green. */
    val ink: Color,
    val onInk: Color,
    /**
     * Money the rider has to physically collect at the door.
     *
     * The warning orange, and deliberately not the danger red: an unpaid order
     * is a normal job with one extra step, not a fault. But it is the single
     * fact on a card that costs a rider real money to miss, so it is the one
     * thing on the board allowed to shout.
     */
    val cash: Color,
    /**
     * The dark block every screen hangs from.
     *
     * It carries the two things that are true regardless of what is on the
     * board — who is signed in, and what the shift has paid so far — so that
     * the scrolling half below it can be nothing but jobs.
     */
    val canopy: Color,
    /** The bottom stop of the canopy's gradient. See [canopy]. */
    val canopyBright: Color,
    val onCanopy: Color,
    val onCanopyMuted: Color,
    val canopyFill: Color,
    /**
     * The teal of the earnings card: money that is already the rider's.
     *
     * There is no platform cut to take off it and no field for one anywhere in
     * this app, which is why this card says a total and not a balance.
     */
    val payout: Color,
    /**
     * The amber of the cash-in-hand card: money the rider is holding that
     * belongs to a shop.
     *
     * The same hue as [cash] on a job card, at card weight, because it is the
     * same fact one step later — collected at a door, owed at a counter.
     */
    val owed: Color,
    val accentSoft: Color,
    val cashSoft: Color,
    val successSoft: Color,
    val dangerSoft: Color,
    val hairline: Color,
)

internal val LocalRiderColors = staticCompositionLocalOf<RiderColors> {
    error("RiderColors requested outside RiderTheme")
}
