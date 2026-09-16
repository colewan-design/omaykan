package com.omaykan.seller.core.designsystem

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
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
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
import com.omaykan.seller.R

/*
 * The brand pieces: the wordmark, the mark, the woven band, the forest bar.
 *
 * Ported from :app's Brand.kt rather than shared with it — see the README's
 * "Nothing shared with :app". The drawing code is the same so the two apps'
 * marks are the same mark.
 */

/**
 * "OMAYKAN" in inscriptional capitals. Read aloud as the name, not spelled out
 * letter by letter.
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    color: Color = SellerTheme.colors.onCanopy,
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
 * "OMAYKAN Seller" — the wordmark with this app's name set after it in the
 * serif, in a brighter terracotta than the buttons use.
 *
 * Brighter because it only ever sits on the forest, where the button
 * terracotta goes muddy. It is a name, never a control, so borrowing the
 * control colour would be the wrong signal anyway.
 */
@Composable
fun SellerLockup(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 20.sp,
    stacked: Boolean = false,
) {
    val seller = @Composable {
        Text(
            text = "Seller",
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
            seller()
        }
    } else {
        Row(modifier, verticalAlignment = Alignment.CenterVertically) {
            Wordmark(fontSize = fontSize)
            Box(Modifier.padding(start = 8.dp)) { seller() }
        }
    }
}

private val LockupEmber = Color(0xFFE58A5C)

/**
 * The mark: three terraced peaks with the sun coming up behind the tallest.
 * Give it roughly a 3:2 box.
 */
@Composable
fun MountainMark(
    modifier: Modifier = Modifier,
    mountain: Color = SellerTheme.colors.canopy,
    line: Color = SellerTheme.colors.onCanopy,
    sun: Color = SellerTheme.colors.gold,
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
 * The highland photograph — misty ridges, pines and terraces at sunrise — that
 * the morning greeting and the shop profile's cover are set on.
 *
 * A picture of the region rather than of anybody's shop, so it being the same
 * on every phone is right for a backdrop. It sits under a forest wash, heavier
 * at the bottom where the greeting is, so white type over it reads whatever
 * part of the sky a phone's shape happens to crop to. Anchored a little above
 * centre so the ridgeline survives both the tall home header and the short
 * profile cover.
 */
@Composable
fun MountainBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val forest = SellerTheme.colors.canopy

    Box(modifier) {
        Image(
            painter = painterResource(R.drawable.highland_banner),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = BiasAlignment(0f, -0.2f),
            modifier = Modifier.matchParentSize(),
        )
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0f to forest.copy(alpha = 0.62f),
                        0.55f to forest.copy(alpha = 0.48f),
                        1f to forest.copy(alpha = 0.92f),
                    ),
                ),
        )
        content()
    }
}

/**
 * A strip of woven diamonds, the edge of a hand-loomed cloth. Fixed colours on
 * purpose: it is a textile, and a textile does not change in the dark.
 */
@Composable
fun WovenBand(modifier: Modifier = Modifier, height: Dp = 20.dp) {
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .clearAndSetSemantics { },
    ) {
        drawRect(WeaveGround)

        val h = size.height
        val step = h * 1.1f
        val mid = h / 2f
        val edge = h * 0.1f

        drawLine(WeaveGold, Offset(0f, edge), Offset(size.width, edge), strokeWidth = h * 0.06f)
        drawLine(WeaveGold, Offset(0f, h - edge), Offset(size.width, h - edge), strokeWidth = h * 0.06f)

        var x = step / 2f
        while (x < size.width + step) {
            diamond(x, mid, step * 0.46f, h * 0.32f, WeaveRed)
            diamond(x, mid, step * 0.16f, h * 0.12f, WeaveCream)
            x += step
        }
    }
}

private fun DrawScope.diamond(cx: Float, cy: Float, halfWidth: Float, halfHeight: Float, color: Color) {
    drawPath(
        Path().apply {
            moveTo(cx, cy - halfHeight)
            lineTo(cx + halfWidth, cy)
            lineTo(cx, cy + halfHeight)
            lineTo(cx - halfWidth, cy)
            close()
        },
        color,
    )
}

private val WeaveGround = Color(0xFF3A1D16)
private val WeaveRed = Color(0xFFB23A2B)
private val WeaveCream = Color(0xFFF1E2C8)
private val WeaveGold = Color(0xFFD69A45)

/**
 * The forest bar a pushed screen opens with: a way back, a centred title, and
 * room on the right for an action or two.
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
    actions: @Composable RowScope.() -> Unit = {},
) {
    val colors = SellerTheme.colors

    Box(
        modifier
            .fillMaxWidth()
            .background(colors.canopy)
            .statusBarsPadding()
            .height(58.dp)
            .padding(horizontal = 4.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    Icons.Filled.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = colors.onCanopy,
                    modifier = Modifier.size(20.dp),
                )
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
                    color = colors.canopyMuted,
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
