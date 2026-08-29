package com.omaykan.storefront.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** --radius-sm through --radius-xl. */
val OmaykanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

private val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = AccentTextOnLight,
    primaryContainer = AccentLight,
    onPrimaryContainer = AccentTextOnLight,
    secondary = AccentLight,
    onSecondary = AccentTextOnLight,
    tertiary = WarningLight,
    onTertiary = AccentTextOnLight,
    background = Color(0xFFFFFFFF),
    onBackground = TextPrimaryLight,
    surface = BgElevatedLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = FillLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = BgElevatedLight,
    surfaceContainerHigh = BgElevatedLight,
    surfaceContainerLow = BgBaseLight,
    outline = SeparatorLight,
    outlineVariant = SeparatorLight,
    error = DangerLight,
    onError = AccentTextOnLight,
)

private val DarkScheme = darkColorScheme(
    primary = AccentDark,
    onPrimary = AccentTextOnDark,
    primaryContainer = AccentDark,
    onPrimaryContainer = AccentTextOnDark,
    secondary = AccentDark,
    onSecondary = AccentTextOnDark,
    tertiary = WarningDark,
    onTertiary = AccentTextOnDark,
    background = BgBaseDark,
    onBackground = TextPrimaryDark,
    surface = BgElevatedDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = FillDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = BgElevatedDark,
    surfaceContainerHigh = BgElevatedDark,
    surfaceContainerLow = BgBaseDark,
    outline = SeparatorDark,
    outlineVariant = SeparatorDark,
    error = DangerDark,
    onError = AccentTextOnDark,
)

/**
 * No dynamic colour. The accent is the brand's, and on this product it is also
 * the thing that says "this is the same shop you saw on the web" — handing it
 * to the phone's wallpaper would trade that away for novelty.
 */
@Composable
fun OmaykanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) {
        OmaykanColors(
            accentPressed = AccentPressedDark,
            success = SuccessDark,
            warning = WarningDark,
            textSecondary = TextSecondaryDark,
            textTertiary = TextTertiaryDark,
            separator = SeparatorDark,
            fill = FillDark,
            ink = InkDark,
            onInk = OnInkDark,
            promo = PromoDark,
            onPromo = OnPromoDark,
            promoAlt = PromoAltDark,
            onPromoAlt = OnPromoAltDark,
            tags = TagsDark,
        )
    } else {
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

    CompositionLocalProvider(LocalOmaykanColors provides extended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = OmaykanTypography,
            shapes = OmaykanShapes,
            content = content,
        )
    }
}

/** `OmaykanTheme.colors.textSecondary` — the tokens Material has no slot for. */
object OmaykanTheme {
    val colors: OmaykanColors
        @Composable get() = LocalOmaykanColors.current
}
