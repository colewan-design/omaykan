package com.omaykan.seller.core.notify

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The switch, and the truth behind it.
 *
 * [watching] is reported by the service itself — set in its onCreate and
 * cleared in its onDestroy — rather than by whoever flipped the switch. That
 * distinction is the reason this class exists: a foreground service can end
 * without anybody asking, from the Stop button on its own notification, from
 * Android 15's six-hour cap on `dataSync`, or from the system reclaiming
 * memory. A switch wired to the request rather than the outcome would sit there
 * saying "watching" while nothing was.
 */
@Singleton
class OrderWatchController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _watching = MutableStateFlow(false)

    val watching: StateFlow<Boolean> = _watching.asStateFlow()

    fun start() = OrderWatchService.start(context)

    fun stop() = OrderWatchService.stop(context)

    internal fun onServiceStarted() {
        _watching.value = true
    }

    internal fun onServiceStopped() {
        _watching.value = false
    }
}
