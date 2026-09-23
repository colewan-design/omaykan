package com.omaykan.storefront.feature.home

import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.storefront.core.designsystem.CompactSnackbarHost
import com.omaykan.storefront.core.designsystem.MountainMark
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.designsystem.SerifFamily
import com.omaykan.storefront.core.designsystem.SnackbarMessageEffect
import com.omaykan.storefront.core.designsystem.Wordmark
import com.omaykan.storefront.core.designsystem.WovenBand
import com.omaykan.storefront.core.model.OrderStage
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.feature.account.AccountScreen
import com.omaykan.storefront.feature.update.UpdatePrompt
import com.omaykan.storefront.navigation.ABOUT_URL
import com.omaykan.storefront.navigation.openInBrowser
import kotlinx.coroutines.launch

internal enum class MarketTab(
    val label: String,
    val selectedIcon: ImageVector,
    val icon: ImageVector,
) {
    Home("Home", Icons.Filled.Home, Icons.Outlined.Home),
    Shop("Shop", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag),
    Stories("Stories", Icons.Filled.Newspaper, Icons.Outlined.Newspaper),
    Favorites("Favorites", Icons.Filled.Favorite, Icons.Outlined.FavoriteBorder),
    Account("Account", Icons.Filled.Person, Icons.Outlined.Person),
}

/**
 * The tabbed frame around everything, with the reference's five tabs.
 *
 * Orders is not one of them any more; it is a screen of its own, one tap from
 * the Account tab's order tiles and from the menu. Every tab still earns its
 * place by having something to show signed out — Account says plainly that
 * ordering does not need it, and Favorites and Stories need no account at all.
 *
 * Home, Shop, Stories and Favorites share one HomeViewModel: hiltViewModel()
 * resolves against this destination's back-stack entry, so the shelf is
 * fetched once and the hearts stay in step across tabs.
 */
@Composable
internal fun MainShell(
    // Hoisted to the nav host: CatalogScreen is a pushed destination that also
    // draws the tab bar, so a tap there has to land on the tab this shell
    // shows when the shopper pops back to it.
    tab: MarketTab,
    onTabChange: (MarketTab) -> Unit,
    onOpenProduct: (StoreRef, String) -> Unit,
    onOpenShop: (StoreRef) -> Unit,
    onOpenAisle: (StoreRef, String) -> Unit,
    onOpenCart: (StoreRef) -> Unit,
    onOpenOrder: (String) -> Unit,
    onOpenOrders: (OrderStage?) -> Unit,
    onOpenProfile: () -> Unit,
    onOpenAddresses: () -> Unit,
    onOpenPayment: () -> Unit,
    onOpenSecurity: () -> Unit,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val context = LocalContext.current

    // Hosted here rather than on the Home tab, for the same reason the view
    // model is: the hearts on Home and on Favorites are the same hearts.
    SnackbarMessageEffect(viewModel.snackbar, snackbarHostState)

    // Hung off the shell rather than the Home tab so it is asked once per
    // launch whichever tab the app opens on.
    UpdatePrompt()

    /*
     * Back does not drop the shopper out of the app on the first press.
     *
     * This is the root of the back stack, so the system's answer here is to
     * close the app, and a phone's back gesture is an edge swipe that is easy
     * to make by accident — losing a half-filled basket to a stray thumb is a
     * real cost. From any other tab, Back goes to Home; from Home it asks, and
     * the window is exactly as long as the snackbar saying so is on screen.
     */
    val activity = LocalActivity.current
    val scope = rememberCoroutineScope()
    var exitPrompted by remember { mutableStateOf(false) }

    BackHandler {
        when {
            tab != MarketTab.Home -> onTabChange(MarketTab.Home)

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

    // Declared after the handler above so it wins while the menu is open:
    // Back closes the menu before it does anything else.
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    val closeMenuThen: (() -> Unit) -> Unit = { action ->
        scope.launch { drawerState.close() }
        action()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        // Swipe to close only. Opening is the menu button's job: an edge swipe
        // on a page full of sideways shelves opens it far too easily.
        gesturesEnabled = drawerState.isOpen,
        drawerContent = {
            MarketMenu(
                current = tab,
                onPick = { picked -> closeMenuThen { onTabChange(picked) } },
                onOpenOrders = { closeMenuThen { onOpenOrders(null) } },
                onOpenAbout = { closeMenuThen { context.openInBrowser(ABOUT_URL) } },
            )
        },
    ) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            // Each tab paints its own header under the status bar, so the
            // scaffold hands down only the tab bar's height.
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { CompactSnackbarHost(snackbarHostState) },
            bottomBar = {
                ForestTabBar(
                    selected = tab,
                    savedCount = state.savedIds.size,
                    onSelect = onTabChange,
                )
            },
        ) { padding ->
            when (tab) {
                MarketTab.Home -> HomeScreen(
                    onOpenProduct = onOpenProduct,
                    onOpenShop = onOpenShop,
                    onOpenAisle = onOpenAisle,
                    onOpenCart = onOpenCart,
                    onOpenMenu = { scope.launch { drawerState.open() } },
                    onSeeAllShops = { onTabChange(MarketTab.Stories) },
                    contentPadding = padding,
                    viewModel = viewModel,
                )

                MarketTab.Shop -> ShopByCategoryTab(
                    state = state,
                    onRefresh = viewModel::refresh,
                    onBack = { onTabChange(MarketTab.Home) },
                    onOpenAisle = { onOpenAisle(viewModel.store, it) },
                    onOpenShelf = { onOpenShop(viewModel.store) },
                    contentPadding = padding,
                )

                MarketTab.Stories -> StoriesTab(
                    state = state,
                    currentStore = viewModel.store,
                    onRefresh = viewModel::refresh,
                    onOpenShop = onOpenShop,
                    onReadStory = { context.openInBrowser(ABOUT_URL) },
                    contentPadding = padding,
                )

                MarketTab.Favorites -> FavoritesTab(
                    state = state,
                    keyOf = viewModel::savedKey,
                    onRefresh = viewModel::refresh,
                    onToggleSaved = viewModel::onToggleSaved,
                    onOpenProduct = { onOpenProduct(viewModel.store, it) },
                    onBrowse = { onOpenShop(viewModel.store) },
                    contentPadding = padding,
                )

                // Its own hiltViewModel, unlike the four above: the session is
                // not the shelf, and AccountRepository is a singleton anyway.
                MarketTab.Account -> AccountScreen(
                    modifier = Modifier.padding(padding),
                    onOpenProfile = onOpenProfile,
                    onOpenAddresses = onOpenAddresses,
                    onOpenPayment = onOpenPayment,
                    onOpenSecurity = onOpenSecurity,
                    // Favorites is a tab of this shell rather than a
                    // destination, so the shortcut switches tabs instead of
                    // pushing a second copy of a screen already one tap away.
                    onOpenSaved = { onTabChange(MarketTab.Favorites) },
                    onOpenOrders = onOpenOrders,
                    onTrackOrder = onOpenOrder,
                )
            }
        }
    }
}

/**
 * The forest tab bar with rounded shoulders.
 *
 * Hand-built rather than Material's NavigationBar, which draws a pill behind
 * the selected icon; the reference marks the selected tab only by filling its
 * icon and brightening its label.
 *
 * Internal rather than private because CatalogScreen carries it too: a shop's
 * shelf is a pushed destination rather than a tab, but the reference still
 * shows the bar there, so the shopper is never more than one tap from Home.
 */
@Composable
internal fun ForestTabBar(
    selected: MarketTab,
    savedCount: Int,
    onSelect: (MarketTab) -> Unit,
) {
    val colors = OmaykanTheme.colors

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(colors.forest)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 6.dp)
            .selectableGroup(),
    ) {
        MarketTab.entries.forEach { entry ->
            val on = entry == selected
            val tint = if (on) colors.onForest else colors.onForestMuted

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = on, onClick = { onSelect(entry) }, role = Role.Tab)
                    .padding(vertical = 6.dp),
            ) {
                val icon = @Composable {
                    Icon(
                        imageVector = if (on) entry.selectedIcon else entry.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                }
                if (entry == MarketTab.Favorites && savedCount > 0) {
                    BadgedBox(
                        badge = {
                            Badge(containerColor = colors.cta, contentColor = colors.onCta) {
                                Text("$savedCount")
                            }
                        },
                    ) { icon() }
                } else {
                    icon()
                }
                Text(
                    text = entry.label,
                    fontSize = 11.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = tint,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

/**
 * What the hamburger opens: every tab by its longer name, the order list that
 * used to be a tab, and the page that explains what this is.
 */
@Composable
private fun MarketMenu(
    current: MarketTab,
    onPick: (MarketTab) -> Unit,
    onOpenOrders: () -> Unit,
    onOpenAbout: () -> Unit,
) {
    val colors = OmaykanTheme.colors
    val itemColors = NavigationDrawerItemDefaults.colors(
        selectedContainerColor = colors.onForest.copy(alpha = 0.12f),
        unselectedContainerColor = Color.Transparent,
        selectedTextColor = colors.onForest,
        unselectedTextColor = colors.onForest,
        selectedIconColor = colors.onForest,
        unselectedIconColor = colors.onForestMuted,
    )

    ModalDrawerSheet(
        drawerContainerColor = colors.forest,
        drawerContentColor = colors.onForest,
        drawerShape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp),
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 24.dp, bottom = 18.dp)) {
            MountainMark(Modifier.size(width = 78.dp, height = 52.dp))
            Wordmark(fontSize = 24.sp, modifier = Modifier.padding(top = 10.dp))
            Text(
                text = "Your neighbourhood shops, at their own counter prices.",
                style = TextStyle(fontFamily = SerifFamily, fontSize = 14.sp, lineHeight = 20.sp),
                color = colors.onForestMuted,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        HorizontalDivider(color = colors.onForest.copy(alpha = 0.14f))
        Spacer(Modifier.size(8.dp))

        val entries = listOf(
            Triple(MarketTab.Home, "Home", Icons.Outlined.Home),
            Triple(MarketTab.Shop, "Shop by Category", Icons.Outlined.ShoppingBag),
            Triple(MarketTab.Stories, "Stories", Icons.Outlined.Newspaper),
            Triple(MarketTab.Favorites, "Favorites", Icons.Outlined.FavoriteBorder),
        )
        entries.forEach { (tab, label, icon) ->
            NavigationDrawerItem(
                label = { Text(label) },
                icon = { Icon(icon, contentDescription = null) },
                selected = current == tab,
                onClick = { onPick(tab) },
                colors = itemColors,
                modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
            )
        }
        NavigationDrawerItem(
            label = { Text("Your Orders") },
            icon = { Icon(Icons.AutoMirrored.Outlined.ReceiptLong, contentDescription = null) },
            selected = false,
            onClick = onOpenOrders,
            colors = itemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )
        NavigationDrawerItem(
            label = { Text("Account") },
            icon = { Icon(Icons.Outlined.Person, contentDescription = null) },
            selected = current == MarketTab.Account,
            onClick = { onPick(MarketTab.Account) },
            colors = itemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        HorizontalDivider(
            color = colors.onForest.copy(alpha = 0.14f),
            modifier = Modifier.padding(vertical = 8.dp),
        )
        NavigationDrawerItem(
            label = { Text("About Omaykan") },
            icon = { Icon(Icons.Outlined.Info, contentDescription = null) },
            selected = false,
            onClick = onOpenAbout,
            colors = itemColors,
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding),
        )

        Spacer(Modifier.weight(1f))
        WovenBand()
    }
}
