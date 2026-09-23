package com.omaykan.seller.core.notify

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.omaykan.seller.MainActivity
import com.omaykan.seller.R
import com.omaykan.seller.core.data.OrderFeed
import com.omaykan.seller.core.data.SessionRepository
import com.omaykan.seller.core.model.SessionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Keeps the order feed running while the app is not on screen.
 *
 * ## Why this exists at all
 *
 * A new-order alert that only fires while the merchant is already looking at
 * the list is not worth having. Android stops an activity the moment another
 * app comes forward, which takes the polling loop down with it — so without
 * something holding the feed open, the notification would announce orders the
 * merchant could already see and stay silent for every one they could not.
 *
 * A foreground service is the sanctioned way to hold it open, and its standing
 * notification is honest rather than a nuisance: it is the phone telling a shop
 * that it is listening on their behalf, with a Stop on it.
 *
 * ## Why it is opt-in
 *
 * It is started by a switch on the orders screen, not on launch. Polling all
 * day costs battery, and a merchant who only wants to glance at the app should
 * not pay for a watch they did not ask for. The switch also makes the promise
 * legible: watching *on*, and the phone can sit face-down on the counter;
 * watching *off*, and it is an app you open.
 *
 * On Android 15 and later a `dataSync` foreground service is capped at roughly
 * six hours in any 24 — long enough for a shift, and the system stops it rather
 * than the app noticing. [OrderWatchController.watching] follows the service's
 * own lifecycle, so the switch turns itself off when that happens instead of
 * claiming a watch that has ended.
 */
@AndroidEntryPoint
class OrderWatchService : Service() {

    companion object {
        private const val CHANNEL_ID = "watching"
        private const val NOTIFICATION_ID = 1

        private const val ACTION_STOP = "com.omaykan.seller.action.STOP_WATCHING"

        fun start(context: Context) {
            // startForegroundService, not startService: from API 26 a
            // background start of a plain service is refused outright. The
            // five-second contract that comes with it — call startForeground
            // or be killed — is met in onCreate below, before any work begins.
            context.startForegroundService(Intent(context, OrderWatchService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, OrderWatchService::class.java))
        }
    }

    @Inject
    lateinit var feed: OrderFeed

    @Inject
    lateinit var controller: OrderWatchController

    @Inject
    lateinit var sessions: SessionRepository

    /**
     * The service's own scope, cancelled in onDestroy.
     *
     * A plain Service and a hand-rolled scope rather than androidx's
     * LifecycleService, which would be one more dependency for the single
     * thing it provides — and that thing is four lines here.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    /** Never bound. This is a started service. */
    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        createChannel()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        controller.onServiceStarted()

        /*
         * Collecting is the whole job.
         *
         * The feed polls while anything is subscribed and stops when nothing
         * is, so simply holding a subscription open is what keeps a shop's
         * orders arriving — and the alerts fire from inside the feed, once,
         * whether or not the orders screen is also watching. There is nothing
         * to do with the values here, and that is correct.
         */
        scope.launch { feed.state.collect { } }

        /*
         * A watch outlives a screen, not a session.
         *
         * Signing out is one way this ends; the other is the device token being
         * refused, which the interceptor handles by throwing it away. Either
         * leaves nothing to poll for, and a service that kept going would sit
         * there collecting 401s behind a notification claiming to watch a shop
         * it can no longer read.
         */
        scope.launch {
            sessions.session.collect { session ->
                if (session is SessionState.SignedOut) stopSelf()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }

        /*
         * NOT_STICKY, deliberately.
         *
         * A service the system killed under memory pressure should stay
         * stopped. Restarting itself with no activity and no explicit request
         * would put a shop's phone back to polling behind their back, and the
         * switch — which follows this service's lifecycle — would have said it
         * was off. If the merchant wants it watching, the app is one tap away.
         */
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        controller.onServiceStopped()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_watching),
            // LOW: silent and no heads-up banner. This one is a status line,
            // not an event. The orders channel is the one allowed to make a
            // noise, and keeping them separate lets a merchant hide this
            // standing notice without muting the thing it is watching for.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_watching_description)
            setShowBadge(false)
        }

        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_order)
        .setContentTitle(getString(R.string.watching_title))
        .setContentText(getString(R.string.watching_body))
        .setContentIntent(MainActivity.pendingIntent(this))
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        // A way out that does not require finding the app first. A standing
        // notification without one is the kind a merchant turns off at the
        // system level, taking the order alerts with it.
        .addAction(
            0,
            getString(R.string.watching_stop),
            PendingIntent.getService(
                this,
                0,
                Intent(this, OrderWatchService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()
}
