package com.omaykan.seller.feature.shell

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.model.PairedStore
import com.omaykan.seller.feature.account.AccountScreen
import com.omaykan.seller.feature.analytics.AnalyticsScreen
import com.omaykan.seller.feature.home.HomeScreen
import com.omaykan.seller.feature.messages.MessagesScreen
import com.omaykan.seller.feature.messages.ThreadScreen
import com.omaykan.seller.feature.orders.OrdersScreen
import com.omaykan.seller.feature.products.ProductEditorScreen
import com.omaykan.seller.feature.products.ProductFilter
import com.omaykan.seller.feature.products.ProductsScreen

/** The five tabs, in the reference's order. */
enum class SellerTab(
    val label: String,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    Home("Home", Icons.Outlined.Home, Icons.Filled.Home),
    Products("Products", Icons.Outlined.Inventory2, Icons.Filled.Inventory2),
    Orders("Orders", Icons.AutoMirrored.Outlined.ReceiptLong, Icons.AutoMirrored.Filled.ReceiptLong),
    Sales("Sales", Icons.Outlined.Insights, Icons.Filled.Insights),
    Account("Account", Icons.Outlined.Person, Icons.Filled.Person),
}

private object Routes {
    const val TABS = "tabs"
    const val MESSAGES = "messages"
    const val THREAD = "thread/{id}?name={name}"
    const val PRODUCT = "product?id={id}"
}

/**
 * Everything a signed-in merchant can reach.
 *
 * This is where the app grew a NavHost, and MainActivity's reason for not
 * having one still holds: signed in and signed out are not a stack, and the
 * session flow still chooses between them. What *is* a stack is inside a
 * session — the tabs, then a product form or a message thread on top — and
 * each of those gets a back-stack entry so its view model is cleared when it
 * closes. A product form reopened later starts from the server's copy, not
 * from whatever was half-typed last time.
 *
 * [ordersSignal] goes up by one whenever a new-order notification is tapped;
 * the shell answers by dropping whatever is open and showing the orders tab.
 */
@Composable
fun SellerShell(store: PairedStore, ordersSignal: Int) {
    val nav = rememberNavController()

    LaunchedEffect(ordersSignal) {
        if (ordersSignal > 0) nav.popBackStack(Routes.TABS, inclusive = false)
    }

    NavHost(
        navController = nav,
        startDestination = Routes.TABS,
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        composable(Routes.TABS) {
            TabsFrame(
                store = store,
                ordersSignal = ordersSignal,
                onOpenMessages = { nav.navigate(Routes.MESSAGES) { launchSingleTop = true } },
                onEditProduct = { id -> nav.navigate("product?id=${id.orEmpty()}") { launchSingleTop = true } },
            )
        }

        composable(Routes.MESSAGES) {
            MessagesScreen(
                onBack = { nav.back() },
                onOpenThread = { id, name -> nav.navigate("thread/$id?name=${Uri.encode(name)}") },
            )
        }

        composable(
            Routes.THREAD,
            arguments = listOf(
                navArgument("id") { type = NavType.StringType },
                navArgument("name") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            ThreadScreen(onBack = { nav.back() })
        }

        composable(
            Routes.PRODUCT,
            arguments = listOf(
                navArgument("id") {
                    type = NavType.StringType
                    defaultValue = ""
                },
            ),
        ) {
            ProductEditorScreen(onDone = { nav.back() })
        }
    }
}

/**
 * Pop, but never the tabs themselves.
 *
 * A form that saves and a back arrow tapped in the same instant would
 * otherwise pop twice, and the second pop takes the start destination with it
 * — a blank window with nothing to press.
 */
private fun NavController.back() {
    if (previousBackStackEntry != null) popBackStack()
}

@Composable
private fun TabsFrame(
    store: PairedStore,
    ordersSignal: Int,
    onOpenMessages: () -> Unit,
    onEditProduct: (String?) -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(SellerTab.Home) }
    var productsFilter by remember { mutableStateOf<ProductFilter?>(null) }

    LaunchedEffect(ordersSignal) {
        if (ordersSignal > 0) tab = SellerTab.Orders
    }

    // Back from any other tab goes Home first, and only Home leaves the app.
    BackHandler(enabled = tab != SellerTab.Home) { tab = SellerTab.Home }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        // Each tab paints its own forest under the status bar, so the scaffold
        // hands down only the tab bar's height.
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = { SellerTabBar(selected = tab, onSelect = { tab = it }) },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (tab) {
                SellerTab.Home -> HomeScreen(
                    store = store,
                    onOpenOrders = { tab = SellerTab.Orders },
                    onOpenProducts = { filter ->
                        productsFilter = filter
                        tab = SellerTab.Products
                    },
                    onOpenSales = { tab = SellerTab.Sales },
                    onOpenMessages = onOpenMessages,
                    onOpenAccount = { tab = SellerTab.Account },
                )

                SellerTab.Products -> ProductsScreen(
                    onEdit = onEditProduct,
                    filterRequest = productsFilter,
                    onFilterRequestHandled = { productsFilter = null },
                )

                SellerTab.Orders -> OrdersScreen()
                SellerTab.Sales -> AnalyticsScreen()
                SellerTab.Account -> AccountScreen(store = store, onOpenMessages = onOpenMessages)
            }
        }
    }
}

/**
 * The forest tab bar with rounded shoulders, the same bar the shopper's app
 * wears.
 *
 * Hand-built rather than Material's NavigationBar, which draws a pill behind
 * the selected icon; the reference marks the selected tab only by filling its
 * icon and brightening its label. No shadow: the forest against cream is
 * already all the separation it needs.
 */
@Composable
private fun SellerTabBar(selected: SellerTab, onSelect: (SellerTab) -> Unit) {
    val colors = SellerTheme.colors

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp))
            .background(colors.canopy)
            .navigationBarsPadding()
            .padding(top = 8.dp, bottom = 6.dp)
            .selectableGroup(),
    ) {
        SellerTab.entries.forEach { entry ->
            val on = entry == selected
            val tint = if (on) colors.onCanopy else colors.canopyMuted

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .selectable(selected = on, onClick = { onSelect(entry) }, role = Role.Tab)
                    .padding(vertical = 6.dp),
            ) {
                Icon(
                    imageVector = if (on) entry.selectedIcon else entry.icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
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
