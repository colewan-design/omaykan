package com.omaykan.seller.core.designsystem

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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/*
 * The pieces every screen in this app is assembled from.
 *
 * They exist so the redesign is one decision made once rather than the same
 * decision made slightly differently on four screens. A card's corner, the
 * weight of a shadow, how a status is coloured — get those wrong in one place
 * here and the whole app is wrong together, which is far easier to see and to
 * fix than three screens quietly drifting apart.
 */

/**
 * The surface everything sits on: white, softly rounded, barely lifted.
 *
 * The shadow is deliberately small. This is a list of cards on a tinted ground
 * — the separation is already done by the colour change, and a heavy shadow
 * under every row turns a day's orders into a pile of receipts.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .shadow(
            elevation = 8.dp,
            shape = CardShape,
            clip = false,
            ambientColor = SellerTheme.colors.shadow,
            spotColor = SellerTheme.colors.shadow,
        )
        .clip(CardShape)
        .background(MaterialTheme.colorScheme.surface)

    Column(if (onClick != null) base.clickable(onClick = onClick) else base, content = content)
}

/**
 * One figure, in a tile.
 *
 * Label above, number below, unit trailing the number in small grey. The unit
 * rides at the baseline of the value rather than joining it, because "37" is
 * the thing being read and "orders" is only there to say what 37 is.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    caption: String? = null,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    badge: String? = null,
    badgeColor: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier
            .shadow(
                elevation = 10.dp,
                shape = TileShape,
                clip = false,
                ambientColor = SellerTheme.colors.shadow,
                spotColor = SellerTheme.colors.shadow,
            )
            .clip(TileShape)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            badge?.let {
                Spacer(Modifier.width(6.dp))
                Pill(text = it, tint = badgeColor, dense = true)
            }
        }

        Row(
            Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineMedium,
                color = valueColor,
                maxLines = 1,
                // A six-figure day is wider than half a phone. Ellipsised
                // rather than clipped, so a merchant can see that the number
                // has been cut rather than misread a shortened one.
                overflow = TextOverflow.Ellipsis,
            )
            unit?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.textTertiary,
                    modifier = Modifier.padding(start = 4.dp, bottom = 3.dp),
                )
            }
        }

        caption?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.textTertiary,
                modifier = Modifier.padding(top = 6.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A word with a colour: a status, a stage, a warning.
 *
 * Tinted ground rather than a solid one, so a card carrying three of these
 * still reads as a card and not as a row of buttons. The one exception is
 * [solid], which is for the count that has to be seen from across a counter.
 */
@Composable
fun Pill(
    text: String,
    tint: Color,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    dense: Boolean = false,
    leading: ImageVector? = null,
) {
    Row(
        modifier
            .clip(PillShape)
            .background(if (solid) tint else tint.copy(alpha = 0.14f))
            .padding(
                horizontal = if (dense) 7.dp else 10.dp,
                vertical = if (dense) 2.dp else 5.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(13.dp),
                tint = if (solid) MaterialTheme.colorScheme.surface else tint,
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = if (solid) MaterialTheme.colorScheme.surface else tint,
            maxLines = 1,
        )
    }
}

/**
 * An icon in a circle, for the two or three controls that live in a header.
 *
 * A bare icon on the dark header would be a 24dp target floating in a lot of
 * green; the circle is what makes it look pressable and gives the thumb an
 * edge to aim at.
 */
@Composable
fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    background: Color = MaterialTheme.colorScheme.surface,
    size: Int = 40,
) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size((size * 0.5f).dp),
        )
    }
}

/**
 * A round ground with one or two letters in it.
 *
 * Stands in for the photograph the reference designs put here. This app shows
 * no pictures — there is nothing to photograph on the seller's side of an
 * order — so the shop's own initial does the job of being the thing your eye
 * lands on first.
 */
@Composable
fun InitialAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 44,
    background: Color = MaterialTheme.colorScheme.primary,
    foreground: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(background),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text.trim().take(1).uppercase().ifBlank { "•" },
            style = MaterialTheme.typography.titleLarge,
            color = foreground,
        )
    }
}

/** One tab: what it says, and the number of things waiting behind it. */
data class TabItem(val label: String, val badge: Int? = null)

/**
 * Two tabs, underlined.
 *
 * Underline rather than the chips this screen used to carry, because chips
 * read as filters you can turn on and off in combination and these are two
 * views of one list. The bar under the selected one is the whole affordance,
 * and it moves the moment you tap — which is what tells a merchant the list
 * below has changed rather than merely reloaded.
 */
@Composable
fun SegmentedTabs(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth()) {
        tabs.forEachIndexed { index, tab ->
            val active = index == selected
            Column(
                Modifier
                    .weight(1f)
                    .clip(MaterialTheme.shapes.small)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = tab.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (active) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            SellerTheme.colors.textTertiary
                        },
                    )
                    // A count only where there is something to count. A "0"
                    // beside a tab is a badge that trains people to ignore
                    // badges.
                    tab.badge?.takeIf { it > 0 }?.let {
                        Spacer(Modifier.width(6.dp))
                        Pill(
                            text = it.toString(),
                            tint = MaterialTheme.colorScheme.primary,
                            solid = active,
                            dense = true,
                        )
                    }
                }

                Spacer(Modifier.height(8.dp))

                Box(
                    Modifier
                        .width(if (active) 28.dp else 0.dp)
                        .height(3.dp)
                        .clip(PillShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}

/**
 * How far along something is, as a row of bars.
 *
 * The reference designs draw this as a vertical timeline with a dot per step,
 * which is right for a customer watching one order and wrong for a shop
 * scanning twenty cards: it costs four lines of height each. Flattened to a
 * rail, it says the same thing — where we are, how much is left — in the space
 * of one.
 */
@Composable
fun StageTrack(
    steps: Int,
    current: Int,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(steps) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(4.dp)
                    .clip(PillShape)
                    .background(
                        if (index <= current) tint else SellerTheme.colors.fill,
                    ),
            )
        }
    }
}

/**
 * The button that does the thing.
 *
 * A capsule, filled with the brand green, and it holds its own busy state:
 * every one of these in this app fires a network write, and a button that
 * looks identical while a request is in flight is a button that gets pressed
 * twice.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    /** 50 on a sheet, 44 in a row of card actions beside a [SecondaryButton]. */
    height: Int = 50,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.height(height.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
        ),
        contentPadding = PaddingValues(horizontal = 20.dp),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                strokeWidth = 2.dp,
                color = content,
            )
        } else {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke(this)
        }
    }
}

/**
 * The same capsule, hollow.
 *
 * For everything beside the main action on a card. Bordered rather than
 * tinted, so that a card with a green button and two of these has exactly one
 * place your eye is pulled to.
 */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
) {
    Row(
        modifier
            .height(44.dp)
            .clip(PillShape)
            .border(1.dp, SellerTheme.colors.separator, PillShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Centred, so the same button works both hugging its label in a row of
        // card actions and stretched to the full width of a sheet.
        horizontalArrangement = Arrangement.Center,
    ) {
        val foreground = if (enabled) {
            MaterialTheme.colorScheme.onSurface
        } else {
            SellerTheme.colors.textTertiary
        }

        leading?.let {
            Icon(
                imageVector = it,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = foreground,
            )
            Spacer(Modifier.width(6.dp))
        }

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = foreground,
            maxLines = 1,
        )
    }
}

/** A small grey heading over a group. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.SemiBold,
        color = SellerTheme.colors.textTertiary,
        modifier = modifier,
    )
}
