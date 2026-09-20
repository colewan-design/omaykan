package com.omaykan.storefront.core.push

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.omaykan.storefront.MainActivity
import com.omaykan.storefront.R

/**
 * The "order updates" channel, and the notification posted on it.
 *
 * Two paths reach the shade. With the app in the background, Android draws the
 * server's notification itself, on the channel the server names — which is
 * why [CHANNEL_ID] has to match `PushSender` on the backend — and a tap starts
 * MainActivity with the message's data as extras. With the app in front, FCM
 * hands the message to [OrderMessagingService] instead, which calls [show], so
 * both paths end in the same place with the same extras.
 */
object OrderNotifications {

    const val CHANNEL_ID = "order_updates"

    /** The intent extra naming the order to open. Also the FCM data key. */
    const val EXTRA_ORDER_ID = "orderId"

    /** Idempotent, so it is simply called on every launch. */
    fun createChannel(context: Context) {
        NotificationManagerCompat.from(context).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Order updates",
                // High: "a rider is coming" is the whole point, and an update
                // that has to be found in a silent shade has not been sent.
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = "When a rider takes your delivery."
            },
        )
    }

    fun show(context: Context, orderId: String, title: String, body: String) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        val open = PendingIntent.getActivity(
            context,
            // Per order, so two orders' notifications do not share one intent.
            orderId.hashCode(),
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_ORDER_ID, orderId)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_order)
            .setColor(ContextCompat.getColor(context, R.color.ic_launcher_background))
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()

        // Tagged with the order, as the server's own notification is: a later
        // update for the same order replaces this one instead of stacking.
        NotificationManagerCompat.from(context).notify(orderId, 0, notification)
    }
}
