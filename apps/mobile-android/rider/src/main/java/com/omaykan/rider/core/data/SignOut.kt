package com.omaykan.rider.core.data

import com.omaykan.rider.core.location.LocationShareController
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Signing out, in the one order that is safe.
 *
 * Its own class rather than a method on [SessionRepository], because the
 * sequence spans three things that do not know about each other — and because
 * there are now two screens that can start it. It used to live in
 * `WorkViewModel` alone; a second copy of this ordering is exactly the kind of
 * duplication that gets one of the two steps dropped later.
 *
 * The order is load-bearing:
 *
 * 1. **Sharing stops first.** A foreground service left running would keep
 *    posting a signed-out rider's coordinates until its next 401 — and on a
 *    shared phone would put the *next* rider's position onto the previous
 *    one's deliveries.
 * 2. **Then the feed.** Signing out clears the token, and a poll already in
 *    flight would 401 against a screen that is gone, leaving the previous
 *    rider's customer addresses in memory for whoever signs in next.
 * 3. **Then the token itself**, which is what turns the session flow SignedOut
 *    and swaps the screen.
 */
@Singleton
class SignOut @Inject constructor(
    private val sharing: LocationShareController,
    private val feed: WorkFeed,
    private val sessions: SessionRepository,
) {
    suspend operator fun invoke() {
        sharing.stop()
        feed.reset()
        sessions.signOut()
    }
}
