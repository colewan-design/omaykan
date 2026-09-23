package com.omaykan.rider.core.designsystem

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
val RiderShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

/*
 * The shapes the screens are actually built out of — the same three :seller
 * uses, so a card is cut the same way on both sides of an order.
 */

/** A job, a figure, a panel — anything that holds a decision. */
val CardShape = RoundedCornerShape(14.dp)

/** Buttons and text fields. A soft rectangle, not a capsule. */
val ButtonShape = RoundedCornerShape(10.dp)

/** Chips, badges, status words, discs. A capsule at any height. */
val PillShape = RoundedCornerShape(percent = 50)

/*
 * The green accent is `primary`, not terracotta, and that is where this app
 * parts from :seller. Here `primary` is what every stock control reaches for on
 * its own — the checked Switch, a spinner, a TextButton — and in a rider's app
 * those are all *state*: sharing is on, the job is loading. Terracotta is kept
 * for the one button a screen wants pressed, which the screens draw by hand
 * through PrimaryButton. The forest is `secondary`: the frame.
 */
private val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = OnAccentLight,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = AccentLight,
    secondary = ForestLight,
    onSecondary = OnForestLight,
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
    primary = AccentDark,
    onPrimary = OnAccentDark,
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentDark,
    secondary = ForestDark,
    onSecondary = OnForestDark,
    tertiary = WarningDark,
    onTertiary = OnAccentDark,
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
 * thing that says this is the same market as the till on the counter.
 *
 * Dark theme is followed rather than forced, unlike the web portals (which pin
 * themselves light). A rider reads this screen in the dark more than anyone
 * else on the platform — a phone on a handlebar mount at ten at night — and
 * they have already told the phone which they want.
 */
@Composable
fun RiderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalRiderColors provides if (darkTheme) DarkRiderColors else LightRiderColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkScheme else LightScheme,
            typography = RiderTypography,
            shapes = RiderShapes,
            content = content,
        )
    }
}

/** `RiderTheme.colors.textSecondary` — the tokens Material has no slot for. */
object RiderTheme {
    val colors: RiderColors
        @Composable get() = LocalRiderColors.current
}
