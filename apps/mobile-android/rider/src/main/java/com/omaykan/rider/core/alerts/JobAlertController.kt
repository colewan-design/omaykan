package com.omaykan.rider.core.alerts

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The job-alerts switch, and the truth behind it.
 *
 * The same arrangement LocationShareController describes, for the same reason:
 * [watching] is reported by the service in its own onCreate and onDestroy, not
 * by whoever flipped the switch. A foreground service can end without anybody
 * asking — the Stop action on its notification, the system reclaiming memory —
 * and a switch wired to the *request* would sit there promising alerts that
 * stopped arriving an hour ago.
 *
 * That failure is quieter than the location one and worse in its own way:
 * nothing appears on screen when alerts stop, so a rider would simply conclude
 * there was no work.
 */
@Singleton
class JobAlertController @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _watching = MutableStateFlow(false)

    /** True only while the watcher service is actually alive. */
    val watching: StateFlow<Boolean> = _watching.asStateFlow()

    fun start() = JobWatcherService.start(context)

    fun stop() = JobWatcherService.stop(context)

    internal fun onServiceStarted() {
        _watching.value = true
    }

    internal fun onServiceStopped() {
        _watching.value = false
    }
}
