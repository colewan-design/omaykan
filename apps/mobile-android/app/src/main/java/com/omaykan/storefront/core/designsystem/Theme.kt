package com.omaykan.storefront.core.designsystem

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
val OmaykanShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

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
 * No dynamic colour. The palette is the brand's, and handing it to the phone's
 * wallpaper would trade the one thing that makes every screen read as the same
 * market for novelty.
 */
@Composable
fun OmaykanTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalOmaykanColors provides if (darkTheme) DarkExtended else LightExtended,
    ) {
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
