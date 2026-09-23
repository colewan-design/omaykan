package com.omaykan.rider.feature.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.omaykan.rider.core.designsystem.PillShape
import com.omaykan.rider.core.designsystem.PrimaryButton
import com.omaykan.rider.core.designsystem.RiderTheme
import kotlinx.coroutines.launch

/** One page: a picture, a claim, and the sentence that backs it up. */
private data class Page(val title: String, val body: String)

/*
 * Three pages, and every one of them is a promise this app can actually keep.
 *
 * The temptation on a screen like this is a feature tour — swipe, filter, dark
 * mode. Nobody installs a delivery app to read about a filter. What somebody
 * standing in a shop doorway with a phone wants to know is what work there is,
 * what it pays, and what they have to do to be paid, so that is the three
 * pages, in that order.
 *
 * The second one is the one that matters and it is the one an aggregator cannot
 * write: the whole delivery fee is the rider's, because there is no platform
 * cut anywhere on this product. See documentation/positioning.md.
 */
private val Pages = listOf(
    Page(
        title = "Every shop, one board",
        body = "Deliveries from every shop on Omaykan, in one list that keeps itself " +
            "current. Take the ones that suit the way you were already going.",
    ),
    Page(
        title = "The whole fee is yours",
        body = "No commission, no service cut, no subscription. What a job says it pays " +
            "is what ends up in your pocket at the door.",
    ),
    Page(
        title = "Pick up, drop off, done",
        body = "The address, the customer's number and the basket to check the bag " +
            "against — then one tap into whichever map app you already ride with.",
    ),
)

/**
 * What the app is for, before it asks anybody to sign in.
 *
 * ## Why it is before the sign-in screen and not after
 *
 * Because it is aimed at somebody who does not have an account. A rider signing
 * in already knows what this is; a rider who has just been handed a link by a
 * friend at a shop does not, and the three sentences here are the ones that
 * decide whether they fill in a registration form with two photographs in it.
 *
 * ## Why it is shown once and never again
 *
 * [com.omaykan.rider.core.data.OnboardingStore] writes a flag on the last page
 * *and on Skip*, and never clears it — not even on sign-out. An introduction
 * that reappears is an introduction that gets skipped faster each time, and on
 * a shared phone it would be three screens between a rider and their shift.
 */
@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val pager = rememberPagerState(pageCount = { Pages.size })
    val scope = rememberCoroutineScope()

    val finish = {
        viewModel.markSeen()
        onDone()
    }

    // Back on the first page leaves the app, which is right: there is nothing
    // behind this screen. On the others it walks back a page rather than
    // throwing away the introduction somebody is halfway through.
    BackHandler(enabled = pager.currentPage > 0) {
        scope.launch { pager.animateScrollToPage(pager.currentPage - 1) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            // Skip counts as seen. Somebody who has decided they do not want
            // the tour has told us something, and asking again tomorrow is not
            // listening to it.
            TextButton(onClick = finish) {
                Text(
                    text = "Skip",
                    style = MaterialTheme.typography.bodyMedium,
                    color = RiderTheme.colors.textSecondary,
                )
            }
        }

        HorizontalPager(
            state = pager,
            modifier = Modifier.weight(1f),
        ) { index ->
            val page = Pages[index]

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                OnboardingArt(page = index, modifier = Modifier.height(ArtHeightDp))

                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 32.dp),
                )

                Text(
                    text = page.body,
                    style = MaterialTheme.typography.bodyLarge,
                    color = RiderTheme.colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        Dots(
            count = Pages.size,
            selected = pager.currentPage,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 24.dp),
        )

        PrimaryButton(
            text = if (pager.currentPage == Pages.lastIndex) "Get started" else "Next",
            onClick = {
                if (pager.currentPage == Pages.lastIndex) {
                    finish()
                } else {
                    scope.launch { pager.animateScrollToPage(pager.currentPage + 1) }
                }
            },
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 24.dp),
        )
    }
}

/**
 * Where you are in three.
 *
 * The current one is a capsule rather than a bigger circle, which is the cue
 * that survives being glanced at: a 10dp dot next to an 8dp dot is a difference
 * you have to compare, and a stretched one is a shape you just see.
 */
@Composable
private fun Dots(count: Int, selected: Int, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(count) { index ->
            val active = index == selected
            val width by animateDpAsState(if (active) 26.dp else 8.dp, label = "dotWidth")

            Box(
                Modifier
                    .width(width)
                    .height(8.dp)
                    .clip(PillShape)
                    .background(
                        if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            RiderTheme.colors.fill
                        },
                    ),
            )
        }
    }
}
