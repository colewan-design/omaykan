package com.omaykan.storefront.core.designsystem

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Ported from packages/core/src/styles/tokens.css.
 *
 * The values are copied, not re-picked. The register, the storefront and this
 * app are three faces of one product, and a shopper who opens the web page and
 * the app on the same phone must not see two different greens. When a token
 * changes there, change it here, in this file and nowhere else.
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
    /** The near-black of the primary action button — not the brand green. */
    val ink: Color,
    val onInk: Color,
    /** Promo banner grounds, alternating down the carousel. */
    val promo: Color,
    val onPromo: Color,
    val promoAlt: Color,
    val onPromoAlt: Color,
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

internal val InkLight = Color(0xFF1F2024)
internal val OnInkLight = Color(0xFFFFFFFF)
internal val InkDark = Color(0xFFF5F5F7)
internal val OnInkDark = Color(0xFF1F2024)

// Promo grounds, alternating down the banner carousel.
internal val PromoLight = Color(0xFFFCC947)
internal val OnPromoLight = Color(0xFF2A1F00)
internal val PromoAltLight = Color(0xFF56C7F5)
internal val OnPromoAltLight = Color(0xFF00252F)
internal val PromoDark = Color(0xFFD8A521)
internal val OnPromoDark = Color(0xFF1A1200)
internal val PromoAltDark = Color(0xFF2E9BC4)
internal val OnPromoAltDark = Color(0xFF001820)

val LocalOmaykanColors = staticCompositionLocalOf {
    OmaykanColors(
        accentPressed = AccentPressedLight,
        success = SuccessLight,
        warning = WarningLight,
        textSecondary = TextSecondaryLight,
        textTertiary = TextTertiaryLight,
        separator = SeparatorLight,
        fill = FillLight,
        ink = InkLight,
        onInk = OnInkLight,
        promo = PromoLight,
        onPromo = OnPromoLight,
        promoAlt = PromoAltLight,
        onPromoAlt = OnPromoAltLight,
        tags = TagsLight,
    )
}
