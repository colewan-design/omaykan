package com.omaykan.rider.feature.work

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Route
import androidx.compose.material.icons.filled.Store
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omaykan.rider.core.designsystem.ChipTone
import com.omaykan.rider.core.designsystem.GhostButton
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.MetaPill
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderCard
import com.omaykan.rider.core.designsystem.RiderTextStyles
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SoftBanner
import com.omaykan.rider.core.designsystem.StatusChip
import com.omaykan.rider.core.model.DeliveryAssignment
import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.model.DeliveryStage
import com.omaykan.rider.core.map.MapPoint
import com.omaykan.rider.core.model.Money
import com.omaykan.rider.core.nav.MapHandoff

/*
 * Every card in this app is the same four bands, in the same order:
 *
 *   the map  →  the money  →  the route  →  the button
 *
 * That order is the order a rider decides in, and holding it fixed is what lets
 * somebody scan a board of six jobs without reading any of them in full: the
 * picture is always the lid, the fee is always below it at the same size, the
 * shop is always the first stop on the rail, and the thing that commits them is
 * always the capsule at the bottom. A card that reshuffles those between the
 * board and the job in hand is a card that has to be read from the beginning
 * every time.
 *
 * The map came last and sits first, because it answers the question the words
 * underneath it cannot: an address is a string a rider has to translate into a
 * direction, and a picture *is* the direction. It is also the one band that can
 * be absent — no token, no coordinates, no network — and every card below is
 * complete without it. See JobMap.
 */

/**
 * The inset the map does not get.
 *
 * The picture runs to the card's edges, so the card itself is drawn with no
 * padding and everything below it carries this instead.
 */
private val CardInset = 18.dp

/**
 * How tall the picture is, and it is deliberately most of what you see first.
 *
 * A strip of map is decoration; a map this size is the thing being read. The
 * job's numbers sit on a panel that overlaps its bottom edge, so the card reads
 * as a sheet drawn *over* a map rather than as a list row with a thumbnail —
 * which is the difference between a picture you glance past and one you use.
 */
private val MapHeight = 208.dp

/** How far the sheet rides up over the map. Enough to read as one, not two. */
private val SheetOverlap = 20.dp

/**
 * An unclaimed job.
 *
 * The four things that decide whether a rider wants it, in the order they
 * decide them: what it pays, where it starts, where it is going, and whether
 * there is money to collect. Nothing about the customer, because the server
 * does not send it and should not — see DeliveryOffer.
 */
@Composable
fun OfferCard(
    offer: DeliveryOffer,
    busy: Boolean,
    enabled: Boolean,
    onAccept: () -> Unit,
    /** Where this rider is, when the phone already knows. Null is ordinary. */
    riderPoint: MapPoint? = null,
) {
    JobSheet(
        map = {
            // The shop, and the rider if they are on the map — but no line and
            // no door. An offer carries the drop-off *area* and not a
            // coordinate, because the server withholds the exact address until
            // the job is claimed. Drawing a route to a guess would put a
            // precise-looking answer on screen where the server deliberately
            // gave a vague one.
            JobMap(
                pickup = MapPoint(offer.pickup.lat, offer.pickup.lng),
                rider = riderPoint,
                height = MapHeight,
            )
        },
    ) {
        Headline(
            cents = offer.deliveryFeeCents,
            // The platform's promise, said on the card rather than in an
            // onboarding screen nobody re-reads. It is the difference between
            // this figure and the one an aggregator quotes.
            caption = "Yours in full",
            value = offer.distanceKm?.let { String.format("%.1f km", it) },
            valueCaption = "${offer.itemCount} item${if (offer.itemCount == 1) "" else "s"} to carry",
        )

        Divider()

        StopRow(
            icon = Icons.Filled.Store,
            label = "Pickup",
            title = offer.pickup.storeName,
            detail = offer.pickup.address,
            tint = MaterialTheme.colorScheme.primary,
        )

        StopRow(
            icon = Icons.Filled.Place,
            label = "Customer drop-off",
            // The area, not the address. The board deliberately gets only the
            // last one or two parts of it — enough to judge the trip, not
            // enough to knock on the door of a job you have not taken.
            title = offer.dropoffArea ?: "Address given when you take it",
            detail = null,
            tint = RiderTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 14.dp),
        )

        if (offer.collectsCash) {
            SoftBanner(
                icon = Icons.Filled.Payments,
                text = "Collect ${Money.peso(offer.collectCents)} at the door",
                tint = RiderTheme.colors.cash,
                container = RiderTheme.colors.cashSoft,
                modifier = Modifier.padding(top = 16.dp),
            )
        }

        PrimaryButton(
            text = "Take this job",
            onClick = onAccept,
            enabled = enabled,
            busy = busy,
            modifier = Modifier.padding(top = 18.dp),
        )
    }
}

/**
 * A job this rider is carrying.
 *
 * Everything the offer had, plus the three things that only arrive with the
 * claim: the full address, the customer's name, and their number. The map and
 * call buttons are the reason this app is worth installing over the web portal
 * — one tap into the navigation app a rider already trusts, and one into the
 * dialler.
 *
 * The rail is the same one the board draws, with one thing added: the stop the
 * rider is actually heading to right now is the lit one, and it moves down the
 * card when they press "Picked up". A rider glancing at three jobs in hand can
 * tell which are still at a shop and which are on the road without reading a
 * single line of text.
 */
@Composable
fun AssignmentCard(
    job: DeliveryAssignment,
    busy: Boolean,
    enabled: Boolean,
    onAdvance: (DeliveryStage) -> Unit,
    onRelease: () -> Unit,
    /** Opens the job's own screen: the road, the car, the next instruction. */
    onOpen: () -> Unit,
    /** The driving route, once it has come back. Null draws the pins alone. */
    route: String? = null,
    riderPoint: MapPoint? = null,
) {
    val context = LocalContext.current
    val heading = job.stage.heading
    val goingToShop = job.stage == DeliveryStage.Assigned || job.stage == DeliveryStage.Pending
    val pickedUp = job.stage == DeliveryStage.PickedUp || job.stage == DeliveryStage.Delivered
    val delivered = job.stage == DeliveryStage.Delivered

    RiderCard(padding = 0.dp) {
        // The whole trip: both ends are real coordinates once a job is claimed,
        // so this is the one card that gets the road drawn between them.
        JobMap(
            pickup = MapPoint(job.offer.pickup.lat, job.offer.pickup.lng),
            dropoff = MapPoint(job.deliveryLat, job.deliveryLng),
            rider = riderPoint,
            route = route,
            // Tapping the picture opens the full one. The gesture people
            // already expect from a map, so it needs no affordance of its own.
            modifier = Modifier.clickable(onClick = onOpen),
        )

        Column(Modifier.padding(CardInset)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaPill(
                    text = heading,
                    container = RiderTheme.colors.accentSoft,
                    content = MaterialTheme.colorScheme.primary,
                )
                job.offer.ticketNumber?.let {
                    Text(
                        text = "Ticket $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = RiderTheme.colors.textTertiary,
                    )
                }
            }

            Fee(
                cents = job.offer.deliveryFeeCents,
                caption = "Yours in full",
                modifier = Modifier.padding(top = 12.dp),
                meta = {
                    job.offer.distanceKm?.let {
                        MetaPill(String.format("%.1f km", it), icon = Icons.Filled.Route)
                    }
                },
            )

            Route(
                pickup = Stop(
                    icon = Icons.Filled.Store,
                    label = "Pick up",
                    title = job.offer.pickup.storeName,
                    detail = job.offer.pickup.address,
                    highlighted = goingToShop,
                    done = pickedUp,
                ),
                dropoff = Stop(
                    icon = Icons.Filled.Place,
                    label = "Drop off",
                    title = job.deliveryAddress ?: job.offer.dropoffArea ?: "No address given",
                    detail = job.customerName,
                    highlighted = job.stage == DeliveryStage.PickedUp,
                    done = delivered,
                ),
            )

            if (job.offer.collectsCash) {
                SoftBanner(
                    icon = Icons.Filled.Payments,
                    text = "Collect ${Money.peso(job.offer.collectCents)} at the door",
                    tint = RiderTheme.colors.cash,
                    container = RiderTheme.colors.cashSoft,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            if (job.items.isNotEmpty()) {
                Text(
                    // The basket, so a rider can check the bag against it before
                    // leaving the shop. This is the one screen in the product where
                    // that list is worth reading out.
                    text = job.items.joinToString(", ") { it.label },
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                GhostButton(
                    text = "Navigate",
                    icon = Icons.Filled.Map,
                    onClick = {
                        // Where the rider is going *now*, which is the shop until
                        // the food is in the bag. A single button that changes
                        // destination with the stage beats two the rider has to
                        // pick between at a junction.
                        if (goingToShop) {
                            MapHandoff.openMap(
                                context,
                                job.offer.pickup.lat,
                                job.offer.pickup.lng,
                                job.offer.pickup.address ?: job.offer.pickup.storeName,
                            )
                        } else {
                            MapHandoff.openMap(
                                context,
                                job.deliveryLat,
                                job.deliveryLng,
                                job.deliveryAddress,
                            )
                        }
                    },
                    modifier = Modifier.weight(1f),
                )

                job.customerPhone?.let { phone ->
                    GhostButton(
                        text = "Call",
                        icon = Icons.Filled.Call,
                        onClick = { MapHandoff.dial(context, phone) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            job.stage.next?.let { next ->
                PrimaryButton(
                    text = job.stage.nextLabel.orEmpty(),
                    onClick = { onAdvance(next) },
                    enabled = enabled,
                    busy = busy,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }

            if (job.canRelease) {
                TextButton(
                    onClick = onRelease,
                    enabled = enabled && !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Give this job back", color = RiderTheme.colors.textSecondary)
                }
            }
        }
    }
}

/**
 * A map with a sheet drawn over it.
 *
 * The shape the whole screen is built on: the picture is the lid, and the
 * numbers sit on a panel that rides up over its bottom edge. The overlap is the
 * only reason this reads as one object — flush, it would be a photo with a
 * caption; overlapped, it is a job.
 *
 * The map slot is a slot rather than a parameter list because the two cards
 * draw genuinely different pictures, and hiding that behind six nullable
 * arguments would make the difference look accidental. It is not: see JobMap.
 */
@Composable
private fun JobSheet(
    map: @Composable () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, RiderTheme.colors.hairline),
    ) {
        Column {
            map()

            Column(
                Modifier
                    // Up over the map, and painted, so the corners cut into the
                    // picture rather than floating above a seam.
                    .offset(y = -SheetOverlap)
                    .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(start = CardInset, end = CardInset, top = 18.dp),
                // No bottom padding: `offset` moves the drawing, not the
                // layout, so the overlap is already left over as space under
                // the button. Padding for it as well doubled the gap.
                content = content,
            )
        }
    }
}

/**
 * The two numbers a rider decides on, side by side.
 *
 * The fee is the larger of the two and always on the left, because it is the
 * one that is often the only thing read. The second is the trip — how far, and
 * what is in the bag — and it is deliberately the same shape so the eye can
 * take both in one movement instead of hunting for the second.
 */
@Composable
private fun Headline(
    cents: Long,
    caption: String,
    value: String?,
    valueCaption: String?,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(
                text = Money.pesoRounded(cents),
                style = RiderTextStyles.money,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = RiderTheme.colors.textTertiary,
            )
        }

        if (value != null) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = RiderTextStyles.money,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                valueCaption?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = RiderTheme.colors.textTertiary,
                    )
                }
            }
        }
    }
}

/** The hairline between the numbers and the trip. */
@Composable
private fun Divider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .height(1.dp)
            .background(RiderTheme.colors.hairline),
    )
}

/**
 * One end of the trip: an icon, what kind of stop it is, and where.
 *
 * Flat rows rather than the connected rail this card used to draw. The rail
 * spent a column of width saying "these two are in order", which the words
 * "Pickup" and "Customer drop-off" already say — and it cost the addresses the
 * room they actually need on a phone.
 */
@Composable
private fun StopRow(
    icon: ImageVector,
    label: String,
    title: String,
    detail: String?,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )

        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = tint,
            )
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textSecondary,
                )
            }
        }
    }
}

/**
 * A finished job. One row, because it is history.
 *
 * Four facts in the order they are wanted: which shop, where it went, what it
 * paid, and that it landed. The ticket number is over the top of the shop name
 * rather than beside it, small and grey, because it is the fact a rider needs
 * exactly once — when a shop rings about an order from two hours ago — and
 * never while scrolling.
 *
 * The tick and the word both say "delivered", which looks like saying it twice
 * and is not: the disc is what a rider sees running an eye down a column, and
 * the chip is what confirms it once they have stopped on one row. This is the
 * only outcome a finished job can have — a job handed back never reaches this
 * list, and there is no cancelled state on the backend to draw in red.
 */
@Composable
fun CompletedRow(job: DeliveryAssignment) {
    RiderCard(padding = 14.dp) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = Icons.Filled.CheckCircle,
                tint = RiderTheme.colors.success,
                container = RiderTheme.colors.successSoft,
                size = 40.dp,
            )

            Column(Modifier.weight(1f)) {
                job.offer.ticketNumber?.let {
                    Text(
                        text = "Order #$it",
                        style = MaterialTheme.typography.bodySmall,
                        color = RiderTheme.colors.textTertiary,
                    )
                }
                Text(
                    text = job.offer.pickup.storeName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = job.offer.dropoffArea ?: "Delivered",
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = Money.pesoRounded(job.offer.deliveryFeeCents),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                StatusChip("Delivered", ChipTone.Success)
            }
        }
    }
}

/**
 * The money band: what the job pays, and the small print beside it.
 *
 * The fee is the largest thing on the card by a wide margin, because on a board
 * of six it is the only thing most riders read before deciding. Everything in
 * [meta] is a detail that refines a decision already half made, so it sits to
 * the right at a size that cannot be mistaken for the number.
 */
@Composable
private fun Fee(
    cents: Long,
    caption: String,
    modifier: Modifier = Modifier,
    meta: @Composable ColumnScope.() -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = Money.pesoRounded(cents),
                style = RiderTextStyles.money,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = caption,
                style = MaterialTheme.typography.bodySmall,
                color = RiderTheme.colors.textTertiary,
            )
        }

        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
            content = meta,
        )
    }
}

/** One end of a trip. */
private data class Stop(
    val icon: ImageVector,
    val label: String,
    val title: String,
    val detail: String?,
    /** Where the rider is going next. Exactly one stop is lit, or neither. */
    val highlighted: Boolean,
    /** Already behind them. */
    val done: Boolean = false,
)

/**
 * The two ends of the trip, with the road drawn between them.
 *
 * The rail is not decoration. Two addresses stacked in a card are two facts a
 * rider has to work out the relationship between; two discs joined by a dotted
 * line are a journey, read in one glance, in the direction it is travelled.
 */
@Composable
private fun Route(pickup: Stop, dropoff: Stop) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp),
    ) {
        StopRow(pickup)
        Rail()
        StopRow(dropoff)
    }
}

@Composable
private fun StopRow(stop: Stop) {
    // Green means "this is where you are going", and only that. A finished stop
    // goes grey with a tick rather than green with one: on a card where both
    // ends are green, a rider has to read the icons to work out which half of
    // the trip they are on, which is exactly the question the rail is here to
    // answer without reading.
    val tint = if (stop.highlighted) {
        MaterialTheme.colorScheme.primary
    } else {
        RiderTheme.colors.textTertiary
    }
    val container = if (stop.highlighted) {
        RiderTheme.colors.accentSoft
    } else {
        RiderTheme.colors.fill
    }

    Row(verticalAlignment = Alignment.Top) {
        IconBadge(
            icon = if (stop.done) Icons.Filled.CheckCircle else stop.icon,
            tint = tint,
            container = container,
            size = 34.dp,
        )
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = stop.label.uppercase(),
                style = RiderTextStyles.overline,
                color = RiderTheme.colors.textTertiary,
            )
            Text(
                text = stop.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            stop.detail?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.textSecondary,
                )
            }
        }
    }
}

/** The dotted line between the two discs, aligned to their centres. */
@Composable
private fun Rail() {
    Box(Modifier.width(34.dp), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(3) {
                Box(
                    Modifier
                        .size(3.dp)
                        .clip(PillShape)
                        .background(RiderTheme.colors.separator),
                )
            }
        }
    }
}

/**
 * Whether there is money to collect, said either way.
 *
 * "Already paid" is worth a line of its own rather than an absence: a rider who
 * sees nothing about cash on a card cannot tell a prepaid order from a card
 * that forgot to mention it, and the cost of guessing wrong is an argument at
 * somebody's door.
 */
@Composable
private fun CashLine(offer: DeliveryOffer) {
    Spacer(Modifier.height(14.dp))

    if (offer.collectsCash) {
        SoftBanner(
            icon = Icons.Filled.Payments,
            text = "Collect ${Money.peso(offer.collectCents)} at the door",
            tint = RiderTheme.colors.cash,
            container = RiderTheme.colors.cashSoft,
        )
    } else {
        SoftBanner(
            icon = Icons.Filled.CheckCircle,
            text = "Already paid — nothing to collect",
            tint = RiderTheme.colors.success,
            container = RiderTheme.colors.successSoft,
        )
    }
}
