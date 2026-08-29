package com.omaykan.storefront.feature.order

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.Refreshable
import com.omaykan.storefront.core.designsystem.RefreshableFill
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.core.model.TrackedOrder
import com.omaykan.storefront.core.model.TrackedOrderItem
import com.omaykan.storefront.feature.cart.CartTopBar
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The confirmation, and then the tracking screen — the same screen, because
 * they are the same thing a minute apart.
 *
 * Laid out to the same reference the Orders tab follows, in the order somebody
 * holding the phone asks the questions: where has my order got to, who is
 * bringing it, where is it going, what was in it, what does it come to. The
 * total and the way out are pinned to the bottom, because those are the two
 * things worth having on screen at any scroll position.
 *
 * Every number here is the server's. The cart's running total was an estimate;
 * this is what the shop will actually ask for, priced from the merchant's own
 * records, and it is the only total the shopper should ever be asked to trust.
 *
 * Three things the reference carries that are absent here, and their absence is
 * deliberate. It heads the items with the seller, which this API does not
 * return. It offers a return or a refund, and there is no such endpoint — a
 * button that cannot do what it says is worse than no button. And it invites a
 * review, which this platform does not have.
 */
@Composable
fun OrderScreen(
    onDone: () -> Unit,
    justPlaced: Boolean = false,
    viewModel: OrderViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val order = state.order

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        CartTopBar(
            title = "Your order",
            subtitle = order?.let { placedOn(it.placedAt) }?.let { "Ordered $it" }.orEmpty(),
            onBack = onDone,
        )

        when {
            state.loading && order == null -> LoadingState()

            order == null -> Refreshable(
                refreshing = state.refreshing,
                onRefresh = viewModel::refresh,
                modifier = Modifier.weight(1f),
            ) {
                RefreshableFill {
                    MessageState(
                        title = "We could not load this order",
                        detail = state.error,
                        icon = Icons.Filled.CloudOff,
                        actionLabel = "Try again",
                        onAction = viewModel::refresh,
                    )
                }
            }

            else -> {
                // Only the scrolling detail, so the indicator does not come
                // down over the top bar or the bar pinned at the bottom.
                Refreshable(
                    refreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                    modifier = Modifier.weight(1f),
                ) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        if (justPlaced && order.status == PLACED_STATUS) PlacedBanner(order)
                        TrackCard(order)
                        FulfillmentCard(order)
                        ItemsCard(order, state.photos)
                        PaymentCard(order)
                    }
                }

                BottomBar(
                    order = order,
                    label = if (justPlaced) "Keep shopping" else "Back to the market",
                    onDone = onDone,
                )
            }
        }
    }
}

/**
 * "Your order is in." The one moment this screen is a confirmation.
 *
 * A strip rather than a card, sitting above the tracking that answers the more
 * useful question — the shopper wants both at once: that the order got through,
 * and what the shop is doing about it.
 *
 * Shown only while the order is still in the state the server placed it in.
 * Once the shop has moved it along, the card below is the better news, and a
 * banner still announcing that the order arrived would be reading the past out
 * loud.
 */
@Composable
private fun PlacedBanner(order: TrackedOrder) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(OmaykanTheme.colors.success.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = OmaykanTheme.colors.success,
        )
        Text(
            text = if (order.isDelivery) {
                "Your order is in. We will call when it is on its way."
            } else {
                "Your order is in. We will call when it is ready."
            },
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * Where the order has got to: the ticket it goes by, the journey with the step
 * in force lit, and the rider once there is one to name.
 */
@Composable
private fun TrackCard(order: TrackedOrder) {
    Card {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // The ticket is the only name this order has, and the thing the
            // shopper will be asked for at the counter — so it is a chip, not
            // another line of body text.
            order.ticketNumber?.let { ticket ->
                Text(
                    text = "Ticket #$ticket",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(OmaykanTheme.colors.fill)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = if (order.isDelivery) "Delivery" else "Pick-up",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
            )
        }

        Timeline(trackSteps(order), cancelled = order.cancelled)

        val rider = order.riderName?.takeIf { it.isNotBlank() }
        if (rider != null && !order.cancelled) {
            HorizontalDivider(
                thickness = 1.dp,
                color = OmaykanTheme.colors.separator,
                modifier = Modifier.padding(top = 14.dp),
            )
            RiderRow(name = rider, phone = order.riderPhone?.takeIf { it.isNotBlank() })
        }
    }
}

/**
 * The journey, top to bottom, with one step lit.
 *
 * The rail is drawn rather than assembled out of a component, because the two
 * things that make it readable are both about measurement: a connector exactly
 * as tall as the text beside it, and a segment coloured only where the order
 * has actually been. `IntrinsicSize.Min` is where the first of those lives.
 */
@Composable
private fun Timeline(steps: List<TrackStep>, cancelled: Boolean) {
    val lit = if (cancelled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    Column(Modifier.fillMaxWidth()) {
        steps.forEachIndexed { index, step ->
            val last = index == steps.lastIndex

            Row(Modifier.height(IntrinsicSize.Min)) {
                Column(
                    Modifier
                        .width(18.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Dot(step.state, lit)
                    if (!last) {
                        Box(
                            Modifier
                                .width(2.dp)
                                .weight(1f)
                                .background(
                                    // A segment is coloured only once the order
                                    // is past it, so the rail is a record and
                                    // never a promise.
                                    if (step.state == StepState.Done) {
                                        lit
                                    } else {
                                        OmaykanTheme.colors.separator
                                    },
                                ),
                        )
                    }
                }

                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp, bottom = if (last) 0.dp else 14.dp),
                ) {
                    Text(
                        text = step.label,
                        style = if (step.state == StepState.Current) {
                            MaterialTheme.typography.titleLarge
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = if (step.state == StepState.Current) {
                            FontWeight.Bold
                        } else {
                            FontWeight.Normal
                        },
                        color = when (step.state) {
                            StepState.Current -> if (cancelled) lit else Color.Unspecified
                            StepState.Done -> OmaykanTheme.colors.textSecondary
                            StepState.Pending -> OmaykanTheme.colors.textTertiary
                        },
                    )
                    step.detail?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = OmaykanTheme.colors.textSecondary,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                }
            }
        }
    }
}

/** Behind, here, or still ahead — the three marks the rail can carry. */
@Composable
private fun Dot(state: StepState, lit: Color) {
    when (state) {
        // Sized and ringed rather than merely coloured, so the step in force is
        // findable without reading, and on a screen where colour alone is not
        // enough to carry it.
        StepState.Current -> Box(
            Modifier
                .size(14.dp)
                .clip(CircleShape)
                .background(lit),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.background),
            )
        }

        StepState.Done -> Box(
            Modifier
                .padding(top = 4.dp)
                .size(9.dp)
                .clip(CircleShape)
                .background(lit),
        )

        StepState.Pending -> Box(
            Modifier
                .padding(top = 4.dp)
                .size(9.dp)
                .border(1.5.dp, OmaykanTheme.colors.separator, CircleShape),
        )
    }
}

/**
 * The courier row from the reference, and the one action on this screen worth a
 * tap: ringing the person holding your food.
 *
 * The dialler is opened rather than the call placed — that asks for no
 * permission, and leaves the shopper the last word on a number the shop
 * supplied.
 */
@Composable
private fun RiderRow(name: String, phone: String?) {
    val context = LocalContext.current

    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Outlined.LocalShipping,
            contentDescription = null,
            tint = OmaykanTheme.colors.textSecondary,
            modifier = Modifier.size(18.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 10.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = phone ?: "Your rider",
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
            )
        }
        if (phone != null) {
            Row(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(OmaykanTheme.colors.fill)
                    .clickable { context.dial(phone) }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Filled.Phone,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = "Call",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
    }
}

/** Where the order is going, or where it is to be collected. */
@Composable
private fun FulfillmentCard(order: TrackedOrder) {
    val address = order.deliveryAddress?.takeIf { it.isNotBlank() }
    // A delivery with no address on it would be a heading over nothing.
    if (order.isDelivery && address == null) return

    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (order.isDelivery) {
                    Icons.Filled.LocationOn
                } else {
                    Icons.Outlined.Storefront
                },
                contentDescription = null,
                tint = OmaykanTheme.colors.textSecondary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = if (order.isDelivery) "Delivering to" else "Collecting at the shop",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
        Text(
            text = address
                ?: "Give your ticket number at the counter and they will hand it over.",
            style = MaterialTheme.typography.bodyMedium,
            color = if (order.isDelivery) Color.Unspecified else OmaykanTheme.colors.textSecondary,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/**
 * What was bought, with a picture wherever this phone has one.
 *
 * The photo comes off the local catalog cache and is often absent — an order
 * placed at a shop whose shelf this phone has not opened has none, and most
 * sari-sari stock has never been photographed anyway. The row is built so that
 * the missing case is the ordinary one: the tile holds its place either way.
 *
 * Every line, not the two the list card shows. Tapping into an order is what
 * somebody does when the summary was not enough.
 */
@Composable
private fun ItemsCard(order: TrackedOrder, photos: Map<String, String>) {
    Card {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Items",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = countOfItems(order),
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
            )
        }

        order.items.forEach { item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp),
            ) {
                RemoteImage(
                    url = photos[item.productId],
                    contentDescription = null,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(OmaykanTheme.colors.fill),
                )
                Column(
                    Modifier
                        .weight(1f)
                        .padding(start = 12.dp),
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "${Money.peso(item.unitPriceCents)} × ${quantity(item.quantity)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = OmaykanTheme.colors.textSecondary,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = Money.peso(item.lineTotalCents),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** The breakdown, and what is still owed at the door. */
@Composable
private fun PaymentCard(order: TrackedOrder) {
    Card {
        TotalRow("Subtotal", order.subtotalCents)
        if (order.taxCents > 0) TotalRow("Tax", order.taxCents)
        if (order.isDelivery) TotalRow("Delivery", order.deliveryFeeCents)

        HorizontalDivider(
            thickness = 1.dp,
            color = OmaykanTheme.colors.separator,
            modifier = Modifier.padding(vertical = 10.dp),
        )

        Row {
            Text(
                text = "Total",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = Money.peso(order.totalCents),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(OmaykanTheme.colors.fill)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (order.settled) {
                    Icons.Filled.CheckCircle
                } else {
                    Icons.Outlined.Payments
                },
                contentDescription = null,
                tint = if (order.settled) {
                    OmaykanTheme.colors.success
                } else {
                    OmaykanTheme.colors.textSecondary
                },
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = paymentNote(order),
                style = MaterialTheme.typography.bodySmall,
                color = OmaykanTheme.colors.textSecondary,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

/**
 * This app never charges anybody — the platform collects nothing and settles
 * everything at the door — so the unpaid line is a reassurance rather than a
 * demand.
 */
private fun paymentNote(order: TrackedOrder): String {
    if (order.settled) return "Paid. Thank you."

    val method = when (order.paymentMethod) {
        "ewallet" -> "GCash"
        else -> "cash"
    }
    return "Pay $method when you get your order. Nothing has been charged."
}

/**
 * The total and the way out, pinned.
 *
 * The breakdown above ends in the same number, and that is the point: on a long
 * order the total scrolls away exactly when somebody is checking it against
 * what they are being asked for at the door.
 */
@Composable
private fun BottomBar(order: TrackedOrder, label: String, onDone: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        HorizontalDivider(thickness = 1.dp, color = OmaykanTheme.colors.separator)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .navigationBarsPadding(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.bodySmall,
                    color = OmaykanTheme.colors.textSecondary,
                )
                Text(
                    text = Money.peso(order.totalCents),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
            Button(
                onClick = onDone,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OmaykanTheme.colors.ink,
                    contentColor = OmaykanTheme.colors.onInk,
                ),
            ) {
                Text(text = label, modifier = Modifier.padding(vertical = 4.dp))
            }
        }
    }
}

@Composable
private fun TotalRow(label: String, cents: Long) {
    Row(Modifier.padding(top = 4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = OmaykanTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text(text = Money.peso(cents), style = MaterialTheme.typography.bodyMedium)
    }
}

/** Hands a number to whatever the phone dials with, or does nothing. */
private fun Context.dial(phone: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_DIAL, "tel:${phone.filterNot { it.isWhitespace() }}".toUri())
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}

/** "3 items", counting the things bought rather than the lines they sit on. */
private fun countOfItems(order: TrackedOrder): String {
    val units = order.items.sumOf(TrackedOrderItem::quantity)
    return if (units == 1.0) "1 item" else "${quantity(units)} items"
}

/** "2", not "2.0" — whole counts are the overwhelming case, and 2.0 kg is not. */
private fun quantity(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else value.toString()

/**
 * "26 August 2025 at 3:04 PM", in the phone's own zone, from the ISO-8601 the
 * API sends.
 *
 * Parsed leniently: a timestamp this screen cannot read is a line it leaves
 * out, not a screen it refuses to draw.
 */
private fun placedOn(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return runCatching {
        OffsetDateTime.parse(iso)
            .atZoneSameInstant(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("d MMMM yyyy 'at' h:mm a", Locale("en", "PH")))
    }.getOrNull()
}

/**
 * What the server puts a brand new online order into (OnlineOrderController) —
 * so it is also how this screen knows the shop has not touched it yet.
 */
private const val PLACED_STATUS = "preparing"

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, OmaykanTheme.colors.separator, RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
    ) {
        content()
    }
}
