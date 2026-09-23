package com.omaykan.storefront.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

/**
 * Pull down to fetch again.
 *
 * One wrapper rather than five copies, because the interesting part is not the
 * gesture — Material supplies that — but the two rules every screen using it
 * has to follow, and which are easy to get wrong separately:
 *
 *  1. **It wraps the content, never the header.** The content itself never
 *     moves — Material3 overlays the indicator rather than translating what is
 *     underneath — but the indicator is positioned against the top of whatever
 *     this wraps. Wrap a search bar or a row of filter chips and the spinner
 *     comes down on top of them instead of above the list.
 *  2. **A failed refresh must not blank what is on screen.** That rule already
 *     lives in the view models — every one of them keeps the last good list on
 *     an error — and this is the affordance that makes people lean on it.
 *
 * The indicator is anchored to the top of the content area rather than the
 * window, so it comes out from under whatever header sits above it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Refreshable(
    refreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = onRefresh,
        state = state,
        modifier = modifier.fillMaxSize(),
        indicator = {
            PullToRefreshDefaults.Indicator(
                state = state,
                isRefreshing = refreshing,
                modifier = Modifier.align(Alignment.TopCenter),
                containerColor = MaterialTheme.colorScheme.surface,
                color = MaterialTheme.colorScheme.primary,
            )
        },
        content = content,
    )
}

/**
 * Makes a page that does not scroll accept the pull anyway.
 *
 * [Refreshable] listens for nested scroll, and an empty state or an error
 * message produces none — so without this the gesture would be dead in exactly
 * the two situations where someone most wants to try again. This gives the page
 * a scroll container that is precisely one screen tall: nothing moves under a
 * normal drag, and the pull still registers.
 */
@Composable
fun RefreshableFill(content: @Composable BoxScope.() -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Captured before the scrolling Column, whose children are measured
        // with an unbounded height and so cannot ask to fill it themselves.
        val screen = maxHeight

        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(screen),
                content = content,
            )
        }
    }
}
