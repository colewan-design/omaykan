package com.omaykan.storefront.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.Refreshable
import com.omaykan.storefront.core.designsystem.RefreshableFill
import com.omaykan.storefront.core.designsystem.SearchPill
import com.omaykan.storefront.core.designsystem.SectionHeader
import com.omaykan.storefront.core.designsystem.StaleBanner
import com.omaykan.storefront.core.designsystem.Wordmark
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.StoreRef

/**
 * The market front page.
 *
 * Laid out to the reference: a forest header with the menu, the name and the
 * cart over a white search pill; a banner; the aisles as circles; then the
 * shelves as horizontal rows of cards. The content underneath is the web
 * landing page's — the same shelves, off the same catalog — with the shop
 * directory at the foot, so the two front doors show the same market.
 */
@Composable
fun HomeScreen(
    onOpenProduct: (StoreRef, String) -> Unit,
    onOpenShop: (StoreRef) -> Unit,
    onOpenAisle: (StoreRef, String) -> Unit,
    onOpenCart: (StoreRef) -> Unit,
    onOpenMenu: () -> Unit,
    onSeeAllShops: () -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val store = viewModel.store
    val isSaved: (String) -> Boolean = { viewModel.savedKey(it) in state.savedIds }

    Column(Modifier.fillMaxSize()) {
        HomeHeader(
            cartCount = cartCount,
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            onOpenMenu = onOpenMenu,
            onOpenCart = { onOpenCart(store) },
        )

        state.staleMessage?.let { StaleBanner(it, onRetry = viewModel::refresh) }

        Refreshable(refreshing = state.refreshing, onRefresh = viewModel::refresh) {
            when {
                state.loading -> LoadingState()

                state.error != null -> RefreshableFill {
                    MessageState(
                        title = "We could not reach the market",
                        detail = state.error,
                        icon = Icons.Filled.CloudOff,
                        actionLabel = "Try again",
                        onAction = viewModel::refresh,
                    )
                }

                // Without this a search would scroll through a banner and a row
                // of aisles that have nothing to do with what was typed.
                state.searching -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        top = 12.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        SearchNote(
                            matches = state.matchCount ?: 0,
                            term = state.query,
                            onClear = { viewModel.onQueryChange("") },
                        )
                    }
                    productGrid(
                        products = state.popular,
                        isSaved = isSaved,
                        onToggleSaved = viewModel::onToggleSaved,
                        onOpenProduct = { onOpenProduct(store, it) },
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    item { HeroBanner(onShopNow = { onOpenShop(store) }) }

                    if (state.aisles.isNotEmpty()) {
                        item {
                            AisleCircles(
                                aisles = state.aisles,
                                onPick = { categoryId -> onOpenAisle(store, categoryId) },
                            )
                        }
                    }

                    if (state.emptyShelf) {
                        item {
                            MessageState(
                                title = "Nothing on the shelf yet",
                                detail = "This market has not listed anything to order.",
                                icon = Icons.Filled.Inventory2,
                            )
                        }
                    }

                    shelf(
                        title = "Featured Products",
                        products = state.popular,
                        onSeeAll = { onOpenShop(store) },
                        isSaved = isSaved,
                        onToggleSaved = viewModel::onToggleSaved,
                        onOpenProduct = { onOpenProduct(store, it) },
                    )

                    shelf(
                        title = "On Sale at the Counter",
                        products = state.deals,
                        isSaved = isSaved,
                        onToggleSaved = viewModel::onToggleSaved,
                        onOpenProduct = { onOpenProduct(store, it) },
                    )

                    shelf(
                        title = "Everyday Essentials Under ₱100",
                        products = state.cheapest,
                        isSaved = isSaved,
                        onToggleSaved = viewModel::onToggleSaved,
                        onOpenProduct = { onOpenProduct(store, it) },
                    )

                    item {
                        SectionHeader(
                            title = "Shops Near You",
                            actionLabel = if (state.shops.isNotEmpty()) "See All" else null,
                            onAction = onSeeAllShops,
                        )
                    }

                    when {
                        state.shopsLoading -> item { Note("Loading shops…") }
                        state.shopsError != null -> item { Note(state.shopsError.orEmpty()) }
                        state.shops.isEmpty() -> item { Note("No shops are open for orders yet.") }
                        else -> items(state.shops, key = { it.ref.key }) { shop ->
                            ShopCard(
                                shop = shop,
                                browsingNow = shop.ref == store,
                                onClick = { onOpenShop(shop.ref) },
                                modifier = Modifier.padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A titled horizontal shelf, or nothing at all when the shelf is empty. */
private fun LazyListScope.shelf(
    title: String,
    products: List<Product>,
    isSaved: (String) -> Boolean,
    onToggleSaved: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onSeeAll: (() -> Unit)? = null,
) {
    if (products.isEmpty()) return

    item(key = title) {
        Column {
            SectionHeader(
                title = title,
                actionLabel = onSeeAll?.let { "See All" },
                onAction = onSeeAll,
            )
            ProductShelf(
                products = products,
                isSaved = isSaved,
                onToggleSaved = onToggleSaved,
                onOpenProduct = onOpenProduct,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

/**
 * Two-up rows inside the page's own vertical scroll.
 *
 * A LazyVerticalGrid cannot be nested in a LazyColumn — two vertical scrollers
 * fighting over the same gesture — so the products are chunked into rows here.
 * The list is short (a shelf, not a catalog), which is what makes that fine.
 */
private fun LazyListScope.productGrid(
    products: List<Product>,
    isSaved: (String) -> Boolean,
    onToggleSaved: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
) {
    items(
        items = products.chunked(2),
        key = { row -> row.first().id },
    ) { row ->
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { product ->
                GridProductCard(
                    product = product,
                    saved = isSaved(product.id),
                    onToggleSaved = { onToggleSaved(product.id) },
                    onClick = { onOpenProduct(product.id) },
                    modifier = Modifier.weight(1f),
                )
            }
            // Keeps a lone card on the last row at one column's width rather
            // than letting it stretch across the whole page.
            if (row.size == 1) Box(Modifier.weight(1f))
        }
    }
}

/**
 * The forest header: menu, name, cart, and the search pill under them.
 *
 * It paints under the status bar itself, so the green runs to the top edge of
 * the phone the way the reference's does.
 */
@Composable
private fun HomeHeader(
    cartCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenMenu: () -> Unit,
    onOpenCart: () -> Unit,
) {
    val colors = OmaykanTheme.colors

    Column(
        Modifier
            .fillMaxWidth()
            .background(colors.forest)
            .statusBarsPadding()
            .padding(bottom = 14.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onOpenMenu, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(Icons.Filled.Menu, contentDescription = "Menu", tint = colors.onForest)
            }
            Wordmark(modifier = Modifier.align(Alignment.Center))
            IconButton(onClick = onOpenCart, modifier = Modifier.align(Alignment.CenterEnd)) {
                BadgedBox(
                    badge = {
                        if (cartCount > 0) {
                            Badge(containerColor = colors.cta, contentColor = colors.onCta) {
                                Text("$cartCount")
                            }
                        }
                    },
                ) {
                    Icon(
                        Icons.Outlined.ShoppingCart,
                        contentDescription = if (cartCount > 0) "Cart, $cartCount items" else "Cart",
                        tint = colors.onForest,
                    )
                }
            }
        }

        SearchPill(
            query = query,
            onQueryChange = onQueryChange,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = OmaykanTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun SearchNote(matches: Int, term: String, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (matches > 0) {
                "$matches match${if (matches == 1) "" else "es"} for “$term”"
            } else {
                "Nothing matches “$term”."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = OmaykanTheme.colors.textSecondary,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onClear) { Text("Clear") }
    }
}
