package com.omaykan.storefront.feature.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.Refreshable
import com.omaykan.storefront.core.designsystem.RefreshableFill
import com.omaykan.storefront.core.designsystem.CompactSnackbarHost
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.RemoteImage
import com.omaykan.storefront.core.designsystem.SnackbarMessageEffect
import com.omaykan.storefront.core.designsystem.StaleBanner
import com.omaykan.storefront.feature.home.CircleButton
import com.omaykan.storefront.feature.home.GridProductCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(
    onOpenProduct: (String) -> Unit,
    onOpenCart: () -> Unit,
    onBack: () -> Unit,
    viewModel: CatalogViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val cartCount by viewModel.cartCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarMessageEffect(viewModel.snackbar, snackbarHostState)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CompactSnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = state.shopName.ifBlank { "Shop" },
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        // Who the shopper is buying from, named on the shelf
                        // rather than buried in a footer: they are about to
                        // hand cash to a stranger on this shop's behalf.
                        state.ownerName?.let { owner ->
                            Text(
                                text = owner,
                                style = MaterialTheme.typography.bodySmall,
                                color = OmaykanTheme.colors.textSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // The market page's own cart control, badge and all. This
                    // screen had a bare icon with no count, so walking into a
                    // shop looked like it had emptied the basket.
                    Box(Modifier.padding(end = 8.dp)) {
                        BadgedBox(
                            badge = { if (cartCount > 0) Badge { Text("$cartCount") } },
                        ) {
                            CircleButton(
                                icon = Icons.Outlined.ShoppingCart,
                                description = if (cartCount > 0) {
                                    "Cart, $cartCount items"
                                } else {
                                    "Cart"
                                },
                                onClick = onOpenCart,
                            )
                        }
                    }
                },
            )
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
                    OutlinedTextField(
                        value = state.query,
                        onValueChange = viewModel::onQueryChange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        placeholder = { Text("Search this shop") },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        singleLine = true,
                        shape = MaterialTheme.shapes.medium,
                    )

                    if (state.categories.isNotEmpty()) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item {
                                FilterChip(
                                    selected = state.selectedCategoryId == null,
                                    onClick = { viewModel.onCategorySelected(null) },
                                    label = { Text("All") },
                                )
                            }
                            items(state.categories, key = { it.id }) { category ->
                                FilterChip(
                                    selected = state.selectedCategoryId == category.id,
                                    onClick = { viewModel.onCategorySelected(category.id) },
                                    label = { Text(category.name) },
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
                                columns = GridCells.Fixed(2),
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                gridItems(state.products, key = { it.id }) { product ->
                                    GridProductCard(
                                        product = product,
                                        saved = viewModel.savedKey(product.id) in state.savedIds,
                                        onToggleSaved = { viewModel.onToggleSaved(product.id) },
                                        onClick = { onOpenProduct(product.id) },
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
