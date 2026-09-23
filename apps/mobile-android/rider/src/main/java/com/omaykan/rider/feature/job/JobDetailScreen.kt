package com.omaykan.rider.feature.job

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ForkLeft
import androidx.compose.material.icons.filled.ForkRight
import androidx.compose.material.icons.filled.Merge
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.RampLeft
import androidx.compose.material.icons.filled.RampRight
import androidx.compose.material.icons.filled.RoundaboutLeft
import androidx.compose.material.icons.filled.RoundaboutRight
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Straight
import androidx.compose.material.icons.filled.TurnLeft
import androidx.compose.material.icons.filled.TurnRight
import androidx.compose.material.icons.filled.TurnSharpLeft
import androidx.compose.material.icons.filled.TurnSharpRight
import androidx.compose.material.icons.filled.TurnSlightLeft
import androidx.compose.material.icons.filled.TurnSlightRight
import androidx.compose.material.icons.filled.UTurnLeft
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Store
import androidx.compose.material.icons.outlined.HeadsetMic
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.ButtonShape
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.CardShape
import com.omaykan.rider.core.designsystem.ChipTone
import com.omaykan.rider.core.designsystem.ForestTopBar
import com.omaykan.rider.core.designsystem.GhostButton
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.MapSheet
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderTextStyles
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.RoundActionButton
import com.omaykan.rider.core.designsystem.SerifFamily
import com.omaykan.rider.core.designsystem.StatusChip
import com.omaykan.rider.core.map.MapPoint
import com.omaykan.rider.core.map.NavigationProgress
import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryItem
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.model.Money
import com.omaykan.rider.core.nav.MapHandoff
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.roundToInt

/**
 * A job, opened. Two faces over one view model: the details, and the road.
 *
 * ## Why two faces
 *
 * The reference draws them as two screens — "Delivery Details", a page of
 * everything about the job with the one button that moves it on, and "On the
 * Way", a map with a sheet — and they answer two different moments. At a
 * counter a rider wants the basket and the customer's name; on the road they
 * want the next turn and how far. So the job opens on the details while the
 * food is still at the shop and on the map once it is in the bag, and either
 * face is one tap from the other. Both read the same state, so they can never
 * disagree about the stage.
 *
 * ## What it still refuses to do
 *
 * No voice, no re-routing, no off-route detection. "Navigate" hands the trip to
 * the app the rider drives with every day, which has all three plus offline
 * tiles for a city they know better than we do. See MapHandoff.
 */
@Composable
fun JobDetailScreen(
    jobId: String,
    onBack: () -> Unit,
    viewModel: JobDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    LaunchedEffect(jobId) { viewModel.open(jobId) }

    // The view model is scoped to the activity and outlives this screen, so it
    // is told when the screen goes: that is what switches the GPS off.
    DisposableEffect(viewModel) { onDispose { viewModel.close() } }

    // Delivered, released, or gone: there is no job behind this screen any
    // more, and a screen with nothing behind it should not be on screen.
    //
    // `everLoaded` is what keeps this from firing on the first frame, when the
    // job is null only because the feed has not answered yet.
    LaunchedEffect(state.job, state.everLoaded) {
        if (state.everLoaded && state.job == null) onBack()
    }

    BackHandler(onBack = onBack)

    // Null until the rider picks a face; until then the stage picks it.
    var mapChoice by rememberSaveable(jobId) { mutableStateOf<Boolean?>(null) }

    JobDetailContent(
        state = state,
        showMap = mapChoice ?: (state.job?.stage == DeliveryStage.PickedUp),
        onShowMap = { mapChoice = it },
        onBack = onBack,
        onSupport = {
            // Asked for when the rider reaches for it rather than on open: most
            // jobs never need it, and the number is the server's, not ours.
            scope.launch {
                val support = viewModel.support()
                context.reachSupport(support?.phone, support?.email)
            }
        },
        onAdvance = viewModel::advance,
        onRelease = viewModel::release,
    )
}

/**
 * Ring the support line, or write to it, or say there is neither.
 *
 * Every channel comes from `GET /api/rider/support`, and an unset one is absent
 * rather than dialling nowhere — the same rule the profile's Help & support
 * panel follows.
 */
private fun Context.reachSupport(phone: String?, email: String?) {
    when {
        !phone.isNullOrBlank() -> MapHandoff.dial(this, phone)
        !email.isNullOrBlank() -> try {
            startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, "Write to $email", Toast.LENGTH_LONG).show()
        }
        else -> Toast.makeText(this, "No support line is available right now.", Toast.LENGTH_LONG).show()
    }
}

/** Stateless production layout, also rendered by the debug gallery. */
@Composable
internal fun JobDetailContent(
    state: JobDetailUiState,
    showMap: Boolean,
    onShowMap: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSupport: () -> Unit,
    onAdvance: (DeliveryStage) -> Unit,
    onRelease: () -> Unit,
) {
    if (showMap) {
        MapFace(state, onShowMap, onBack, onAdvance, onRelease)
    } else {
        DetailsFace(state, onShowMap, onBack, onSupport, onAdvance, onRelease)
    }
}

private fun DeliveryAssignment.goingToShop(): Boolean =
    stage == DeliveryStage.Assigned || stage == DeliveryStage.Pending

// -- The details ------------------------------------------------------------

/** The width of the column the two stops' markers and the line between them sit in. */
private val RailWidth = 22.dp

/**
 * The reference's "Delivery Details": one white sheet rather than a stack of
 * cards, read top to bottom in the order a job is done — the ticket and what it
 * pays, the two stops joined by a line, who to ring, what is in the bag, and
 * how it was paid for. The step that moves the job on is pinned beneath.
 *
 * ## Where the reference had data this app does not
 *
 * Its item rows carry a photograph and a price, and its drop-off carries a
 * landmark. The rider's copy of an order is names and quantities and an
 * address — `RiderDeliveryController::asAssignment` sends nothing else — so the
 * rows carry the item's initial, and the note under the drop-off is the one
 * fact that belongs there: how much cash to collect at the door.
 */
@Composable
private fun DetailsFace(
    state: JobDetailUiState,
    onShowMap: (Boolean) -> Unit,
    onBack: () -> Unit,
    onSupport: () -> Unit,
    onAdvance: (DeliveryStage) -> Unit,
    onRelease: () -> Unit,
) {
    val job = state.job

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        ForestTopBar(
            title = "Delivery details",
            onBack = onBack,
            actions = {
                // A delivered job has no road left to show.
                if (job != null && job.stage != DeliveryStage.Delivered) {
                    CanopyIconButton(Icons.Outlined.Map, "Show the route", { onShowMap(true) })
                }
                CanopyIconButton(Icons.Outlined.HeadsetMic, "Call support", onSupport)
            },
        )

        if (job == null) {
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Column
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 18.dp, bottom = 24.dp),
        ) {
            Header(job)
            SheetDivider()
            Stops(job)
            job.customerPhone?.let { phone ->
                SheetDivider()
                CustomerRow(job, phone)
            }
            if (job.items.isNotEmpty()) {
                SheetDivider()
                Items(job)
            }
            SheetDivider()
            FeeRow(job)
        }

        // Nothing to press on a delivered job, so no bar: an empty white strip
        // pinned to the bottom reads as a button that failed to load.
        if (job.stage.next != null || job.canRelease || state.error != null) {
            ActionBar(job = job, busy = state.busy, error = state.error, onAdvance = onAdvance, onRelease = onRelease)
        } else {
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun SheetDivider() {
    HorizontalDivider(Modifier.padding(vertical = 16.dp), color = RiderTheme.colors.hairline)
}

/** "#2487 · Pick up order", and what the job pays, top right. */
@Composable
private fun Header(job: DeliveryAssignment) {
    val colors = RiderTheme.colors

    Row(verticalAlignment = Alignment.CenterVertically) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = job.offer.ticketNumber?.let { "#$it" } ?: "Delivery",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            StageChip(job.stage)
        }

        Column(horizontalAlignment = Alignment.End) {
            Text("Your fee", style = MaterialTheme.typography.bodySmall, color = colors.textTertiary)
            Text(
                text = Money.pesoRounded(job.offer.deliveryFeeCents),
                style = RiderTextStyles.statValue.copy(fontSize = 24.sp, lineHeight = 28.sp),
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** The stage, as the reference's chip: a dot and the word, on a soft ground. */
@Composable
private fun StageChip(stage: DeliveryStage) {
    val colors = RiderTheme.colors
    val (label, tint, ground) = when (stage) {
        DeliveryStage.Pending, DeliveryStage.Assigned ->
            Triple("Pick up order", MaterialTheme.colorScheme.primary, colors.accentSoft)
        DeliveryStage.PickedUp -> Triple("On the way", MaterialTheme.colorScheme.primary, colors.accentSoft)
        DeliveryStage.Delivered -> Triple("Delivered", colors.success, colors.successSoft)
    }

    Row(
        Modifier
            .clip(PillShape)
            .background(ground)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(PillShape)
                .background(tint),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = tint,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

/**
 * The two stops, joined by a line: the shop at the top with a ringed dot, the
 * door below with a pin. The line is what makes two addresses read as one
 * journey, in the direction it is travelled.
 */
@Composable
private fun Stops(job: DeliveryAssignment) {
    val context = LocalContext.current
    val colors = RiderTheme.colors
    val pickup = job.offer.pickup
    val delivered = job.stage == DeliveryStage.Delivered

    Column {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Column(
                Modifier
                    .width(RailWidth)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PickupMarker(done = !job.goingToShop())
                Box(
                    Modifier
                        .padding(vertical = 4.dp)
                        .width(2.dp)
                        .weight(1f)
                        .background(colors.separator),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp, bottom = 20.dp),
            ) {
                StopHeading("Pickup location", doneLabel = "Picked up".takeUnless { job.goingToShop() })
                StopBody(
                    title = pickup.storeName,
                    detail = pickup.address,
                    onNavigate = if (delivered) {
                        null
                    } else {
                        { MapHandoff.openMap(context, pickup.lat, pickup.lng, pickup.address ?: pickup.storeName) }
                    },
                )
                if (job.goingToShop() && job.items.isNotEmpty()) {
                    InsetNote(
                        icon = Icons.Outlined.Inventory2,
                        text = "Pick up ${job.items.size} ${if (job.items.size == 1) "item" else "items"}",
                        trailing = "Check the bag",
                    )
                }
            }
        }

        Row {
            Box(Modifier.width(RailWidth), contentAlignment = Alignment.TopCenter) {
                Icon(
                    Icons.Filled.Place,
                    contentDescription = null,
                    tint = colors.cta,
                    modifier = Modifier.size(22.dp),
                )
            }

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                StopHeading("Drop-off location", doneLabel = "Delivered".takeIf { delivered })
                StopBody(
                    title = job.customerName ?: "The customer",
                    detail = job.deliveryAddress ?: job.offer.dropoffArea ?: "No address given",
                    onNavigate = if (delivered) {
                        null
                    } else {
                        { MapHandoff.openMap(context, job.deliveryLat, job.deliveryLng, job.deliveryAddress) }
                    },
                )
                if (job.offer.collectsCash) {
                    val amount = Money.peso(job.offer.collectCents)
                    InsetNote(
                        icon = Icons.Filled.Payments,
                        text = if (delivered) "Collected $amount in cash" else "Collect $amount in cash at the door",
                        tint = colors.cash,
                        ground = colors.cashSoft,
                    )
                }
            }
        }
    }
}

/** The shop's marker: a ringed dot while it is the next stop, a tick once it is behind. */
@Composable
private fun PickupMarker(done: Boolean) {
    val colors = RiderTheme.colors

    Box(
        Modifier
            .padding(top = 2.dp)
            .size(18.dp)
            .clip(PillShape)
            .background(if (done) colors.success else MaterialTheme.colorScheme.primary),
        contentAlignment = Alignment.Center,
    ) {
        if (done) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.surface,
                modifier = Modifier.size(12.dp),
            )
        } else {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(PillShape)
                    .background(MaterialTheme.colorScheme.surface),
            )
        }
    }
}

@Composable
private fun StopHeading(text: String, doneLabel: String? = null) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        doneLabel?.let { StatusChip(it, ChipTone.Success) }
    }
}

@Composable
private fun StopBody(title: String, detail: String?, onNavigate: (() -> Unit)?) {
    Row(
        Modifier.padding(top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            detail?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = RiderTheme.colors.textSecondary)
            }
        }
        onNavigate?.let {
            GhostButton(
                text = "Navigate",
                icon = Icons.Filled.Navigation,
                onClick = it,
                modifier = Modifier.padding(start = 10.dp),
            )
        }
    }
}

/** The grey line under a stop: "Pick up 3 items · Check the bag", or the cash to collect. */
@Composable
private fun InsetNote(
    icon: ImageVector,
    text: String,
    trailing: String? = null,
    tint: Color = RiderTheme.colors.textSecondary,
    ground: Color = RiderTheme.colors.fill,
) {
    Row(
        Modifier
            .padding(top = 10.dp)
            .fillMaxWidth()
            .clip(ButtonShape)
            .background(ground)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp),
        )
        trailing?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = RiderTheme.colors.textTertiary)
        }
    }
}

/**
 * Who to ring. Absent rather than dead when there is no number: the shop's is
 * not on the wire, and a customer's only arrives with the claim. No message
 * button beside it, unlike the reference — riders have no messaging.
 */
@Composable
private fun CustomerRow(job: DeliveryAssignment, phone: String) {
    val context = LocalContext.current
    val colors = RiderTheme.colors
    val who = job.customerName ?: "The customer"

    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            Icons.Filled.Phone,
            contentDescription = null,
            tint = colors.textSecondary,
            modifier = Modifier
                .width(RailWidth)
                .size(20.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(
                text = who,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(phone, style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
        }
        RoundActionButton(
            icon = Icons.Filled.Call,
            description = "Call $who",
            onClick = { MapHandoff.dial(context, phone) },
            container = colors.accentSoft,
            content = MaterialTheme.colorScheme.primary,
            size = 42.dp,
        )
    }
}

/**
 * The basket, to check the bag against before leaving the shop.
 *
 * The reference shows a photograph and a price per line. Neither is on the
 * rider's copy of an order, so each row leads with the item's initial on a
 * peach tile, and the price stays the shop's business — what matters to the
 * rider is the total they may have to collect, which is on the drop-off.
 */
@Composable
private fun Items(job: DeliveryAssignment) {
    Text(
        text = "Order items (${job.items.size})",
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurface,
    )

    job.items.forEach { item ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemTile(item.name)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(item.pieces(), style = MaterialTheme.typography.bodySmall, color = RiderTheme.colors.textSecondary)
            }
        }
    }
}

/** "1 pc", "3 pcs" — or "Qty 1.5" for something sold by weight. */
private fun DeliveryItem.pieces(): String = when {
    quantity % 1.0 != 0.0 -> "Qty $quantityLabel"
    quantity == 1.0 -> "1 pc"
    else -> "$quantityLabel pcs"
}

@Composable
private fun ItemTile(name: String) {
    val colors = RiderTheme.colors

    Box(
        Modifier
            .size(46.dp)
            .clip(ButtonShape)
            .background(colors.peach),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = name.trim().take(1).uppercase().ifBlank { "•" },
            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
            color = colors.onPeach,
        )
    }
}

/**
 * The fee, and how the order was paid for — said either way, because a rider
 * who sees nothing about cash cannot tell a prepaid order from a screen that
 * forgot to mention one.
 */
@Composable
private fun FeeRow(job: DeliveryAssignment) {
    val colors = RiderTheme.colors
    val cash = job.offer.collectsCash

    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Delivery fee",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (cash) Icons.Filled.Payments else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (cash) colors.cash else colors.success,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = if (cash) "Cash on delivery" else "Paid online",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = Money.peso(job.offer.deliveryFeeCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text("Yours in full", style = MaterialTheme.typography.bodySmall, color = colors.success)
        }
    }
}

/**
 * The one button that moves the job on, pinned under the details.
 *
 * Handing a job back is a text link beneath it rather than a second button of
 * equal weight: it is the rarer choice and the destructive one, and two filled
 * buttons side by side is how a thumb takes the wrong one. It disappears once
 * the food is in the bag — the server refuses it then anyway.
 */
@Composable
private fun ActionBar(
    job: DeliveryAssignment,
    busy: Boolean,
    error: String?,
    onAdvance: (DeliveryStage) -> Unit,
    onRelease: () -> Unit,
) {
    val colors = RiderTheme.colors

    HorizontalDivider(color = colors.hairline)

    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }

        job.stage.next?.let { next ->
            AdvanceButton(
                text = when (job.stage) {
                    DeliveryStage.PickedUp -> "I have delivered the order"
                    else -> "I have picked up the order"
                },
                busy = busy,
                onClick = { onAdvance(next) },
            )
        }

        if (job.canRelease) {
            TextButton(
                onClick = onRelease,
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Give this job back",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }
    }
}

/**
 * The reference's big terracotta capsule with a white disc at its head.
 *
 * It looks like a slider and is a tap. A swipe-to-confirm would be a new
 * gesture to learn on a moving bike, and the confirmation it buys is already
 * provided by the server — a stage can only move forward one step.
 */
@Composable
private fun AdvanceButton(text: String, busy: Boolean, onClick: () -> Unit) {
    val colors = RiderTheme.colors

    Button(
        onClick = onClick,
        enabled = !busy,
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp),
        shape = PillShape,
        colors = ButtonDefaults.buttonColors(containerColor = colors.cta, contentColor = colors.onCta),
        contentPadding = PaddingValues(start = 7.dp, end = 20.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(PillShape)
                .background(Color.White),
            contentAlignment = Alignment.Center,
        ) {
            if (busy) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = colors.cta)
            } else {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = colors.cta)
            }
        }
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
    }
}

// -- The road ---------------------------------------------------------------

@Composable
private fun MapFace(
    state: JobDetailUiState,
    onShowMap: (Boolean) -> Unit,
    onBack: () -> Unit,
    onAdvance: (DeliveryStage) -> Unit,
    onRelease: () -> Unit,
) {
    val job = state.job
    val density = LocalDensity.current

    // What covers the map, measured rather than guessed: the bar and banner
    // change height with the instruction's length, and the sheet with the job.
    var topPx by remember { mutableIntStateOf(0) }
    var sheetPx by remember { mutableIntStateOf(0) }

    Box(
        Modifier
            .fillMaxSize()
            .background(RiderTheme.colors.hairline),
    ) {
        JobRouteMap(
            pickup = job?.let { MapPoint(it.offer.pickup.lat, it.offer.pickup.lng) },
            dropoff = job?.let { MapPoint(it.deliveryLat, it.deliveryLng) },
            // The exact point, every fix. The map is rendered on the device
            // and redraws for free.
            here = state.here,
            headingDeg = state.headingDeg,
            route = state.route?.polyline,
            modifier = Modifier.fillMaxSize(),
            obscuredTop = with(density) { topPx.toDp() },
            obscuredBottom = with(density) { sheetPx.toDp() },
        )

        Column(Modifier.fillMaxSize()) {
            Column(Modifier.onSizeChanged { topPx = it.height }) {
                ForestTopBar(
                    title = if (job?.goingToShop() != false) "To the shop" else "On the way",
                    onBack = onBack,
                    actions = {
                        CanopyIconButton(
                            Icons.AutoMirrored.Outlined.ReceiptLong,
                            "Order details",
                            { onShowMap(false) },
                        )
                    },
                )

                state.step?.let {
                    ManeuverBanner(it.distanceLabel, it.step.instruction, maneuverIcon(it.step.type, it.step.modifier))
                }
            }

            val minutes = state.minutesToGo
            val metres = state.metresToGo
            if (minutes != null && metres != null) {
                EtaBubble(
                    minutes,
                    metres,
                    Modifier
                        .align(Alignment.End)
                        .padding(12.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            job?.let {
                TripSheet(
                    it,
                    state,
                    onShowMap,
                    onAdvance,
                    onRelease,
                    Modifier.onSizeChanged { size -> sheetPx = size.height },
                )
            }
        }
    }
}

/**
 * "250 m — Turn right onto Santa Cruz Ave", on black.
 *
 * Black rather than the app's forest, and it is the only black surface in the
 * product: this is the one thing on screen a rider reads at speed, in sunlight,
 * out of the corner of an eye. It has to win against a map, and it has to read
 * as separate from the forest bar directly above it.
 */
@Composable
private fun ManeuverBanner(distance: String, instruction: String, icon: ImageVector) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clip(CardShape)
            .background(Color(0xFF0B0B0C))
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(32.dp),
        )

        Column(Modifier.padding(start = 16.dp)) {
            Text(text = distance, style = RiderTextStyles.statValue, color = Color.White)
            Text(
                text = instruction,
                style = MaterialTheme.typography.bodyLarge,
                color = Color.White.copy(alpha = 0.92f),
            )
        }
    }
}

/**
 * The arrow for a Directions step, from the two fields Mapbox sends with it.
 *
 * Mapbox writes the sentence; this only has to draw the same turn. `type` says
 * what kind of manoeuvre it is (a roundabout, a fork, the arrival) and
 * `modifier` which way (left, slight right, U-turn). The glyphs are the plain
 * Material ones rather than the auto-mirrored set on purpose: a left turn is a
 * left turn in a right-to-left language too.
 *
 * A U-turn is drawn to the left because the Philippines drives on the right,
 * so that is the way one is made.
 */
private fun maneuverIcon(type: String?, modifier: String?): ImageVector {
    val left = modifier?.contains("left") == true

    return when {
        type == "arrive" -> Icons.Filled.SportsScore
        type == "roundabout" || type == "rotary" || type == "roundabout turn" ||
            type?.startsWith("exit ") == true ->
            if (left) Icons.Filled.RoundaboutLeft else Icons.Filled.RoundaboutRight
        type == "merge" -> Icons.Filled.Merge
        type == "fork" -> if (left) Icons.Filled.ForkLeft else Icons.Filled.ForkRight
        type == "on ramp" || type == "off ramp" -> if (left) Icons.Filled.RampLeft else Icons.Filled.RampRight
        modifier == "uturn" -> Icons.Filled.UTurnLeft
        modifier == "sharp left" -> Icons.Filled.TurnSharpLeft
        modifier == "sharp right" -> Icons.Filled.TurnSharpRight
        modifier == "slight left" -> Icons.Filled.TurnSlightLeft
        modifier == "slight right" -> Icons.Filled.TurnSlightRight
        modifier == "left" -> Icons.Filled.TurnLeft
        modifier == "right" -> Icons.Filled.TurnRight
        else -> Icons.Filled.Straight
    }
}

/** The reference's "12 min / 4.2 km" tag, pinned to the corner of the map. */
@Composable
private fun EtaBubble(minutes: Int, metres: Double, modifier: Modifier = Modifier) {
    Column(
        modifier
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, RiderTheme.colors.hairline, CardShape)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "$minutes min",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = distanceLabel(metres),
            style = MaterialTheme.typography.bodySmall,
            color = RiderTheme.colors.textSecondary,
        )
    }
}

/**
 * The trip, on one sheet over the map.
 *
 * Who the job is with and how far; where it is up to; whether there is money
 * to collect; and the controls — hand off to a map app, ring the customer,
 * move the job on. Which *end* of the trip is described changes with the stage,
 * and only one is described at a time: a rider on the way to a shop is not
 * deciding anything about the customer's door.
 */
@Composable
private fun TripSheet(
    job: DeliveryAssignment,
    state: JobDetailUiState,
    onShowMap: (Boolean) -> Unit,
    onAdvance: (DeliveryStage) -> Unit,
    onRelease: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val colors = RiderTheme.colors
    val goingToShop = job.goingToShop()

    val partyName = if (goingToShop) job.offer.pickup.storeName else job.customerName ?: "The customer"

    // The shop's number is not on the wire — `asAssignment` sends the
    // customer's and nothing else — so on the way to a pickup there is nobody
    // to ring, and the button is absent rather than dead.
    val phone = job.customerPhone.takeUnless { goingToShop }

    val near = job.stage == DeliveryStage.PickedUp &&
        isNear(state.here, MapPoint(job.deliveryLat, job.deliveryLng))

    MapSheet(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(
                icon = if (goingToShop) Icons.Filled.Store else Icons.Filled.Place,
                tint = if (goingToShop) colors.onPeach else MaterialTheme.colorScheme.primary,
                container = if (goingToShop) colors.peach else colors.accentSoft,
                size = 46.dp,
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(
                    text = if (goingToShop) "Picking up from" else "Delivering to",
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textTertiary,
                )
                Text(
                    text = partyName,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                job.offer.ticketNumber?.let {
                    Text("#$it", style = MaterialTheme.typography.bodySmall, color = colors.textSecondary)
                }
            }

            val minutes = state.minutesToGo
            val metres = state.metresToGo
            if (minutes != null && metres != null) {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "$minutes min",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${distanceLabel(metres)} away",
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textTertiary,
                    )
                }
            }
        }

        DeliverySteps(job.stage, near, Modifier.padding(top = 18.dp))

        HorizontalDivider(Modifier.padding(vertical = 14.dp), color = colors.hairline)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (job.offer.collectsCash) Icons.Filled.Payments else Icons.Filled.CheckCircle,
                contentDescription = null,
                tint = if (job.offer.collectsCash) colors.cash else colors.success,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = "Payment",
                style = MaterialTheme.typography.bodyMedium,
                color = colors.textSecondary,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
            )
            StatusChip(
                text = if (job.offer.collectsCash) {
                    "Collect ${Money.peso(job.offer.collectCents)}"
                } else {
                    "Paid online"
                },
                tone = if (job.offer.collectsCash) ChipTone.Cash else ChipTone.Success,
            )
        }

        state.error?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = colors.danger,
                modifier = Modifier.padding(top = 12.dp),
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundActionButton(
                icon = Icons.Filled.Navigation,
                description = "Open in a map app",
                onClick = {
                    if (goingToShop) {
                        val pickup = job.offer.pickup
                        MapHandoff.openMap(context, pickup.lat, pickup.lng, pickup.address ?: pickup.storeName)
                    } else {
                        MapHandoff.openMap(context, job.deliveryLat, job.deliveryLng, job.deliveryAddress)
                    }
                },
                container = colors.accentSoft,
                content = MaterialTheme.colorScheme.primary,
                size = 52.dp,
            )

            phone?.let {
                RoundActionButton(
                    icon = Icons.Filled.Call,
                    description = "Call $partyName",
                    onClick = { MapHandoff.dial(context, it) },
                    container = colors.accentSoft,
                    content = MaterialTheme.colorScheme.primary,
                    size = 52.dp,
                )
            }

            job.stage.next?.let { next ->
                PrimaryButton(
                    text = job.stage.nextLabel.orEmpty(),
                    onClick = { onAdvance(next) },
                    busy = state.busy,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (job.canRelease) {
            TextButton(
                onClick = onRelease,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Give this job back",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.textSecondary,
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp)
                .clip(ButtonShape)
                .clickable(onClickLabel = "Show the order details") { onShowMap(false) }
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "View order details",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Icon(
                Icons.Filled.ExpandLess,
                contentDescription = null,
                tint = colors.textSecondary,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(20.dp),
            )
        }
    }
}

/** "650 m" up close, "2.4 km" further out — the same rounding the turn banner uses. */
private fun distanceLabel(metres: Double): String =
    if (metres >= 1_000) {
        String.format(Locale.US, "%.1f km", metres / 1_000)
    } else {
        "${(metres / 10).roundToInt() * 10} m"
    }

/** Close enough to the door to call it arriving. A street, not a district. */
private const val NEAR_METRES = 300.0

private fun isNear(here: MapPoint?, door: MapPoint): Boolean =
    here != null && here.placed && door.placed &&
        NavigationProgress.distanceMetres(here, door) < NEAR_METRES

private enum class StepState { Done, Current, Ahead }

/**
 * Accepted → Picked up → Arriving → Delivered.
 *
 * The reference's four steps, with the one in the middle made honest: it calls
 * it "Near You", which is the customer's word, and it is lit here only when the
 * phone actually has a fix within [NEAR_METRES] of the door. Without a fix it
 * simply stays ahead, rather than guessing.
 */
@Composable
private fun DeliverySteps(stage: DeliveryStage, near: Boolean, modifier: Modifier = Modifier) {
    val labels = listOf("Accepted", "Picked up", "Arriving", "Delivered")
    val done = when {
        stage == DeliveryStage.Delivered -> 4
        stage == DeliveryStage.PickedUp && near -> 3
        stage == DeliveryStage.PickedUp -> 2
        else -> 1
    }

    val colors = RiderTheme.colors
    val lit = MaterialTheme.colorScheme.primary

    Row(
        modifier
            .fillMaxWidth()
            .clearAndSetSemantics {
                contentDescription = "Delivery progress: ${labels[done - 1]}" +
                    (labels.getOrNull(done)?.let { ", next $it" } ?: "")
            },
    ) {
        labels.forEachIndexed { index, label ->
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (index > 0) {
                        Box(
                            Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(0.5f)
                                .height(2.dp)
                                .background(if (index <= done) lit else colors.separator),
                        )
                    }
                    if (index < labels.lastIndex) {
                        Box(
                            Modifier
                                .align(Alignment.CenterEnd)
                                .fillMaxWidth(0.5f)
                                .height(2.dp)
                                .background(if (index < done) lit else colors.separator),
                        )
                    }
                    StepMarker(
                        when {
                            index < done -> StepState.Done
                            index == done -> StepState.Current
                            else -> StepState.Ahead
                        },
                    )
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (index <= done) MaterialTheme.colorScheme.onSurface else colors.textTertiary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun StepMarker(state: StepState) {
    val lit = MaterialTheme.colorScheme.primary

    when (state) {
        StepState.Done -> Box(
            Modifier
                .size(22.dp)
                .clip(PillShape)
                .background(lit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(14.dp),
            )
        }

        StepState.Current -> Box(
            Modifier
                .size(22.dp)
                .clip(PillShape)
                .background(MaterialTheme.colorScheme.surface)
                .border(2.dp, lit, PillShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(PillShape)
                    .background(lit),
            )
        }

        StepState.Ahead -> Box(
            Modifier
                .size(12.dp)
                .clip(PillShape)
                .background(RiderTheme.colors.separator),
        )
    }
}
