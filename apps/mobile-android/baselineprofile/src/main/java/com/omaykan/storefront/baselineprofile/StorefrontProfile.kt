package com.omaykan.storefront.baselineprofile

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
 * Records what the storefront actually runs, so the next install can compile
 * it ahead of time.
 *
 * This is not a test and it asserts nothing. It drives a real release build on
 * a real emulator while ART writes down every class and method that gets
 * touched; the plugin collects that list, keeps the part that is stable across
 * repeated runs, and writes it into `:app` as `baseline-prof.txt`. Adding a
 * screen here does not make the app faster on that screen by magic — it makes
 * the first visit to it stop paying for interpretation and JIT.
 *
 * ## Why it survives a shop that will not load
 *
 * The release build talks to the live API, so this runs against whatever
 * omaykan.com is serving at the time — and on CI, possibly against nothing at
 * all. Every step below waits for what it wants and moves on without it, so a
 * dead network yields a smaller profile rather than a red build. The parts
 * worth the most are the parts that need no network anyway: process start,
 * Hilt's graph, the theme, the nav host, and Compose itself.
 */
@RunWith(AndroidJUnit4::class)
class StorefrontProfile {

    @get:Rule
    val baselineProfile = BaselineProfileRule()

    /**
     * Cold start, and nothing else.
     *
     * Kept separate because of `includeInStartupProfile`: this one also feeds
     * the *startup* profile, a smaller list that ART treats with more urgency
     * and that dex layout is ordered around. Mixing a scroll into it would
     * dilute that with code no launch ever reaches.
     */
    @Test
    fun startup() = baselineProfile.collect(
        packageName = targetPackage,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
        dismissWelcome()
        awaitMarket()
    }

    /** The first minute of use: the market, a scroll, and the other tabs. */
    @Test
    fun browse() = baselineProfile.collect(packageName = targetPackage) {
        pressHome()
        startActivityAndWait()
        dismissWelcome()
        awaitMarket()

        scrollAround()

        // The five tabs of the shell. Each one is a screen's worth of Compose
        // that is otherwise cold the first time a shopper taps it.
        listOf("Shop", "Stories", "Favorites", "Account", "Home").forEach { tab ->
            if (tap(tab)) scrollAround()
        }
    }
}

/**
 * The application id of the build under test, handed over by Gradle.
 *
 * Not a constant: the variant this runs against is `nonMinifiedRelease`, which
 * the plugin synthesises, and hard-coding the release id here would be a
 * second place that has to stay in step with `:app`'s. The fallback is that
 * same id, so the class still runs if the argument ever goes missing.
 */
private val targetPackage: String
    get() = InstrumentationRegistry.getArguments().getString("targetAppId")
        ?: "com.omaykan.storefront"

/**
 * First launch after install shows the welcome, later ones do not — and the
 * profile run installs fresh and then repeats. So this is written to be true
 * either way rather than assuming which iteration it is on.
 */
private fun MacrobenchmarkScope.dismissWelcome() {
    if (device.wait(Until.hasObject(By.text("Get Started")), 5_000)) {
        tap("Get Started")
    }
}

/** Wait for the shell's tab bar, which is the app's first real frame. */
private fun MacrobenchmarkScope.awaitMarket() {
    device.wait(Until.hasObject(By.text("Home")), 10_000)
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
