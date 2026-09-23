package com.omaykan.seller.feature.orders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.omaykan.seller.core.designsystem.CardShape
import com.omaykan.seller.core.designsystem.Pill
import com.omaykan.seller.core.designsystem.PillShape
import com.omaykan.seller.core.designsystem.PrimaryButton
import com.omaykan.seller.core.designsystem.SecondaryButton
import com.omaykan.seller.core.designsystem.SectionLabel
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.map.StaticMap
import com.omaykan.seller.core.model.DeliveryStage
import com.omaykan.seller.core.model.RecentRider
import com.omaykan.seller.core.model.RiderDirectory
import com.omaykan.seller.core.model.SavedRider
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.model.PaymentMethod
import com.omaykan.seller.core.model.SellerOrder

/**
 * The two decisions that need more than a tap.
 *
 * Sheets rather than dialogs: both want the keyboard or a column of choices,
 * and a sheet keeps the order it is about visible above it. A merchant
 * confirming a ₱480 payment should be able to see the ₱480.
 */
@Composable
fun OrderSheets(state: OrdersUiState, viewModel: OrdersViewModel) {
    // Read out of the live list rather than captured when the sheet opened, so
    // a poll landing underneath updates what the sheet is showing. An order
    // settled from the till while this was open should not still be offering to
    // settle it.
    val order = state.sheetOrder() ?: return

    when (state.sheet) {
        is OrderSheet.Rider -> RiderSheet(
            order = order,
            riders = state.riders,
            busy = state.busyOrderId == order.id,
            error = state.actionError,
            onDismiss = viewModel::dismissSheet,
            onAssign = { name, phone, save -> viewModel.assignRider(order.id, name, phone, save) },
            onPick = { savedRiderId -> viewModel.assignSavedRider(order.id, savedRiderId) },
            onKeep = { rider -> viewModel.keepRider(rider.riderId, rider.name, rider.phone) },
            onReturnToBoard = { viewModel.returnToBoard(order.id) },
        )

        is OrderSheet.Settle -> SettleSheet(
            order = order,
            busy = state.busyOrderId == order.id,
            error = state.actionError,
            onDismiss = viewModel::dismissSheet,
            onSettle = { method -> viewModel.settle(order.id, method) },
        )

        OrderSheet.None -> Unit
    }
}

/** The corner the sheets share, deeper than a card's so the panel reads as a
 *  layer over the screen rather than another card on it. */
private val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)

/**
 * "Who is taking this?"
 *
 * A name and, optionally, a number. There are rider accounts in this system and
 * a rider portal that claims jobs off a board — but a shop also hands an order
 * to whoever is standing there, and that person needs recording too. Naming
 * somebody here is what puts a name on the customer's tracking page and gives
 * the shop a number to ring when an order goes quiet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun RiderSheet(
    order: SellerOrder,
    riders: RiderDirectory,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onAssign: (String, String?, Boolean) -> Unit,
    onPick: (String) -> Unit,
    onKeep: (RecentRider) -> Unit,
    onReturnToBoard: () -> Unit,
) {
    // Straight to full height, and scrollable inside.
    //
    // The default opens a tall sheet half-way, which put the Assign button
    // below the fold on a phone — a merchant would tap "assign a rider", see a
    // map and two boxes, and have to guess that dragging reveals the button
    // that does it. Seen on a Pixel 6.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember(order.id) { mutableStateOf(order.riderName.orEmpty()) }
    var phone by remember(order.id) { mutableStateOf(order.riderPhone.orEmpty()) }
    // Defaulted on: a shop typing a name into this box almost always has a
    // rider they will use again, and the cost of a wrong guess is one row they
    // can delete — against retyping the same number four times a day.
    var save by remember(order.id) { mutableStateOf(true) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
        ) {
            SheetHeading(
                title = if (order.riderName == null) "Assign a rider" else "Change the rider",
                subtitle = order.customerName.takeIf { it.isNotBlank() },
                ticket = order.ticketNumber,
                amount = Money.peso(order.totalCents),
            )

            // Where the rider is, when there is one reporting. Above the form
            // because on a re-open — "change the rider" — the question the
            // merchant actually has is "where has he got to", and the answer
            // is often that they do not need to change anybody.
            DeliveryMapImage(order)

            // The shop's own riders, as a row of chips. One tap assigns, which
            // is the whole reason for keeping them.
            if (riders.saved.isNotEmpty()) {
                SectionLabel("Your riders", Modifier.padding(top = 20.dp, bottom = 10.dp))
                RiderChips(
                    riders = riders.saved,
                    enabled = !busy,
                    onPick = onPick,
                )
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                label = { Text("Rider's name") },
                singleLine = true,
                enabled = !busy,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
            )

            OutlinedTextField(
                value = phone,
                onValueChange = { phone = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                label = { Text("Phone (optional)") },
                singleLine = true,
                enabled = !busy,
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = save, onCheckedChange = { save = it }, enabled = !busy)
                Text(
                    "Save for next time",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SellerTheme.colors.textSecondary,
                )
            }

            error?.let { SheetError(it) }

            PrimaryButton(
                label = "Assign",
                onClick = { onAssign(name, phone, save) },
                enabled = name.isNotBlank(),
                busy = busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )

            // Only before pickup. Once the food is in the bag the order is
            // physically with the rider and handing it back is a phone call —
            // the server refuses this, and so does the button.
            if (order.riderName != null && order.deliveryStage == DeliveryStage.Assigned) {
                SecondaryButton(
                    label = "Put back on the board",
                    onClick = onReturnToBoard,
                    enabled = !busy,
                    leading = Icons.AutoMirrored.Filled.Undo,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp),
                )
            }

            // Riders who have carried for this shop but are not on its list.
            // The shop already learned that name and number when the rider
            // turned up at the counter; this is the tap that stops it being
            // retyped for the rest of the year.
            if (riders.recent.isNotEmpty()) {
                SectionLabel(
                    "Delivered for you before",
                    Modifier.padding(top = 22.dp, bottom = 10.dp),
                )
                // Wrapping rather than a Row: three names of any length do not
                // fit across a phone, and a chip cut off at the edge is a chip
                // nobody taps.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    riders.recent.take(3).forEach { rider ->
                        SecondaryButton(
                            label = "+ ${rider.name}",
                            onClick = { onKeep(rider) },
                            enabled = !busy,
                        )
                    }
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/**
 * "How did the money arrive?"
 *
 * Three rows and no amount field. The amount is the order total — that is what
 * the customer owes and what this records — and a phone is not where a drawer's
 * arithmetic belongs. Tender and change stay on the till that has the drawer;
 * see SellerOrderRepository.settle.
 *
 * Rows rather than buttons, because each one is a choice that immediately does
 * the thing: an icon says which way the money came, and the chevron says the
 * tap lands somewhere. Three identical outlined buttons made the destructive
 * one look like the cancel.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettleSheet(
    order: SellerOrder,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSettle: (PaymentMethod) -> Unit,
) {
    // Full height for the same reason as the rider sheet: three rows of
    // payment methods do not fit in a half sheet, and the third one is a
    // choice a merchant should not have to drag to find.
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = SheetShape,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            Modifier
                .padding(horizontal = 20.dp)
                .navigationBarsPadding(),
        ) {
            SheetHeading(
                title = "Mark ${Money.peso(order.totalCents)} paid",
                subtitle = order.customerName.takeIf { it.isNotBlank() },
                ticket = order.ticketNumber,
                amount = null,
            )

            Text(
                // Said plainly, because it is the one thing on this screen that
                // touches somebody's till reconciliation. This is a record that
                // money arrived, not a charge — there is no gateway behind it.
                text = "This records that you received the money. It cannot be undone from here.",
                style = MaterialTheme.typography.bodyMedium,
                color = SellerTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp),
            )

            error?.let { SheetError(it) }

            SectionLabel("How it arrived", Modifier.padding(top = 22.dp, bottom = 10.dp))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                PaymentMethod.entries.forEach { method ->
                    MethodRow(
                        method = method,
                        enabled = !busy,
                        onClick = { onSettle(method) },
                    )
                }
            }

            if (busy) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}

/** One way the money can have arrived. */
@Composable
private fun MethodRow(method: PaymentMethod, enabled: Boolean, onClick: () -> Unit) {
    val icon: ImageVector = when (method) {
        PaymentMethod.Cash -> Icons.Filled.Payments
        PaymentMethod.EWallet -> Icons.Filled.AccountBalanceWallet
        PaymentMethod.Card -> Icons.Filled.CreditCard
    }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(CardShape)
            .border(1.dp, SellerTheme.colors.separator, CardShape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(SellerTheme.colors.accentSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = SellerTheme.colors.onAccentSoft,
            )
        }

        Text(
            text = method.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )

        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = SellerTheme.colors.textTertiary,
        )
    }
}

/**
 * The top of a sheet: what this is about, and which order.
 *
 * The ticket number rides in a pill beside the title rather than in the line of
 * grey under it. It is the one thing a merchant cross-checks against the paper
 * on the counter before they touch anything, so it should not be the smallest
 * text on the panel.
 */
@Composable
private fun SheetHeading(title: String, subtitle: String?, ticket: String?, amount: String?) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ticket?.let {
                Pill(text = "#$it", tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.size(8.dp))
            }
            amount?.let {
                Pill(text = it, tint = SellerTheme.colors.textSecondary)
            }
        }

        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = if (ticket != null || amount != null) 10.dp else 0.dp),
        )

        subtitle?.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = SellerTheme.colors.textSecondary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

/**
 * The server's refusal, shown where the decision was made.
 *
 * Inside the sheet rather than behind it, because the two refusals a merchant
 * will actually meet here — an order settled twice, a rider assigned to a
 * pickup — are answers to what they just tapped, and they should not have to
 * close the sheet to find out why nothing happened.
 */
@Composable
private fun SheetError(message: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 16.dp)
            .clip(CardShape)
            .background(SellerTheme.colors.danger.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.Error,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = SellerTheme.colors.danger,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/**
 * The shop's riders, as chips.
 *
 * The dot is the fact that matters when choosing: filled means that rider's app
 * is reporting a position right now, so assigning reaches a phone. Hollow means
 * they have an account but are not on; absent means there is no account at all
 * and the shop is about to make a phone call. A merchant should be able to tell
 * those three apart before they tap, not after nothing happens for ten minutes.
 */
@Composable
private fun RiderChips(
    riders: List<SavedRider>,
    enabled: Boolean,
    onPick: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        riders.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { rider ->
                    // A suspended account still shows, greyed. A shop needs to
                    // see *why* their usual rider is unavailable rather than
                    // find them silently missing from the list.
                    val pickable = enabled && rider.assignable

                    Row(
                        Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(PillShape)
                            .background(SellerTheme.colors.accentSoft)
                            .clickable(enabled = pickable) { onPick(rider.id) }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (rider.onPlatform) {
                            Box(
                                Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (rider.online) {
                                            SellerTheme.colors.success
                                        } else {
                                            SellerTheme.colors.textTertiary
                                        },
                                    ),
                            )
                            Spacer(Modifier.size(8.dp))
                        }
                        Text(
                            text = rider.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = if (pickable) {
                                SellerTheme.colors.onAccentSoft
                            } else {
                                SellerTheme.colors.textTertiary
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Keeps a lone chip on an odd row from stretching to full width.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

/**
 * Where the rider is, as one picture.
 *
 * A fetched PNG rather than an embedded map — see core/map/StaticMap.kt for the
 * argument, which is mostly that a merchant at a counter is asking "is he
 * close" and not asking to pan around Baguio.
 *
 * Fetched only while this sheet is open, because each distinct image is a
 * metered Mapbox request. Coil's cache means a rider who has not moved between
 * two openings of the sheet does not cost a second one.
 *
 * Shows nothing at all rather than an error when there is no token, no
 * coordinates, or no rider reporting. That is the state every order was in
 * before this feature existed and the rest of the sheet works exactly as it did.
 */
@Composable
private fun DeliveryMapImage(order: SellerOrder) {
    val url = remember(order.riderPosition, order.route) {
        StaticMap.url(
            rider = order.riderPosition,
            pickup = order.route?.pickup,
            dropoff = order.route?.dropoff,
        )
    } ?: return

    Box(Modifier.padding(top = 18.dp)) {
        AsyncImage(
            model = url,
            contentDescription = "Where the rider is",
            modifier = Modifier
                .fillMaxWidth()
                .height(170.dp)
                .clip(CardShape)
                .background(SellerTheme.colors.fill),
            contentScale = ContentScale.Crop,
        )

        // The timestamp sits on the picture rather than under it. "Last seen
        // eleven minutes ago" is the caption that decides whether the map means
        // anything, and a merchant who reads the map and stops has read the
        // wrong half.
        order.riderPosition?.let { position ->
            Text(
                text = if (position.stale) {
                    "Last seen ${position.ageLabel} — " +
                        "${order.riderName ?: "the rider"}'s phone has gone quiet"
                } else {
                    "${order.riderName ?: "The rider"} was here ${position.ageLabel}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = SellerTheme.colors.onCanopy,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(10.dp)
                    .clip(PillShape)
                    .background(SellerTheme.colors.canopy.copy(alpha = 0.82f))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }
    }
}
