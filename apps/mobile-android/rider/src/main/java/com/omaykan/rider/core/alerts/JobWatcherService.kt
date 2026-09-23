package com.omaykan.rider.core.alerts

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
import com.omaykan.rider.MainActivity
import com.omaykan.rider.R
import com.omaykan.rider.core.data.SessionRepository
import com.omaykan.rider.core.model.DeliveryOffer
import com.omaykan.rider.core.model.SessionState
import com.omaykan.rider.core.model.Money
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Tells a rider a job appeared while they were not looking.
 *
 * The README opens its "next, in the order it is worth doing" list with this,
 * and is precise about why: *"a rider who has to keep the app open to hear
 * about a job will keep the app open, and will still miss jobs."*
 *
 * ## Why a foreground service and not a background job
 *
 * WorkManager's floor is fifteen minutes, which is longer than most jobs stay
 * unclaimed on this board — an alert that arrives after somebody else has taken
 * the row is worse than no alert, because it teaches a rider to ignore them.
 * A foreground service can poll at the cadence the feature actually needs, and
 * the price is a permanent notification, which is the honest trade: the rider
 * can see this running and can end it from the notification itself.
 *
 * Typed `dataSync` rather than `location` — this reads a list, it touches no
 * sensor, and claiming a location type for it would put a location indicator in
 * the status bar for something that is not reading a position.
 *
 * ## What this is not
 *
 * Not push. See [JobWatcher] for what that needs on the backend and why it does
 * not exist yet. Alerts stop when this service does, and `START_NOT_STICKY`
 * means a system kill or a reboot ends them silently — the same choice
 * LocationShareService makes, and for the same reason: quietly resuming a
 * service nobody re-enabled is what makes people distrust an app. The cost here
 * is lower than it is there, because a missed alert is a missed job rather than
 * an unannounced disclosure of somebody's position.
 */
@AndroidEntryPoint
class JobWatcherService : Service() {

    companion object {
        /** The ongoing notification: this service is running. */
        private const val CHANNEL_ONGOING = "job_watch"

        /** The alerts themselves: a job appeared. */
        private const val CHANNEL_ALERTS = "job_alerts"

        private const val NOTIFICATION_ID = 4201

        /**
         * Alerts start here and climb, so two jobs arriving a minute apart are
         * two notifications rather than one replacing the other.
         */
        private const val ALERT_ID_BASE = 4300

        private const val ACTION_STOP = "com.omaykan.rider.action.STOP_JOB_WATCH"

        fun start(context: Context) {
            // startForegroundService, not startService: a background start of a
            // plain service is illegal on modern Android, and the contract that
            // comes with it — call startForeground or be killed — is met in
            // onCreate.
            context.startForegroundService(Intent(context, JobWatcherService::class.java))
        }

        fun stop(context: Context) {
            context.startService(
                Intent(context, JobWatcherService::class.java).setAction(ACTION_STOP),
            )
        }
    }

    @Inject
    lateinit var watcher: JobWatcher

    @Inject
    lateinit var controller: JobAlertController

    @Inject
    lateinit var sessions: SessionRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var nextAlertId = ALERT_ID_BASE

    /** Never bound. This is a started service. */
    override fun onBind(intent: Intent): IBinder? = null

    override fun onCreate() {
        super.onCreate()

        createChannels()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            ongoingNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )

        controller.onServiceStarted()

        scope.launch { watcher.watch(::announce) }

        /*
         * Watching outlives a screen, not a session.
         *
         * Signing out is one way this ends; the other is the token being
         * refused. Either leaves a service polling a job board against
         * credentials that no longer resolve, behind a notification claiming to
         * be watching for work on somebody's behalf.
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

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        controller.onServiceStopped()
        super.onDestroy()
    }

    /**
     * One notification per job, not one summarising many.
     *
     * A rider glancing at a lock screen is deciding whether to get the phone
     * out, and "3 new jobs" does not help them decide — what pays and where it
     * starts does. Collapsing them would also mean a second job silently
     * replacing the first while the rider was still reading it.
     */
    private fun announce(jobs: List<DeliveryOffer>) {
        val manager = NotificationManagerCompat.from(this)

        // Newest last, so the most recent sits at the top of the shade.
        jobs.takeLast(5).forEach { job ->
            // Rounded, like the board's own headline number: this is read at a
            // glance from a lock screen, which is exactly the case pesoRounded exists for.
            val fee = Money.pesoRounded(job.deliveryFeeCents)
            val from = job.pickup.storeName
            val to = job.dropoffArea

            val line = if (to.isNullOrBlank()) from else "$from → $to"

            val notification = NotificationCompat.Builder(this, CHANNEL_ALERTS)
                .setSmallIcon(R.drawable.ic_notification_rider)
                .setContentTitle(getString(R.string.alert_new_job, fee))
                .setContentText(line)
                .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setAutoCancel(true)
                .setContentIntent(openApp())
                .build()

            // POST_NOTIFICATIONS may have been refused; the switch asks for it
            // first, but a rider can revoke it from settings afterwards without
            // this service noticing. Nothing to do about it here but not crash.
            if (manager.areNotificationsEnabled()) {
                runCatching { manager.notify(nextAlertId++, notification) }
            }
        }
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE,
    )

    private fun createChannels() {
        val manager = NotificationManagerCompat.from(this)

        // Low: this one is furniture. It exists so the service is visible and
        // stoppable, and a rider does not need to be buzzed about its presence.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ONGOING,
                getString(R.string.channel_job_watch),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = getString(R.string.channel_job_watch_description)
                setShowBadge(false)
            },
        )

        // High: this is the entire point of the feature, and a job that has to
        // be found in a silent shade has not been announced.
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                getString(R.string.channel_job_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                description = getString(R.string.channel_job_alerts_description)
            },
        )
    }

    private fun ongoingNotification() = NotificationCompat.Builder(this, CHANNEL_ONGOING)
        .setSmallIcon(R.drawable.ic_notification_rider)
        .setContentTitle(getString(R.string.job_watch_title))
        .setContentText(getString(R.string.job_watch_body))
        .setOngoing(true)
        .setCategory(NotificationCompat.CATEGORY_SERVICE)
        .setContentIntent(openApp())
        .addAction(
            0,
            getString(R.string.sharing_stop),
            PendingIntent.getService(
                this,
                1,
                Intent(this, JobWatcherService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE,
            ),
        )
        .build()
}
