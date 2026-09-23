package com.omaykan.rider.baselineprofile

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
 * Records what the rider app runs on the way in, so the next install can
 * compile it ahead of time.
 *
 * ## What this covers, and what it does not
 *
 * The three onboarding pages and the sign-in form. Not the board, not a job,
 * not the map — those are behind a personal token on the rider guard, and this
 * module has no credentials.
 *
 * The onboarding is worth more here than its three screens suggest: it is a
 * HorizontalPager, and paging it compiles the scrolling and animation
 * machinery that the board and the job list then reuse. It is also the one
 * part of this app that every rider sees exactly once, on a phone that has
 * just installed — which is the worst moment to be interpreting it.
 *
 * If a throwaway rider account is ever wired into CI, signing in here is where
 * the board would come from. See baselineprofile/README.md.
 */
@RunWith(AndroidJUnit4::class)
class RiderProfile {

    @get:Rule
    val baselineProfile = BaselineProfileRule()

    /**
     * Cold start, and nothing else.
     *
     * Separate because of `includeInStartupProfile`: this one also feeds the
     * startup profile, a smaller list ART treats with more urgency and that
     * dex layout is ordered around.
     */
    @Test
    fun startup() = baselineProfile.collect(
        packageName = targetPackage,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()
    }

    /** Onboarding, paged through rather than skipped, and then the form. */
    @Test
    fun signIn() = baselineProfile.collect(packageName = targetPackage) {
        pressHome()
        startActivityAndWait()
        pageThroughOnboarding()
        awaitSignIn()

        scrollAround()

        tap("Log in")
        device.wait(Until.hasObject(By.text("Log in to ride")), 5_000)
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
        ?: "com.omaykan.rider"

/**
 * Advance the pager to the end and leave it.
 *
 * The button reads "Next" until the last page and "Get started" on it, so
 * tapping Next while it is there and Get started when it appears walks the
 * whole thing without hard-coding how many pages there are.
 *
 * Onboarding is shown once and then remembered, so the second and later
 * iterations of a profile run land straight on sign-in. Every step here is
 * written to find nothing and do nothing in that case.
 */
private fun MacrobenchmarkScope.pageThroughOnboarding() {
    if (!device.wait(Until.hasObject(By.text("Next")), 8_000)) return

    // Bounded rather than `while`, so a page that stops advancing ends the
    // run instead of hanging it. Four covers the three pages there are today
    // and one more if somebody adds it.
    repeat(4) { tap("Next") }

    tap("Get started")
}

/** Wait for the signed-out landing, and move on if it never comes. */
private fun MacrobenchmarkScope.awaitSignIn() {
    device.wait(Until.hasObject(By.text("Log in")), 10_000)
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
