package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.auth.CustomerTokenStore
import com.omaykan.storefront.core.auth.GoogleAuthFlow
import com.omaykan.storefront.core.model.CustomerAccount
import com.omaykan.storefront.core.model.CustomerPreferences
import com.omaykan.storefront.core.model.PaymentPreference
import com.omaykan.storefront.core.model.SavedAddress
import com.omaykan.storefront.core.model.SavedPaymentMethod
import com.omaykan.storefront.core.model.SessionState
import com.omaykan.storefront.core.model.Substitution
import com.omaykan.storefront.core.network.ApiCaller
import com.omaykan.storefront.core.network.ApiException
import com.omaykan.storefront.core.network.GoogleOAuthApi
import com.omaykan.storefront.core.network.OmaykanApi
import com.omaykan.storefront.core.network.dto.CustomerAccountDto
import com.omaykan.storefront.core.network.dto.CustomerPreferencesDto
import com.omaykan.storefront.core.network.dto.ForgotPasswordRequestDto
import com.omaykan.storefront.core.network.dto.GoogleSignInRequestDto
import com.omaykan.storefront.core.network.dto.LoginRequestDto
import com.omaykan.storefront.core.network.dto.RegisterRequestDto
import com.omaykan.storefront.core.network.dto.ResetPasswordRequestDto
import com.omaykan.storefront.core.network.dto.SavedAddressDto
import com.omaykan.storefront.core.network.dto.SavedAddressRequestDto
import com.omaykan.storefront.core.network.dto.SavedPaymentMethodDto
import com.omaykan.storefront.core.network.dto.SavedPaymentMethodRequestDto
import com.omaykan.storefront.core.network.dto.SessionDto
import com.omaykan.storefront.core.network.dto.UpdateEmailRequestDto
import com.omaykan.storefront.core.network.dto.UpdatePasswordRequestDto
import com.omaykan.storefront.core.network.dto.UpdateProfileRequestDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Who is signed in, and the two-step dance that gets them there.
 *
 * Sign-in with Google is two network calls, and it matters which server does
 * what:
 *
 *  1. The authorization code goes to *Google*, with the PKCE verifier, and
 *     comes back as an ID token. A public client, no secret — see
 *     GoogleAuthFlow.
 *  2. The ID token goes to *us*, and the backend checks Google's signature on
 *     it before it will agree who anybody is. Nothing the app says about the
 *     shopper's identity is taken on trust; the token is the whole claim.
 *
 * Singleton and StateFlow-backed, because the account is not one screen's
 * state. Checkout prefills from it, the header greets from it, and a 401 on any
 * call has to move all of them at once.
 *
 * Nothing here gates anything. Guest checkout is untouched by every line of it
 * (mobile-plan.md §5, fact 3) — signing in prefills the contact fields and puts
 * the order in a history, and that is the entire difference.
 */
@Singleton
class AccountRepository @Inject constructor(
    private val api: OmaykanApi,
    private val googleApi: GoogleOAuthApi,
    private val caller: ApiCaller,
    private val tokens: CustomerTokenStore,
    private val flow: GoogleAuthFlow,
    @AppScope scope: CoroutineScope,
) {
    private val state = MutableStateFlow<SessionState>(
        // A stored token means there is something to restore; without one the
        // answer is already known and no spinner should ever be shown for it.
        if (tokens.current() == null) SessionState.SignedOut else SessionState.Restoring,
    )

    val session: StateFlow<SessionState> = state.asStateFlow()

    /** One restore at a time however many screens ask on the same launch. */
    private val restoreLock = Mutex()

    private var restored = false

    init {
        /*
         * Resolve the session as soon as anything asks for this repository,
         * rather than waiting for the account screen to be opened.
         *
         * This used to be the account screen's job, and that was a bug with a
         * long reach: a shopper who signed in, closed the app, reopened it and
         * went straight to checkout got a session stuck on `Restoring` — so
         * checkout prefilled nothing, and they retyped a name the app already
         * knew. Anything that reads `session` deserves an answer, and only the
         * repository knows when it has one.
         *
         * Idempotent: `restore()` takes the lock and returns immediately if it
         * has already run, so the account screen calling it too costs nothing.
         */
        scope.launch { restore() }

        /*
         * The token can be thrown away by CustomerAuthInterceptor, from a
         * background call on a screen nobody is looking at, when our API
         * answers 401. This is what turns that into a signed-out session
         * everywhere rather than a header that silently stops being sent.
         */
        scope.launch {
            tokens.token.collect { token ->
                if (token == null && state.value !is SessionState.SignedOut) {
                    restored = false
                    state.value = SessionState.SignedOut
                }
            }
        }
    }

    val googleAvailable: Boolean get() = flow.available

    /**
     * Trades the stored token for the account it belongs to.
     *
     * A token the server no longer honours — revoked from another device, or
     * cleared by a password reset — is dropped here rather than left to fail
     * the next call. Anything else (offline, API down) leaves it alone: a flaky
     * connection on a jeepney is not a reason to sign someone out.
     */
    suspend fun restore() {
        restoreLock.withLock {
            if (restored) return
            restored = true

            if (tokens.current() == null) {
                state.value = SessionState.SignedOut
                return
            }

            try {
                state.value = SessionState.SignedIn(caller.call { api.customerAccount() }.account.toModel())
            } catch (e: ApiException.Unauthorized) {
                tokens.clear()
                state.value = SessionState.SignedOut
            } catch (e: ApiException) {
                // Still holding a token we have no reason to distrust. Try again
                // on the next launch rather than making them sign in because a
                // cell handover ate one request.
                restored = false
                state.value = SessionState.SignedOut
            }
        }
    }

    /**
     * Email and password.
     *
     * The other door, and for most shoppers the only one: the Google button
     * needs an OAuth client id the build may not have, and a shopper who made
     * their account on the web storefront already has a password.
     */
    suspend fun signIn(email: String, password: String): CustomerAccount =
        adopt(
            caller.call {
                api.login(LoginRequestDto(email = email.trim().lowercase(), password = password))
            },
        )

    /**
     * Creates an account. Does **not** sign in, and must not pretend to.
     *
     * The server answers 201 with no token: it has sent a verification link and
     * will refuse `login` until it is clicked. The screen's job afterwards is to
     * say so and go back to the sign-in form — anything that looked like a
     * successful sign-in here would strand the shopper on a shop they cannot
     * order from.
     *
     * @return the server's own sentence about what to do next.
     */
    suspend fun register(
        name: String,
        email: String,
        phone: String,
        password: String,
    ): String {
        val reply = caller.call {
            api.register(
                RegisterRequestDto(
                    name = name.trim(),
                    email = email.trim().lowercase(),
                    phone = phone.trim().ifBlank { null },
                    password = password,
                    passwordConfirmation = password,
                ),
            )
        }

        return reply.message.ifBlank {
            "Check your email for a verification link before signing in."
        }
    }

    /**
     * Asks for a reset link.
     *
     * The reply is the same whether or not the address has an account — that is
     * the server refusing to be a way of testing which addresses shop here — so
     * the screen must not add "if we found you" language of its own.
     */
    suspend fun requestPasswordReset(email: String): String {
        val reply = caller.call {
            api.forgotPassword(ForgotPasswordRequestDto(email = email.trim().lowercase()))
        }

        return reply.message.ifBlank { "If that email has an account, a reset link is on its way." }
    }

    /**
     * Redeems the token from a reset email, and signs in on the way through.
     *
     * The server clears every other token on the account as part of this, so a
     * shopper resetting because somebody else had their password ends up the
     * only one signed in. That is also why this adopts a session rather than
     * returning a message: they have just proved they hold the mailbox, and
     * sending them to a sign-in form to type the password they set four seconds
     * ago would be a fine way to lose them.
     *
     * @throws ApiException.Validation with `token` for a link that has expired
     *   or has already been used.
     */
    suspend fun resetPassword(token: String, email: String, password: String): CustomerAccount =
        adopt(
            caller.call {
                api.resetPassword(
                    ResetPasswordRequestDto(
                        token = token,
                        email = email.trim().lowercase(),
                        password = password,
                        passwordConfirmation = password,
                    ),
                )
            },
        )

    /**
     * Completes a sign-in from the code the browser handed back.
     *
     * @throws ApiException on anything that fails, mapped by ApiCaller — a 422
     *   from our side is a token the server would not believe, and a failure
     *   from Google's endpoint is most often a code already redeemed, which is
     *   what a double-tap looks like.
     */
    suspend fun completeGoogleSignIn(code: String, codeVerifier: String): CustomerAccount {
        val exchanged = caller.call {
            googleApi.exchangeCode(
                url = GoogleOAuthApi.TOKEN_ENDPOINT,
                clientId = flow.clientId,
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = flow.redirectUri,
                grantType = "authorization_code",
            )
        }

        if (exchanged.idToken.isBlank()) {
            throw ApiException.Unexpected(IllegalStateException("Google returned no ID token."))
        }

        return adopt(
            caller.call { api.signInWithGoogle(GoogleSignInRequestDto(credential = exchanged.idToken)) },
        )
    }

    /**
     * Takes a minted session and makes it this device's.
     *
     * Every road in ends here — password, Google, and whatever comes next — so
     * that nothing downstream can tell which door was used, and so the token is
     * never stored in one place and the account in another.
     */
    private fun adopt(session: SessionDto): CustomerAccount {
        tokens.save(session.token)
        restored = true

        val account = session.account.toModel()
        state.value = SessionState.SignedIn(account)

        return account
    }

    /*
     * The account portal.
     *
     * Every one of these writes and then republishes the whole account into
     * `session`, because that flow is what checkout prefills from and what the
     * header greets by name. A screen that updated only its own copy would
     * leave a saved address invisible to the checkout screen behind it until
     * the next launch.
     *
     * None of them catch anything: a failed save is the calling screen's to
     * show, next to the field that caused it.
     */

    /** Name, phone and the four preferences. Nulls are left alone server-side. */
    suspend fun updateProfile(
        name: String? = null,
        phone: String? = null,
        preferences: CustomerPreferences? = null,
    ): CustomerAccount = publish(
        caller.call {
            api.updateProfile(
                UpdateProfileRequestDto(
                    name = name?.trim(),
                    phone = phone?.trim(),
                    preferences = preferences?.toDto(),
                ),
            ).account
        },
    )

    /**
     * Moves the address the account signs in with.
     *
     * @throws ApiException.Validation with `email` for an address already
     *   taken, for a wrong password, and for a Google-only account — whose
     *   address is Google's to change, not ours.
     */
    suspend fun updateEmail(email: String, currentPassword: String): CustomerAccount = publish(
        caller.call {
            api.updateEmail(
                UpdateEmailRequestDto(
                    email = email.trim().lowercase(),
                    currentPassword = currentPassword,
                ),
            ).account
        },
    )

    /**
     * Changes the password, or sets the first one on a Google-only account.
     *
     * @param currentPassword null only when the account has none to prove. The
     *   server signs out every other device on success; this one survives, so
     *   the stored token is still good and is deliberately not touched.
     */
    suspend fun updatePassword(currentPassword: String?, password: String): CustomerAccount = publish(
        caller.call {
            api.updatePassword(
                UpdatePasswordRequestDto(
                    currentPassword = currentPassword,
                    password = password,
                    passwordConfirmation = password,
                ),
            ).account
        },
    )

    /** The first address saved becomes the default whatever is asked for. */
    suspend fun saveAddress(draft: AddressDraft): CustomerAccount =
        publish(caller.call { api.createAddress(draft.toDto()).account })

    suspend fun updateAddress(id: String, draft: AddressDraft): CustomerAccount =
        publish(caller.call { api.updateAddress(id, draft.toDto()).account })

    suspend fun deleteAddress(id: String): CustomerAccount =
        publish(caller.call { api.deleteAddress(id).account })

    /**
     * Makes one address the one checkout opens on.
     *
     * Its own call rather than a flag on the edit form: the server refuses to
     * *clear* the flag — a list without a default is not a state it allows — so
     * moving it is always "make this one the default", never "unset that one".
     */
    suspend fun makeAddressDefault(id: String): CustomerAccount =
        publish(caller.call { api.updateAddress(id, SavedAddressRequestDto(isDefault = true)).account })

    /**
     * @throws ApiException.Validation with `kind` for a second cash row, which
     *   would be an exact duplicate of the first.
     */
    suspend fun savePaymentMethod(kind: PaymentPreference, detail: String): CustomerAccount = publish(
        caller.call {
            api.createPaymentMethod(
                SavedPaymentMethodRequestDto(
                    kind = kind.wire,
                    detail = detail.trim().ifBlank { null },
                ),
            ).account
        },
    )

    suspend fun deletePaymentMethod(id: String): CustomerAccount =
        publish(caller.call { api.deletePaymentMethod(id).account })

    suspend fun makePaymentMethodDefault(id: String): CustomerAccount =
        publish(
            caller.call {
                api.updatePaymentMethod(id, SavedPaymentMethodRequestDto(isDefault = true)).account
            },
        )

    /**
     * Takes the account off a write and makes it the session's.
     *
     * Only ever called while signed in — every caller above is behind a token —
     * so it overwrites the state rather than merging into whatever was there.
     */
    private fun publish(dto: CustomerAccountDto): CustomerAccount {
        val account = dto.toModel()
        state.value = SessionState.SignedIn(account)

        return account
    }

    /**
     * Signs out this device.
     *
     * The server call is best-effort. Someone who pressed sign out has been
     * signed out whatever the network said — leaving them signed in because a
     * request timed out is the wrong answer to the one thing they asked for.
     */
    suspend fun signOut() {
        try {
            caller.call { api.customerLogout() }
        } catch (e: ApiException) {
            // Already-dead token, or offline. The local clear below is the part
            // that matters.
        }

        tokens.clear()
        state.value = SessionState.SignedOut
    }

}

private fun CustomerAccountDto.toModel() = CustomerAccount(
    id = id,
    name = name,
    email = email,
    phone = phone,
    avatarUrl = avatarUrl,
    googleLinked = googleLinked,
    hasPassword = hasPassword,
    preferences = CustomerPreferences(
        emailUpdates = preferences.emailUpdates,
        smsUpdates = preferences.smsUpdates,
        marketingEmails = preferences.marketingEmails,
        substitutions = Substitution.fromWire(preferences.substitutions),
    ),
    addresses = addresses.map { it.toModel() },
    paymentMethods = paymentMethods.map { it.toModel() },
)

private fun CustomerPreferences.toDto() = CustomerPreferencesDto(
    emailUpdates = emailUpdates,
    smsUpdates = smsUpdates,
    marketingEmails = marketingEmails,
    substitutions = substitutions.wire,
)

/**
 * An address as a form holds it, before the server has an opinion.
 *
 * Separate from SavedAddress because a draft has no id and no default flag —
 * and because the pin travels as a pair or not at all, which this enforces on
 * the way out rather than leaving to each caller.
 */
data class AddressDraft(
    val label: String,
    val line1: String,
    val barangay: String,
    val city: String,
    val notes: String,
    val lat: Double? = null,
    val lng: Double? = null,
) {
    fun toDto(): SavedAddressRequestDto {
        val pinned = lat != null && lng != null

        return SavedAddressRequestDto(
            label = label.trim(),
            line1 = line1.trim(),
            barangay = barangay.trim().ifBlank { null },
            city = city.trim(),
            notes = notes.trim().ifBlank { null },
            lat = if (pinned) lat else null,
            lng = if (pinned) lng else null,
        )
    }
}

private fun SavedAddressDto.toModel() = SavedAddress(
    id = id,
    label = label,
    line1 = line1,
    barangay = barangay,
    city = city,
    notes = notes,
    lat = lat,
    lng = lng,
    isDefault = isDefault,
)

private fun SavedPaymentMethodDto.toModel() = SavedPaymentMethod(
    id = id,
    // Anything the server adds later reads as cash rather than throwing: an
    // unknown payment preference must not be able to stop a shopper checking
    // out, and cash is what both options settle as anyway.
    kind = PaymentPreference.entries.firstOrNull { it.wire == kind } ?: PaymentPreference.Cash,
    detail = detail,
    isDefault = isDefault,
)
