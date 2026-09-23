package com.omaykan.storefront.feature.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.CompactSnackbarHost
import com.omaykan.storefront.core.designsystem.ForestTopBar
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.Refreshable
import com.omaykan.storefront.core.designsystem.RefreshableFill
import com.omaykan.storefront.core.designsystem.SearchPill
import com.omaykan.storefront.core.designsystem.SnackbarMessageEffect
import com.omaykan.storefront.core.designsystem.StaleBanner
import com.omaykan.storefront.feature.home.ForestTabBar
import com.omaykan.storefront.feature.home.GridProductCard
import com.omaykan.storefront.feature.home.MarketTab

/**
 * One shop's shelf: the forest bar naming the shop and the person behind it,
 * the search pill on the same green, the aisles as chips, the products three
 * up, and the tab bar along the bottom.
 *
 * The tab bar is here although this is a pushed destination rather than a tab.
 * A shopper who has walked into a shop should not have to find Back to reach
 * Home, and the reference draws it on this screen; tapping a tab pops back to
 * the shell on that tab rather than stacking a second copy of it.
 */
@Composable
internal fun CatalogScreen(
    onOpenProduct: (String) -> Unit,
    onOpenCart: () -> Unit,
    onBack: () -> Unit,
    onSelectTab: (MarketTab) -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val shelfReady = !state.loading && state.error == null && !state.emptyShelf

    SnackbarMessageEffect(viewModel.snackbar, snackbarHostState)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CompactSnackbarHost(snackbarHostState) },
        // Shop stays lit: a shop's shelf is what the Shop tab leads to, so
        // lighting anything else would tell the shopper they are somewhere
        // they are not.
        bottomBar = {
            ForestTabBar(
                selected = MarketTab.Shop,
                savedCount = state.savedIds.size,
                onSelect = onSelectTab,
            )
        },
        topBar = {
            Column {
                ForestTopBar(
                    title = state.shopName.ifBlank { "Shop" },
                    // Who the shopper is buying from, named on the shelf
                    // rather than buried in a footer: they are about to hand
                    // cash to a stranger on this shop's behalf.
                    subtitle = state.ownerName,
                    onBack = onBack,
                    actions = {
                        // This shop's basket only — the button opens this
                        // shop's cart, and baskets do not mix across shops.
                        IconButton(onClick = onOpenCart) {
                            BadgedBox(
                                badge = {
                                    if (cartCount > 0) {
                                        Badge(
                                            containerColor = OmaykanTheme.colors.cta,
                                            contentColor = OmaykanTheme.colors.onCta,
                                        ) { Text("$cartCount") }
                                    }
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.ShoppingCart,
                                    contentDescription = if (cartCount > 0) {
                                        "Cart, $cartCount items"
                                    } else {
                                        "Cart"
                                    },
                                    tint = OmaykanTheme.colors.onForest,
                                )
                            }
                        }
                    },
                )
                if (shelfReady) {
                    SearchPill(
                        query = state.query,
                        onQueryChange = viewModel::onQueryChange,
                        placeholder = "Search this shop…",
                        modifier = Modifier
                            .background(OmaykanTheme.colors.forest)
                            .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )
                }
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            state.staleMessage?.let { message ->
                StaleBanner(message = message, onRetry = viewModel::refresh)
            }

            when {
                state.loading -> LoadingState()

                state.error != null -> MessageState(
                    title = "We could not open this shop",
                    detail = state.error,
                    icon = Icons.Filled.CloudOff,
                    actionLabel = "Try again",
                    onAction = viewModel::refresh,
                )

                // A live, successful, empty answer. Never a cached shelf in
                // disguise — a successful fetch always overwrites the cache
                // first, so an empty shelf here is the shop's real answer.
                state.emptyShelf -> Refreshable(
                    refreshing = state.refreshing,
                    onRefresh = viewModel::refresh,
                ) {
                    RefreshableFill {
                        MessageState(
                            title = "Nothing on the shelf yet",
                            detail = "This shop has not listed anything to order. Try again later.",
                            icon = Icons.Filled.Inventory2,
                        )
                    }
                }

                else -> {
                    if (state.categories.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                AisleChip(
                                    label = "All",
                                    selected = state.selectedCategoryId == null,
                                    onClick = { viewModel.onCategorySelected(null) },
                                )
                            }
                            items(state.categories, key = { it.id }) { category ->
                                AisleChip(
                                    label = category.name,
                                    selected = state.selectedCategoryId == category.id,
                                    onClick = { viewModel.onCategorySelected(category.id) },
                                )
                            }
                        }
                    }

                    Refreshable(refreshing = state.refreshing, onRefresh = viewModel::refresh) {
                        if (state.products.isEmpty()) {
                            MessageState(
                                title = "Nothing matches that",
                                detail = "Try a different word, or clear the filter.",
                                actionLabel = "Show everything",
                                onAction = {
                                    viewModel.onQueryChange("")
                                    viewModel.onCategorySelected(null)
                                },
                            )
                        } else {
                            LazyVerticalGrid(
                                // Three up, per the reference. The gutters and
                                // side padding come in with it: at 12.dp and
                                // 16.dp a third column leaves the cards too
                                // narrow for a two-line name and a price.
                                columns = GridCells.Fixed(3),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = 12.dp,
                                    end = 12.dp,
                                    top = 4.dp,
                                    bottom = 20.dp,
                                ),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                gridItems(state.products, key = { it.id }) { product ->
                                    GridProductCard(
                                        product = product,
                                        saved = viewModel.savedKey(product.id) in state.savedIds,
                                        onToggleSaved = { viewModel.onToggleSaved(product.id) },
                                        onClick = { onOpenProduct(product.id) },
                                        // Three up: the name needs the smaller
                                        // size to survive the narrower card.
                                        compact = true,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** An aisle filter: forest when chosen, a quiet outline when not. */
@Composable
private fun AisleChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(999.dp)
    val colors = OmaykanTheme.colors

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = if (selected) colors.onForest else colors.ink,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.forest else MaterialTheme.colorScheme.surface)
            .border(1.dp, if (selected) colors.forest else colors.separator, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
