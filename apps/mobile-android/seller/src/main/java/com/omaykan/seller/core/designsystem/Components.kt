package com.omaykan.seller.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/*
 * The pieces every screen in this app is assembled from.
 *
 * They exist so the redesign is one decision made once rather than the same
 * decision made slightly differently on eight screens. A card's corner, the
 * weight of a shadow, how a status is coloured — get those wrong in one place
 * here and the whole app is wrong together, which is far easier to see and to
 * fix than screens quietly drifting apart.
 */

/**
 * The white, softly lifted card the reference sets everything on.
 *
 * A shadow rather than a border: on cream, a hairline reads as a box drawn
 * around the content, and a shadow reads as the content resting on the page.
 * Kept small, because a day's orders under heavy shadows is a pile of receipts.
 */
@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val base = modifier
        .shadow(
            elevation = 3.dp,
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
 * An icon in a circle, for the controls that live in a header or on a card.
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
 * A round ground with a letter in it.
 *
 * Stands in for the portrait the reference puts beside a shop or a customer.
 * The app has no photograph of either — the platform stores none of customers,
 * and a shop without an uploaded photo has none of itself — and a stock face
 * in its place would be a stranger on every order.
 */
@Composable
fun InitialAvatar(
    text: String,
    modifier: Modifier = Modifier,
    size: Int = 44,
    background: Color = SellerTheme.colors.accentSoft,
    foreground: Color = SellerTheme.colors.onAccentSoft,
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
            fontSize = (size * 0.42f).sp,
            color = foreground,
        )
    }
}

/** One tab: what it says, and how many things are behind it. */
data class TabItem(val label: String, val count: Int? = null)

/**
 * A row of filter chips — "All (28)", "Preparing (5)" — the reference's way of
 * cutting one list several ways.
 *
 * The count is part of the label rather than a badge beside it: these are
 * read as a sentence ("five preparing"), not scanned for a red dot. It scrolls
 * sideways rather than wrapping, so a narrow phone keeps the list below it
 * where the thumb expects it.
 */
@Composable
fun ChipTabs(
    tabs: List<TabItem>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * Share the row out evenly instead of scrolling — the products screen's
     * three filters, which the reference draws as one full-width band.
     * Only for a set short enough to fit.
     */
    equalWidth: Boolean = false,
    /**
     * Fill the chosen chip solid terracotta with white words, as the
     * reference's inbox does, instead of the tinted ground the list filters
     * use. For a two- or three-way switch where "which one am I on" is the
     * whole point.
     */
    filledSelected: Boolean = false,
) {
    Row(
        if (equalWidth) modifier.fillMaxWidth() else modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tabs.forEachIndexed { index, tab ->
            val active = index == selected
            val tint = MaterialTheme.colorScheme.primary
            val activeGround = if (filledSelected) tint else tint.copy(alpha = 0.12f)
            val activeText = if (filledSelected) MaterialTheme.colorScheme.onPrimary else tint
            Box(
                (if (equalWidth) Modifier.weight(1f) else Modifier)
                    .clip(PillShape)
                    .background(if (active) activeGround else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (active) tint else SellerTheme.colors.separator, PillShape)
                    .selectable(selected = active, role = Role.Tab) { onSelect(index) }
                    .padding(horizontal = if (equalWidth) 6.dp else 14.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = tab.count?.let { "${tab.label} ($it)" } ?: tab.label,
                    style = if (equalWidth) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (active) activeText else SellerTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/**
 * How far along something is, as a row of bars.
 *
 * Flattened from the vertical timeline a customer sees, because a shop scans
 * twenty of these and a four-line timeline per card would cost the list.
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
 * The terracotta button — the thing a screen wants pressed.
 *
 * It holds its own busy state: every one of these in this app fires a network
 * write, and a button that looks identical while a request is in flight is a
 * button that gets pressed twice.
 */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    /** 50 on a form or a sheet, 44 in a row of card actions. */
    height: Int = 50,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier.height(height.dp),
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = container.copy(alpha = 0.4f),
            disabledContentColor = content.copy(alpha = 0.85f),
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
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing?.invoke(this)
        }
    }
}

/**
 * The same button, hollow.
 *
 * For everything beside the main action. Bordered rather than tinted, so a
 * card with one terracotta button and two of these has exactly one place your
 * eye is pulled to.
 */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    height: Int = 44,
) {
    Row(
        modifier
            .height(height.dp)
            .clip(ButtonShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, SellerTheme.colors.separator, ButtonShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        // Centred, so the same button works both hugging its label in a row of
        // card actions and stretched to the full width of a form.
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

/** "Top Products ········ See All ›". */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = SellerTheme.colors.ink,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onAction)
                    .padding(start = 6.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/** The forest switch: "on" is the frame colour, never the button colour. */
@Composable
fun SellerSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val colors = SellerTheme.colors
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        modifier = modifier,
        colors = SwitchDefaults.colors(
            checkedThumbColor = Color.White,
            checkedTrackColor = colors.canopy,
            checkedBorderColor = colors.canopy,
            uncheckedThumbColor = colors.textTertiary,
            uncheckedTrackColor = colors.fill,
            uncheckedBorderColor = colors.separator,
        ),
    )
}

/**
 * One line of a settings-style list: an icon, what it is, a line under it,
 * and either a chevron (it opens something) or whatever [trailing] draws.
 */
@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: (() -> Unit)? = null,
    tint: Color = SellerTheme.colors.ink,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
        Column(
            Modifier
                .weight(1f)
                .padding(start = 14.dp, end = 8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = if (tint == SellerTheme.colors.ink) MaterialTheme.colorScheme.onSurface else tint,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = SellerTheme.colors.textTertiary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = SellerTheme.colors.textTertiary,
            )
        }
    }
}

/**
 * A shortcut on the home screen: a terracotta square with an icon in it, and
 * the name of where it goes underneath.
 */
@Composable
fun QuickTile(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int = 0,
) {
    SoftCard(modifier, onClick = onClick) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp),
                    )
                }
                if (count > 0) {
                    CountBadge(
                        count,
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(start = 30.dp),
                        ground = SellerTheme.colors.canopy,
                    )
                }
            }
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/** A small round count — unread messages, waiting orders. "99+" past that. */
@Composable
fun CountBadge(
    count: Int,
    modifier: Modifier = Modifier,
    ground: Color = MaterialTheme.colorScheme.primary,
) {
    Box(
        modifier
            .defaultMinSize(minWidth = 18.dp, minHeight = 18.dp)
            .clip(PillShape)
            .background(ground)
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = if (count > 99) "99+" else count.toString(),
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/**
 * A labelled text field, the way the product form is laid out: the label
 * above the box rather than floating inside it, and a terracotta star when
 * the field is required.
 */
@Composable
fun FormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    placeholder: String? = null,
    enabled: Boolean = true,
    error: String? = null,
    supporting: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    prefix: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    Column(modifier) {
        Row {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (required) {
                Text(
                    text = " *",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            enabled = enabled,
            singleLine = singleLine,
            minLines = minLines,
            isError = error != null,
            placeholder = placeholder?.let { { Text(it) } },
            prefix = prefix?.let { { Text(it) } },
            supportingText = (error ?: supporting)?.let { { Text(it) } },
            shape = ButtonShape,
            keyboardOptions = keyboardOptions,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = SellerTheme.colors.separator,
                focusedContainerColor = MaterialTheme.colorScheme.surface,
                unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                disabledContainerColor = SellerTheme.colors.fill,
            ),
        )
    }
}

/** The bordered search box at the top of a list. */
@Composable
fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search",
) {
    val keyboard = LocalSoftwareKeyboardController.current

    Row(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(ButtonShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, SellerTheme.colors.separator, ButtonShape)
            .padding(start = 14.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Search,
            contentDescription = null,
            tint = SellerTheme.colors.textSecondary,
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
                    color = SellerTheme.colors.textTertiary,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (query.isNotEmpty()) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Clear search",
                tint = SellerTheme.colors.textSecondary,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onQueryChange("") }
                    .padding(8.dp)
                    .size(18.dp),
            )
        }
    }
}

/**
 * A screen with nothing to show yet, said as a picture rather than a
 * paragraph: a peach circle, an icon, one line saying what is true and one
 * saying what will change it.
 *
 * A grey sentence alone in the middle of a screen reads as something that
 * failed to load; most of the empty states here have not failed.
 */
@Composable
fun ScreenMessage(
    icon: ImageVector,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(84.dp)
                .clip(CircleShape)
                .background(SellerTheme.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = SellerTheme.colors.onAccentSoft,
            )
        }

        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 20.dp),
        )

        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = SellerTheme.colors.textTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (actionLabel != null && onAction != null) {
            SecondaryButton(
                label = actionLabel,
                onClick = onAction,
                modifier = Modifier.padding(top = 20.dp),
            )
        }
    }
}
