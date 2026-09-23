package com.omaykan.seller.baselineprofile

import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.StaleObjectException
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records what the seller app runs on the way in, so the next install can
 * compile it ahead of time.
 *
 * ## What this covers, and what it does not
 *
 * Everything up to the sign-in form, and nothing past it. The orders list, the
 * decisions a shop owner makes on it and the sheets those open are all behind
 * a paired device token, and this module has no credentials — it would need a
 * test shop on the live API and a secret to reach it with.
 *
 * That is a smaller profile than the storefront's, not a pointless one. What
 * it does cover is the part every launch pays for and no shop owner can skip:
 * process start, the Hilt graph, the theme, session restore, and Compose
 * itself coming up cold. The order screens stay uncompiled on first use.
 *
 * If a throwaway shop account is ever wired into CI, signing in here is where
 * the rest would come from. See baselineprofile/README.md.
 */
@RunWith(AndroidJUnit4::class)
class SellerProfile {

    @get:Rule
    val baselineProfile = BaselineProfileRule()

    /**
     * Cold start, and nothing else.
     *
     * Separate because of `includeInStartupProfile`: this one also feeds the
     * startup profile, a smaller list ART treats with more urgency and that
     * dex layout is ordered around. A form walk in it would dilute that.
     */
    @Test
    fun startup() = baselineProfile.collect(
        packageName = targetPackage,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        awaitSignIn()
    }

    /** The way in: the landing, then the form on it. */
    @Test
    fun signIn() = baselineProfile.collect(packageName = targetPackage) {
        pressHome()
        startActivityAndWait()
        awaitSignIn()

        scrollAround()

        // The form is a screen's worth of Compose — text fields above all,
        // which are among the most expensive things the toolkit brings up.
        tap("Log In")
        device.wait(Until.hasObject(By.text("Log in to your shop")), 5_000)
        device.waitForIdle()
        scrollAround()

        device.pressBack()
        device.waitForIdle()
    }
}

/**
 * The application id of the build under test, handed over by Gradle. The
 * fallback is the release id, so this still runs if the argument goes missing.
 */
private val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: "com.omaykan.seller"

/**
 * Wait for the signed-out landing.
 *
 * Session restore is asynchronous, so the first frame is a spinner rather than
 * this. On a device where a previous iteration left a paired session, the
 * landing never arrives and the wait simply expires — the steps after it find
 * nothing and do nothing, which is the intended behaviour rather than a
 * failure.
 */
private fun MacrobenchmarkScope.awaitSignIn() {
    device.wait(Until.hasObject(By.text("Log In")), 10_000)
    device.waitForIdle()
}

/**
 * Tap the thing that carries [label], rather than the label itself.
 *
 * Compose puts the text in its own node inside the clickable one, so the
 * obvious `By.text(...)` finds a TextView that reports `clickable: false` and
 * UiAutomator logs "Clicking on non-clickable object" before poking at its
 * centre and hoping the parent hears it. Asking for the clickable ancestor
 * makes the tap land on the control. Falls back to the bare text, since a
 * screen that has changed shape is a smaller profile and not a failure.
 */
private fun MacrobenchmarkScope.tap(label: String): Boolean {
    val target = device.findObject(By.clickable(true).hasDescendant(By.text(label)))
        ?: device.findObject(By.text(label))
        ?: return false

    target.click()
    device.waitForIdle()
    return true
}

/**
 * Fling the tallest scroller on screen and come back.
 *
 * The tallest one, because a screen that nests rows inside a column would
 * otherwise give up whichever came first in the tree.
 *
 * Re-found before every fling rather than held across them. A UiObject2 is a
 * handle on an accessibility node, and Compose replaces those as it
 * recomposes, so a handle kept across two flings went stale and threw
 * StaleObjectException — which failed the run rather than the step. A stale
 * handle means the screen has moved on under us: a reason to stop scrolling,
 * not a reason to fail.
 */
private fun MacrobenchmarkScope.scrollAround(flings: Int = 3) {
    repeat(flings) { index ->
        try {
            val list = device.findObjects(By.scrollable(true))
                .maxByOrNull { it.visibleBounds.height() } ?: return

            // Keeps the fling off the edges, where the system back gesture
            // would eat it.
            list.setGestureMargin(device.displayWidth / 5)
            list.fling(if (index == flings - 1) Direction.UP else Direction.DOWN)
        } catch (stale: StaleObjectException) {
            return
        }

        device.waitForIdle()
    }
}
