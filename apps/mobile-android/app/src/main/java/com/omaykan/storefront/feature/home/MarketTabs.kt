package com.omaykan.storefront.feature.home

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
import androidx.compose.foundation.layout.fillMaxWidth as fillWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omaykan.storefront.R
import com.omaykan.storefront.core.designsystem.ForestTopBar
import com.omaykan.storefront.core.designsystem.LoadingState
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.MountainMark
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.Refreshable
import com.omaykan.storefront.core.designsystem.RefreshableFill
import com.omaykan.storefront.core.designsystem.SerifFamily
import com.omaykan.storefront.core.model.Money
import com.omaykan.storefront.core.model.StoreRef

/*
 * The three tabs between Home and Account. All three read the one
 * HomeViewModel the shell holds, so the shelf is fetched once and a heart
 * tapped on Home is already lit on Favorites.
 */

/**
 * Shop by Category: every aisle of the front page's shop as a wide photo card,
 * and the whole shelf at the end for anyone who would rather browse it all.
 */
@Composable
internal fun ShopByCategoryTab(
    state: HomeUiState,
    onRefresh: () -> Unit,
    onBack: () -> Unit,
    onOpenAisle: (String) -> Unit,
    onOpenShelf: () -> Unit,
    contentPadding: PaddingValues,
) {
    Column(Modifier.fillMaxSize()) {
        ForestTopBar(
            title = "Shop by Category",
            subtitle = state.shopName,
            onBack = onBack,
        )

        Refreshable(refreshing = state.refreshing, onRefresh = onRefresh) {
            when {
                state.loading -> LoadingState()

                state.error != null && state.aisles.isEmpty() -> RefreshableFill {
                    MessageState(
                        title = "We could not reach the market",
                        detail = state.error,
                        icon = Icons.Filled.CloudOff,
                        actionLabel = "Try again",
                        onAction = onRefresh,
                    )
                }

                state.emptyShelf -> RefreshableFill {
                    MessageState(
                        title = "Nothing on the shelf yet",
                        detail = "This market has not listed anything to order.",
                        icon = Icons.Filled.Inventory2,
                    )
                }

                state.aisles.isEmpty() -> RefreshableFill {
                    MessageState(
                        title = "No aisles yet",
                        detail = "This shop keeps everything on one shelf.",
                        icon = Icons.Filled.Inventory2,
                        actionLabel = "Browse the shelf",
                        onAction = onOpenShelf,
                    )
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 14.dp,
                        bottom = contentPadding.calculateBottomPadding() + 20.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.aisles, key = { it.id }) { aisle ->
                        AisleCard(
                            title = aisle.name,
                            subtitle = aisleSummary(aisle.itemCount, aisle.fromCents),
                            imageUrl = aisle.imageUrl,
                            onClick = { onOpenAisle(aisle.id) },
                        )
                    }
                    item(key = "everything") {
                        AisleCard(
                            title = "Everything",
                            subtitle = aisleSummary(
                                state.allProducts.size,
                                state.allProducts.minOfOrNull { it.priceCents } ?: 0L,
                            ),
                            imageUrl = state.popular.firstOrNull { !it.imageUrl.isNullOrBlank() }?.imageUrl,
                            onClick = onOpenShelf,
                        )
                    }
                }
            }
        }
    }
}

/** "12 items · from ₱45" — the reference's tagline slot, filled with the shelf's own facts. */
private fun aisleSummary(count: Int, fromCents: Long): String =
    "$count item${if (count == 1) "" else "s"} · from ${Money.peso(fromCents)}"

/**
 * Stories: the shops behind the counters.
 *
 * The reference's Stories page is a magazine of artisan features. This app has
 * no articles and will not invent any, so the stories here are the shops
 * themselves — their photograph, their name, what they sell and where — and
 * the long-form story is the one the web already tells, a tap away.
 */
@Composable
internal fun StoriesTab(
    state: HomeUiState,
    currentStore: StoreRef,
    onRefresh: () -> Unit,
    onOpenShop: (StoreRef) -> Unit,
    onReadStory: () -> Unit,
    contentPadding: PaddingValues,
) {
    Refreshable(refreshing = state.refreshing, onRefresh = onRefresh) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 24.dp),
        ) {
            item { StoriesHero(onReadStory) }

            item {
                when {
                    state.shopsLoading -> StoriesNote("Finding shops…")
                    state.shopsError != null -> StoriesNote(state.shopsError)
                    state.shops.isEmpty() -> StoriesNote(
                        "No shops are open for orders yet. They appear here once they have " +
                            "something on the shelf.",
                    )

                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.shops, key = { it.ref.key }) { shop ->
                            StoryCard(shop = shop, onClick = { onOpenShop(shop.ref) })
                        }
                    }
                }
            }

            item { Quote() }

            // The reference ends on the quote; the directory below it is the
            // one addition, because a row that scrolls sideways hides every
            // shop past the third, and "which shops are there" deserves a
            // straight answer.
            if (state.shops.size > 2) {
                item {
                    Text(
                        text = "All Shops",
                        style = MaterialTheme.typography.titleLarge,
                        color = OmaykanTheme.colors.ink,
                        modifier = Modifier.padding(start = 16.dp, top = 28.dp, bottom = 10.dp),
                    )
                }
                items(state.shops, key = { "all/" + it.ref.key }) { shop ->
                    ShopCard(
                        shop = shop,
                        browsingNow = shop.ref == currentStore,
                        onClick = { onOpenShop(shop.ref) },
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun StoriesHero(onReadStory: () -> Unit) {
    val forest = OmaykanTheme.colors.forest
    val onForest = OmaykanTheme.colors.onForest

    Box(
        Modifier
            .fillMaxWidth()
            .height(372.dp)
            .background(forest),
    ) {
        Image(
            painter = painterResource(R.drawable.hero_market),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to forest.copy(alpha = 0.8f),
                        0.3f to forest.copy(alpha = 0.25f),
                        0.55f to forest.copy(alpha = 0.45f),
                        1f to forest.copy(alpha = 0.95f),
                    ),
                ),
        )

        Text(
            text = "Stories from Our Shops",
            style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.Medium, fontSize = 26.sp),
            color = onForest,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(top = 14.dp, start = 16.dp, end = 16.dp),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = 22.dp),
        ) {
            Text(
                text = "People. Places. Prices.",
                style = TextStyle(fontFamily = SerifFamily, fontWeight = FontWeight.SemiBold, fontSize = 22.sp),
                color = onForest,
            )
            Text(
                text = "Meet the shops behind the counter — their own shelves, their own " +
                    "prices, and not a peso of it taken as commission.",
                style = MaterialTheme.typography.bodyMedium,
                color = onForest.copy(alpha = 0.92f),
                modifier = Modifier
                    .padding(top = 8.dp)
                    .fillWidth(0.9f),
            )
            Text(
                text = "Read Our Story",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF2A2420),
                modifier = Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.White)
                    .clickable(onClick = onReadStory)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun StoriesNote(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = OmaykanTheme.colors.textSecondary,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
    )
}

@Composable
private fun Quote() {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(start = 32.dp, end = 32.dp, top = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MountainMark(
            mountain = Color.Transparent,
            line = OmaykanTheme.colors.textSecondary,
            sun = Color.Transparent,
            modifier = Modifier.size(width = 54.dp, height = 32.dp),
        )
        Text(
            text = "“When you buy local, the whole price\nstays in the neighbourhood.”",
            style = TextStyle(
                fontFamily = SerifFamily,
                fontStyle = FontStyle.Italic,
                fontSize = 18.sp,
                lineHeight = 26.sp,
            ),
            color = OmaykanTheme.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

/** Favorites: the hearted products, two up, at today's price. */
@Composable
internal fun FavoritesTab(
    state: HomeUiState,
    keyOf: (String) -> String,
    onRefresh: () -> Unit,
    onToggleSaved: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    onBrowse: () -> Unit,
    contentPadding: PaddingValues,
) {
    // Resolved against the cached shelf rather than stored as whole products: a
    // hearted item must show today's price, and a saved copy of yesterday's
    // would be the one lie this screen could tell.
    val saved = state.allProducts.filter { keyOf(it.id) in state.savedIds }

    Column(Modifier.fillMaxSize()) {
        ForestTopBar(title = "Favorites")

        Refreshable(refreshing = state.refreshing, onRefresh = onRefresh) {
            if (saved.isEmpty()) {
                RefreshableFill {
                    MessageState(
                        title = "Nothing saved yet",
                        detail = "Tap the heart on anything you want to find again.",
                        icon = Icons.Outlined.FavoriteBorder,
                        actionLabel = "Browse the shelf",
                        onAction = onBrowse,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = 14.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(saved.chunked(2), key = { it.first().id }) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            row.forEach { product ->
                                GridProductCard(
                                    product = product,
                                    saved = true,
                                    onToggleSaved = { onToggleSaved(product.id) },
                                    onClick = { onOpenProduct(product.id) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size == 1) Box(Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
