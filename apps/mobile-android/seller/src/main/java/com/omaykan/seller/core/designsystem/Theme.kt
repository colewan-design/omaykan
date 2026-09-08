package com.omaykan.seller.core.designsystem

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

/** --radius-sm through --radius-xl. */
val SellerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

/*
 * The shapes the screens are actually built out of.
 *
 * Kept beside [SellerShapes] rather than replacing its values, because those
 * are the ported ones — a text field and a bottom sheet still round the way
 * the web portals do. These are this app's own: bigger, softer corners for the
 * cards and tiles a merchant reads at arm's length, where a 12dp radius reads
 * as a box and a 20dp one reads as a card.
 */

/** An order, a sheet panel — anything that holds a decision. */
val CardShape = RoundedCornerShape(20.dp)

/** A figure in the takings grid. Slightly tighter than a card, so a tile
 *  never looks like something you can open. */
val TileShape = RoundedCornerShape(18.dp)

/** The header, rounded only where it meets the list below it. */
val HeroShape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)

/** Buttons, chips, badges. A capsule at any height. */
val PillShape = RoundedCornerShape(percent = 50)

private val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = AccentTextOnLight,
    primaryContainer = AccentLight,
    onPrimaryContainer = AccentTextOnLight,
    secondary = AccentLight,
    onSecondary = AccentTextOnLight,
    tertiary = WarningLight,
    onTertiary = AccentTextOnLight,
    background = BgBaseLight,
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
 * No dynamic colour, for the same reason :app refuses it: the accent is the
 * brand's, and handing it to the phone's wallpaper would trade away the one
 * thing that says this is the same product as the till on the counter.
 *
 * Dark theme is followed rather than forced, unlike the web portals (which pin
 * themselves light). A phone in a shop is read at six in the morning and at ten
 * at night, and the person holding it has already told the phone which they
 * want.
 */
@Composable
fun SellerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) {
        SellerColors(
            accentPressed = AccentPressedDark,
            success = SuccessDark,
            warning = WarningDark,
            danger = DangerDark,
            textSecondary = TextSecondaryDark,
            textTertiary = TextTertiaryDark,
            separator = SeparatorDark,
            fill = FillDark,
            ink = InkDark,
            onInk = OnInkDark,
            attention = WarningDark,
            canopy = CanopyDark,
            onCanopy = OnCanopyDark,
            canopyMuted = CanopyMutedDark,
            accentSoft = AccentSoftDark,
            onAccentSoft = OnAccentSoftDark,
            shadow = ShadowDark,
        )
    } else {
        SellerColors(
            accentPressed = AccentPressedLight,
            success = SuccessLight,
            warning = WarningLight,
            danger = DangerLight,
            textSecondary = TextSecondaryLight,
            textTertiary = TextTertiaryLight,
            separator = SeparatorLight,
            fill = FillLight,
            ink = InkLight,
            onInk = OnInkLight,
            attention = WarningLight,
            canopy = CanopyLight,
            onCanopy = OnCanopyLight,
            canopyMuted = CanopyMutedLight,
            accentSoft = AccentSoftLight,
            onAccentSoft = OnAccentSoftLight,
            shadow = ShadowLight,
        )
    }

    CompositionLocalProvider(LocalSellerColors provides extended) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = SellerTypography,
            shapes = SellerShapes,
            content = content,
        )
    }
}

/** `SellerTheme.colors.textSecondary` — the tokens Material has no slot for. */
object SellerTheme {
    val colors: SellerColors
        @Composable get() = LocalSellerColors.current
}
