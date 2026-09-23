package com.omaykan.rider.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import android.graphics.Matrix
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.omaykan.rider.R

/*
 * The brand pieces: the wordmark, the mark, the woven band, the forest bar.
 *
 * Ported from :seller's Brand.kt (itself ported from :app's) rather than shared
 * with it — see the README's "Nothing shared with :app or :seller". The drawing
 * code is the same so the three apps' marks are the same mark.
 */

/**
 * "OMAYKAN" in inscriptional capitals. Read aloud as the name, not spelled out
 * letter by letter.
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    color: Color = RiderTheme.colors.onCanopy,
    fontSize: TextUnit = 22.sp,
) {
    Text(
        text = "OMAYKAN",
        color = color,
        style = TextStyle(
            fontFamily = WordmarkFamily,
            fontWeight = FontWeight.Bold,
            fontSize = fontSize,
            letterSpacing = 0.14.em,
        ),
        maxLines = 1,
        modifier = modifier.clearAndSetSemantics { contentDescription = "Omaykan" },
    )
}

/**
 * "OMAYKAN Rider" — the wordmark with this app's name set after it in the
 * serif, in a brighter terracotta than the buttons use.
 *
 * Brighter because it only ever sits on the forest, where the button
 * terracotta goes muddy. It is a name, never a control, so borrowing the
 * control colour would be the wrong signal anyway.
 */
@Composable
fun RiderLockup(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    stacked: Boolean = false,
) {
    val rider = @Composable {
        Text(
            text = "Rider",
            color = LockupEmber,
            style = TextStyle(
                fontFamily = SerifFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = fontSize * (if (stacked) 1.15f else 0.95f),
            ),
            maxLines = 1,
        )
    }

    if (stacked) {
        Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
            Wordmark(fontSize = fontSize)
            rider()
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Wordmark(fontSize = fontSize)
            Box(Modifier.padding(start = 8.dp)) { rider() }
        }
    }
}

/** The name's terracotta, and the mark under the current tab. Only ever on forest. */
internal val LockupEmber = Color(0xFFE58A5C)

/**
 * The line the welcome screen and the home screen sign off with.
 *
 * "Craft. Culture. Livelihood." The reference's third word read "Kabuklan",
 * which is taken to be a slip for *kabuhayan* — the same correction :seller
 * made, so the two apps say the same thing. Confirm before release.
 */
const val SIGN_OFF = "Likha. Kultura. Kabuhayan."

/**
 * The mark: three terraced peaks with the sun coming up behind the tallest.
 * Give it roughly a 3:2 box.
 */
@Composable
fun MountainMark(
    modifier: Modifier = Modifier,
    mountain: Color = RiderTheme.colors.canopy,
    line: Color = RiderTheme.colors.onCanopy,
    sun: Color = RiderTheme.colors.gold,
) {
    Canvas(modifier.clearAndSetSemantics { }) {
        val w = size.width
        val h = size.height

        drawCircle(sun, radius = w * 0.14f, center = Offset(w * 0.5f, h * 0.27f))

        terracedPeak(0.02f, 0.27f, 0.50f, 0.54f, mountain, line)
        terracedPeak(0.46f, 0.73f, 0.50f, 0.98f, mountain, line)
        terracedPeak(0.18f, 0.50f, 0.26f, 0.82f, mountain, line)

        val base = h - h * 0.015f
        drawLine(line, Offset(0f, base), Offset(w, base), strokeWidth = h * 0.03f)
    }
}

private fun DrawScope.terracedPeak(
    leftX: Float,
    peakX: Float,
    peakY: Float,
    rightX: Float,
    fill: Color,
    line: Color,
) {
    val w = size.width
    val h = size.height
    val peak = Path().apply {
        moveTo(leftX * w, h)
        lineTo(peakX * w, peakY * h)
        lineTo(rightX * w, h)
        close()
    }

    drawPath(peak, fill)

    clipPath(peak) {
        val gap = h * 0.085f
        var y = peakY * h + gap
        var tilt = 1
        while (y < h) {
            drawLine(
                line,
                Offset(leftX * w, y + tilt * gap * 0.12f),
                Offset(rightX * w, y - tilt * gap * 0.12f),
                strokeWidth = h * 0.022f,
            )
            y += gap
            tilt = -tilt
        }
    }

    drawPath(peak, line, style = Stroke(width = h * 0.03f, join = StrokeJoin.Round))
}

/**
 * The highland photograph — rice terraces falling into a misty valley at
 * sunrise — that the home screen's greeting is set on (`hero_highland`).
 *
 * A picture of the region rather than of anybody's shop or anybody's face, so
 * it being the same on every phone is right for a backdrop. It sits under a
 * forest wash, heavier at the bottom where the words are, so white type over it
 * reads whatever part of the sky a phone's shape happens to crop to. Biased
 * left, because the terraces are on the left and the home header is close to
 * square, which crops the sides of a 3:2 photograph.
 */
@Composable
fun MountainBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val forest = RiderTheme.colors.canopy

    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.hero_highland),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(-0.3f, 0f),
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to forest.copy(alpha = 0.55f),
                        0.5f to forest.copy(alpha = 0.35f),
                        1f to forest.copy(alpha = 0.90f),
                    ),
                ),
        )
        content()
    }
}

/**
 * A strip of hand-loomed cloth: a border of small diamonds, a row of large
 * ones, and the border again (`weave_band`, cut from the pattern tile).
 *
 * The strip is scaled to the band's height and repeated sideways — the tile's
 * motif repeats every quarter of its width, so the seam never shows. Fixed
 * colours on purpose: it is a textile, and a textile does not change in the
 * dark.
 */
@Composable
fun WovenBand(modifier: Modifier = Modifier, height: Dp = 20.dp) {
    val weave = ImageBitmap.imageResource(R.drawable.weave_band)

    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { }
            .drawWithCache {
                val scale = size.height / weave.height
                val shader = ImageShader(weave, TileMode.Repeated, TileMode.Clamp).apply {
                    setLocalMatrix(Matrix().apply { setScale(scale, scale) })
                }
                val brush = ShaderBrush(shader)
                onDrawBehind { drawRect(brush) }
            },
    )
}

/** The loom's dark ground, for anything that has to butt up against a band. */
internal val WeaveGround = Color(0xFF2A1410)

/**
 * The forest bar a screen opens with: a way back when there is one, a centred
 * title, and room either side for an action.
 *
 * It paints under the status bar itself, which is why the screens using it do
 * not pad for the status bar on their own — a bar that stopped short of the
 * top edge would leave a cream strip above the green.
 */
@Composable
fun ForestTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = RiderTheme.colors
    LightStatusBarIcons()

    Box(
        modifier
            .fillMaxWidth()
            .background(colors.canopy)
            .statusBarsPadding()
            .height(58.dp)
            .padding(horizontal = 4.dp),
    ) {
        Box(Modifier.align(Alignment.CenterStart)) {
            when {
                onBack != null -> IconButton(onClick = onBack) {
                    Icon(
                        Icons.Filled.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = colors.onCanopy,
                        modifier = Modifier.size(20.dp),
                    )
                }

                leading != null -> leading()
            }
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 18.sp,
                color = colors.onCanopy,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onCanopyMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            Modifier.align(Alignment.CenterEnd),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}
