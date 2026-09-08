package com.omaykan.rider.feature.work

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.data.WorkState
import com.omaykan.rider.core.designsystem.Canopy
import com.omaykan.rider.core.designsystem.CanopyIconButton
import com.omaykan.rider.core.designsystem.IconBadge
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.feature.job.JobDetailScreen
import com.omaykan.rider.core.designsystem.SectionLabel
import com.omaykan.rider.core.designsystem.SegmentedPills
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.feature.common.Notice

/**
 * The job board and this rider's own work.
 *
 * ## Nothing on this screen but jobs
 *
 * It used to carry the rider's name, three running totals and the location
 * switch above the list. All three are standing facts that do not change when
 * the board does, and all three scrolled away the moment a rider looked at the
 * second card — so they moved to [com.omaykan.rider.feature.home.HomeScreen],
 * which never scrolls them. What is left here is a short canopy that says which
 * screen this is, the choice between the two lists, and the jobs.
 *
 * ## The count on the second pill
 *
 * "My jobs (2)" carries its number in the label rather than in a badge beside
 * it, and it carries the zero too. A rider carrying two orders should be able
 * to see that from the board without switching, because forgetting a second job
 * is how a customer waits an hour for food that is on a bike two streets away.
 */
@Composable
fun WorkScreen(
    rider: RiderProfile,
    viewModel: WorkViewModel = hiltViewModel(),
) {
    /*
     * The one piece of navigation in this app, and it is a variable.
     *
     * A NavHost for a single child screen would be a graph, a route type and a
     * serializer to express "or the job you tapped" — which is what this line
     * says. `rememberSaveable` is what carries it through a rotation, and the
     * detail screen handles Back itself.
     */
    var openJobId by rememberSaveable { mutableStateOf<String?>(null) }

    openJobId?.let { id ->
        JobDetailScreen(jobId = id, onBack = { openJobId = null })
        return
    }

    val work by viewModel.work.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()

    // collectAsStateWithLifecycle above is doing two jobs: it reads the jobs,
    // and its subscription is what runs the polling loop. Both stop when this
    // screen stops. See WorkFeed.state.

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Header(
            rider = rider,
            work = work,
            onRefresh = viewModel::refresh,
        )

        Column(Modifier.padding(horizontal = 20.dp)) {
            SegmentedPills(
                options = WorkTab.entries.map { tab ->
                    when (tab) {
                        WorkTab.Board -> tab.label
                        WorkTab.Mine -> "${tab.label} (${work.active.size})"
                    }
                },
                selectedIndex = ui.tab.ordinal,
                onSelect = { viewModel.onTabChange(WorkTab.entries[it]) },
                modifier = Modifier.padding(top = 16.dp),
            )

            ui.notice?.let { Notice(it) }

            (ui.error ?: work.error)?.let { Notice(it, warning = true) }
        }

        when {
            // Only before the first answer of the session. Every refresh
            // after that leaves the last good list on screen — a rider
            // halfway to a shop must not have the address replaced by a
            // spinner.
            work.loading && work.board.isEmpty() && work.active.isEmpty() ->
                Loading(Modifier.weight(1f))

            ui.tab == WorkTab.Board -> BoardList(viewModel, work, ui, Modifier.weight(1f))

            else -> MineList(viewModel, work, ui, { openJobId = it }, Modifier.weight(1f))
        }
    }
}

/**
 * The canopy: which screen this is, and the plate the shop will ask for.
 *
 * Short, on purpose. The running totals that used to live here are on the home
 * screen now, where they stay put — this block exists to say "board" and to
 * hold the one control that belongs to a list of jobs, which is the one that
 * fetches it again.
 *
 * The plate is the exception worth keeping: it is what a shop reads off a
 * counter screen and asks the person in front of them to confirm, and this is
 * the screen a rider has open when they walk in.
 */
@Composable
private fun Header(
    rider: RiderProfile,
    work: WorkState,
    onRefresh: () -> Unit,
) {
    Canopy {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 18.dp, bottom = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Jobs",
                    style = MaterialTheme.typography.headlineMedium,
                    color = RiderTheme.colors.onCanopy,
                )
                Text(
                    text = if (work.board.isEmpty()) {
                        "Riding as ${rider.plateNumber}"
                    } else {
                        "${work.board.size} on the board · ${rider.plateNumber}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = RiderTheme.colors.onCanopyMuted,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            /*
             * Refresh, and nothing beside it.
             *
             * Sign out used to sit one thumb-width away, on the screen a rider
             * looks at every few minutes while moving, next to the control they
             * press most. It lives on the account screen now — somewhere you go
             * on purpose.
             */
            CanopyIconButton(Icons.Filled.Refresh, "Refresh", onRefresh)
        }
    }
}

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

/** The padding under every list: clear of the gesture bar, and then some. */
private val ListPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp)

@Composable
private fun BoardList(
    viewModel: WorkViewModel,
    work: WorkState,
    ui: WorkUiState,
    modifier: Modifier = Modifier,
) {
    val riderPoint by viewModel.riderPoint.collectAsStateWithLifecycle()

    if (work.board.isEmpty()) {
        Empty(
            icon = Icons.Filled.Inbox,
            title = "Nothing on the board right now.",
            detail = "New jobs appear here on their own — the screen checks every few seconds.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.navigationBarsPadding(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(work.board, key = { it.id }) { offer ->
            OfferCard(
                offer = offer,
                busy = ui.busyId == offer.id,
                enabled = ui.busyId == null,
                onAccept = { viewModel.accept(offer.id) },
                riderPoint = riderPoint,
            )
        }
    }
}

@Composable
private fun MineList(
    viewModel: WorkViewModel,
    work: WorkState,
    ui: WorkUiState,
    onOpenJob: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val riderPoint by viewModel.riderPoint.collectAsStateWithLifecycle()

    if (work.active.isEmpty() && work.completed.isEmpty()) {
        Empty(
            icon = Icons.Filled.TwoWheeler,
            title = "You are not carrying anything.",
            detail = "Take a job from the board and it will show up here.",
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier = modifier.navigationBarsPadding(),
        contentPadding = ListPadding,
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(work.active, key = { it.id }) { job ->
            // Asked for here rather than up front, so a job scrolled past
            // never costs a Directions call. RouteRepository caches, so
            // scrolling back is free and a rotation re-reads rather than
            // re-fetches.
            val route by produceState<String?>(null, job.id) {
                value = viewModel.routeFor(job)
            }

            AssignmentCard(
                job = job,
                busy = ui.busyId == job.id,
                enabled = ui.busyId == null,
                onAdvance = { stage -> viewModel.advance(job, stage) },
                onRelease = { viewModel.release(job) },
                onOpen = { onOpenJob(job.id) },
                route = route,
                riderPoint = riderPoint,
            )
        }

        if (work.completed.isNotEmpty()) {
            item {
                SectionLabel("Finished", modifier = Modifier.padding(top = 10.dp))
            }

            // The server caps this at 30, so there is no paging to write and
            // nothing here pretends to be a full earnings history. That is a
            // report, and it belongs somewhere a rider can read it sitting
            // down — see rider/README.md.
            items(work.completed, key = { it.id }) { job -> CompletedRow(job) }
        }
    }
}

/**
 * An empty list, with the reason it is empty.
 *
 * The disc is not decoration: an empty board and an empty "my jobs" are the two
 * states a rider hits most often in a shift, and the icon is what tells them
 * which one they are looking at before they have read the sentence.
 */
@Composable
private fun Empty(
    icon: ImageVector,
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            IconBadge(
                icon = icon,
                tint = RiderTheme.colors.textTertiary,
                container = RiderTheme.colors.fill,
                size = 64.dp,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodyMedium,
                color = RiderTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}
