package com.omaykan.storefront.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.exclude
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.CompactSnackbarHost
import com.omaykan.storefront.core.designsystem.MessageState
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.Refreshable
import com.omaykan.storefront.core.designsystem.RefreshableFill
import com.omaykan.storefront.core.designsystem.SnackbarMessageEffect
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.feature.account.AccountScreen
import com.omaykan.storefront.feature.orders.OrderFilter
import com.omaykan.storefront.feature.orders.OrdersScreen
import com.omaykan.storefront.feature.update.UpdatePrompt
import kotlinx.coroutines.launch

/**
 * How much of the system navigation inset the tab bar gives back, so it sits
 * nearer the bottom edge. Small on purpose: the remainder is what keeps the
 * labels clear of the system buttons.
 */
private val BOTTOM_BAR_LIFT = 12.dp

private enum class MarketTab(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Saved("Saved", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    Shops("Shops", Icons.Filled.Storefront, Icons.Outlined.Storefront),
    Orders("Orders", Icons.Filled.ReceiptLong, Icons.Outlined.ReceiptLong),
    Account("Account", Icons.Filled.Person, Icons.Outlined.Person),
}

/**
 * The tabbed frame the reference puts around everything.
 *
 * Five tabs. Each one earns its place by having something to show whether or
 * not anybody is signed in — Account offers one button and says plainly that
 * ordering does not need it, and Orders lists what this phone has ordered even
 * with no account behind it, because a guest order is findable by its own id.
 * A tab that could only ever say "sign in first" would still not be here.
 *
 * Home, Saved and Shops share one HomeViewModel: hiltViewModel() resolves
 * against this destination's back-stack entry, so the shelf is fetched once and
 * the hearts stay in step across tabs without any state being lifted by hand.
 */
@Composable
fun MainShell(
    onOpenProduct: (StoreRef, String) -> Unit,
    onOpenShop: (StoreRef) -> Unit,
    onOpenAisle: (StoreRef, String) -> Unit,
    onOpenCart: (StoreRef) -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenPayment: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(MarketTab.Home) }

    /*
     * A filter the account page asked the Orders tab for.
     *
     * Held here rather than passed as a route argument because these are tabs,
     * not destinations — there is nothing to navigate to. Cleared the moment
     * the Orders tab applies it, so coming back later shows what the shopper
     * last chose there rather than replaying a tap from earlier.
     */
    var requestedOrderFilter by remember { mutableStateOf<OrderFilter?>(null) }
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Hosted here rather than on the Home tab, for the same reason the view
    // model is: the hearts on Home and on Saved are the same hearts, and their
    // answer should not depend on which tab the shopper tapped it from.
    SnackbarMessageEffect(viewModel.snackbar, snackbarHostState)

    // Hung off the shell rather than the Home tab so it is asked once per
    // launch whichever tab the app opens on, and survives switching between
    // them without asking the server again.
    UpdatePrompt()

    /*
     * Back does not drop the shopper out of the app on the first press.
     *
     * This is the root of the back stack, so the system's answer here is to
     * close the app, and a phone's back gesture is an edge swipe that is easy
     * to make by accident — losing a half-filled basket to a stray thumb is a
     * real cost, and the shopper has no way to undo it.
     *
     * Two steps out rather than one. From any other tab, Back goes to Home,
     * which is what the tab bar implies and costs nothing to be wrong about.
     * From Home it asks, and the ask is only good while the shopper can see
     * it: the window is exactly as long as the snackbar is on screen, so
     * "press back again" is never true of a screen that is not saying it.
     */
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var exitPrompted by remember { mutableStateOf(false) }

    BackHandler {
        when {
            tab != MarketTab.Home -> tab = MarketTab.Home

            exitPrompted -> activity?.finish()

            else -> {
                exitPrompted = true
                scope.launch {
                    // An "Added to cart" still showing would otherwise hold the
                    // host's lock and swallow the one message that matters here.
                    snackbarHostState.currentSnackbarData?.dismiss()
                    snackbarHostState.showSnackbar(
                        message = "Press back again to exit",
                        duration = SnackbarDuration.Short,
                    )
                    exitPrompted = false
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CompactSnackbarHost(snackbarHostState) },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                /*
                 * Sits a little lower than Material would put it.
                 *
                 * The default keeps the whole system navigation inset below the
                 * items, which on a three-button phone is a full bar of empty
                 * background under the labels. A slice of that is given back so
                 * the tabs sit closer to the bottom edge — trimmed, not
                 * removed, because the rest of it is what keeps the labels from
                 * disappearing behind the system buttons.
                 */
                windowInsets = WindowInsets.navigationBars
                    .only(WindowInsetsSides.Bottom)
                    .exclude(WindowInsets(bottom = BOTTOM_BAR_LIFT)),
            ) {
                MarketTab.entries.forEach { entry ->
                    NavigationBarItem(
                        selected = tab == entry,
                        onClick = { tab = entry },
                        icon = {
                            val icon = @Composable {
                                Icon(
                                    imageVector = if (tab == entry) entry.selectedIcon else entry.icon,
                                    contentDescription = entry.label,
                                )
                            }
                            if (entry == MarketTab.Saved && state.savedIds.isNotEmpty()) {
                                BadgedBox(badge = { Badge { Text("${state.savedIds.size}") } }) { icon() }
                            } else {
                                icon()
                            }
                        },
                        label = { Text(entry.label) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = OmaykanTheme.colors.ink,
                            selectedTextColor = OmaykanTheme.colors.ink,
                            unselectedIconColor = OmaykanTheme.colors.textTertiary,
                            unselectedTextColor = OmaykanTheme.colors.textTertiary,
                            indicatorColor = OmaykanTheme.colors.fill,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        when (tab) {
            MarketTab.Home -> HomeScreen(
                onOpenProduct = onOpenProduct,
                onOpenShop = onOpenShop,
                onOpenAisle = onOpenAisle,
                onOpenCart = onOpenCart,
                contentPadding = padding,
                viewModel = viewModel,
            )

            MarketTab.Saved -> SavedTab(
                onRefresh = viewModel::refresh,
                state = state,
                keyOf = viewModel::savedKey,
                onToggleSaved = viewModel::onToggleSaved,
                onOpenProduct = { onOpenProduct(viewModel.store, it) },
                contentPadding = padding,
            )

            MarketTab.Shops -> ShopsTab(
                onRefresh = viewModel::refresh,
                state = state,
                currentStore = viewModel.store,
                onOpenShop = onOpenShop,
                contentPadding = padding,
            )

            MarketTab.Orders -> OrdersScreen(
                requestedFilter = requestedOrderFilter,
                onFilterApplied = { requestedOrderFilter = null },
                onOpenOrder = onOpenOrder,
                contentPadding = padding,
            )

            // Its own hiltViewModel, unlike the three above: the session is not
            // the shelf, and AccountRepository is a singleton anyway, so there
            // is nothing to lift.
            MarketTab.Account -> AccountScreen(
                modifier = Modifier.padding(padding),
                onOpenProfile = onOpenProfile,
                onOpenAddresses = onOpenAddresses,
                onOpenPayment = onOpenPayment,
                onOpenSecurity = onOpenSecurity,
                // Two of the account page's shortcuts are tabs of this shell
                // rather than destinations, so they switch tabs instead of
                // pushing a second copy of a screen already one tap away.
                onOpenSaved = { tab = MarketTab.Saved },
                onOpenOrders = { stage ->
                    requestedOrderFilter = stage
                        ?.let { wanted -> OrderFilter.entries.first { it.stage == wanted } }
                        ?: OrderFilter.All
                    tab = MarketTab.Orders
                },
                onTrackOrder = onOpenOrder,
            )
        }
    }
}

@Composable
private fun SavedTab(
    state: HomeUiState,
    keyOf: (String) -> String,
    onRefresh: () -> Unit,
    onToggleSaved: (String) -> Unit,
    onOpenProduct: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    // Resolved against the cached shelf rather than stored as whole products: a
    // hearted item must show today's price, and a saved copy of yesterday's
    // would be the one lie this screen could tell.
    val saved = state.allProducts.filter { keyOf(it.id) in state.savedIds }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        TabTitle("Saved")

        Refreshable(refreshing = state.refreshing, onRefresh = onRefresh) {
            if (saved.isEmpty()) {
                RefreshableFill {
                    MessageState(
                        title = "Nothing saved yet",
                        detail = "Tap the heart on anything you want to find again.",
                        icon = Icons.Outlined.FavoriteBorder,
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(saved.chunked(2), key = { it.first().id }) { row ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp),
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

@Composable
private fun ShopsTab(
    onRefresh: () -> Unit,
    state: HomeUiState,
    currentStore: StoreRef,
    onOpenShop: (StoreRef) -> Unit,
    contentPadding: PaddingValues,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        TabTitle("Shops")

        Refreshable(refreshing = state.refreshing, onRefresh = onRefresh) {
            when {
                state.shopsLoading -> MessageState(
                    title = "Finding shops…",
                    icon = Icons.Outlined.Storefront,
                )

                state.shopsError != null -> RefreshableFill {
                    MessageState(
                        title = "We could not reach the market",
                        detail = state.shopsError,
                        icon = Icons.Outlined.Storefront,
                    )
                }

                state.shops.isEmpty() -> RefreshableFill {
                    MessageState(
                        title = "No shops are open for orders yet",
                        detail = "Shops appear here once they have something on the shelf.",
                        icon = Icons.Outlined.Storefront,
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        top = 8.dp,
                        bottom = contentPadding.calculateBottomPadding() + 24.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(state.shops, key = { it.ref.key }) { shop ->
                        ShopCard(
                            shop = shop,
                            browsingNow = shop.ref == currentStore,
                            onClick = { onOpenShop(shop.ref) },
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineMedium,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}
