package com.omaykan.rider.core.location

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.omaykan.rider.MainActivity
import com.omaykan.rider.R
import com.omaykan.rider.core.data.SessionRepository
import com.omaykan.rider.core.model.SessionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * Reports the rider's position while the app is not on screen.
 *
 * ## Why a service, and why this one is `location`
 *
 * A rider does not watch the app while riding — they are looking at the road,
 * or at whatever navigation app they handed the address to. The moment another
 * app comes forward Android stops the activity, and with it any coroutine
 * running there. A live map that only updates while the rider stares at the job
 * board is not a live map.
 *
 * A foreground service is the sanctioned way to hold it open, and this one is
 * typed `location` rather than `dataSync` — that is not a technicality. On API
 * 34+ the type is what grants access to the sensor with the app in the
 * background, and it is what makes the system show the location indicator in
 * the status bar. The rider's phone tells them, continuously, that this app is
 * reading their position. That is the correct arrangement for a feature like
 * this and it is worth being unable to hide.
 *
 * ## Why it is opt-in and off by default
 *
 * The switch lives on the work screen and starts off. Continuous location is
 * the most invasive thing this app does — it is a record of where a person is
 * all day — and the answer to that is not a privacy policy, it is a switch the
 * rider controls, a notification they can always see, and a server that keeps
 * no history (see the `add_rider_live_position` migration).
 *
 * Turning it off erases the last fix from the server as well as stopping new
 * ones, so "off" means gone rather than merely frozen.
 */
@AndroidEntryPoint
class LocationShareService : Service() {

    companion object {
        private const val CHANNEL_ID = "sharing"
        private const val NOTIFICATION_ID = 2

        private const val ACTION_STOP = "com.omaykan.rider.action.STOP_SHARING"

        fun start(context: Context) {
            // startForegroundService, not startService: a background start of a
            // plain service is refused from API 26. The five-second contract
            // that comes with it — call startForeground or be killed — is met
            // in onCreate, before any sensor is touched.
            context.startForegroundService(Intent(context, LocationShareService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationShareService::class.java))
        }
    }

    @Inject
    lateinit var reporter: PositionReporter

    @Inject
    lateinit var controller: LocationShareController

    @Inject
    lateinit var sessions: SessionRepository

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
            if (LocationSource.NEEDS_TYPED_FOREGROUND_SERVICE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                0
            },
        )

        controller.onServiceStarted()

        // The reporting loop. It suspends for as long as sharing lasts, so
        // cancelling this scope in onDestroy is the entire off switch.
        scope.launch { reporter.report() }

        /*
         * Sharing outlives a screen, not a session.
         *
         * Signing out is one way this ends; the other is the token being
         * refused, which the interceptor handles by throwing it away. Either
         * leaves a service posting a real person's coordinates against
         * credentials that no longer resolve, behind a notification claiming to
         * be helping with a delivery that is not theirs any more.
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
         * NOT_STICKY, deliberately, and more firmly than :seller's watcher.
         *
         * A service the system killed should stay dead. Silently resuming a
         * location feed that the rider did not re-enable — after a reboot, or
         * an out-of-memory kill — is exactly the behaviour that makes people
         * distrust an app like this. If they want it on, it is one tap away on
         * a screen they are already looking at.
         */
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        /*
         * Erase the last fix on the way out.
         *
         * runBlocking in onDestroy is a thing to be careful with, and it is the
         * right call here: the alternative is launching into a scope that is
         * about to be cancelled, which would leave the rider's last position
         * sitting on the server until it aged out. "Off" has to mean gone.
         *
         * NonCancellable and a plain IO hop, so it survives the cancel below;
         * runCatching inside stopSharing means a dead network costs nothing.
         */
        runBlocking {
            withContext(NonCancellable + Dispatchers.IO) { reporter.stopSharing() }
        }

        scope.cancel()
        controller.onServiceStopped()
        super.onDestroy()
    }

    private fun createChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.channel_sharing),
            // LOW: silent, no heads-up banner. This is a status line, not an
            // event — and it is the honest disclosure that the app is reading
            // the phone's location, which should be visible without being a
            // nuisance every ten seconds.
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.channel_sharing_description)
            setShowBadge(false)
        }

        NotificationManagerCompat.from(this).createNotificationChannel(channel)
    }

    private fun notification() = NotificationCompat.Builder(this, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_rider)
        .setContentTitle(getString(R.string.sharing_title))
        .setContentText(getString(R.string.sharing_body))
        .setContentIntent(MainActivity.pendingIntent(this))
        .setOngoing(true)
        .setSilent(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        // A way out that does not require finding the app first. For a location
        // feed this is not a nicety: a rider who wants to stop being tracked
        // must be able to do it from wherever they are, in one tap.
        .addAction(
            0,
            getString(R.string.sharing_stop),
            PendingIntent.getService(
                this,
                0,
                Intent(this, LocationShareService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()
}
