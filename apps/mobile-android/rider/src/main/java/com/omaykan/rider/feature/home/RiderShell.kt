package com.omaykan.rider.feature.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.TwoWheeler
import androidx.compose.material.icons.filled.Wallet
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.feature.account.AccountScreen
import com.omaykan.rider.feature.earnings.EarningsScreen
import com.omaykan.rider.feature.work.WorkScreen

/** The four screens an approved rider has. */
private enum class RiderTab(val label: String, val icon: ImageVector) {
    Home("Home", Icons.Filled.Home),
    Jobs("Jobs", Icons.Filled.TwoWheeler),
    Earnings("Earnings", Icons.Filled.Wallet),
    Account("Account", Icons.Filled.AccountCircle),
}

/**
 * What an approved rider sees: a standing summary, the board, what it has paid,
 * and their account.
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
 * ## Why four rather than three
 *
 * The home screen was the top third of the board, and it lost that argument the
 * moment it had anything worth keeping still. A rider opens this app far more
 * often to check what they have made and what they are holding than to look for
 * new work, and both of those were scrolling away under a list. Splitting them
 * is what lets the board be nothing but jobs.
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

    BackHandler(enabled = tab != RiderTab.Home) { tab = RiderTab.Home }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                RiderTab.Home -> HomeScreen(
                    rider = rider,
                    onSeeJobs = { tab = RiderTab.Jobs },
                    onSeeEarnings = { tab = RiderTab.Earnings },
                )

                RiderTab.Jobs -> WorkScreen(rider = rider)
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
 * Four targets along the bottom, with the current one named.
 *
 * Material's `NavigationBar` writes every label all the time, which on a
 * four-tab bar is four words competing for a strip 56dp tall — and it draws the
 * selected state as a pale pill behind an icon that keeps its own colour, which
 * is a cue you have to look for. This one names only the tab you are on, and
 * fills its pill with the brand green: the selected tab is a *shape* on a white
 * bar, readable at the speed a glance actually runs at, and the other three
 * stay icons because a rider already knows what they are.
 *
 * The labels are not lost to a screen reader — every target carries its own
 * `contentDescription` and the `selectable` role, so TalkBack reads "Jobs, tab,
 * not selected" whether or not the word is drawn.
 */
@Composable
private fun TabBar(selected: RiderTab, onSelect: (RiderTab) -> Unit) {
    HorizontalDivider(color = RiderTheme.colors.hairline)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RiderTab.entries.forEach { entry ->
            TabTarget(
                tab = entry,
                selected = entry == selected,
                onSelect = { onSelect(entry) },
            )
        }
    }
}

@Composable
private fun TabTarget(tab: RiderTab, selected: Boolean, onSelect: () -> Unit) {
    // Animated so that switching tabs is one object moving along the bar rather
    // than one pill vanishing and another appearing somewhere else.
    val container by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        },
        label = "tabContainer",
    )

    val content = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        RiderTheme.colors.textTertiary
    }

    Row(
        modifier = Modifier
            .clip(PillShape)
            .background(container)
            .selectable(
                selected = selected,
                onClick = onSelect,
                role = Role.Tab,
            )
            .height(44.dp)
            .padding(horizontal = if (selected) 18.dp else 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = tab.icon,
            // Named on every target, selected or not: the word beside it is
            // drawn for one of the four, and a screen reader needs all four.
            contentDescription = tab.label,
            tint = content,
            modifier = Modifier.size(22.dp),
        )

        if (selected) {
            Text(
                text = tab.label,
                style = MaterialTheme.typography.bodyMedium,
                color = content,
            )
        }
    }
}
