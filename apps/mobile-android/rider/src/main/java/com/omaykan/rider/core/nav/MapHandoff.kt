package com.omaykan.rider.core.nav

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

/**
 * Handing an address to the phone's own map and dialler.
 *
 * ## Why there is no map in this app
 *
 * An embedded map would need the Maps SDK, an API key, a billing account and a
 * Google-flavoured phone — and it would still be worse than what a rider
 * already has. They navigate for a living, on an app they have chosen, with
 * offline tiles for a city they know better than we do. The useful thing this
 * app can do is give that app the destination in one tap, which is all of what
 * follows.
 *
 * `geo:` is the platform's own scheme, so it reaches Google Maps, Waze, Organic
 * Maps or whatever else is installed, and the phone's chooser decides. The
 * `<queries>` block in AndroidManifest.xml is what lets this app see any of
 * them at all on API 30+.
 */
object MapHandoff {

    /**
     * Open a place on a map.
     *
     * Coordinates when the row has them, and the address as the label so the
     * map shows a name rather than a pin over an empty grid. When it does not —
     * `stores.lat` is nullable and plenty of shops have never set one — this
     * falls back to a query on the address text, which is what a rider would
     * type anyway.
     *
     * Returns false when the phone has no map app and nothing was opened, so
     * the caller can say so rather than leaving a button that does nothing. In
     * practice that is a bare AOSP build or a very locked-down phone; it is
     * handled because a rider finding out on a doorstep is not the moment.
     */
    fun openMap(
        context: Context,
        lat: Double?,
        lng: Double?,
        label: String?,
    ): Boolean {
        val uri = when {
            lat != null && lng != null -> {
                // The `q=` parameter alongside the coordinate is what puts a
                // named pin there. Without it most map apps drop the rider at
                // the point with no indication of what they are looking for.
                val query = label?.let { "($it)" }.orEmpty()
                Uri.parse("geo:$lat,$lng?q=$lat,$lng${Uri.encode(query)}")
            }
            !label.isNullOrBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(label)}")
            else -> return false
        }

        return context.start(Intent(Intent.ACTION_VIEW, uri))
    }

    /**
     * Open the dialler with a number in it — ACTION_DIAL, never ACTION_CALL.
     *
     * Dial shows the number and waits for the rider to press the button.
     * ACTION_CALL would place the call immediately and needs the CALL_PHONE
     * permission, and neither is right: a mis-tap while riding must not ring a
     * customer, and a delivery app that asks for permission to make calls is a
     * delivery app people uninstall.
     */
    fun dial(context: Context, phone: String): Boolean =
        context.start(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(phone)}")))

    private fun Context.start(intent: Intent): Boolean = try {
        // NEW_TASK because this may be started from a bottom sheet's callback,
        // where the context can be an application one rather than the activity.
        startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        true
    } catch (e: ActivityNotFoundException) {
        false
    }
}
