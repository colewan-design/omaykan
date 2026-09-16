package com.omaykan.rider.feature.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SupportAgent
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.data.WorkState
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.LightStatusBarIcons
import com.omaykan.rider.core.designsystem.MountainBackdrop
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.RiderAvatar
import com.omaykan.rider.core.designsystem.RiderLockup
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.designsystem.SerifFamily
import com.omaykan.rider.core.designsystem.SpacedCaps
import com.omaykan.rider.core.location.SharingState
import com.omaykan.rider.core.model.Money
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.feature.common.Notice
import com.omaykan.rider.feature.work.WorkTab

private val HomeCardShape = RoundedCornerShape(18.dp)
private val ActionCardShape = RoundedCornerShape(14.dp)
private val HeroOverlap = 36.dp

/** Rider home built from the supplied mobile reference with production data. */
@Composable
fun HomeScreen(
    rider: RiderProfile,
    onOpenJobs: (WorkTab) -> Unit,
    onOpenEarnings: () -> Unit,
    onOpenAccount: () -> Unit,
    onOpenJob: (String) -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val work by viewModel.work.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sharing by viewModel.sharingState.collectAsStateWithLifecycle()
    val watchingJobs by viewModel.watchingJobs.collectAsStateWithLifecycle()

    HomeContent(
        rider = rider,
        work = work,
        state = state,
        watchingJobs = watchingJobs,
        sharing = sharing,
        locationGranted = viewModel.locationGranted(),
        onSetJobAlerts = viewModel::setJobAlerts,
        onSetSharing = viewModel::setSharing,
        onRefresh = viewModel::refresh,
        onOpenJobs = onOpenJobs,
        onOpenEarnings = onOpenEarnings,
        onOpenAccount = onOpenAccount,
        onOpenJob = onOpenJob,
    )
}

/** Stateless production layout, also rendered by the debug gallery. */
@Composable
internal fun HomeContent(
    rider: RiderProfile,
    work: WorkState,
    state: HomeUiState,
    watchingJobs: Boolean,
    sharing: SharingState,
    locationGranted: Boolean,
    onSetJobAlerts: (Boolean) -> Unit,
    onSetSharing: (Boolean) -> Unit,
    onRefresh: () -> Unit,
    onOpenJobs: (WorkTab) -> Unit,
    onOpenEarnings: () -> Unit,
    onOpenAccount: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onOpenJob: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        HomeTopBar(rider = rider, onOpenAccount = onOpenAccount, onRefresh = onRefresh)

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            HomeHero(
                rider = rider,
                watchingJobs = watchingJobs,
                sharing = sharing,
                locationGranted = locationGranted,
                onSetJobAlerts = onSetJobAlerts,
                onSetSharing = onSetSharing,
            )

            Column(
                Modifier
                    .offset(y = -HeroOverlap)
                    .padding(horizontal = 14.dp),
            ) {
                EarningsCard(state = state, cashCents = work.cashInHand)

                state.error?.let { Notice(it, warning = true) }
                work.error?.let { Notice(it, warning = true) }

                QuickActionsHeader(Modifier.padding(top = 12.dp))
                QuickActions(
                    onJobBoard = { onOpenJobs(WorkTab.Board) },
                    onMyJobs = { onOpenJobs(WorkTab.Mine) },
                    onCash = onOpenEarnings,
                    onSupport = onOpenAccount,
                    modifier = Modifier.padding(top = 6.dp),
                )

                AvailableJobsHeader(
                    onViewAll = { onOpenJobs(WorkTab.Board) },
                    modifier = Modifier.padding(top = 10.dp),
                )
                AvailableJobsCard(
                    work = work,
                    onOpenBoard = { onOpenJobs(WorkTab.Board) },
                    modifier = Modifier.padding(top = 6.dp),
                )

                Spacer(Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun HomeTopBar(rider: RiderProfile, onOpenAccount: () -> Unit, onRefresh: () -> Unit) {
    val colors = RiderTheme.colors
    LightStatusBarIcons()

    Box(
        Modifier
            .fillMaxWidth()
            .background(colors.canopy)
            .statusBarsPadding()
            .height(64.dp)
            .padding(horizontal = 12.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterStart)
                .size(46.dp)
                .clip(PillShape)
                .clickable(onClickLabel = "Open your profile", onClick = onOpenAccount)
                .semantics(mergeDescendants = true) { contentDescription = "Your profile" },
            contentAlignment = Alignment.Center,
        ) {
            RiderAvatar(name = rider.name, photoUrl = rider.photoUrl, size = 40.dp)
        }

        RiderLockup(Modifier.align(Alignment.Center), fontSize = 19.sp)

        CanopyIconButton(
            icon = Icons.Filled.Refresh,
            description = "Refresh",
            onClick = onRefresh,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
private fun HomeHero(
    rider: RiderProfile,
    watchingJobs: Boolean,
    sharing: SharingState,
    locationGranted: Boolean,
    onSetJobAlerts: (Boolean) -> Unit,
    onSetSharing: (Boolean) -> Unit,
) {
    val colors = RiderTheme.colors
    val firstName = rider.name.trim().substringBefore(' ').ifBlank { "Rider" }

    MountainBackdrop(
        Modifier
            .fillMaxWidth()
            .height(275.dp),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = HeroOverlap + 8.dp),
        ) {
            AvailabilityCard(
                watchingJobs = watchingJobs,
                sharing = sharing,
                locationGranted = locationGranted,
                onSetJobAlerts = onSetJobAlerts,
                onSetSharing = onSetSharing,
            )

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Hello, $firstName.",
                    style = TextStyle(
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 27.sp,
                        lineHeight = 32.sp,
                    ),
                    color = colors.onCanopy,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "“Same mountains.\nMore good deliveries.”",
                    style = TextStyle(
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Normal,
                        fontStyle = FontStyle.Italic,
                        fontSize = 18.sp,
                        lineHeight = 24.sp,
                    ),
                    color = colors.onCanopy,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    text = "RIDING AS ${rider.plateNumber.uppercase()}",
                    style = SpacedCaps,
                    color = colors.onCanopy,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        }
    }
}

/** A unified online control with independent location and alert status chips. */
@Composable
private fun AvailabilityCard(
    watchingJobs: Boolean,
    sharing: SharingState,
    locationGranted: Boolean,
    onSetJobAlerts: (Boolean) -> Unit,
    onSetSharing: (Boolean) -> Unit,
) {
    val colors = RiderTheme.colors
    val online = watchingJobs && sharing.sharing
    val availabilityMessage = when {
        online -> "You can receive new delivery requests."
        sharing.sharing -> "Turn on job alerts to receive delivery requests."
        watchingJobs -> "Turn on location to receive delivery requests."
        else -> "Turn on location and job alerts to receive delivery requests."
    }
    var enableAlertsAfterLocation by remember { mutableStateOf(false) }

    val notificationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    val locationRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { granted ->
        if (granted.values.any { it }) {
            onSetSharing(true)
            if (enableAlertsAfterLocation) {
                onSetJobAlerts(true)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        enableAlertsAfterLocation = false
    }

    fun enableAlerts() {
        onSetJobAlerts(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationRequest.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun enableLocation(andAlerts: Boolean) {
        if (locationGranted) {
            onSetSharing(true)
            if (andAlerts) enableAlerts()
        } else {
            enableAlertsAfterLocation = andAlerts
            locationRequest.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    ElevatedHomeCard(padding = 0.dp) {
        Row(
            Modifier.padding(start = 12.dp, end = 8.dp, top = 13.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AvailabilityIndicator(online)

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 8.dp, end = 6.dp),
            ) {
                Text(
                    text = if (online) "You’re online" else "You’re currently offline",
                    style = TextStyle(
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = availabilityMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    lineHeight = 15.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }

            Button(
                onClick = {
                    if (online) {
                        onSetSharing(false)
                        onSetJobAlerts(false)
                    } else {
                        enableLocation(andAlerts = true)
                    }
                },
                modifier = Modifier
                    .width(110.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(11.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.cta, contentColor = colors.onCta),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp),
            ) {
                Text(
                    text = if (online) "GO OFFLINE" else "GO ONLINE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = colors.hairline)

        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AvailabilityChip(
                icon = Icons.Filled.LocationOn,
                text = if (sharing.sharing) "Location On" else "Location Off",
                on = sharing.sharing,
                onClick = {
                    if (sharing.sharing) onSetSharing(false) else enableLocation(andAlerts = false)
                },
                modifier = Modifier.weight(1f),
            )
            AvailabilityChip(
                icon = Icons.Filled.Notifications,
                text = if (watchingJobs) "Job Alerts On" else "Job Alerts Off",
                on = watchingJobs,
                onClick = {
                    if (watchingJobs) onSetJobAlerts(false) else enableAlerts()
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AvailabilityIndicator(online: Boolean) {
    val colors = RiderTheme.colors
    Box(
        Modifier
            .size(30.dp)
            .clip(PillShape)
            .background(if (online) colors.success.copy(alpha = 0.14f) else colors.peach.copy(alpha = 0.65f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(13.dp)
                .clip(PillShape)
                .background(if (online) colors.success else colors.cta.copy(alpha = 0.72f)),
        )
    }
}

@Composable
private fun AvailabilityChip(
    icon: ImageVector,
    text: String,
    on: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = RiderTheme.colors
    val tint = if (on) colors.success else colors.onPeach
    val container = if (on) colors.successSoft else colors.peach.copy(alpha = 0.62f)

    Row(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(container)
            .clickable(role = Role.Switch, onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(17.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium,
            color = tint,
            maxLines = 1,
        )
    }
}

@Composable
private fun EarningsCard(state: HomeUiState, cashCents: Long) {
    val colors = RiderTheme.colors
    val today = state.earnings.today

    ElevatedHomeCard(padding = 0.dp) {
        Row(
            Modifier.padding(horizontal = 18.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            HomeIconDisc(
                icon = Icons.Filled.AccountBalanceWallet,
                tint = colors.onPeach,
                container = colors.peach.copy(alpha = 0.72f),
                size = 46.dp,
            )
            Column(Modifier.padding(start = 18.dp)) {
                Text(
                    text = "TODAY’S EARNINGS",
                    style = SpacedCaps.copy(fontSize = 10.sp, lineHeight = 13.sp),
                    color = colors.textSecondary,
                )
                Text(
                    text = if (state.loaded) Money.peso(today.feeCents) else "—",
                    style = TextStyle(
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 35.sp,
                        lineHeight = 40.sp,
                    ),
                    color = colors.canopy,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        HorizontalDivider(Modifier.padding(horizontal = 14.dp), color = colors.hairline)

        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniStat(
                icon = Icons.Filled.Inventory2,
                value = if (state.loaded) today.jobs.toString() else "—",
                label = "deliveries",
                tint = colors.cash,
                container = colors.cashSoft,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(colors.hairline),
            )
            MiniStat(
                icon = Icons.Filled.Payments,
                value = Money.peso(cashCents),
                label = "cash to collect",
                tint = colors.cash,
                container = colors.cashSoft,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun MiniStat(
    icon: ImageVector,
    value: String,
    label: String,
    tint: Color,
    container: Color,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        HomeIconDisc(icon, tint, container, size = 38.dp)
        Column(Modifier.padding(start = 9.dp)) {
            Text(
                text = value,
                style = TextStyle(
                    fontFamily = SerifFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = if (value.length > 8) 15.sp else 19.sp,
                    lineHeight = 21.sp,
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = RiderTheme.colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun QuickActionsHeader(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
        SerifSectionTitle("Quick actions", Modifier.weight(1f))
        Text(
            text = "Tools for a smoother ride.",
            style = MaterialTheme.typography.bodySmall,
            color = RiderTheme.colors.textSecondary,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

@Composable
private fun QuickActions(
    onJobBoard: () -> Unit,
    onMyJobs: () -> Unit,
    onCash: () -> Unit,
    onSupport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickAction(Icons.Outlined.Inventory2, "Job Board", onJobBoard, Modifier.weight(1f))
        QuickAction(Icons.Outlined.TwoWheeler, "My Jobs", onMyJobs, Modifier.weight(1f))
        QuickAction(Icons.Outlined.Payments, "Cash", onCash, Modifier.weight(1f))
        QuickAction(Icons.Outlined.SupportAgent, "Support", onSupport, Modifier.weight(1f))
    }
}

@Composable
private fun QuickAction(icon: ImageVector, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RiderTheme.colors
    Surface(
        onClick = onClick,
        modifier = modifier.height(78.dp),
        shape = ActionCardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, colors.hairline),
        shadowElevation = 2.dp,
    ) {
        Column(
            Modifier.padding(vertical = 9.dp, horizontal = 3.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            HomeIconDisc(icon, colors.onCanopy, colors.canopy, size = 40.dp)
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun AvailableJobsHeader(onViewAll: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        SerifSectionTitle("Available jobs", Modifier.weight(1f))
        TextButton(onClick = onViewAll, contentPadding = ButtonDefaults.TextButtonContentPadding) {
            Text(
                text = "View all",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = RiderTheme.colors.cta,
            )
            Icon(
                Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = RiderTheme.colors.cta,
                modifier = Modifier
                    .padding(start = 7.dp)
                    .size(14.dp),
            )
        }
    }
}

@Composable
private fun AvailableJobsCard(work: WorkState, onOpenBoard: () -> Unit, modifier: Modifier = Modifier) {
    val colors = RiderTheme.colors
    ElevatedHomeCard(modifier = modifier, padding = 4.dp) {
        if (work.board.isEmpty()) {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                HomeIconDisc(
                    icon = Icons.Filled.TwoWheeler,
                    tint = colors.textSecondary,
                    container = colors.fill,
                    size = 32.dp,
                )
                Text(
                    text = if (work.loading) "Finding nearby jobs" else "No jobs nearby",
                    style = TextStyle(
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        lineHeight = 22.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = if (work.loading) {
                        "Checking the job board now…"
                    } else {
                        "We’ll show new requests here automatically."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 1.dp, bottom = 3.dp),
                )
            }
        } else {
            val first = work.board.first()
            Row(verticalAlignment = Alignment.CenterVertically) {
                HomeIconDisc(Icons.Filled.TwoWheeler, colors.onCanopy, colors.canopy, size = 43.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                ) {
                    Text(
                        text = first.pickup.storeName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = listOfNotNull(
                            first.distanceKm?.let { "$it km" },
                            "${first.itemCount} ${if (first.itemCount == 1) "item" else "items"}",
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.textSecondary,
                    )
                }
                Text(
                    text = Money.peso(first.deliveryFeeCents),
                    style = TextStyle(
                        fontFamily = SerifFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                    ),
                    color = colors.canopy,
                )
            }
            Text(
                text = if (work.board.size == 1) "1 request available" else "${work.board.size} requests available",
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp, bottom = 7.dp),
            )
        }

        Button(
            onClick = onOpenBoard,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
            shape = RoundedCornerShape(11.dp),
            colors = ButtonDefaults.buttonColors(containerColor = colors.cta, contentColor = colors.onCta),
        ) {
            Text(
                text = if (work.board.isEmpty()) "FIND A JOB" else "VIEW JOB BOARD",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun SerifSectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = SerifFamily,
            fontWeight = FontWeight.Bold,
            fontSize = 21.sp,
            lineHeight = 26.sp,
        ),
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier,
    )
}

@Composable
private fun HomeIconDisc(
    icon: ImageVector,
    tint: Color,
    container: Color,
    size: Dp,
) {
    Box(
        Modifier
            .size(size)
            .clip(PillShape)
            .background(container),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.48f))
    }
}

@Composable
private fun ElevatedHomeCard(
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = HomeCardShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, RiderTheme.colors.hairline),
        shadowElevation = 4.dp,
    ) {
        Column(Modifier.padding(padding), content = content)
    }
}
