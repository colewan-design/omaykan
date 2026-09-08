package com.omaykan.rider.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/*
 * The kit every screen in this app is built out of.
 *
 * It exists because the four screens kept re-deciding the same six questions —
 * how round is a card, how tall is a button, what a small tinted badge looks
 * like — and answering them slightly differently each time. A rider moves
 * between all four in a minute; a card that changes shape between the board and
 * the status screen is the difference between one app and three.
 *
 * Nothing here knows about deliveries. Anything that does belongs in the
 * feature package that owns it.
 */

/** The standard press target: tall enough for a thumb, in a hurry, outdoors. */
private val ButtonHeight = 54.dp

/**
 * The tile everything sits on.
 *
 * A flat surface with a hairline rather than a shadow. Elevation in Material is
 * drawn as a tonal shift, which on this app's near-black dark ground turns a
 * card grey and washes out the one thing that has to stay legible — and a real
 * shadow under thirty scrolling cards is a cost paid every frame for a
 * separation the hairline already provides.
 */
@Composable
fun RiderCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surface,
    padding: Dp = 18.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = color,
        border = BorderStroke(1.dp, RiderTheme.colors.hairline),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/**
 * The one button on a screen that a rider is meant to press.
 *
 * Full width, capsule, and it carries its own spinner: every action in this app
 * is a network write against a shared board, and a button that looks identical
 * while the request is in the air is a button that gets pressed twice.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
        ),
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = content,
            )
        } else {
            Text(text, style = MaterialTheme.typography.titleMedium)
        }
    }
}

/**
 * The second-rank button: Navigate, Call, and the pair of them side by side.
 *
 * Outlined rather than filled, so that however many of these sit on a card,
 * there is never a question about which button finishes the job.
 */
@Composable
fun GhostButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(46.dp),
        shape = PillShape,
        border = BorderStroke(1.dp, RiderTheme.colors.separator),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(start = if (icon != null) 8.dp else 0.dp),
        )
    }
}

/**
 * A choice between two or three things, as capsules in a tray.
 *
 * Replaces the underlined tab row. A tab underline is a 2dp cue read at the
 * bottom edge of a word; a filled capsule is read as a shape, which is what a
 * glance is actually capable of. Each label carries its own count where there
 * is one, so "My jobs (2)" stays one thing to read rather than a word and a
 * badge that have to be assembled.
 */
@Composable
fun SegmentedPills(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(RiderTheme.colors.fill)
            .padding(4.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            // The `selected` overload rather than the plain clickable one:
            // it is what puts the selected state into the semantics tree, so
            // TalkBack says "selected" instead of leaving a rider to infer it
            // from a fill they cannot see.
            Surface(
                selected = selected,
                onClick = { onSelect(index) },
                modifier = Modifier
                    .weight(1f)
                    .height(40.dp),
                shape = PillShape,
                color = if (selected) {
                    MaterialTheme.colorScheme.surface
                } else {
                    Color.Transparent
                },
                contentColor = if (selected) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    RiderTheme.colors.textSecondary
                },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = label,
                        style = if (selected) {
                            MaterialTheme.typography.titleMedium
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                    )
                }
            }
        }
    }
}

/**
 * One fact, small, in a capsule: a distance, an item count, a ticket number.
 *
 * These are the details a rider checks after they have already decided from the
 * fee, so they group at the top right of a card where the eye lands second, and
 * they are sized so they never compete with the number they sit beside.
 */
@Composable
fun MetaPill(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    container: Color = RiderTheme.colors.fill,
    content: Color = RiderTheme.colors.textSecondary,
) {
    Row(
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = content,
            )
        }
        Text(text = text, style = MaterialTheme.typography.bodySmall, color = content)
    }
}

/**
 * A whole line that is trying to be noticed: the cash to collect at a door, or
 * the confirmation that there is none.
 *
 * Tinted ground rather than coloured text. Coloured text on white is a contrast
 * problem at small sizes in daylight; a soft ground carries the same meaning and
 * leaves the words themselves at full contrast.
 */
@Composable
fun SoftBanner(
    icon: ImageVector,
    text: String,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Every text field in the app.
 *
 * Filled rather than outlined, with the border kept for the focused state only.
 * A screen of outlined fields is a screen of rectangles competing with the cards
 * around them; a soft ground says "type here" without drawing a box, and the
 * green ring on focus is then the only rectangle on screen, which is exactly the
 * one a rider needs to find after the keyboard has covered half the form.
 *
 * The supporting line takes the error when there is one and the hint otherwise,
 * in that order, so a field is never both explaining itself and complaining at
 * the same time — and never changes height when it switches between the two.
 */
@Composable
fun RiderTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    error: String? = null,
    hint: String? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailingIcon: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        enabled = enabled,
        isError = error != null,
        supportingText = (error ?: hint)?.let { { Text(it) } },
        shape = MaterialTheme.shapes.medium,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        trailingIcon = trailingIcon,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = RiderTheme.colors.fill,
            unfocusedContainerColor = RiderTheme.colors.fill,
            disabledContainerColor = RiderTheme.colors.fill,
            errorContainerColor = RiderTheme.colors.fill,
            unfocusedBorderColor = Color.Transparent,
            disabledBorderColor = Color.Transparent,
        ),
    )
}

/** An icon in a tinted disc. The shop, the door, the state of an application. */
@Composable
fun IconBadge(
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(PillShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(size * 0.5f),
        )
    }
}

/**
 * The green block at the top of every screen.
 *
 * Every screen opens with it, and that is the navigation model made visible:
 * the tab bar says which of four screens this is, and the block is what says
 * the four are one app. It paints under the status bar and pads itself out from
 * underneath it, so no caller ever handles a top inset.
 *
 * ## Why it is a gradient now
 *
 * A flat near-black green read as chrome - a title bar that happened to be
 * green. Two stops give the block a direction: it is darkest where it meets the
 * status bar, which is where the white text sits, and brightest at the rounded
 * bottom edge, which is the edge that is meant to be seen. The brand is then
 * the first thing on the screen rather than the frame around it.
 *
 * Both stops hold white above 5:1 in both themes. That is the constraint the
 * brightness was picked against, and it is why this is not the lighter green a
 * mock would use - see Color.kt.
 */
@Composable
fun Canopy(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = RiderTheme.colors

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp))
            .background(
                Brush.verticalGradient(listOf(colors.canopy, colors.canopyBright)),
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        content = content,
    )
}

/**
 * A person, as a letter in a disc.
 *
 * There are no profile photographs on this platform - registration uploads a
 * licence and a plate, and neither is a picture of a face anybody would want on
 * a home screen. The initial is what a rider recognises as *their* app in the
 * half-second before they have read the name beside it, which is all this has
 * to do.
 */
@Composable
fun Avatar(
    name: String,
    modifier: Modifier = Modifier,
    size: Dp = 46.dp,
    container: Color = RiderTheme.colors.canopyFill,
    content: Color = RiderTheme.colors.onCanopy,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(PillShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifBlank { "R" },
            style = MaterialTheme.typography.titleLarge,
            color = content,
        )
    }
}

/** What a chip is saying. One hue per meaning, everywhere in the app. */
enum class ChipTone { Success, Danger, Cash, Accent, Neutral }

/**
 * A verdict in a capsule: Delivered, Carrying, Cash at the door.
 *
 * Tinted ground with the word on it, which is the same rule [SoftBanner]
 * follows one size up. The ground is what the eye finds when scanning a column
 * of rows, and it means the same thing here as it does on a banner or a card:
 * green happened, red failed, amber is money.
 */
@Composable
fun StatusChip(
    text: String,
    tone: ChipTone,
    modifier: Modifier = Modifier,
) {
    val colors = RiderTheme.colors

    val container = when (tone) {
        ChipTone.Success -> colors.successSoft
        ChipTone.Danger -> colors.dangerSoft
        ChipTone.Cash -> colors.cashSoft
        ChipTone.Accent -> colors.accentSoft
        ChipTone.Neutral -> colors.fill
    }

    val tint = when (tone) {
        ChipTone.Success -> colors.success
        ChipTone.Danger -> colors.danger
        ChipTone.Cash -> colors.cash
        ChipTone.Accent -> MaterialTheme.colorScheme.primary
        ChipTone.Neutral -> colors.textSecondary
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = tint,
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/**
 * One of the two money cards at the top of the home screen.
 *
 * A saturated tile with white on it, rather than the tinted band the rest of
 * the app uses, and this is the one place that exception is worth making:
 * these two are read in the three seconds a rider looks at the app between
 * jobs, and they have to be told apart from *each other* before either is
 * read. Colour does that; two white cards with different headings do not.
 *
 * The action is a small capsule inside the card rather than a button
 * underneath, so the tile stays one object - the number and what to do about
 * it in the same coloured rectangle.
 */
@Composable
fun MoneyCard(
    label: String,
    value: String,
    caption: String,
    actionLabel: String,
    onAction: () -> Unit,
    container: Color,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val onContainer = Color.White

    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.large)
            .background(container)
            .padding(16.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = onContainer.copy(alpha = 0.85f),
        )

        Text(
            text = value,
            style = RiderTextStyles.statValue,
            color = onContainer,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )

        Text(
            text = caption,
            style = MaterialTheme.typography.bodySmall,
            color = onContainer.copy(alpha = 0.8f),
            // Two lines whatever it says, so the pair of cards keep their
            // buttons on one line as the captions change under them.
            minLines = 2,
            maxLines = 2,
        )

        Surface(
            onClick = onAction,
            enabled = enabled,
            modifier = Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .height(36.dp),
            shape = PillShape,
            // The card's own colour lightened, not white: a white pill on teal
            // is a second card inside the first, and the eye reads it before
            // the number it is meant to follow.
            color = Color.White.copy(alpha = if (enabled) 0.22f else 0.10f),
            contentColor = onContainer,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(actionLabel, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

/**
 * One number and what it counts, as a card: finished jobs, jobs in hand.
 *
 * The disc carries the colour and the number stays at full contrast - the same
 * split as [StatusChip], one size up. Two of these sit side by side and are
 * given equal width by the caller rather than being sized by their contents:
 * "150" and "5" have to line up, or the smaller one reads as less important
 * rather than merely smaller.
 */
@Composable
fun OverviewTile(
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
) {
    RiderCard(modifier = modifier, padding = 16.dp) {
        IconBadge(icon = icon, tint = tint, container = container, size = 38.dp)

        Text(
            text = value,
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 12.dp),
        )

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = RiderTheme.colors.textSecondary,
        )
    }
}

/**
 * A round filled button: call, navigate.
 *
 * Round rather than a capsule with a word in it, because these sit over a map
 * or beside a name where there is no room for a label - and because a rider
 * reaching for "call the customer" at a junction is reaching for a target, not
 * reading a button.
 */
@Composable
fun RoundActionButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    container: Color = MaterialTheme.colorScheme.primary,
    content: Color = MaterialTheme.colorScheme.onPrimary,
    size: Dp = 46.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(PillShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = content,
            modifier = Modifier.size(size * 0.45f),
        )
    }
}

/**
 * The panel that comes up over a map.
 *
 * Rounded at the top only and painted with the app's surface, so it reads as a
 * sheet the map runs underneath rather than as a card floating on top of one.
 * It takes the bottom inset itself: this is always the last thing on its
 * screen, and every caller was otherwise remembering the same line.
 */
@Composable
fun MapSheet(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 18.dp)
            .navigationBarsPadding(),
        content = content,
    )
}

/**
 * A round, translucent button on the canopy.
 *
 * Refresh, sign out, back. All three are the same gesture from a rider's point
 * of view — a small thing at the edge of the dark block that is not a job — so
 * they are one shape, and the icon inside is the only thing that differs.
 */
@Composable
fun CanopyIconButton(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(PillShape)
            .background(RiderTheme.colors.canopyFill)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = RiderTheme.colors.onCanopy,
            modifier = Modifier.size(20.dp),
        )
    }
}

/**
 * The strip of numbers along the bottom of the canopy.
 *
 * What the shift has amounted to, kept out of the scrolling half of the screen
 * on purpose: it is the reason a rider is here, and it should not be something
 * they have to scroll back up to find. Three columns rather than a grid of
 * tiles, because on the board the jobs are the content and this is the frame.
 */
@Composable
fun CanopyStats(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(RiderTheme.colors.canopyFill)
            .heightIn(min = 68.dp)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** One column of [CanopyStats]: a number, and what it counts. */
@Composable
fun RowScope.CanopyStat(value: String, label: String) {
    Column(
        modifier = Modifier.weight(1f),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = value,
            style = RiderTextStyles.statValue,
            color = RiderTheme.colors.onCanopy,
            maxLines = 1,
        )
        Text(
            text = label.uppercase(),
            style = RiderTextStyles.overline,
            color = RiderTheme.colors.onCanopyMuted,
            textAlign = TextAlign.Center,
        )
    }
}

/** The hairline between two [CanopyStat]s. */
@Composable
fun CanopyStatDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(28.dp)
            .background(RiderTheme.colors.canopyFill),
    )
}

/** The small capitals that open a group of fields or a run of cards. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = RiderTextStyles.overline,
        color = RiderTheme.colors.textTertiary,
        modifier = modifier,
    )
}
