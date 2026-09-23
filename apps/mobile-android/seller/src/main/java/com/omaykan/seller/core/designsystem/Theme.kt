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

/** Softer and smaller than Material's, the way the reference's cards are cut. */
val SellerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

/*
 * The shapes the screens are actually built out of.
 *
 * Tighter than the capsules this app used to wear. The reference cuts its cards
 * at a modest radius and its buttons as soft rectangles, and a capsule-shaped
 * "Save Product" beside a rectangular text field reads as two design systems.
 */

/** An order, a product row, a panel — anything that holds a decision. */
val CardShape = RoundedCornerShape(14.dp)

/** A figure or a shortcut tile. A little tighter than a card. */
val TileShape = RoundedCornerShape(12.dp)

/** A screen's opening panel, rounded only where it meets what is below it. */
val HeroShape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp)

/** Buttons and text fields. */
val ButtonShape = RoundedCornerShape(10.dp)

/** Chips, badges, status words. A capsule at any height. */
val PillShape = RoundedCornerShape(percent = 50)

/*
 * Terracotta is `primary`, so every Material control that reaches for the
 * accent on its own — a TextButton, a spinner, a focused field — lands on the
 * same colour as the buttons the screens draw by hand. The forest is
 * `secondary`: the frame, not the thing to press.
 */
private val LightScheme = lightColorScheme(
    primary = CtaLight,
    onPrimary = OnCtaLight,
    primaryContainer = PeachLight,
    onPrimaryContainer = TextPrimaryLight,
    secondary = ForestLight,
    onSecondary = OnForestLight,
    secondaryContainer = PeachLight,
    onSecondaryContainer = TextPrimaryLight,
    tertiary = WarningLight,
    onTertiary = OnCtaLight,
    background = BgBaseLight,
    onBackground = TextPrimaryLight,
    surface = BgElevatedLight,
    onSurface = TextPrimaryLight,
    surfaceVariant = FillLight,
    onSurfaceVariant = TextSecondaryLight,
    surfaceContainer = BgElevatedLight,
    surfaceContainerHigh = BgElevatedLight,
    surfaceContainerHighest = FillLight,
    surfaceContainerLow = BgBaseLight,
    surfaceContainerLowest = BgElevatedLight,
    outline = SeparatorLight,
    outlineVariant = SeparatorLight,
    error = DangerLight,
    onError = OnCtaLight,
)

private val DarkScheme = darkColorScheme(
    primary = CtaDark,
    onPrimary = OnCtaDark,
    primaryContainer = PeachDark,
    onPrimaryContainer = TextPrimaryDark,
    secondary = ForestDark,
    onSecondary = OnForestDark,
    secondaryContainer = PeachDark,
    onSecondaryContainer = TextPrimaryDark,
    tertiary = WarningDark,
    onTertiary = OnCtaDark,
    background = BgBaseDark,
    onBackground = TextPrimaryDark,
    surface = BgElevatedDark,
    onSurface = TextPrimaryDark,
    surfaceVariant = FillDark,
    onSurfaceVariant = TextSecondaryDark,
    surfaceContainer = BgElevatedDark,
    surfaceContainerHigh = BgElevatedDark,
    surfaceContainerHighest = FillDark,
    surfaceContainerLow = BgBaseDark,
    surfaceContainerLowest = BgBaseDark,
    outline = SeparatorDark,
    outlineVariant = SeparatorDark,
    error = DangerDark,
    onError = BgBaseDark,
)

/**
 * No dynamic colour, for the same reason :app refuses it: the palette is the
 * brand's, and handing it to the phone's wallpaper would trade away the one
 * thing that says this is the same market as the shopper's app.
 *
 * Dark theme is followed rather than forced. A phone in a shop is read at six
 * in the morning and at ten at night, and the person holding it has already
 * told the phone which they want.
 */
@Composable
fun SellerTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSellerColors provides if (darkTheme) DarkSellerColors else LightSellerColors,
    ) {
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
