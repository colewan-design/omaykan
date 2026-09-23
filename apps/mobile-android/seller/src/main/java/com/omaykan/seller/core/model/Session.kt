package com.omaykan.seller.core.model

/**
 * The shop this phone is signed in to.
 *
 * Nothing here addresses a request: the API scopes every seller call to the
 * store named in the session token's own abilities. It is what the app puts in
 * the title bar, so a merchant with two branches can see at a glance which one
 * they are looking at before they mark somebody's order ready.
 */
data class PairedStore(
    val id: String,
    val name: String,
    val code: String,
    val organizationSlug: String,
)

/**
 * One shop this account can open, as the sign-in reply lists them.
 *
 * Not [PairedStore]: this is a shop on offer, before a token exists for it.
 * They carry the same fields and mean different things, and collapsing the two
 * would let a screen show a chooser row as if it were a live session.
 */
data class StaffStore(
    val id: String,
    val name: String,
    val code: String,
    val organizationSlug: String,
)

/**
 * Whether this phone is signed in, resolved once at launch.
 *
 * [Restoring] is a real state and not a detail: the token lives in
 * EncryptedSharedPreferences, which is a disk read behind an Android keystore
 * unlock. Treating "not loaded yet" as "signed out" is what makes an app show
 * its sign-in screen for one frame on every cold start.
 */
sealed interface SessionState {
    data object Restoring : SessionState

    data object SignedOut : SessionState

    /** Signed in, with a store chosen. Named for what the app shows: one shop. */
    data class Paired(val store: PairedStore) : SessionState
}
