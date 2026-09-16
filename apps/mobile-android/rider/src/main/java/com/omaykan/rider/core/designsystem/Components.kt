package com.omaykan.rider.core.designsystem

import android.app.Activity
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/*
 * The kit every screen in this app is built out of.
 *
 * It exists because the screens kept re-deciding the same six questions — how
 * round is a card, how tall is a button, what a small tinted badge looks like —
 * and answering them slightly differently each time. A rider moves between all
 * of them in a minute; a card that changes shape between the board and the
 * status screen is the difference between one app and three.
 *
 * Nothing here knows about deliveries. Anything that does belongs in the
 * feature package that owns it.
 */

/** The standard press target: tall enough for a thumb, in a hurry, outdoors. */
private val ButtonHeight = 52.dp

/**
 * The tile everything sits on: white on the cream page.
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
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = CardShape,
        color = color,
        border = BorderStroke(1.dp, RiderTheme.colors.hairline),
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}

/**
 * The one button a screen wants pressed. Terracotta, full width, 52dp.
 *
 * [container] can be handed the green accent for the second-rank "open this"
 * buttons the reference draws in forest — View order details — so that
 * terracotta stays reserved for the step that actually moves a job on.
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    busy: Boolean = false,
    container: Color = RiderTheme.colors.cta,
    content: Color = RiderTheme.colors.onCta,
    leadingIcon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = modifier
            .fillMaxWidth()
            .height(ButtonHeight),
        shape = ButtonShape,
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
            if (leadingIcon != null) {
                Icon(
                    leadingIcon,
                    contentDescription = null,
                    modifier = Modifier
                        .padding(end = 10.dp)
                        .size(20.dp),
                )
            }
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
        modifier = modifier.height(44.dp),
        shape = ButtonShape,
        border = BorderStroke(1.dp, RiderTheme.colors.separator),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        contentPadding = ButtonDefaults.TextButtonContentPadding,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
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
                color = if (selected) RiderTheme.colors.canopy else Color.Transparent,
                contentColor = if (selected) {
                    RiderTheme.colors.onCanopy
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
            .clip(ButtonShape)
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
        shape = ButtonShape,
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
 * White status-bar icons for as long as the calling screen is on screen.
 *
 * Every screen that opens under the forest or a darkened photograph calls this.
 * The onboarding pages and the job map's full-bleed moments do not, and keep
 * whatever the theme chose for them.
 */
@Composable
fun LightStatusBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
}

/**
 * The forest block a form screen opens with — sign-up, the forgotten-password
 * form, the status letter.
 *
 * Flat, as the reference draws its headers: the curved edge it used to have
 * made the canopy read as an illustration rather than as the app's frame.
 */
@Composable
fun Canopy(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    LightStatusBarIcons()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(RiderTheme.colors.canopy)
            .statusBarsPadding()
            .padding(horizontal = 20.dp),
        content = content,
    )
}

/**
 * A person, as a letter in a disc.
 *
 * The fallback [RiderAvatar] draws while a photo loads, when it fails, and for
 * the many riders who never upload one. The initial is what a rider recognises
 * as *their* app in the half-second before they have read the name beside it,
 * which is all this has to do.
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
            fontSize = MaterialTheme.typography.titleLarge.fontSize * (size / 46.dp).coerceIn(0.7f, 1.8f),
            color = content,
        )
    }
}

/** What a chip is saying. One hue per meaning, everywhere in the app. */
enum class ChipTone { Success, Danger, Cash, Accent, Neutral, Cta }

/**
 * A verdict in a capsule: Delivered, On the way, Cash on delivery.
 *
 * Tinted ground with the word on it, which is the same rule [SoftBanner]
 * follows one size up. The ground is what the eye finds when scanning a column
 * of rows, and it means the same thing here as it does on a banner or a card:
 * green happened, red failed, amber is money, peach is the next thing to do.
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
        ChipTone.Cta -> colors.peach
    }

    val tint = when (tone) {
        ChipTone.Success -> colors.success
        ChipTone.Danger -> colors.danger
        ChipTone.Cash -> colors.cash
        ChipTone.Accent -> MaterialTheme.colorScheme.primary
        ChipTone.Neutral -> colors.textSecondary
        ChipTone.Cta -> colors.onPeach
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        fontWeight = FontWeight.Medium,
        color = tint,
        maxLines = 1,
        modifier = modifier
            .clip(PillShape)
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
    )
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
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .navigationBarsPadding(),
        content = content,
    )
}

/**
 * An icon on the forest: refresh, sign out, back.
 *
 * Bare, the way the reference draws its bar icons, with a full 48dp target
 * behind it — the translucent disc it used to sit in was a second shape on a
 * bar that already has a title and a mark competing for the eye.
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
            .size(48.dp)
            .clip(PillShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = RiderTheme.colors.onCanopy.copy(alpha = if (enabled) 1f else 0.5f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** Sentence-case section heading for clear hierarchy. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

/**
 * A section heading with a way to see the rest: "Active orders · View all →".
 *
 * The action is terracotta text rather than a button because it is a link to
 * another screen, not a step in a job.
 */
@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SectionLabel(title, Modifier.weight(1f))

        if (action != null) {
            TextButton(onClick = onAction) {
                Text(
                    text = "$action  →",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = RiderTheme.colors.cta,
                )
            }
        }
    }
}
