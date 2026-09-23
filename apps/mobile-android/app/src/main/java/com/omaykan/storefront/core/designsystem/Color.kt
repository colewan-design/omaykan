package com.omaykan.storefront.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * The highland palette: forest-green bars, a white page, terracotta actions.
 *
 * Taken from the storefront reference the app was redrawn to — see
 * documentation/design/customer-refresh/README.md. The forest is the frame
 * (headers, the tab bar, the splash), terracotta is reserved for the one thing
 * a screen wants pressed, and everything a shopper reads sits on white. When a
 * colour changes, change it here, in this file and nowhere else.
 */

// --- light ---
internal val ForestLight = Color(0xFF1E3A2B)
internal val OnForestLight = Color(0xFFF6EFE3)
internal val OnForestMutedLight = Color(0xFFC9D2C6)
internal val CtaLight = Color(0xFFB0512E)
internal val CtaPressedLight = Color(0xFF943F20)
internal val OnCtaLight = Color(0xFFFFFFFF)
internal val PeachLight = Color(0xFFF1E0CE)
internal val GoldLight = Color(0xFFE2A24A)
internal val SuccessLight = Color(0xFF2F7D4A)
internal val WarningLight = Color(0xFFC27A12)
internal val DangerLight = Color(0xFFC0392B)
internal val BgBaseLight = Color(0xFFFFFFFF)
internal val BgElevatedLight = Color(0xFFFFFFFF)
internal val FillLight = Color(0xFFEFE7DB)
internal val TextPrimaryLight = Color(0xFF2A2420)
internal val TextSecondaryLight = Color(0xFF6B6158)
internal val TextTertiaryLight = Color(0xFF978C81)
internal val SeparatorLight = Color(0xFFE5DBCD)

// --- dark ---
// Only a shade under the light forest: any darker and the bars and the chosen
// aisle chip sink into the near-black page instead of framing it.
internal val ForestDark = Color(0xFF1C3527)
internal val OnForestDark = Color(0xFFF1E9DC)
internal val OnForestMutedDark = Color(0xFFA9B5A8)
internal val CtaDark = Color(0xFFC4623B)
internal val CtaPressedDark = Color(0xFFD57A55)
internal val OnCtaDark = Color(0xFFFFFFFF)
internal val PeachDark = Color(0xFF3A2E25)
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

/**
 * The tokens Material 3's ColorScheme has no slot for.
 *
 * Rather than bend `tertiary` into meaning "success" and leave the next person
 * guessing, the surplus tokens travel in their own holder and keep their names.
 */
@Immutable
data class OmaykanColors(
    val accentPressed: Color,
    val success: Color,
    val warning: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val separator: Color,
    val fill: Color,
    /** The strongest reading colour — headings, counts, the snackbar ground. */
    val ink: Color,
    val onInk: Color,
    /** The frame: headers, the tab bar, the splash. */
    val forest: Color,
    val onForest: Color,
    /** Secondary words on the forest — an email under a name, an idle tab. */
    val onForestMuted: Color,
    /** The one action a screen wants pressed. Never used for anything else. */
    val cta: Color,
    val onCta: Color,
    /** The warm ground behind the aisle circles and small illustrations. */
    val peach: Color,
    /** The sun in the mark, and nothing else that could be mistaken for a price. */
    val gold: Color,
    /**
     * Categorical hues for category badges. Fixed order and constant across
     * themes on purpose: a category keeps its colour whichever theme is on, so
     * "the blue one" stays a usable thing for a shopper to say.
     */
    val tags: List<Color>,
)

internal val TagsLight = listOf(
    Color(0xFF2A78D6),
    Color(0xFF1BAF7A),
    Color(0xFFEDA100),
    Color(0xFF008300),
    Color(0xFF4A3AA7),
    Color(0xFFE34948),
    Color(0xFFE87BA4),
    Color(0xFFEB6834),
)

internal val TagsDark = listOf(
    Color(0xFF3987E5),
    Color(0xFF199E70),
    Color(0xFFC98500),
    Color(0xFF008300),
    Color(0xFF9085E9),
    Color(0xFFE66767),
    Color(0xFFD55181),
    Color(0xFFD95926),
)

internal val LightExtended = OmaykanColors(
    accentPressed = CtaPressedLight,
    success = SuccessLight,
    warning = WarningLight,
    textSecondary = TextSecondaryLight,
    textTertiary = TextTertiaryLight,
    separator = SeparatorLight,
    fill = FillLight,
    ink = TextPrimaryLight,
    onInk = BgBaseLight,
    forest = ForestLight,
    onForest = OnForestLight,
    onForestMuted = OnForestMutedLight,
    cta = CtaLight,
    onCta = OnCtaLight,
    peach = PeachLight,
    gold = GoldLight,
    tags = TagsLight,
)

internal val DarkExtended = OmaykanColors(
    accentPressed = CtaPressedDark,
    success = SuccessDark,
    warning = WarningDark,
    textSecondary = TextSecondaryDark,
    textTertiary = TextTertiaryDark,
    separator = SeparatorDark,
    fill = FillDark,
    ink = TextPrimaryDark,
    onInk = BgBaseDark,
    forest = ForestDark,
    onForest = OnForestDark,
    onForestMuted = OnForestMutedDark,
    cta = CtaDark,
    onCta = OnCtaDark,
    peach = PeachDark,
    gold = GoldDark,
    tags = TagsDark,
)

val LocalOmaykanColors = staticCompositionLocalOf { LightExtended }
