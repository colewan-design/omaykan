package com.omaykan.storefront.feature.home

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.R
import com.omaykan.storefront.core.designsystem.Refreshable
import com.omaykan.storefront.core.designsystem.RefreshableFill
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.StaleBanner
import com.omaykan.storefront.core.model.Product
import com.omaykan.storefront.core.model.StoreRef

/**
 * The market front page.
 *
 * Laid out to the reference: brand line and search at the top, a promo carousel,
 * the aisles as circles, then Featured Products two-up, then the shop directory.
 * The content underneath is the web landing page's — the same shelves, off the
 * same catalog — so the two front doors show the same market.
 */
@Composable
fun HomeScreen(
    onOpenProduct: (StoreRef, String) -> Unit,
    onOpenShop: (StoreRef) -> Unit,
    onOpenAisle: (StoreRef, String) -> Unit,
    onOpenCart: (StoreRef) -> Unit,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val store = viewModel.store

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        BrandBar(
            cartCount = cartCount,
            onOpenCart = { onOpenCart(store) },
        )

        SearchPill(
            query = state.query,
            onQueryChange = viewModel::onQueryChange,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
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

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    // Without this a no-match search would simply empty every
                    // shelf below, leaving a blank page with no explanation.
                    if (state.searching) {
                        item {
                            SearchNote(
                                matches = state.matchCount ?: 0,
                                term = state.query,
                                onClear = { viewModel.onQueryChange("") },
                            )
                        }
                    }

                    if (!state.searching) {
                        item {
                            PromoCarousel(
                                deals = state.deals,
                                onOpenProduct = { onOpenProduct(store, it) },
                            )
                        }

                        if (state.categories.isNotEmpty()) {
                            item {
                                CategoryRail(
                                    categories = state.categories.map { it.id to it.name },
                                    onPick = { categoryId -> onOpenAisle(store, categoryId) },
                                )
                            }
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

                    if (state.popular.isNotEmpty()) {
                        item { SectionHeading("Featured Products") }
                        productGrid(
                            products = state.popular,
                            savedIds = state.savedIds,
                            keyOf = viewModel::savedKey,
                            onToggleSaved = viewModel::onToggleSaved,
                            onOpenProduct = { onOpenProduct(store, it) },
                        )
                    }

                    if (state.cheapest.isNotEmpty() && !state.searching) {
                        item { SectionHeading("Everyday essentials under ₱100") }
                        productGrid(
                            products = state.cheapest,
                            savedIds = state.savedIds,
                            keyOf = viewModel::savedKey,
                            onToggleSaved = viewModel::onToggleSaved,
                            onOpenProduct = { onOpenProduct(store, it) },
                        )
                    }

                    if (!state.searching) {
                        item { SectionHeading("Shops near you") }

                        when {
                            state.shopsLoading -> item { Note("Loading shops…") }
                            state.shopsError != null -> item { Note(state.shopsError.orEmpty()) }
                            state.shops.isEmpty() -> item { Note("No shops are open for orders yet.") }
                            else -> items(state.shops, key = { it.ref.key }) { shop ->
                                ShopCard(
                                    shop = shop,
                                    browsingNow = shop.ref == store,
                                    onClick = { onOpenShop(shop.ref) },
                                    modifier = Modifier.padding(horizontal = 20.dp),
                                )
                            }
                        }
                    }
                }
            }
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
private fun androidx.compose.foundation.lazy.LazyListScope.productGrid(
    products: List<Product>,
    savedIds: Set<String>,
    keyOf: (String) -> String,
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
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            row.forEach { product ->
                GridProductCard(
                    product = product,
                    saved = keyOf(product.id) in savedIds,
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

/** The brand line. The wordmark is the app's own logo asset, not set type. */
@Composable
private fun BrandBar(
    cartCount: Int,
    onOpenCart: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = "Welcome to",
                style = MaterialTheme.typography.bodyMedium,
                color = OmaykanTheme.colors.textSecondary,
            )
            Image(
                painter = painterResource(R.drawable.logo_wordmark),
                contentDescription = "Omaykan",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .height(28.dp)
                    .padding(top = 2.dp),
            )
        }
        Box(Modifier.padding(start = 8.dp)) {
            BadgedBox(
                badge = { if (cartCount > 0) Badge { Text("$cartCount") } },
            ) {
                CircleButton(
                    icon = Icons.Outlined.ShoppingCart,
                    description = "Cart",
                    onClick = onOpenCart,
                )
            }
        }
    }
}

/**
 * The round, filled icon button the market page uses for its cart and its store
 * code. Shared with the shop shelf so the cart control is the same control in
 * both places rather than two that merely mean the same thing.
 */
@Composable
internal fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(OmaykanTheme.colors.fill)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SearchPill(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text("Search product…") },
        leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
        singleLine = true,
        shape = RoundedCornerShape(999.dp),
        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Search),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = OmaykanTheme.colors.fill,
            unfocusedContainerColor = OmaykanTheme.colors.fill,
            focusedBorderColor = OmaykanTheme.colors.separator,
            unfocusedBorderColor = androidx.compose.ui.graphics.Color.Transparent,
            focusedLeadingIconColor = OmaykanTheme.colors.textSecondary,
            unfocusedLeadingIconColor = OmaykanTheme.colors.textSecondary,
            focusedPlaceholderColor = OmaykanTheme.colors.textTertiary,
            unfocusedPlaceholderColor = OmaykanTheme.colors.textTertiary,
        ),
    )
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun Note(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = OmaykanTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 20.dp),
    )
}

@Composable
private fun SearchNote(matches: Int, term: String, onClear: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
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
