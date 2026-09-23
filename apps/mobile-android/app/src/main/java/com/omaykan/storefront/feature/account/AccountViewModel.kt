package com.omaykan.storefront.feature.account

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.auth.GoogleAuthFlow
import com.omaykan.storefront.core.auth.GoogleAuthResult
import com.omaykan.storefront.core.data.AccountRepository
import com.omaykan.storefront.core.data.CheckoutPrefsStore
import com.omaykan.storefront.core.data.OrderRepository
import com.omaykan.storefront.core.data.SavedProductsStore
import com.omaykan.storefront.core.model.OrderStage
import com.omaykan.storefront.core.model.SessionState
import com.omaykan.storefront.core.model.TrackedOrder
import com.omaykan.storefront.core.network.ApiException
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Three states of one card, not three screens.
 *
 * The web storefront's sign-in card does the same thing for the same reason:
 * "who are you" is one decision, and bouncing someone between destinations to
 * answer it loses them. Reset is a fourth state on the web, where the emailed
 * link lands; on the phone that link opens the browser, so this stops at
 * asking for it.
 */
enum class AuthMode { SignIn, Register, Forgot }

/**
 * What the account page counts, and the one order it puts a name to.
 *
 * Every number here is derived from something the app already holds — orders
 * the shopper actually placed, addresses they actually saved, products they
 * actually hearted. Nothing on this page is a balance, a tier or a points
 * total, because this platform has none of those and a zero with nothing
 * behind it is worse than an absence.
 */
data class AccountSummary(
    val loaded: Boolean = false,
    /** A recount the shopper pulled for, as opposed to the one on resume. */
    val refreshing: Boolean = false,
    val orders: Int = 0,
    /** Counts per bucket. Absent keys are zero; cancelled orders are in none. */
    val stages: Map<OrderStage, Int> = emptyMap(),
    val saved: Int = 0,
    /**
     * The order worth putting on the page: the newest one still moving.
     *
     * Null once everything has been received, which is the common state and
     * deliberately shows nothing rather than a "no active orders" card.
     */
    val live: TrackedOrder? = null,
) {
    val active: Int
        get() = stages.filterKeys { it != OrderStage.Completed }.values.sum()

    fun count(stage: OrderStage): Int = stages[stage] ?: 0
}

data class AccountUiState(
    val session: SessionState = SessionState.Restoring,
    val mode: AuthMode = AuthMode.SignIn,
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val password: String = "",
    val showPassword: Boolean = false,
    /** True from the moment something is submitted until there is an answer. */
    val busy: Boolean = false,
    val error: String? = null,
    /** The neutral replies — "check your email" — kept apart from errors. */
    val notice: String? = null,
    /** False when the build has no OAuth client id; the button is then absent. */
    val googleAvailable: Boolean = false,
    val summary: AccountSummary = AccountSummary(),
) {
    /**
     * Checked here only to spare a round trip on the obvious mistakes. The API
     * validates all of it again and its messages win when the two disagree.
     */
    val localProblem: String?
        get() = when {
            !email.contains('@') || email.isBlank() -> "That doesn't look like an email address."
            mode == AuthMode.Forgot -> null
            mode == AuthMode.Register && name.isBlank() -> "We need a name to put on your orders."
            password.isEmpty() -> "Please enter your password."
            // Matches PasswordRule::min(8) on the API — shorter would only bounce.
            mode == AuthMode.Register && password.length < 8 ->
                "Passwords need at least 8 characters."

            else -> null
        }

    val canSubmit: Boolean get() = !busy && localProblem == null
}

/**
 * The account tab.
 *
 * Two ways in and one way out. The password form is the one most shoppers will
 * use — it is the same account the web storefront makes, and a build with no
 * Google client id has nothing else to offer — so it is always present, and
 * Google sits above it when it is configured.
 *
 * The Google half has a second responsibility that looks like the same one: it
 * drives the browser handoff, and it waits for what comes back. They are
 * separate because the app can be killed in between — the shopper is in
 * Chrome, the phone is under memory pressure, and the process that started the
 * sign-in is gone by the time Google redirects. The result arrives on
 * GoogleAuthFlow's replayed flow, collected on construction rather than only
 * after a button press, so a sign-in that outlived the process still lands.
 */
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: AccountRepository,
    private val orderRepository: OrderRepository,
    private val savedProductsStore: SavedProductsStore,
    private val prefs: CheckoutPrefsStore,
    private val flow: GoogleAuthFlow,
) : ViewModel() {

    private val _state = MutableStateFlow(
        AccountUiState(googleAvailable = repository.googleAvailable),
    )
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { repository.restore() }

        viewModelScope.launch {
            repository.session.collect { session ->
                _state.update { it.copy(session = session) }
            }
        }

        viewModelScope.launch {
            flow.results.collect(::onAuthResult)
        }

        // Hearted products are a local set, so this is a disk read and can
        // stand on its own — it must not wait on the order fetch, and it works
        // signed out, which is the whole point of the Saved tab.
        viewModelScope.launch {
            savedProductsStore.saved.collect { ids ->
                _state.update { it.copy(summary = it.summary.copy(saved = ids.size)) }
            }
        }

        // Re-runs when the session changes, so signing in or out recounts
        // rather than leaving the previous shopper's totals on screen.
        viewModelScope.launch {
            combine(repository.session, prefs.recentOrderIds) { session, ids -> session to ids }
                .collect { (session, ids) ->
                    if (session is SessionState.Restoring) return@collect
                    loadSummary(signedIn = session is SessionState.SignedIn, deviceIds = ids)
                }
        }
    }

    /**
     * Recounts the order buckets.
     *
     * A failure is swallowed on purpose. These are decorations on a page whose
     * job is elsewhere — an error banner over someone's own name because the
     * count could not be fetched would be a worse page than one that quietly
     * shows the last numbers it had.
     */
    private suspend fun loadSummary(signedIn: Boolean, deviceIds: List<String>) {
        val orders = runCatching { orderRepository.mine(signedIn, deviceIds) }.getOrNull() ?: return

        _state.update { current ->
            current.copy(
                summary = current.summary.copy(
                    loaded = true,
                    orders = orders.size,
                    stages = orders.mapNotNull { it.stage }.groupingBy { it }.eachCount(),
                    // Newest first out of the repository, so the first one
                    // still moving is the one to surface.
                    live = orders.firstOrNull {
                        it.stage != null && it.stage != OrderStage.Completed
                    },
                ),
            )
        }
    }

    /**
     * Recounts on demand.
     *
     * @param shown true when the shopper asked for it with the pull gesture, so
     *   the indicator stays up until the answer lands. The silent call on
     *   resume leaves it false — a spinner appearing every time this tab comes
     *   forward would be motion nobody asked for.
     */
    fun refreshSummary(shown: Boolean = false) {
        if (shown && _state.value.summary.refreshing) return

        if (shown) {
            _state.update { it.copy(summary = it.summary.copy(refreshing = true)) }
        }

        viewModelScope.launch {
            try {
                val session = repository.session.first()
                if (session is SessionState.Restoring) return@launch

                loadSummary(
                    signedIn = session is SessionState.SignedIn,
                    deviceIds = prefs.recentOrderIds.first(),
                )
            } finally {
                // In a finally because loadSummary returns early on a failed
                // fetch, and an indicator that never stops is worse than a
                // refresh that quietly found nothing new.
                if (shown) {
                    _state.update { it.copy(summary = it.summary.copy(refreshing = false)) }
                }
            }
        }
    }

    // -- The form -------------------------------------------------------------

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }

    fun onEmailChange(value: String) = _state.update { it.copy(email = value.trim(), error = null) }

    fun onPhoneChange(value: String) = _state.update { it.copy(phone = value, error = null) }

    fun onPasswordChange(value: String) = _state.update { it.copy(password = value, error = null) }

    fun togglePasswordVisible() = _state.update { it.copy(showPassword = !it.showPassword) }

    /**
     * The password is dropped on every switch, and on every failure.
     *
     * On a shared phone the next person should not be able to reveal it with
     * the eye toggle, and a wrong one is retyped anyway.
     */
    fun switchTo(mode: AuthMode) = _state.update {
        it.copy(mode = mode, password = "", showPassword = false, error = null, notice = null)
    }

    fun submit() {
        val current = _state.value
        if (current.busy) return

        current.localProblem?.let { problem ->
            _state.update { it.copy(error = problem) }
            return
        }

        _state.update { it.copy(busy = true, error = null, notice = null) }

        viewModelScope.launch {
            try {
                when (current.mode) {
                    AuthMode.SignIn -> {
                        repository.signIn(current.email, current.password)
                        // No success message: the screen becomes the account.
                        _state.update { it.copy(busy = false, password = "") }
                    }

                    AuthMode.Register -> {
                        val message = repository.register(
                            name = current.name,
                            email = current.email,
                            phone = current.phone,
                            password = current.password,
                        )
                        // Back to sign-in carrying the server's sentence. The
                        // account exists but cannot sign in until the emailed
                        // link is clicked, so leaving them on the register form
                        // would invite them to make a second one.
                        _state.update {
                            it.copy(
                                busy = false,
                                mode = AuthMode.SignIn,
                                name = "",
                                phone = "",
                                password = "",
                                showPassword = false,
                                notice = message,
                            )
                        }
                    }

                    AuthMode.Forgot -> {
                        val message = repository.requestPasswordReset(current.email)
                        _state.update { it.copy(busy = false, password = "", notice = message) }
                    }
                }
            } catch (e: ApiException) {
                _state.update { it.copy(busy = false, password = "", error = formMessage(e)) }
            }
        }
    }

    // -- Google ---------------------------------------------------------------

    /**
     * Opens Google's consent page in a Custom Tab.
     *
     * Takes the Activity and does not keep it: a Custom Tab launched from an
     * application context lands in a task of its own, and the shopper ends up
     * with two Omaykans in their recents. Null is the screen saying it could
     * not find one — a real state on a wrapped context, not a bug to crash on.
     */
    fun onSignInPressed(activity: Activity?) {
        if (_state.value.busy) return

        _state.update { it.copy(busy = true, error = null, notice = null) }

        if (activity == null || !flow.launch(activity)) {
            _state.update {
                it.copy(busy = false, error = "No browser on this phone can open the sign-in page.")
            }
        }
    }

    /** The tab closed with nothing decided — usually Back out of Google's page. */
    fun onFlowAbandoned() {
        if (_state.value.busy) flow.onAbandoned()
    }

    private fun onAuthResult(result: GoogleAuthResult) {
        flow.consume()

        when (result) {
            is GoogleAuthResult.Cancelled ->
                // Backing out is not an error and gets no red line. The screen
                // simply goes back to how it was.
                _state.update { it.copy(busy = false, error = null) }

            is GoogleAuthResult.Failed ->
                _state.update { it.copy(busy = false, error = result.message) }

            is GoogleAuthResult.Code -> {
                _state.update { it.copy(busy = true, error = null) }

                viewModelScope.launch {
                    try {
                        repository.completeGoogleSignIn(result.code, result.codeVerifier)
                        _state.update { it.copy(busy = false, error = null, password = "") }
                    } catch (e: ApiException) {
                        _state.update { it.copy(busy = false, error = signInMessage(e)) }
                    }
                }
            }
        }
    }

    // -- Session --------------------------------------------------------------

    fun signOut() {
        if (_state.value.busy) return

        _state.update { it.copy(busy = true, error = null) }

        viewModelScope.launch {
            repository.signOut()
            _state.update {
                it.copy(busy = false, mode = AuthMode.SignIn, email = "", password = "", notice = null)
            }
        }
    }

    fun dismissError() = _state.update { it.copy(error = null) }

    fun dismissNotice() = _state.update { it.copy(notice = null) }

    /**
     * The server's own sentence wherever it wrote one.
     *
     * "Please verify your email first" and "This account signs in with Google"
     * are both answers a shopper can act on, and both arrive as a 422 on
     * `email`. Flattening them into "something went wrong" would leave someone
     * retyping a password that was never the problem.
     */
    private fun formMessage(e: ApiException): String = when (e) {
        is ApiException.Validation ->
            e.first("email") ?: e.first("password") ?: e.first("name") ?: e.message

        is ApiException.RateLimited -> e.retryAfterSeconds
            ?.let { "Too many attempts. Try again in ${it}s." }
            ?: e.message

        is ApiException.Offline -> "No connection. Your basket is safe — try again when you're back."
        else -> e.message
    }

    private fun signInMessage(e: ApiException): String = when (e) {
        is ApiException.Validation -> e.first("credential") ?: e.message
        else -> formMessage(e)
    }
}
