package com.omaykan.rider.core.location

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The sharing switch, and the truth behind it.
 *
 * Modelled on :seller's OrderWatchController and for the same reason: [sharing]
 * is reported by the service itself, in its onCreate and onDestroy, rather than
 * by whoever flipped the switch. A foreground service can end without anybody
 * asking — the Stop action on its own notification, the system reclaiming
 * memory, the OS revoking location mid-shift — and a switch wired to the
 * *request* would sit there saying "sharing" while a customer watched a marker
 * that had not moved in twenty minutes.
 *
 * For a location feed that distinction matters more than it did for the order
 * watcher, because the person misled by a wrong switch is not the person
 * holding the phone.
 */
@Singleton
class LocationShareController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _sharing = MutableStateFlow(false)

    /** True only while the service is actually alive. */
    val sharing: StateFlow<Boolean> = _sharing.asStateFlow()

    fun start() = LocationShareService.start(context)

    fun stop() = LocationShareService.stop(context)

    internal fun onServiceStarted() {
        _sharing.value = true
    }

    internal fun onServiceStopped() {
        _sharing.value = false
    }
}
