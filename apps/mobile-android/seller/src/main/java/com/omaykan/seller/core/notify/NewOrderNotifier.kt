package com.omaykan.seller.core.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.omaykan.seller.MainActivity
import com.omaykan.seller.R
import com.omaykan.seller.core.model.Money
import com.omaykan.seller.core.model.SellerOrder
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The sound a shop hears when an order arrives.
 *
 * ## What this is, and what it is not
 *
 * This fires while the app is running and the orders screen is polling. It is
 * not a push notification: nothing wakes this app when it is closed, because
 * that needs FCM, a device-token registry on the backend, and a Google project
 * — a chunk of work with its own decisions, noted at the end of seller/README.md.
 *
 * So the promise this app makes today is "leave it open on the counter", and
 * the notification is what lets a merchant do that with the screen off or
 * another app in front. That is genuinely most of the value: the phone is
 * propped up next to the till, and the point is that somebody hears it.
 */
@Singleton
class NewOrderNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val CHANNEL_ID = "new_orders"

        /**
         * One notification per order, keyed on its id.
         *
         * Not a single collapsing one: two orders arriving a minute apart are
         * two things to make, and the second must not quietly replace the
         * first. `hashCode` can in principle collide, and the cost if it ever
         * did is one notification replacing another — not a lost order, which
         * is on the screen either way.
         */
        fun idFor(order: SellerOrder): Int = order.id.hashCode()
    }

    private val manager = NotificationManagerCompat.from(context)

    /**
     * Created eagerly at construction rather than before the first post.
     *
     * A channel is what the merchant edits in Android's own settings — to make
     * it louder, or to let it through Do Not Disturb, which for a shop is a
     * reasonable thing to want. It should be there to be found before the first
     * order of the day, not after it.
     */
    init {
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_new_orders),
            // HIGH, so it makes a sound and shows as a heads-up banner. This is
            // the app's entire reason to be on the counter; a silent entry in
            // the shade would be a notification nobody ever sees in time.
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.channel_new_orders_description)
            enableVibration(true)
            setSound(
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .build(),
            )
        }

        manager.createNotificationChannel(channel)
    }

    /**
     * Whether a posted notification would actually appear.
     *
     * Two separate gates, and both can be shut: the runtime permission (API 33+)
     * and the user's own switch for this app in Android settings. The orders
     * screen asks about the first; nothing overrides the second, and nothing
     * should try.
     */
    fun enabled(): Boolean = permitted() && manager.areNotificationsEnabled()

    private fun permitted(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED

    fun notifyArrivals(orders: List<SellerOrder>) {
        if (orders.isEmpty() || !enabled()) return

        orders.forEach { order -> post(order) }
    }

    // Lint cannot see that `enabled()` gated this, because the check is a
    // method call away. The permission genuinely is verified — see permitted()
    // — and the call is wrapped besides.
    @SuppressLint("MissingPermission")
    private fun post(order: SellerOrder) {
        val ticket = order.ticketNumber?.let { "#$it" } ?: "New order"
        val kind = if (order.isDelivery) "Delivery" else "Pickup"

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_order)
            .setContentTitle("$ticket · ${Money.peso(order.totalCents)}")
            // The three facts worth waking someone for: how it leaves, how much
            // there is to make, and who it is for. Not the basket — that is a
            // tap away on a screen built to show it.
            .setContentText("$kind · ${order.itemCount} item${if (order.itemCount == 1) "" else "s"} · ${order.customerName}")
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setContentIntent(MainActivity.pendingIntent(context))
            .build()

        // Permission can be revoked between the check above and this call, and
        // NotificationManagerCompat throws rather than returning false when it
        // is. A missed sound must never take the orders screen down with it.
        runCatching { manager.notify(idFor(order), notification) }
    }
}
