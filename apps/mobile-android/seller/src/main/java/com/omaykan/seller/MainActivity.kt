package com.omaykan.seller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.model.SessionState
import com.omaykan.seller.feature.shell.SellerShell
import com.omaykan.seller.feature.signin.SignInScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * The whole app, in one activity.
 *
 * Signed out and signed in are chosen here, by the session flow, and not by a
 * navigation graph: they are not a stack but the two answers to one question,
 * and a back stack across them would add a state where somebody has pressed
 * Back from their orders onto a sign-in screen they already passed. Inside a
 * session there *is* a stack — tabs, and a product form or a thread on top —
 * and SellerShell owns that one.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        private const val EXTRA_OPEN_ORDERS = "com.omaykan.seller.OPEN_ORDERS"

        /**
         * What a new-order notification opens: this app, on the orders tab.
         *
         * FLAG_IMMUTABLE because nothing may rewrite this intent. CLEAR_TOP and
         * SINGLE_TOP rather than a new task, so tapping the alert brings the
         * running app forward instead of stacking a second copy behind it —
         * and the extra is what tells that running copy to show the orders.
         */
        fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                .putExtra(EXTRA_OPEN_ORDERS, true)

            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    /** Goes up by one per notification tap; the shell shows the orders tab for each. */
    private var ordersSignal by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        /*
         * Light status-bar icons everywhere. Every screen in this app opens
         * with forest green or the welcome photograph under the clock, in
         * both themes. `SystemBarStyle.dark` is the instruction "the background
         * behind these is dark", not a request for dark theme.
         */
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        // A cold start from the notification. A restored activity has already
        // handled the intent that created it.
        if (savedInstanceState == null) consume(intent)

        setContent {
            SellerTheme {
                val viewModel: LaunchViewModel = hiltViewModel()
                val session by viewModel.session.collectAsStateWithLifecycle()

                Surface(color = MaterialTheme.colorScheme.background) {
                    when (val current = session) {
                        // The launch window is forest, so the frame while the
                        // keystore is read is forest too — not a flash of cream
                        // between two dark screens.
                        SessionState.Restoring -> Box(
                            Modifier
                                .fillMaxSize()
                                .background(SellerTheme.colors.canopy),
                        )
                        SessionState.SignedOut -> SignInScreen()
                        is SessionState.Paired -> SellerShell(
                            store = current.store,
                            ordersSignal = ordersSignal,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        consume(intent)
    }

    private fun consume(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_ORDERS, false) == true) ordersSignal++
    }
}
