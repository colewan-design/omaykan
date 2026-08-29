package com.omaykan.storefront.feature.checkout

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.omaykan.storefront.core.data.AccountRepository
import com.omaykan.storefront.core.data.CartRepository
import com.omaykan.storefront.core.data.CheckoutPrefsStore
import com.omaykan.storefront.core.data.OrderRepository
import com.omaykan.storefront.core.data.ShopDirectoryRepository
import com.omaykan.storefront.core.model.Cart
import com.omaykan.storefront.core.model.Contact
import com.omaykan.storefront.core.model.CustomerAccount
import com.omaykan.storefront.core.model.DeliveryDestination
import com.omaykan.storefront.core.model.FulfillmentMethod
import com.omaykan.storefront.core.model.PaymentPreference
import com.omaykan.storefront.core.model.SessionState
import com.omaykan.storefront.core.model.StoreRef
import com.omaykan.storefront.core.network.ApiException
import com.omaykan.storefront.navigation.CheckoutRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CheckoutUiState(
    val cart: Cart,
    val name: String = "",
    val phone: String = "",
    val email: String = "",
    val method: FulfillmentMethod = FulfillmentMethod.Pickup,
    val address: String = "",
    /**
     * The landmark, held apart from the address rather than typed into it.
     *
     * It is what the rider actually navigates by, and it outlives the address:
     * a house number changes when someone moves along the street, the green
     * gate past the basketball court does not. Its own box, its own memory, and
     * joined to the address only on the way to the server, which has one field.
     */
    val landmark: String = "",
    /**
     * The drop-off pin, when the address in the box came from a saved one that
     * had it. Null for anything typed by hand — this app has no geocoder, so a
     * typed address genuinely has no coordinates, and the server then charges
     * the shop's flat base fee.
     */
    val dropLat: Double? = null,
    val dropLng: Double? = null,
    val payment: PaymentPreference = PaymentPreference.Cash,
    /**
     * True once the shopper has picked a payment themselves.
     *
     * The account's saved default arrives after first paint, and without this
     * it would land on top of a choice already made — a shopper who tapped
     * GCash would watch it flip back to Cash a second later.
     */
    val paymentTouched: Boolean = false,
    val submitting: Boolean = false,
    /**
     * Null until the session resolves.
     *
     * Deliberately not a reason to disable the button. Restoring a session can
     * take a connect timeout on a bad line, and a shopper who never had an
     * account would be left staring at a dead Place order button for fifteen
     * seconds. Unknown means the prompt is skipped, not that the order is.
     */
    val signedIn: Boolean? = null,
    /** True while the sign-in prompt is on screen. */
    val signInPrompt: Boolean = false,
    /**
     * Set for one frame when the form is good and the review screen should
     * open. Consumed by the screen the moment it has navigated, so backing out
     * of the review does not bounce straight back into it.
     */
    val openReview: Boolean = false,
    /**
     * Set once the shopper has answered the prompt, either way.
     *
     * Asked once per checkout. A prompt that reappears every time the session
     * re-emits is not a prompt, it is a wall wearing a disguise, and this app
     * does not have one — see `place`.
     */
    val guestChosen: Boolean = false,
    /** Keyed by the field paths Laravel uses, e.g. "fulfillment.address". */
    val fieldErrors: Map<String, String> = emptyMap(),
    val error: String? = null,
    val placedOrderId: String? = null,
) {
    val isDelivery: Boolean get() = method == FulfillmentMethod.Delivery

    /**
     * The one string the order carries.
     *
     * `fulfillment.address` is a single field on the API and the merchant reads
     * it as one line off the ticket, so the two boxes are joined here rather
     * than kept apart all the way down. The landmark goes last because that is
     * the order someone says it out loud in.
     */
    val addressForOrder: String
        get() = listOf(address.trim(), landmark.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" — ")

    /**
     * Mirrors the server's rules rather than inventing stricter ones: a name,
     * and at least one of a phone or an email. Checking here saves a round
     * trip; the server checks again because it must.
     *
     * Gates both buttons — Review order on the form, Place order on the review
     * screen — so a form that went stale between the two cannot be sent.
     */
    val canSubmit: Boolean
        get() = !submitting &&
            !cart.isEmpty &&
            name.isNotBlank() &&
            (phone.isNotBlank() || email.isNotBlank()) &&
            (!isDelivery || address.isNotBlank())
}

@HiltViewModel
class CheckoutViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val cartRepository: CartRepository,
    private val orderRepository: OrderRepository,
    private val shopDirectoryRepository: ShopDirectoryRepository,
    private val prefs: CheckoutPrefsStore,
    private val accountRepository: AccountRepository,
) : ViewModel() {

    private val route = savedStateHandle.toRoute<CheckoutRoute>()
    val ref = StoreRef(route.orgSlug, route.storeCode)

    private val _state = MutableStateFlow(CheckoutUiState(Cart(ref)))
    val state: StateFlow<CheckoutUiState> = _state.asStateFlow()

    init {
        /*
         * Two sources, in this order, and the order is the whole point.
         *
         * What this device last used comes first — it is the most recent thing
         * the shopper actually typed. The account fills whatever is still
         * blank after that. They are in one coroutine rather than two because
         * as two they raced: a warm session emits SignedIn immediately, and the
         * DataStore read that landed afterwards would overwrite good account
         * values with this device's empty ones.
         */
        viewModelScope.launch {
            val contact = prefs.contact.first()
            val savedAddress = prefs.address.first()
            val savedLandmark = prefs.landmark.first()

            _state.update {
                it.copy(
                    name = contact.name,
                    phone = contact.phone,
                    email = contact.email,
                    address = savedAddress,
                    landmark = savedLandmark,
                )
            }

            // The repository resolves itself on construction; this is the
            // retry. A restore that failed on a dead connection at launch
            // leaves the session signed out and nothing else would ask again —
            // and arriving at checkout is exactly when it matters.
            accountRepository.restore()

            accountRepository.session.collect { session ->
                if (session is SessionState.Restoring) return@collect

                val account = (session as? SessionState.SignedIn)?.account

                _state.update {
                    if (account != null) return@update it.copy(signedIn = true).applyAccount(account)

                    /*
                     * Asked on arrival, not at the button.
                     *
                     * It used to be raised from `place()`, which could never
                     * fire for the shopper who most needed it: signed out means
                     * an empty form, an empty form means `canSubmit` is false,
                     * and a disabled Place order button cannot be pressed. The
                     * question belongs at the top of the screen anyway — it is
                     * about who is ordering, which is the first thing checkout
                     * asks.
                     */
                    it.copy(signedIn = false, signInPrompt = !it.guestChosen)
                }
            }
        }

        viewModelScope.launch {
            cartRepository.cart(ref).collect { cart -> _state.update { it.copy(cart = cart) } }
        }
    }

    /**
     * Fill the blanks from the account, and only the blanks.
     *
     * The server already back-fills the contact fields from the token, so an
     * empty one would not have failed the order — but `canSubmit` checks them
     * client-side, so without this a shopper who signed in with Google and
     * never typed a name on this phone would be blocked at the button by a rule
     * the API does not have. What they typed themselves always wins; this is a
     * prefill, not an override.
     *
     * The saved address is the one that earns its keep. It arrives with the pin
     * the shopper set when they saved it, and this app cannot produce a pin any
     * other way — so a delivery that would have been charged the shop's flat
     * base fee gets quoted on the real distance instead.
     */
    private fun CheckoutUiState.applyAccount(account: CustomerAccount): CheckoutUiState {
        val filled = copy(
            name = name.ifBlank { account.name },
            phone = phone.ifBlank { account.phone },
            email = email.ifBlank { account.email },
            // A saved card only moves the default; it never overrides a choice
            // already made on this screen, which is why it is guarded on the
            // untouched state rather than applied outright.
            payment = if (paymentTouched) payment else account.preferredPayment?.kind ?: payment,
        )

        val saved = account.preferredAddress

        // Only into an empty box. Replacing an address the shopper typed —
        // possibly the one place they want this order to go — would be the
        // rudest thing on the screen.
        if (saved == null || filled.address.isNotBlank()) return filled

        return filled.copy(
            address = saved.oneLine,
            // Only into an empty box, same rule as the address. Someone who has
            // typed a landmark for this order means it for this order.
            landmark = filled.landmark.ifBlank { saved.notes },
            dropLat = saved.lat,
            dropLng = saved.lng,
        )
    }

    fun onName(value: String) = _state.update { it.copy(name = value, fieldErrors = it.fieldErrors - "guest.name") }
    fun onPhone(value: String) = _state.update { it.copy(phone = value, fieldErrors = it.fieldErrors - "guest.phone") }
    fun onEmail(value: String) = _state.update { it.copy(email = value, fieldErrors = it.fieldErrors - "guest.email") }
    /**
     * Editing the address drops the pin with it.
     *
     * The coordinates came from a saved address; the moment the text stops
     * being that address they describe somewhere else. Keeping them would quote
     * — and charge — a delivery fee for the wrong door.
     */
    fun onAddress(value: String) = _state.update {
        it.copy(
            address = value,
            dropLat = null,
            dropLng = null,
            fieldErrors = it.fieldErrors - "fulfillment.address",
        )
    }

    /**
     * The landmark does not clear the pin, unlike the address.
     *
     * The coordinates describe the place; the landmark only describes how to
     * recognise it once you are there. Refining "green gate" to "green gate,
     * second floor" has not moved the delivery an inch.
     */
    fun onLandmark(value: String) = _state.update {
        it.copy(landmark = value, fieldErrors = it.fieldErrors - "fulfillment.address")
    }

    /**
     * Closed on the way to signing in.
     *
     * `guestChosen` is deliberately left alone: they have not chosen anything
     * yet, and if they come back without a session the question is still worth
     * asking.
     */
    fun dismissSignInPrompt() = _state.update { it.copy(signInPrompt = false) }

    /**
     * Answered with "continue as guest", or waved away.
     *
     * Either counts as an answer, and the checkout carries on exactly as it
     * always did — the order a guest places is the same order.
     */
    fun continueAsGuest() = _state.update { it.copy(signInPrompt = false, guestChosen = true) }

    fun onMethod(method: FulfillmentMethod) = _state.update { it.copy(method = method) }
    fun onPayment(payment: PaymentPreference) =
        _state.update { it.copy(payment = payment, paymentTouched = true) }

    /**
     * The Place order button: review, do not send.
     *
     * It runs the two things that must happen before an order can be spoken
     * for — the client-side check, and the offer to sign in — and then asks for
     * the review screen. See `confirm` for the half that talks to the server.
     *
     * Offer to sign in, once, before the first attempt at placing an order.
     *
     * A prompt and not a gate. An account means this form is filled in next
     * time and the order joins a history that survives a new phone, which is
     * worth interrupting once to say — but ordering has never required one and
     * still does not (mobile-plan.md §5, fact 3), so "continue as guest" is
     * right there and the order behind it is identical.
     */
    fun place() {
        val current = _state.value
        if (!current.canSubmit) return

        // The backstop. The prompt normally appears on arrival, but a session
        // that resolves late — a slow restore on a bad line — can leave a
        // shopper who typed fast reaching this first.
        if (current.signedIn == false && !current.guestChosen) {
            _state.update { it.copy(signInPrompt = true) }
            return
        }

        /*
         * Nothing is sent yet. The button opens the review; `confirm` there is
         * what actually spends the stock.
         *
         * Last time's complaints are dropped on the way. The review screen
         * bounces back here the moment it sees a field error, so carrying a
         * stale one forward would make it bounce on arrival — and a rejected
         * `items.0.productId` clears on no keystroke at all, which would leave
         * the review screen permanently unreachable.
         */
        _state.update { it.copy(openReview = true, fieldErrors = emptyMap(), error = null) }
    }

    /** Consumed by the screen once it has navigated, so Back cannot re-fire it. */
    fun onReviewOpened() = _state.update { it.copy(openReview = false) }

    /**
     * Yes, place it — the one call that spends real stock, made from the review
     * screen and nowhere else.
     *
     * Guarded against a double tap by `canSubmit`, and never retried on its
     * own: this request decrements stock and mints a ticket a merchant will
     * start preparing. If it fails, the shopper decides whether to try again.
     */
    fun confirm() {
        val current = _state.value
        if (!current.canSubmit) return

        _state.update { it.copy(submitting = true, error = null, fieldErrors = emptyMap()) }

        viewModelScope.launch {
            try {
                // The catalog does not report a shop's business mode and the
                // order endpoint requires it — the directory is the one public
                // place that carries it. See ShopDirectoryRepository.shop.
                val mode = shopDirectoryRepository.shop(ref)?.businessMode?.wire
                if (mode.isNullOrBlank()) {
                    _state.update {
                        it.copy(
                            submitting = false,
                            error = "This shop is not taking orders right now.",
                        )
                    }
                    return@launch
                }

                val contact = Contact(current.name, current.phone, current.email)
                val placed = orderRepository.place(
                    cart = current.cart,
                    businessMode = mode,
                    contact = contact,
                    method = current.method,
                    destination = if (current.isDelivery) {
                        // A pin only ever comes from a saved address — this
                        // project has no geocoder, so an address typed here
                        // cannot produce one, and the fee then falls back to
                        // the shop's flat rate. The API rejects half a
                        // coordinate, so the pair travels whole or not at all.
                        DeliveryDestination(
                            address = current.addressForOrder,
                            lat = current.dropLat,
                            lng = current.dropLng,
                        )
                    } else {
                        null
                    },
                    payment = current.payment,
                )

                // Only after the server has the order: emptying a basket for a
                // request that failed would lose the shopper their work.
                cartRepository.clear(ref)
                prefs.remember(contact, current.address, current.landmark)
                prefs.rememberOrder(placed.orderId)

                _state.update { it.copy(submitting = false, placedOrderId = placed.orderId) }
            } catch (e: ApiException) {
                _state.update { it.copy(submitting = false).withFailure(e) }
            }
        }
    }

    private fun CheckoutUiState.withFailure(e: ApiException): CheckoutUiState = when (e) {
        // Laravel keys these by field path. An address outside the shop's
        // delivery range arrives here, and belongs on the address field rather
        // than in a toast that leaves the shopper guessing what to change.
        is ApiException.Validation -> copy(
            fieldErrors = e.errors.mapValues { (_, messages) -> messages.first() },
            error = if (e.errors.isEmpty()) e.message else null,
        )
        is ApiException.RateLimited -> copy(
            error = e.retryAfterSeconds
                ?.let { "Too many attempts. Try again in ${it}s." }
                ?: e.message,
        )
        else -> copy(error = e.message)
    }

    /** Consumed by the screen once it has navigated, so Back cannot re-fire it. */
    fun onNavigated() = _state.update { it.copy(placedOrderId = null) }
}
