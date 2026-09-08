package com.omaykan.rider.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.omaykan.rider.core.designsystem.RiderTheme
import kotlin.math.min

/*
 * The three pictures on the way in, drawn rather than shipped.
 *
 * ## Why they are code
 *
 * A raster illustration is one asset per density, it is a fixed colour scheme
 * on an app that follows the phone's, and it is the first thing to look wrong
 * when a token moves. These take their palette from [RiderTheme] on every
 * recomposition, so a dark-mode phone gets a dark-mode illustration and a
 * change to the brand green reaches them for free. They also weigh nothing,
 * which matters on an APK aimed at a cheap phone on a metered connection.
 *
 * ## The design space
 *
 * Every scene is drawn against a fixed 320 × 260 box and scaled to fit
 * whatever it is given, so the coordinates below can be read as a drawing
 * rather than as arithmetic. Nothing here is measured from the composable's
 * own size.
 */

private const val ArtWidth = 320f
private const val ArtHeight = 260f

/**
 * A scene, scaled into whatever space it is handed.
 *
 * The uniform scale is the point: fitting width and height separately would
 * turn the wheels into ellipses on a short screen, which is exactly the phone
 * where somebody first sees this.
 */
@Composable
private fun Scene(modifier: Modifier, draw: DrawScope.(palette: Palette) -> Unit) {
    val colors = RiderTheme.colors
    val palette = Palette(
        accent = MaterialTheme.colorScheme.primary,
        soft = colors.accentSoft,
        line = colors.textSecondary,
        ink = MaterialTheme.colorScheme.onBackground,
        surface = MaterialTheme.colorScheme.surface,
        cash = colors.cash,
        cashSoft = colors.cashSoft,
    )

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val factor = min(size.width / ArtWidth, size.height / ArtHeight)

            translate(
                left = (size.width - ArtWidth * factor) / 2f,
                top = (size.height - ArtHeight * factor) / 2f,
            ) {
                scale(factor, pivot = Offset.Zero) {
                    draw(palette)
                }
            }
        }
    }
}

/** The colours a scene is allowed to use. Nothing here picks its own. */
private data class Palette(
    val accent: Color,
    val soft: Color,
    val line: Color,
    val ink: Color,
    val surface: Color,
    val cash: Color,
    val cashSoft: Color,
)

/** The soft disc every scene stands on, so none of them float. */
private fun DrawScope.ground(palette: Palette) {
    drawCircle(color = palette.soft, radius = 118f, center = Offset(160f, 138f))
}

/**
 * A rider on a bicycle, which is what most of them are actually on.
 *
 * Not a scooter and not a van. The platform's riders ride what they own, in a
 * mountain city, and the first picture in the app should be somebody they
 * recognise rather than a stock courier on a moped.
 */
@Composable
fun BoardArt(modifier: Modifier = Modifier) = Scene(modifier) { palette ->
    ground(palette)

    val frame = Stroke(width = 7f, cap = StrokeCap.Round)
    val wheel = Stroke(width = 7f)

    // Wheels first, so the frame sits over the rims where they meet.
    drawCircle(palette.line, radius = 36f, center = Offset(96f, 200f), style = wheel)
    drawCircle(palette.line, radius = 36f, center = Offset(226f, 200f), style = wheel)

    val bicycle = Path().apply {
        moveTo(96f, 200f)
        lineTo(160f, 200f)   // chainstay to the bottom bracket
        lineTo(146f, 148f)   // seat tube
        lineTo(96f, 200f)    // and back down the rear triangle
        moveTo(146f, 148f)
        lineTo(200f, 146f)   // top tube
        lineTo(226f, 200f)   // down tube to the front hub
        moveTo(160f, 200f)
        lineTo(200f, 146f)   // the diagonal a real frame has
    }
    drawPath(bicycle, palette.accent, style = frame)

    // Handlebar and saddle: two short strokes that turn a triangle into a bike.
    drawLine(palette.accent, Offset(192f, 140f), Offset(214f, 136f), strokeWidth = 7f, cap = StrokeCap.Round)
    drawLine(palette.accent, Offset(134f, 144f), Offset(158f, 144f), strokeWidth = 7f, cap = StrokeCap.Round)

    // The rider: hip, shoulder, head — leaning forward, because somebody
    // upright on a bicycle is somebody who is not going anywhere.
    val body = Path().apply {
        moveTo(146f, 146f)
        lineTo(168f, 104f)   // torso
        moveTo(168f, 104f)
        lineTo(206f, 134f)   // arm to the bar
        moveTo(146f, 146f)
        lineTo(166f, 176f)   // thigh
        lineTo(152f, 200f)   // shin to the pedal
    }
    drawPath(body, palette.ink, style = Stroke(width = 9f, cap = StrokeCap.Round))

    drawCircle(palette.ink, radius = 16f, center = Offset(176f, 88f))

    // The bag on their back, in the brand green, because it is the one part of
    // this picture that is the platform rather than the person.
    drawRoundRect(
        color = palette.accent,
        topLeft = Offset(128f, 92f),
        size = Size(40f, 44f),
        cornerRadius = CornerRadius(10f, 10f),
    )
}

/**
 * A note, a coin, and nothing taken off either.
 *
 * Deliberately not a wallet or a bank card: this platform pays a rider in cash
 * at a door, and a picture of a balance to withdraw would promise a feature
 * that does not exist.
 */
@Composable
fun FeeArt(modifier: Modifier = Modifier) = Scene(modifier) { palette ->
    ground(palette)

    // Three notes fanned, the top one square on, so the stack reads as more
    // than one without any of them being ambiguous.
    drawRoundRect(
        color = palette.cashSoft,
        topLeft = Offset(72f, 118f),
        size = Size(176f, 84f),
        cornerRadius = CornerRadius(14f, 14f),
    )
    drawRoundRect(
        color = palette.surface,
        topLeft = Offset(84f, 104f),
        size = Size(176f, 84f),
        cornerRadius = CornerRadius(14f, 14f),
    )
    drawRoundRect(
        color = palette.cash,
        topLeft = Offset(96f, 90f),
        size = Size(176f, 84f),
        cornerRadius = CornerRadius(14f, 14f),
    )

    // The oval on a banknote, and two rules where the small print goes.
    drawCircle(
        color = palette.surface,
        radius = 22f,
        center = Offset(184f, 132f),
        style = Stroke(width = 6f),
    )
    drawLine(palette.surface, Offset(112f, 106f), Offset(146f, 106f), strokeWidth = 6f, cap = StrokeCap.Round)
    drawLine(palette.surface, Offset(222f, 158f), Offset(256f, 158f), strokeWidth = 6f, cap = StrokeCap.Round)

    // A coin, half behind the stack, in the brand green: the fee is the
    // platform's promise rather than the shop's change.
    drawCircle(palette.accent, radius = 30f, center = Offset(84f, 180f))
    drawCircle(
        color = palette.surface,
        radius = 16f,
        center = Offset(84f, 180f),
        style = Stroke(width = 6f),
    )
}

/**
 * Two ends of a trip and the road between them.
 *
 * The same rail every job card draws, at illustration size — so the shape a
 * rider meets on the way in is the shape they read all day.
 */
@Composable
fun RouteArt(modifier: Modifier = Modifier) = Scene(modifier) { palette ->
    ground(palette)

    val road = Path().apply {
        moveTo(84f, 194f)
        cubicTo(120f, 120f, 190f, 210f, 230f, 106f)
    }
    drawPath(
        path = road,
        color = palette.line,
        style = Stroke(
            width = 6f,
            cap = StrokeCap.Round,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 14f)),
        ),
    )

    // The shop: a squat building with an awning.
    drawRoundRect(
        color = palette.surface,
        topLeft = Offset(52f, 168f),
        size = Size(64f, 56f),
        cornerRadius = CornerRadius(10f, 10f),
    )
    drawRoundRect(
        color = palette.accent,
        topLeft = Offset(46f, 154f),
        size = Size(76f, 22f),
        cornerRadius = CornerRadius(8f, 8f),
    )

    // The door: a pin, which is what the customer is on every other screen.
    val pin = Path().apply {
        moveTo(230f, 118f)
        lineTo(212f, 82f)
        lineTo(248f, 82f)
        close()
    }
    drawPath(pin, palette.accent)
    drawCircle(palette.accent, radius = 26f, center = Offset(230f, 72f))
    drawCircle(palette.surface, radius = 11f, center = Offset(230f, 72f))
}

/** The height every scene is given. Enough to be a picture, not a banner. */
val ArtHeightDp = 260.dp

/** A scene at the standard size, full width. */
@Composable
fun OnboardingArt(page: Int, modifier: Modifier = Modifier) {
    val sized = modifier.fillMaxWidth()

    when (page) {
        0 -> BoardArt(sized)
        1 -> FeeArt(sized)
        else -> RouteArt(sized)
    }
}
