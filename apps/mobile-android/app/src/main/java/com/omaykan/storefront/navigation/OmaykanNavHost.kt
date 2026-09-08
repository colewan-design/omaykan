package com.omaykan.storefront.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navDeepLink
import androidx.navigation.toRoute
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.feature.account.AccountScreen
import com.omaykan.storefront.feature.account.AddressFormScreen
import com.omaykan.storefront.feature.account.AddressesScreen
import com.omaykan.storefront.feature.account.PaymentMethodFormScreen
import com.omaykan.storefront.feature.account.PaymentMethodsScreen
import com.omaykan.storefront.feature.account.ProfileScreen
import com.omaykan.storefront.feature.account.ResetPasswordScreen
import com.omaykan.storefront.feature.account.SecurityScreen
import com.omaykan.storefront.feature.cart.CartScreen
import com.omaykan.storefront.feature.catalog.CatalogScreen
import com.omaykan.storefront.feature.catalog.ProductDetailScreen
import com.omaykan.storefront.feature.checkout.CheckoutScreen
import com.omaykan.storefront.feature.checkout.OrderReviewScreen
import com.omaykan.storefront.feature.home.MainShell
import com.omaykan.storefront.feature.order.OrderScreen

/**
 * The market front page is always the root.
 *
 * A shopper who was last inside one shop is taken back into it on launch, but
 * as a push onto the market rather than in place of it — so Back is always a
 * way out to the rest of the shops, and the app never traps someone inside a
 * store that has since emptied its shelf.
 */
@Composable
fun OmaykanNavHost(
    resumeShop: StoreRef?,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(resumeShop) {
        if (resumeShop != null) {
            navController.navigate(CatalogRoute(resumeShop.orgSlug, resumeShop.storeCode))
        }
    }

    NavHost(
        navController = navController,
        startDestination = HomeRoute,
        modifier = modifier,
    ) {
        composable<HomeRoute> {
            MainShell(
                onOpenProduct = { store, productId ->
                    navController.navigate(ProductRoute(store.orgSlug, store.storeCode, productId))
                },
                onOpenShop = { ref ->
                    navController.navigate(CatalogRoute(ref.orgSlug, ref.storeCode))
                },
                onOpenAisle = { store, categoryId ->
                    navController.navigate(
                        CatalogRoute(store.orgSlug, store.storeCode, categoryId),
                    )
                },
                onOpenCart = { ref ->
                    navController.navigate(CartRoute(ref.orgSlug, ref.storeCode))
                },
                onOpenOrder = { orderId -> navController.navigate(OrderRoute(orderId)) },
                onOpenProfile = { navController.navigate(ProfileRoute) },
                onOpenAddresses = { navController.navigate(AddressesRoute) },
                onOpenPayment = { navController.navigate(PaymentMethodsRoute) },
                onOpenSecurity = { navController.navigate(SecurityRoute) },
            )
        }

        composable<CatalogRoute> { entry ->
            val route = entry.toRoute<CatalogRoute>()
            CatalogScreen(
                onOpenProduct = { productId ->
                    navController.navigate(
                        ProductRoute(route.orgSlug, route.storeCode, productId),
                    )
                },
                onOpenCart = {
                    navController.navigate(CartRoute(route.orgSlug, route.storeCode))
                },
                onBack = navController::navigateUp,
            )
        }

        composable<ProductRoute> { entry ->
            val route = entry.toRoute<ProductRoute>()
            ProductDetailScreen(
                onOpenCart = {
                    navController.navigate(CartRoute(route.orgSlug, route.storeCode))
                },
                onBack = navController::navigateUp,
            )
        }

        composable<CartRoute> { entry ->
            val route = entry.toRoute<CartRoute>()
            CartScreen(
                onCheckout = {
                    navController.navigate(CheckoutRoute(route.orgSlug, route.storeCode))
                },
                onBack = navController::navigateUp,
            )
        }

        composable<AccountRoute> {
            AccountScreen(
                onBack = navController::navigateUp,
                // Signing in was the errand; the shopper wants to be back where
                // they were, not looking at their own profile.
                onSignedIn = { navController.popBackStack() },
            )
        }

        composable<CheckoutRoute> { entry ->
            val route = entry.toRoute<CheckoutRoute>()
            CheckoutScreen(
                onSignIn = { navController.navigate(AccountRoute) },
                onReview = {
                    navController.navigate(OrderReviewRoute(route.orgSlug, route.storeCode))
                },
                onBack = navController::navigateUp,
            )
        }

        composable<OrderReviewRoute> { entry ->
            val route = entry.toRoute<OrderReviewRoute>()

            /*
             * The checkout's view model, not one of its own.
             *
             * Resolved against the CheckoutRoute entry still sitting under this
             * one, so both screens read and write the same form — which is what
             * lets Back return to a checkout with every field as it was, and
             * lets a 422 on the address land on the box that holds it.
             */
            val parent = remember(entry) {
                navController.getBackStackEntry(
                    CheckoutRoute(route.orgSlug, route.storeCode),
                )
            }

            OrderReviewScreen(
                viewModel = hiltViewModel(parent),
                onPlaced = { orderId ->
                    navController.navigate(OrderRoute(orderId, justPlaced = true)) {
                        // The order is placed and the basket is gone. Walking
                        // back into checkout would offer to place it again
                        // against a cart that no longer exists.
                        popUpTo(HomeRoute)
                    }
                },
                onBack = navController::navigateUp,
            )
        }

        composable<OrderRoute> { entry ->
            OrderScreen(
                justPlaced = entry.toRoute<OrderRoute>().justPlaced,
                onDone = {
                    navController.navigate(HomeRoute) {
                        popUpTo(HomeRoute) { inclusive = true }
                    }
                },
            )
        }

        /*
         * The far end of a reset email.
         *
         * The deep link is the same URL the web portal honours, so one mail
         * template serves both clients and the link still works on a phone
         * without the app. Both route arguments are required, which is what
         * keeps a plain visit to /account from matching: that opens the app at
         * the market instead, which is where the shopper wanted to be anyway.
         */
        /*
         * The account portal. Every one of these reads the signed-in account
         * off AccountRepository's session flow rather than taking it as an
         * argument, so a save made here is already visible to the checkout
         * screen underneath — and so a deep link into one does not need an
         * account it has no way to supply.
         */
        composable<ProfileRoute> { ProfileScreen(onBack = navController::navigateUp) }

        composable<AddressesRoute> {
            AddressesScreen(
                onBack = navController::navigateUp,
                onOpenForm = { navController.navigate(AddressFormRoute) },
            )
        }

        /*
         * The address form, over the list that opened it.
         *
         * Its view model is the list's — same trick as the order review screen,
         * and for a related reason: the form is already filled in by the time
         * this is navigated to, and a save has to be able to leave "Address
         * saved." on the list it pops back to.
         */
        composable<AddressFormRoute> { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(AddressesRoute) }

            AddressFormScreen(
                viewModel = hiltViewModel(parent),
                onDone = { navController.popBackStack(AddressFormRoute, inclusive = true) },
            )
        }

        composable<PaymentMethodsRoute> {
            PaymentMethodsScreen(
                onBack = navController::navigateUp,
                onOpenForm = { navController.navigate(PaymentMethodFormRoute) },
            )
        }

        composable<PaymentMethodFormRoute> { entry ->
            val parent = remember(entry) { navController.getBackStackEntry(PaymentMethodsRoute) }

            PaymentMethodFormScreen(
                viewModel = hiltViewModel(parent),
                onDone = { navController.popBackStack(PaymentMethodFormRoute, inclusive = true) },
            )
        }

        composable<SecurityRoute> { SecurityScreen(onBack = navController::navigateUp) }

        composable<ResetPasswordRoute>(
            deepLinks = listOf(navDeepLink<ResetPasswordRoute>(basePath = "$WEB_ORIGIN/account")),
        ) {
            ResetPasswordScreen(
                onDone = {
                    navController.navigate(HomeRoute) {
                        // The token is spent. Leaving this on the back stack
                        // would offer to redeem it a second time.
                        popUpTo(HomeRoute) { inclusive = true }
                    }
                },
            )
        }
    }
}

/**
 * Where the reset link points.
 *
 * A constant rather than BuildConfig.API_BASE_URL: a debug build aimed at a
 * laptop still has to honour a link that came from a production email, and the
 * manifest's intent filter is a fixed string either way — the two have to
 * agree, so they are both written out rather than derived.
 */
private const val WEB_ORIGIN = "https://omaykan.com"
