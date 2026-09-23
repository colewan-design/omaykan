package com.omaykan.storefront.core.push

import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Where FCM delivers a message while the app is in front.
 *
 * In the background Android draws the notification itself and never calls
 * this, so all it does is post the same notification the system would have.
 */
@AndroidEntryPoint
class OrderMessagingService : FirebaseMessagingService() {

    @Inject
    lateinit var registrar: PushRegistrar

    override fun onMessageReceived(message: RemoteMessage) {
        val orderId = message.data[OrderNotifications.EXTRA_ORDER_ID] ?: return
        val notification = message.notification ?: return

        OrderNotifications.show(
            context = this,
            orderId = orderId,
            title = notification.title.orEmpty(),
            body = notification.body.orEmpty(),
        )
    }

    /**
     * Rare — a reinstall, or cleared app data. An open tracking screen
     * re-registers its order with the new token on its next poll; see
     * OrderViewModel.
     */
    override fun onNewToken(token: String) {
        registrar.onTokenChanged()
    }
}
