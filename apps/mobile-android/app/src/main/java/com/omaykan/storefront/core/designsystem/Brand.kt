package com.omaykan.storefront.core.designsystem

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/*
 * The pieces the highland redesign is built from, shared so a header or a
 * button is the same control on every screen rather than five that merely look
 * alike. See documentation/design/customer-refresh/README.md.
 */

/**
 * The forest bar a screen opens with: a way back, a centred title, and room
 * on the right for one or two actions.
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
    val colors = OmaykanTheme.colors

    Box(
        modifier
            .fillMaxWidth()
            .background(colors.forest)
            .statusBarsPadding()
            .height(58.dp)
            .padding(horizontal = 4.dp),
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    Icons.Filled.ArrowBackIosNew,
                    contentDescription = "Back",
                    tint = colors.onForest,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(horizontal = 56.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontSize = 18.sp,
                color = colors.onForest,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onForestMuted,
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

/**
 * "OMAYKAN" in inscriptional capitals.
 *
 * Set type rather than the logo bitmap, because the redesign's lockup is
 * spaced capitals on forest green and the bitmap is the lowercase Nunito mark.
 * Read aloud as the name, not spelled out letter by letter.
 */
@Composable
fun Wordmark(
    modifier: Modifier = Modifier,
    color: Color = OmaykanTheme.colors.onForest,
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
 * The mark: three terraced peaks with the sun coming up behind the tallest.
 *
 * Drawn rather than shipped as a bitmap so it is sharp at splash size and at
 * the size of a quotation glyph alike. Give it roughly a 3:2 box.
 */
@Composable
fun MountainMark(
    modifier: Modifier = Modifier,
    mountain: Color = OmaykanTheme.colors.forest,
    line: Color = OmaykanTheme.colors.onForest,
    sun: Color = OmaykanTheme.colors.gold,
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

    // The terraces: level lines clipped to the slope, each a little tilted so
    // the flanks read as steps cut into a hillside rather than a ruled page.
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
 * Layered ranges and a few pines, with two lines of spaced capitals across
 * them. The quiet footer under the cart total.
 *
 * Tinted from the text tokens rather than fixed greys, so it sits on the page
 * in both themes without a second set of colours to maintain.
 */
@Composable
fun MountainScene(
    lines: List<String>,
    modifier: Modifier = Modifier,
) {
    val far = OmaykanTheme.colors.textTertiary.copy(alpha = 0.26f)
    val mid = OmaykanTheme.colors.textTertiary.copy(alpha = 0.52f)
    val near = OmaykanTheme.colors.textSecondary.copy(alpha = 0.62f)
    val trees = OmaykanTheme.colors.textSecondary.copy(alpha = 0.85f)

    Box(modifier.height(170.dp).fillMaxWidth()) {
        Canvas(Modifier.matchParentSize().clearAndSetSemantics { }) {
            range(
                listOf(
                    0f to .52f, .10f to .36f, .20f to .46f, .36f to .14f, .50f to .40f,
                    .64f to .26f, .78f to .42f, .90f to .30f, 1f to .44f,
                ),
                far,
            )
            range(
                listOf(0f to .66f, .14f to .50f, .30f to .64f, .46f to .38f, .60f to .58f, .78f to .46f, 1f to .62f),
                mid,
            )
            range(
                listOf(0f to .84f, .20f to .70f, .40f to .80f, .62f to .66f, .82f to .78f, 1f to .70f),
                near,
            )
            listOf(.05f to .30f, .10f to .22f, .15f to .26f, .83f to .24f, .89f to .32f, .95f to .22f)
                .forEach { (x, tall) -> pine(x * size.width, size.height, size.height * tall, trees) }
        }

        Column(
            Modifier
                .align(Alignment.Center)
                .padding(top = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            lines.forEach { text ->
                Text(
                    text = text.uppercase(),
                    style = SpacedCaps.copy(
                        fontWeight = FontWeight.SemiBold,
                        shadow = Shadow(Color.Black.copy(alpha = 0.28f), blurRadius = 8f),
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

private fun DrawScope.range(points: List<Pair<Float, Float>>, color: Color) {
    val path = Path().apply {
        moveTo(0f, size.height)
        points.forEach { (x, y) -> lineTo(x * size.width, y * size.height) }
        lineTo(size.width, size.height)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.pine(centerX: Float, baseY: Float, tall: Float, color: Color) {
    repeat(3) { tier ->
        val top = baseY - tall + tier * tall * 0.24f
        val bottom = top + tall * 0.42f
        val spread = tall * (0.16f + tier * 0.07f)
        drawPath(
            Path().apply {
                moveTo(centerX, top)
                lineTo(centerX - spread, bottom)
                lineTo(centerX + spread, bottom)
                close()
            },
            color,
        )
    }
    drawRect(
        color,
        topLeft = Offset(centerX - tall * 0.03f, baseY - tall * 0.14f),
        size = Size(tall * 0.06f, tall * 0.14f),
    )
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
 * The terracotta button — the one thing on a screen that moves the order
 * forward. A screen with two of these is a screen that has not decided.
 */
@Composable
fun CtaButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    icon: ImageVector? = null,
    shape: Shape = RoundedCornerShape(10.dp),
) {
    val colors = OmaykanTheme.colors

    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        shape = shape,
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.cta,
            contentColor = colors.onCta,
            disabledContainerColor = colors.cta.copy(alpha = 0.4f),
            disabledContentColor = colors.onCta.copy(alpha = 0.85f),
        ),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        modifier = modifier.heightIn(min = 50.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = colors.onCta,
            )
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(10.dp))
            }
            Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** The boxed "−  1  +" of the reference, for a product page or a cart line. */
@Composable
fun QuantityBox(
    quantity: Double,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val shape = RoundedCornerShape(8.dp)
    val tall = if (compact) 36.dp else 50.dp
    val button = if (compact) 32.dp else 44.dp

    Row(
        modifier
            .height(tall)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, OmaykanTheme.colors.separator, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StepButton(Icons.Filled.Remove, "One fewer", onDecrement, button)
        Text(
            text = if (quantity % 1.0 == 0.0) quantity.toInt().toString() else quantity.toString(),
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(min = if (compact) 26.dp else 34.dp),
        )
        StepButton(Icons.Filled.Add, "One more", onIncrement, button)
    }
}

@Composable
private fun StepButton(icon: ImageVector, description: String, onClick: () -> Unit, width: Dp) {
    Box(
        Modifier
            .width(width)
            .fillMaxHeight()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = OmaykanTheme.colors.ink,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** The heart on a card, a product or a cart line. Filled terracotta once saved. */
@Composable
fun HeartToggle(
    saved: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 20.dp,
    idleTint: Color = OmaykanTheme.colors.ink,
) {
    Icon(
        imageVector = if (saved) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
        contentDescription = if (saved) "Remove from saved" else "Save for later",
        tint = if (saved) OmaykanTheme.colors.cta else idleTint,
        modifier = modifier
            .clip(CircleShape)
            .clickable(onClick = onToggle)
            .padding(6.dp)
            .size(size),
    )
}

/** "Featured Products ········ See All". */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = OmaykanTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Text(
                text = actionLabel,
                style = MaterialTheme.typography.bodyMedium,
                color = OmaykanTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

/**
 * One fold of the product page's "Product Details / Delivery / Sold by" list:
 * a hairline, a title with a chevron, and whatever opens under it.
 */
@Composable
fun AccordionRow(
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val turn by animateFloatAsState(if (expanded) 180f else 0f, label = "accordion-chevron")

    Column(modifier.fillMaxWidth()) {
        HorizontalDivider(color = OmaykanTheme.colors.separator)
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Collapse" else "Expand") {
                    expanded = !expanded
                }
                .padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = OmaykanTheme.colors.ink,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = OmaykanTheme.colors.textSecondary,
                modifier = Modifier.rotate(turn),
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                Modifier.padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/**
 * The white, softly lifted card the reference sets everything on.
 *
 * The shadow alone used to be the whole answer, and on cream it was: a
 * hairline there reads as a box drawn around the content, where a shadow reads
 * as the content resting on the page. The page is white now, and a white card
 * on a white page has nothing but 3dp of warm shadow to mark its edge — at
 * three columns, with the gutters down to 8dp, the shelf read as photos
 * floating on nothing. So the hairline is back, under the shadow rather than
 * instead of it.
 */
@Composable
fun Modifier.softCard(shape: Shape = RoundedCornerShape(10.dp)): Modifier =
    this
        .shadow(
            elevation = 3.dp,
            shape = shape,
            ambientColor = CardShadow,
            spotColor = CardShadow,
        )
        .background(MaterialTheme.colorScheme.surface, shape)
        .border(1.dp, OmaykanTheme.colors.separator, shape)
        .clip(shape)

private val CardShadow = Color(0x553A2A1E)

/**
 * The white search pill that sits on a forest header — Home's and a shop's.
 *
 * A bare text field rather than Material's outlined one, which has a 56dp
 * floor and a floating label; the reference's pill is shorter and says what
 * to type in place.
 */
@Composable
fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search for products…",
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = OmaykanTheme.colors.ink,
            modifier = Modifier.size(22.dp),
        )
        Box(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            if (query.isEmpty()) {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = OmaykanTheme.colors.textTertiary,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = OmaykanTheme.colors.ink),
                cursorBrush = SolidColor(OmaykanTheme.colors.cta),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = OmaykanTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onQueryChange("") }
                    .padding(8.dp)
                    .size(18.dp),
            )
        }
    }
}
