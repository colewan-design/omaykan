package com.omaykan.storefront.core.data

import com.omaykan.storefront.core.model.AppUpdate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The one rule in the update check, kept honest.
 *
 * The app is sideloaded, so this decides whether a shopper ever hears about a
 * new build — and, just as importantly, whether they are asked twice.
 */
class UpdateRepositoryTest {

    private fun release(code: Long, apkUrl: String? = "https://omaykan.com/apk/omaykan.apk") =
        AppUpdate(versionCode = code, versionName = "$code.0.0", apkUrl = apkUrl, notes = null)

    @Test
    fun `a newer build is offered`() {
        val offer = chooseUpdate(release(3), installedVersionCode = 2, dismissedVersionCode = 0)

        assertEquals(3L, offer?.versionCode)
    }

    @Test
    fun `nothing published is silence, not an error`() {
        assertNull(chooseUpdate(null, installedVersionCode = 2, dismissedVersionCode = 0))
    }

    /** A phone running exactly what is published is current. */
    @Test
    fun `the same build is not an update`() {
        assertNull(chooseUpdate(release(2), installedVersionCode = 2, dismissedVersionCode = 0))
    }

    /** A sideloaded downgrade must not be offered as an upgrade. */
    @Test
    fun `an older published build is not offered`() {
        assertNull(chooseUpdate(release(1), installedVersionCode = 2, dismissedVersionCode = 0))
    }

    @Test
    fun `a build with no apk yet is not offered`() {
        assertNull(chooseUpdate(release(3, apkUrl = null), installedVersionCode = 2, dismissedVersionCode = 0))
        assertNull(chooseUpdate(release(3, apkUrl = "  "), installedVersionCode = 2, dismissedVersionCode = 0))
    }

    @Test
    fun `later silences the build that was waved off`() {
        assertNull(chooseUpdate(release(3), installedVersionCode = 2, dismissedVersionCode = 3))
    }

    /** …and only that build. The next release still gets asked. */
    @Test
    fun `later does not silence the next release`() {
        val offer = chooseUpdate(release(4), installedVersionCode = 2, dismissedVersionCode = 3)

        assertEquals(4L, offer?.versionCode)
    }

    /**
     * Installing after a dismissal must not leave the prompt owing an answer:
     * the version check alone already covers it, with no need to clear the
     * dismissal on install.
     */
    @Test
    fun `installing a dismissed build ends the prompt`() {
        assertNull(chooseUpdate(release(3), installedVersionCode = 3, dismissedVersionCode = 3))
    }
}
