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

/**
 * --radius-sm through --radius-xl, with the top of the scale opened up.
 *
 * `large` is the card radius and it is the one that moved: 16dp reads as a
 * rounded rectangle, 20dp reads as a tile, and every job on the board is a
 * tile. `extraLarge` is the canopy's bottom corners and the one sheet in the
 * app, where the curve is meant to be seen.
 */
val RiderShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/**
 * A shape Material's five slots have no room for.
 *
 * Every button a rider actually presses is this one. A full-width capsule is
 * harder to miss with a thumb at a junction than a 14dp rectangle of the same
 * height, and it is what separates "press this" from the fields and cards it
 * sits under.
 */
val PillShape = RoundedCornerShape(percent = 50)

private val LightScheme = lightColorScheme(
    primary = AccentLight,
    onPrimary = AccentTextOnLight,
    primaryContainer = AccentSoftLight,
    onPrimaryContainer = AccentLight,
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
    primaryContainer = AccentSoftDark,
    onPrimaryContainer = AccentDark,
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
 * themselves light). A rider reads this screen in the dark more than anyone
 * else on the platform — a phone on a handlebar mount at ten at night — and
 * they have already told the phone which they want.
 */
@Composable
fun RiderTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val extended = if (darkTheme) {
        RiderColors(
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
            cash = WarningDark,
            canopy = CanopyDark,
            canopyBright = CanopyBrightDark,
            onCanopy = OnCanopyDark,
            onCanopyMuted = OnCanopyMutedDark,
            canopyFill = CanopyFillDark,
            payout = PayoutDark,
            owed = OwedDark,
            accentSoft = AccentSoftDark,
            cashSoft = CashSoftDark,
            successSoft = SuccessSoftDark,
            dangerSoft = DangerSoftDark,
            hairline = HairlineDark,
        )
    } else {
        RiderColors(
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
            cash = WarningLight,
            canopy = CanopyLight,
            canopyBright = CanopyBrightLight,
            onCanopy = OnCanopyLight,
            onCanopyMuted = OnCanopyMutedLight,
            canopyFill = CanopyFillLight,
            payout = PayoutLight,
            owed = OwedLight,
            accentSoft = AccentSoftLight,
            cashSoft = CashSoftLight,
            successSoft = SuccessSoftLight,
            dangerSoft = DangerSoftLight,
            hairline = HairlineLight,
        )
    }

    CompositionLocalProvider(LocalRiderColors provides extended) {
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
