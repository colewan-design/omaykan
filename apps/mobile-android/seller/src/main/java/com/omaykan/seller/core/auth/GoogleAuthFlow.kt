package com.omaykan.seller.core.auth

import android.content.ActivityNotFoundException
import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import com.omaykan.seller.BuildConfig
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sign in with Google, in the phone's own browser.
 *
 * A near-copy of :app's, ported rather than imported — the same rule the theme
 * and `ApiCaller` follow here, and for the same reason: factoring a shared
 * module out of a shipped app before this one has settled would mean designing
 * it from one example.
 *
 * Custom Tabs and a hand-rolled PKCE exchange, not Credential Manager and not
 * the Google Identity SDK. That is a deliberate choice and it follows the rest
 * of mobile-plan.md: the app depends on no part of Google Play Services, so it
 * works on a phone that has none, and there is no `google-services.json` for
 * anyone to drop Firebase back in through. The cost is this file. The benefit
 * is that the app keeps working on the hardware a lot of Philippine merchants
 * actually carry.
 *
 * Not a WebView either. A WebView asking for a Google password is exactly what
 * a phishing page looks like, Google blocks the flow inside one, and the
 * merchant would get no address bar to check. Custom Tabs is the real browser,
 * with the real lock icon and the session they may already have.
 *
 * PKCE is what makes a client secret unnecessary. This app is a public client —
 * anything compiled into it is readable by anyone holding the APK — so the
 * authorization code is bound to a random verifier that never leaves the
 * device's memory, and a code intercepted on its way back is worthless without
 * it. See RFC 7636.
 */
@Singleton
class GoogleAuthFlow @Inject constructor() {

    private companion object {
        const val AUTH_ENDPOINT = "https://accounts.google.com/o/oauth2/v2/auth"

        /*
         * The custom scheme Google redirects back to. It is the package name,
         * which is what an Android OAuth client is registered against, so the
         * debug build (`.debug` suffix) redirects somewhere the release build
         * does not — and needs its own client in the console to match.
         */
        const val REDIRECT_PATH = ":/oauth2redirect"
    }

    /** One request in flight at a time; a second press replaces the first. */
    private var inFlight: Pending? = null

    private data class Pending(val state: String, val codeVerifier: String)

    /**
     * Results delivered from GoogleAuthRedirectActivity, which is a different
     * activity in a different task from whatever is collecting.
     *
     * Replay of 1 on purpose: the redirect can land while the process is being
     * brought back to the foreground and before the ViewModel has re-collected,
     * and a sign-in silently dropped for that is a merchant pressing the button
     * again wondering what happened.
     */
    private val resultStream = MutableSharedFlow<GoogleAuthResult>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    val results: SharedFlow<GoogleAuthResult> = resultStream.asSharedFlow()

    val clientId: String get() = BuildConfig.GOOGLE_OAUTH_CLIENT_ID

    /**
     * False when the build has no OAuth client id, which is a supported state:
     * the sign-in screen simply has no Google button on it, and a username and
     * password reach every account that has one.
     */
    val available: Boolean get() = clientId.isNotBlank()

    val redirectUri: String get() = BuildConfig.APPLICATION_ID + REDIRECT_PATH

    /**
     * Opens Google's consent page.
     *
     * Returns false when there is no browser at all to open it in — rare, but a
     * stripped device does exist, and it deserves a sentence rather than a
     * crash.
     */
    fun launch(context: Context): Boolean {
        if (!available) return false

        val verifier = randomUrlSafe(64)
        val state = randomUrlSafe(24)

        inFlight = Pending(state = state, codeVerifier = verifier)

        val url = Uri.parse(AUTH_ENDPOINT).buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            // openid for the ID token, which is the only thing the backend
            // wants; email and profile for the address and the name on it. No
            // Gmail, no Drive, no contacts — the consent screen should be able
            // to say exactly that.
            .appendQueryParameter("scope", "openid email profile")
            .appendQueryParameter("code_challenge", challengeFor(verifier))
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            // Ask every time rather than silently reusing whichever account the
            // browser is already signed into. A shared phone is the normal case.
            .appendQueryParameter("prompt", "select_account")
            .build()

        return try {
            CustomTabsIntent.Builder()
                .setShowTitle(true)
                .setUrlBarHidingEnabled(false)
                .build()
                .launchUrl(context, url)
            true
        } catch (e: ActivityNotFoundException) {
            inFlight = null
            false
        }
    }

    /**
     * Called by GoogleAuthRedirectActivity with whatever came back.
     *
     * The `state` check is the CSRF defence: without it, anyone able to fire an
     * intent at our redirect scheme could hand the app an authorization code of
     * their own choosing and sign the merchant into somebody else's account.
     */
    fun onRedirect(uri: Uri) {
        val pending = inFlight
        inFlight = null

        publish(
            readRedirect(
                expectedState = pending?.state,
                codeVerifier = pending?.codeVerifier,
                returnedState = uri.getQueryParameter("state"),
                error = uri.getQueryParameter("error"),
                code = uri.getQueryParameter("code"),
            ),
        )
    }

    /** The tab was dismissed without Google redirecting anywhere. */
    fun onAbandoned() {
        if (inFlight == null) return
        inFlight = null
        publish(GoogleAuthResult.Cancelled)
    }

    /** Dropped once acted on, so returning to the screen does not replay it. */
    @OptIn(ExperimentalCoroutinesApi::class)
    fun consume() {
        resultStream.resetReplayCache()
    }

    private fun publish(result: GoogleAuthResult) {
        resultStream.tryEmit(result)
    }

    private fun randomUrlSafe(bytes: Int): String {
        val buffer = ByteArray(bytes)
        SecureRandom().nextBytes(buffer)
        return encode(buffer)
    }

    private fun challengeFor(verifier: String): String =
        encode(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

    /** base64url without padding, which is what RFC 7636 asks for. */
    private fun encode(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
}

/**
 * What a redirect means, decided from its parameters alone.
 *
 * Pulled out of [GoogleAuthFlow.onRedirect] so it can be tested without an
 * Android `Uri`: the `state` comparison below is this feature's CSRF defence,
 * and a defence nothing exercises is a defence nobody can trust.
 *
 * The state check comes first and answers before anything else is read.
 * Without it, any app on the phone could fire an intent at our redirect scheme
 * carrying an authorization code of its own and sign the merchant into an
 * account they do not own.
 */
internal fun readRedirect(
    expectedState: String?,
    codeVerifier: String?,
    returnedState: String?,
    error: String?,
    code: String?,
): GoogleAuthResult {
    if (expectedState == null || codeVerifier == null || returnedState != expectedState) {
        return GoogleAuthResult.Failed("That sign-in did not match the one this app started.")
    }

    if (error != null) {
        // `access_denied` is the merchant pressing cancel or closing the tab.
        // Not a failure to apologise for — just put the screen back.
        return if (error == "access_denied") {
            GoogleAuthResult.Cancelled
        } else {
            GoogleAuthResult.Failed("Google could not complete that sign-in.")
        }
    }

    if (code.isNullOrBlank()) {
        return GoogleAuthResult.Failed("Google sent no sign-in back.")
    }

    return GoogleAuthResult.Code(code = code, codeVerifier = codeVerifier)
}

/** What came back from the browser. Not yet a session — see SignInViewModel. */
sealed interface GoogleAuthResult {
    /** An authorization code and the verifier it is bound to. */
    data class Code(val code: String, val codeVerifier: String) : GoogleAuthResult

    /** The merchant backed out. Say nothing; just stop the spinner. */
    data object Cancelled : GoogleAuthResult

    data class Failed(val message: String) : GoogleAuthResult
}
