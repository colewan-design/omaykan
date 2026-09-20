package com.omaykan.storefront.core.push

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.omaykan.storefront.core.data.AppScope
import com.omaykan.storefront.core.network.ApiCaller
import com.omaykan.storefront.core.network.OmaykanApi
import com.omaykan.storefront.core.network.dto.PushTokenRequestDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume

/**
 * Tells the server which phone to notify about an order.
 *
 * Per order rather than per account, because a guest checkout has no account
 * and is waiting on a rider all the same.
 *
 * Everything here is best effort and silent. A build with no
 * google-services.json has no FirebaseApp at all; a phone without Play
 * services has no token; the network may be gone. None of those is worth a
 * word on a screen somebody opened to see where their food is — the tracking
 * screen works exactly as it did before push existed.
 */
@Singleton
class PushRegistrar @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: OmaykanApi,
    private val caller: ApiCaller,
    @AppScope private val scope: CoroutineScope,
) {
    /** Orders already registered with the current token, this process. */
    private val registered = mutableSetOf<String>()

    /**
     * Fire and forget, on the app scope: the request should finish even if the
     * shopper leaves the screen that asked for it.
     */
    fun register(orderId: String) {
        if (!available()) return

        scope.launch {
            val token = token() ?: return@launch
            synchronized(registered) {
                if (!registered.add(orderId)) return@launch
            }

            runCatching { caller.call { api.registerPushToken(orderId, PushTokenRequestDto(token)) } }
                .onFailure { synchronized(registered) { registered.remove(orderId) } }
        }
    }

    /** A new token invalidates every earlier registration. */
    fun onTokenChanged() {
        synchronized(registered) { registered.clear() }
    }

    private fun available(): Boolean = FirebaseApp.getApps(context).isNotEmpty()

    private suspend fun token(): String? = suspendCancellableCoroutine { continuation ->
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            continuation.resume(if (task.isSuccessful) task.result else null)
        }
    }
}
