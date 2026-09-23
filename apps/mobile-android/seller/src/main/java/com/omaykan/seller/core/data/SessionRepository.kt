package com.omaykan.seller.core.data

import com.omaykan.seller.core.auth.GoogleAuthFlow
import com.omaykan.seller.core.auth.StaffTokenStore
import com.omaykan.seller.core.model.PairedStore
import com.omaykan.seller.core.model.SessionState
import com.omaykan.seller.core.model.StaffStore
import com.omaykan.seller.core.network.ApiCaller
import com.omaykan.seller.core.network.ApiException
import com.omaykan.seller.core.network.GoogleOAuthApi
import com.omaykan.seller.core.network.SellerApi
import com.omaykan.seller.core.network.dto.SelectStoreRequestDto
import com.omaykan.seller.core.network.dto.StaffGoogleRequestDto
import com.omaykan.seller.core.network.dto.StaffSignInRequestDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signing a person in to a shop, and knowing whether one is.
 *
 * ## Why a person and not a device
 *
 * This app used to pair: one code, typed once, off the paperwork the shop
 * already had — no username to remember and no password to type on a phone at
 * six in the morning. It was a good sign-in for a counter and it is gone for
 * the reason it was good. A shared secret printed on a receipt identifies a
 * *shop*, so nothing done here could be attributed to a person, settling a
 * payment left `payment_confirmed_by_user_id` null, and somebody who left last
 * month kept access until the whole shop rotated its code and every till
 * re-paired.
 *
 * What replaces it is a staff account: a password, or the Google button, and
 * the same account the register on the counter uses. The trade is real and runs
 * the other way now — a barista has something to remember — and what the shop
 * gets for it is a name against every settled payment and a way to remove one
 * person without disturbing anybody else.
 *
 * ## Two calls, one sign-in
 *
 * Proving who you are does not say which shop you are standing in, so
 * [signIn] answers with the shops this account can act for and [chooseStore]
 * mints the token the rest of the app runs on. A manager covering three
 * branches is the ordinary case here, and picking one for them would mean
 * orders advanced at the wrong counter.
 */
@Singleton
class SessionRepository @Inject constructor(
    private val api: SellerApi,
    private val googleApi: GoogleOAuthApi,
    private val google: GoogleAuthFlow,
    private val caller: ApiCaller,
    private val tokens: StaffTokenStore,
    private val stores: PairedStoreStore,
) {
    /**
     * Signed in only when both halves are present.
     *
     * A token with no store record would leave the title bar blank; a store
     * record with no token would show a shop's name above a list that 401s.
     * Both happen — the two are written to different files, and only one of
     * them is cleared when a token dies — so the session is the conjunction
     * rather than either one alone.
     */
    val session: Flow<SessionState> = combine(tokens.token, stores.paired) { token, store ->
        when {
            token == null || store == null -> SessionState.SignedOut
            else -> SessionState.Paired(store)
        }
    }

    /**
     * Step one: prove who this is.
     *
     * Returns the pending token and the shops it can open. Nothing is stored
     * yet — a token that names no shop cannot fetch an order, and writing it
     * would put the app in a state where it believes it is signed in and every
     * screen 403s.
     */
    suspend fun signIn(identifier: String, password: String): PendingSignIn =
        caller.call {
            api.signIn(StaffSignInRequestDto(identifier.trim(), password))
        }.let { dto ->
            PendingSignIn(
                token = dto.token,
                stores = dto.stores.map { StaffStore(it.id, it.name, it.code, it.organizationSlug.orEmpty()) },
            )
        }

    /**
     * The same step one, from the code Google's browser page handed back.
     *
     * The exchange happens here rather than on our own server, which keeps
     * `/api/staff/auth/google` to one payload shape — an ID token — shared with
     * the web register's button. A failure from Google's endpoint is most often
     * a code already redeemed, which is what a double tap looks like.
     */
    suspend fun signInWithGoogle(code: String, codeVerifier: String): PendingSignIn {
        val exchanged = caller.call {
            googleApi.exchangeCode(
                url = GoogleOAuthApi.TOKEN_ENDPOINT,
                clientId = google.clientId,
                code = code,
                codeVerifier = codeVerifier,
                redirectUri = google.redirectUri,
                grantType = "authorization_code",
            )
        }

        if (exchanged.idToken.isBlank()) {
            throw ApiException.Unexpected(IllegalStateException("Google returned no ID token."))
        }

        val dto = caller.call { api.signInWithGoogle(StaffGoogleRequestDto(exchanged.idToken)) }

        return PendingSignIn(
            token = dto.token,
            stores = dto.stores.map { StaffStore(it.id, it.name, it.code, it.organizationSlug.orEmpty()) },
        )
    }

    /**
     * Step two: choose the shop, and become signed in.
     *
     * The pending token is passed as an explicit bearer — it is not the stored
     * one, and will not be unless this call succeeds.
     */
    suspend fun chooseStore(pendingToken: String, store: StaffStore): PairedStore {
        val session = caller.call {
            api.selectStore("Bearer $pendingToken", SelectStoreRequestDto(store.id))
        }

        val paired = PairedStore(
            id = session.store.id,
            name = session.store.name,
            code = session.store.code,
            organizationSlug = session.store.organizationSlug.orEmpty(),
        )

        // The store record first, then the token. The session flow above only
        // reports Paired when it holds both, and this order means the one frame
        // where exactly one is written shows the sign-in screen rather than an
        // empty shop.
        stores.save(paired)
        tokens.save(session.token)

        return paired
    }

    /**
     * Sign this phone out.
     *
     * Tells the server first so the token is actually retired — pairing had no
     * such endpoint, and a lost phone meant rotating the shop's code — but a
     * failure there is not allowed to keep somebody signed in on the device in
     * front of them. The local clear always happens.
     */
    suspend fun signOut() {
        runCatching { caller.call { api.signOut() } }

        tokens.clear()
        stores.clear()
    }
}

/** A sign-in that has proved who, and not yet where. */
data class PendingSignIn(
    val token: String,
    val stores: List<StaffStore>,
)
