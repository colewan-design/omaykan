package com.omaykan.storefront.core.auth

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app is allowed to believe when the browser comes back.
 *
 * The redirect is a public entry point — the intent filter on
 * GoogleAuthRedirectActivity means anything on the phone can fire one — so the
 * `state` comparison is the only thing standing between a hostile app and a
 * shopper signed into somebody else's account. These are the tests for that.
 */
class GoogleAuthRedirectTest {

    private val issuedState = "state-this-app-generated"
    private val verifier = "the-pkce-verifier"

    @Test
    fun `a matching state and a code is a sign-in`() {
        val result = readRedirect(
            expectedState = issuedState,
            codeVerifier = verifier,
            returnedState = issuedState,
            error = null,
            code = "4/authorization-code",
        )

        assertEquals(GoogleAuthResult.Code("4/authorization-code", verifier), result)
    }

    @Test
    fun `a code arriving under someone else's state is refused`() {
        val result = readRedirect(
            expectedState = issuedState,
            codeVerifier = verifier,
            returnedState = "state-an-attacker-chose",
            error = null,
            code = "4/attackers-code",
        )

        assertTrue(result is GoogleAuthResult.Failed)
    }

    @Test
    fun `a code arriving when this app started nothing is refused`() {
        // The unsolicited case: no sign-in is in flight, and an intent turns up
        // anyway carrying a perfectly well-formed code.
        val result = readRedirect(
            expectedState = null,
            codeVerifier = null,
            returnedState = "any-state-at-all",
            error = null,
            code = "4/attackers-code",
        )

        assertTrue(result is GoogleAuthResult.Failed)
    }

    @Test
    fun `a redirect carrying no state at all is refused`() {
        val result = readRedirect(
            expectedState = issuedState,
            codeVerifier = verifier,
            returnedState = null,
            error = null,
            code = "4/authorization-code",
        )

        assertTrue(result is GoogleAuthResult.Failed)
    }

    @Test
    fun `backing out of Google's page is a cancellation, not an error`() {
        val result = readRedirect(
            expectedState = issuedState,
            codeVerifier = verifier,
            returnedState = issuedState,
            error = "access_denied",
            code = null,
        )

        assertEquals(GoogleAuthResult.Cancelled, result)
    }

    @Test
    fun `any other error from Google is a failure worth saying`() {
        val result = readRedirect(
            expectedState = issuedState,
            codeVerifier = verifier,
            returnedState = issuedState,
            error = "invalid_request",
            code = null,
        )

        assertTrue(result is GoogleAuthResult.Failed)
    }

    @Test
    fun `an error wins over a code that came with it`() {
        // Both fields present is malformed. Trusting the code would be trusting
        // the half of a contradictory answer that happens to let someone in.
        val result = readRedirect(
            expectedState = issuedState,
            codeVerifier = verifier,
            returnedState = issuedState,
            error = "invalid_request",
            code = "4/authorization-code",
        )

        assertTrue(result is GoogleAuthResult.Failed)
    }

    @Test
    fun `a matching state with no code is a failure`() {
        val result = readRedirect(
            expectedState = issuedState,
            codeVerifier = verifier,
            returnedState = issuedState,
            error = null,
            code = "",
        )

        assertTrue(result is GoogleAuthResult.Failed)
    }
}
