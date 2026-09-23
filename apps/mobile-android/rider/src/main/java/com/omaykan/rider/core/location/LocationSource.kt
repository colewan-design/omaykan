package com.omaykan.rider.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One fix, as this app cares about it.
 *
 * Speed and bearing are nullable rather than zero-defaulted because a phone
 * standing still genuinely has neither, and a bearing of 0 means north — a
 * marker snapping to north every time a rider stops at a light is worse than a
 * marker that keeps the last direction it knew.
 */
data class Fix(
    val lat: Double,
    val lng: Double,
    val headingDeg: Float?,
    val speedKph: Float?,
    val accuracyM: Float?,
)

/**
 * Where the phone is, behind an interface.
 *
 * [PositionReporter] holds one of these rather than [LocationSource] itself, so
 * the throttling rules that keep a live map from flattening a battery can be
 * tested against a scripted sequence of fixes instead of against a sensor. The
 * seam is worth its one file: those rules are the whole reason this feature is
 * shippable, and they are invisible to anyone reading the code.
 */
interface FixSource {
    /** Whether the rider has granted location at all. */
    fun permitted(): Boolean

    /** Whether location is switched on at the OS level. */
    fun enabled(): Boolean

    /** Live fixes, until the collector goes away. Empty when either check above fails. */
    fun fixes(minIntervalMs: Long = 5_000L): Flow<Fix>

    /**
     * The last fix the system already had, without waiting for the sensor.
     *
     * Deliberately *not* the first emission of [fixes]. It used to be, and that
     * was a bug found the first time this ran on a device: the reporter takes
     * one fix per tick, so a cached value emitted ahead of the live stream won
     * every race and the app reported the same stale coordinate forever. The
     * two are different questions — "where was this phone last seen" and "where
     * is it now" — so they are two calls, and the reporter prefers the second.
     */
    fun lastKnown(): Fix?
}

/**
 * Where the phone is, as a flow.
 *
 * ## Why LocationManager and not the fused provider
 *
 * `play-services-location` is the usual answer and it is a better provider: it
 * fuses GPS with wifi and the accelerometer and costs less battery for the same
 * accuracy. It is not used here for the reason this whole app avoids Play
 * Services — a rider's phone is a cheap phone, often a Huawei or a grey-market
 * import with no Google services on it at all, and an app that will not report
 * a position on those is an app that does not work for a chunk of the people it
 * is for. The platform `LocationManager` is on every Android device ever made.
 *
 * Both providers are requested and whichever answers is used. GPS is the
 * accurate one and the one that fails indoors and under a jeepney roof;
 * NETWORK is a cell-and-wifi guess that is often 100m out but arrives in a
 * basement. For "where is my order roughly", the second beats nothing.
 *
 * ## What it does not do
 *
 * No background location permission is requested anywhere in this app, and this
 * flow is only ever collected from a foreground service with a visible
 * notification. A rider is told, by their own phone, exactly when this is
 * running — and it only runs while they have switched it on.
 */
@Singleton
class LocationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : FixSource {
    /** Whether the rider has granted either location permission. */
    override fun permitted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** Whether location is switched on at the OS level at all. */
    override fun enabled(): Boolean {
        val manager = context.getSystemService<LocationManager>() ?: return false
        return manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /**
     * Fixes, until the collector goes away.
     *
     * Emits nothing at all — rather than throwing — when there is no permission
     * or no provider. The caller's job is to keep working with no position, not
     * to handle an exception from a sensor.
     *
     * [minIntervalMs] is a request, not a promise: the system decides, and on a
     * phone in a pocket it will be slower. Asking for a distance of zero metres
     * is deliberate, because a rider stopped at a shop still needs their marker
     * to be *there* rather than at the last place they moved.
     */
    @Suppress("MissingPermission") // Guarded by permitted() on the line above the registration.
    override fun fixes(minIntervalMs: Long): Flow<Fix> = callbackFlow {
        val manager = context.getSystemService<LocationManager>()

        if (manager == null || !permitted()) {
            close()
            return@callbackFlow
        }

        val listener = LocationListener { location -> trySend(location.toFix()) }

        val providers = listOfNotNull(
            LocationManager.GPS_PROVIDER.takeIf { manager.isProviderEnabled(it) },
            LocationManager.NETWORK_PROVIDER.takeIf { manager.isProviderEnabled(it) },
        )

        if (providers.isEmpty()) {
            close()
            return@callbackFlow
        }

        for (provider in providers) {
            try {
                manager.requestLocationUpdates(
                    provider,
                    minIntervalMs,
                    0f,
                    listener,
                    // The service's own thread has no Looper of its own, and a
                    // listener registered without one throws.
                    Looper.getMainLooper(),
                )
            } catch (_: SecurityException) {
                // Revoked between the check above and here — a rider changing
                // the setting while the service starts. Nothing to report.
            }
        }

        awaitClose { manager.removeUpdates(listener) }
    }

    /**
     * Whatever the system already had, from whichever provider has one.
     *
     * Used only as a fallback when the sensor has not answered in time — GPS
     * can take thirty seconds from cold, and thirty seconds of an empty map is
     * thirty seconds of a customer deciding the app is broken. It is never
     * preferred over a live fix; see PositionReporter.
     */
    @Suppress("MissingPermission") // Guarded by permitted() below.
    override fun lastKnown(): Fix? {
        val manager = context.getSystemService<LocationManager>()
        if (manager == null || !permitted()) return null

        return listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .firstNotNullOfOrNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            ?.toFix()
    }

    private fun Location.toFix() = Fix(
        lat = latitude,
        lng = longitude,
        headingDeg = if (hasBearing()) bearing else null,
        // m/s on the wire, km/h to a human. The API takes km/h because that is
        // what every screen showing it will want.
        speedKph = if (hasSpeed()) speed * 3.6f else null,
        accuracyM = if (hasAccuracy()) accuracy else null,
    )

    companion object {
        /**
         * Fixes worse than this are not positions, they are areas.
         *
         * A 500m accuracy reading is a cell tower, and drawing it as a rider
         * puts a marker in someone else's barangay. Better to send nothing and
         * let the map say "last seen" than to send a confident wrong answer.
         */
        const val MAX_USABLE_ACCURACY_M = 200f

        /** Whether the OS build needs the API 34+ foreground service type. */
        val NEEDS_TYPED_FOREGROUND_SERVICE: Boolean
            get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
    }
}
