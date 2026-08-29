package com.omaykan.storefront.navigation

import kotlinx.serialization.Serializable

/*
 * Type-safe destinations. A screen's arguments are its constructor, so a route
 * that needs a store cannot be navigated to without one.
 *
 * Every shop-scoped route carries orgSlug and storeCode rather than reading the
 * remembered shop out of DataStore. Two reasons: a deep link into an order or a
 * product (Phase 3 and 4) arrives with no session at all, and a screen that
 * silently reads "whichever shop is current" is a screen that shows the wrong
 * shelf the moment the shopper backs out into the market and into another shop.
 */

/** The market front page — the app's answer to the web landing page. */
@Serializable
object HomeRoute

@Serializable
object PairingRoute

/**
 * One shop's shelf. With [categoryId] set it opens as that aisle, which is what
 * a category chip on the front page navigates to — the web page does the same
 * thing in place with ?category=, but Back is a better answer on a phone than a
 * scroll position.
 */
@Serializable
data class CatalogRoute(
    val orgSlug: String,
    val storeCode: String,
    val categoryId: String? = null,
)

@Serializable
data class ProductRoute(
    val orgSlug: String,
    val storeCode: String,
    val productId: String,
)

/** The basket for one shop. Baskets do not mix across shops — one order, one counter. */
@Serializable
data class CartRoute(
    val orgSlug: String,
    val storeCode: String,
)

/**
 * The account screen as a destination, rather than as the Account tab.
 *
 * Reached from checkout, which offers a signed-out shopper the chance to sign
 * in before placing an order. It is a push onto whatever asked for it, so the
 * checkout underneath keeps its filled-in form and its cart, and signing in
 * pops straight back to it.
 */
@Serializable
object AccountRoute

@Serializable
data class CheckoutRoute(
    val orgSlug: String,
    val storeCode: String,
)

/**
 * The last look at the order, and the only place it can actually be placed.
 *
 * Carries the shop rather than the order: there is no order yet. It shares the
 * checkout's view model — resolved against the CheckoutRoute entry underneath
 * it — so the form does not travel as arguments, which is what keeps a name, a
 * phone number and a home address out of the back stack's saved state.
 */
@Serializable
data class OrderReviewRoute(
    val orgSlug: String,
    val storeCode: String,
)

/**
 * One placed order, by its UUID.
 *
 * The id is the capability — the same unguessable value the public tracking
 * endpoint and the customer's broadcast channel are keyed on — which is what
 * makes this screen reachable by a guest with no account at all.
 */
@Serializable
data class OrderRoute(
    val orderId: String,
    /**
     * True only when this screen was reached by placing the order.
     *
     * The confirmation and the tracking screen are the same screen, so this is
     * how it knows which one it is being. Coming back to the same order later —
     * from the order list, from a deep link — arrives with the default, and the
     * screen quite rightly does not congratulate anyone twice.
     */
    val justPlaced: Boolean = false,
)

/**
 * Choosing a new password from the link in a reset email.
 *
 * Reached almost only as a deep link: the mail points at
 * `https://omaykan.com/account?token=…&email=…`, the same URL the web portal
 * honours, and an Android App Link brings it here instead of the browser when
 * the app is installed. Both arguments are required, so a visit to `/account`
 * with no token does not match this route at all — the app opens at the market
 * and the shopper is exactly where the web page would have put them.
 *
 * The token is a one-time capability with a short life, which is why it is a
 * route argument and never stored: leaving it in DataStore would outlive the
 * minutes it is good for and give a lost phone something to redeem.
 */
@Serializable
data class ResetPasswordRoute(
    val token: String,
    val email: String,
)

/*
 * The account portal, one route per section.
 *
 * Separate routes rather than tabs inside the Account tab: each of these is a
 * place a shopper goes to do one thing and then leaves, and Back is the right
 * way out of every one of them. It is also what the web portal does with
 * ?section=, for the same reason.
 *
 * None of them take the account as an argument — it lives in the session flow
 * on AccountRepository, so a save made on one of these screens is already
 * visible to checkout behind it.
 */

/** Name, phone, and the four settings that change what happens to an order. */
@Serializable
object ProfileRoute

@Serializable
object AddressesRoute

/**
 * The add/edit address form, a screen of its own rather than a dialog over the
 * list.
 *
 * The one route here that carries no arguments while plainly being about one
 * row. The form is held by AddressesViewModel — resolved against the
 * AddressesRoute entry still sitting underneath, the way OrderReviewRoute
 * shares checkout's — so which address is being edited was decided before this
 * was navigated to. Spelling it into the route would put a shopper's home
 * address in the back stack's saved state, and would let a restored form be
 * reopened against a row that has since been deleted.
 */
@Serializable
object AddressFormRoute

@Serializable
object PaymentMethodsRoute

/** Adding a payment method. Shares its list's view model, as above. */
@Serializable
object PaymentMethodFormRoute

/** Email and password — the credentials, each behind the current password. */
@Serializable
object SecurityRoute
