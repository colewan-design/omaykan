package com.omaykan.rider.feature.home

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.TwoWheeler
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.omaykan.rider.core.designsystem.LockupEmber
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.feature.account.AccountScreen
import com.omaykan.rider.feature.earnings.EarningsScreen
import com.omaykan.rider.feature.job.JobDetailScreen
import com.omaykan.rider.feature.work.WorkScreen
import com.omaykan.rider.feature.work.WorkTab

/** The four screens an approved rider has. */
internal enum class RiderTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Jobs("Jobs", Icons.Outlined.TwoWheeler, Icons.Filled.TwoWheeler),
    Earnings("Earnings", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    Account("Profile", Icons.Outlined.Person, Icons.Filled.Person),
}

/**
 * What an approved rider sees: a standing summary, the board, what it has paid,
 * and their profile — and, over all four, the job they have opened.
 *
 * ## Still no NavHost
 *
 * MainActivity's argument holds — these are not a stack. They are four views of
 * one signed-in session, and a rider pressing Back from Earnings should not be
 * walked through Jobs and out to a sign-in screen they are not on. A tab bar
 * over a `rememberSaveable` enum says exactly that and needs no library, no
 * route type and no serializer.
 *
 * Back from a tab other than Home returns to Home, which is the one place the
 * gesture means something here, and the reason this is a [BackHandler] rather
 * than nothing at all: on Home the system default (leave the app) is right, so
 * the handler is disabled there rather than swallowing it.
 *
 * ## The open job lives here, not on the Jobs tab
 *
 * It used to be a variable inside WorkScreen, which was fine while the board
 * was the only way into a job. Home's active-order cards open one too now, so
 * the id is held one level up, and an open job covers the tab bar the way the
 * reference draws its delivery screens: a rider in the middle of a job has one
 * thing on screen, and it is not four tabs.
 *
 * ## Why the bar is only in this state
 *
 * A pending or suspended rider has one screen. A bar under it with three dead
 * tabs would be furniture that says the opposite of what the status screen
 * above it says. `MainActivity` picks between them.
 */
@Composable
fun RiderShell(
    rider: RiderProfile,
    viewModel: RiderShellViewModel = hiltViewModel(),
) {
    var tab by rememberSaveable { mutableStateOf(RiderTab.Home) }
    var openJobId by rememberSaveable { mutableStateOf<String?>(null) }

    // Which list the Jobs tab should show when Home sends a rider there. Not
    // saved: it is an instruction consumed on arrival, not a state.
    var workRequest by remember { mutableStateOf<WorkTab?>(null) }

    openJobId?.let { id ->
        JobDetailScreen(jobId = id, onBack = { openJobId = null })
        return
    }

    BackHandler(enabled = tab != RiderTab.Home) { tab = RiderTab.Home }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                RiderTab.Home -> HomeScreen(
                    rider = rider,
                    onOpenJobs = { which ->
                        workRequest = which
                        tab = RiderTab.Jobs
                    },
                    onOpenEarnings = { tab = RiderTab.Earnings },
                    onOpenAccount = { tab = RiderTab.Account },
                    onOpenJob = { openJobId = it },
                )

                RiderTab.Jobs -> WorkScreen(
                    rider = rider,
                    onOpenJob = { openJobId = it },
                    tabRequest = workRequest,
                    onTabRequestHandled = { workRequest = null },
                )

                RiderTab.Earnings -> EarningsScreen()
                RiderTab.Account -> AccountScreen(
                    rider = rider,
                    onSignOut = viewModel::signOut,
                )
            }
        }

        TabBar(selected = tab, onSelect = { tab = it })
    }
}

/**
 * The forest tab bar with rounded shoulders, the same bar :app and :seller wear.
 *
 * Hand-built rather than Material's NavigationBar, which draws a pale pill
 * behind an icon that keeps its own colour — a cue you have to look for. Here
 * the current tab gets a filled icon, a brighter label, and a short ember bar
 * over it, which is a shape and can be seen from a handlebar mount. All four
 * keep their labels: the reference shows them, and a rider's second week is not
 * the time to learn which glyph is which.
 */
@Composable
internal fun TabBar(selected: RiderTab, onSelect: (RiderTab) -> Unit) {
    val colors = RiderTheme.colors
    LightNavigationBarIcons()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(colors.canopy)
            .navigationBarsPadding()
            .padding(top = 3.dp)
            .selectableGroup(),
    ) {
        RiderTab.entries.forEach { entry ->
            val on = entry == selected
            val tint = if (on) colors.onCanopy else colors.onCanopyMuted

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = on, onClick = { onSelect(entry) }, role = Role.Tab),
            ) {
                Box(
                    Modifier
                        .width(22.dp)
                        .height(3.dp)
                        .clip(PillShape)
                        .background(if (on) LockupEmber else Color.Transparent),
                )
                Icon(
                    imageVector = if (on) entry.selectedIcon else entry.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .size(22.dp),
                )
                Text(
                    text = entry.label,
                    fontSize = 10.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = tint,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/**
 * White navigation-bar icons while the forest tab bar sits under them. The
 * light theme would otherwise draw dark ones, invisible on the green.
 */
@Composable
private fun LightNavigationBarIcons() {
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightNavigationBars = false
        onDispose { if (previous != null) controller.isAppearanceLightNavigationBars = previous }
    }
}
