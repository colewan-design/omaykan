package com.omaykan.seller

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.seller.core.designsystem.SellerTheme
import com.omaykan.seller.core.model.SessionState
import com.omaykan.seller.feature.orders.OrdersScreen
import com.omaykan.seller.feature.signin.SignInScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * The whole app, in one activity and two screens.
 *
 * There is no NavHost, and that is a decision rather than an omission. The two
 * screens are not a stack — they are the two sides of one question, "is this
 * phone paired?" — and the session flow already answers it. A nav graph here
 * would add a back stack whose only reachable states are "signed in" and
 * "signed in, having pressed back onto the sign-in screen", which is a bug
 * waiting to be filed. Everything the orders screen needs on top of itself is a
 * bottom sheet, which is where a decision about one order belongs anyway.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /**
         * What a new-order notification opens.
         *
         * FLAG_IMMUTABLE because nothing may rewrite this intent — it is
         * handed to the system, and on API 31+ a mutable one without a good
         * reason is refused outright. CLEAR_TOP rather than a new task, so
         * tapping the alert brings the running app forward on the orders
         * screen instead of stacking a second copy behind it.
         */
        fun pendingIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

            return PendingIntent.getActivity(
                context,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        /*
         * Light status-bar icons, on both screens, in both themes.
         *
         * The default here is "match the theme", which picks dark icons under
         * a light theme — and the top of every screen in this app is now the
         * dark green header, so the clock and the battery would be drawn
         * near-black on near-black. `SystemBarStyle.dark` is the instruction
         * "the background behind these is dark", not a request for dark theme.
         */
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)

        setContent {
            SellerTheme {
                val viewModel: LaunchViewModel = hiltViewModel()
                val session by viewModel.session.collectAsStateWithLifecycle()

                Surface(color = MaterialTheme.colorScheme.background) {
                    when (val current = session) {
                        // The launch theme has already painted this exact
                        // ground, so a phone that reads its keystore slowly
                        // shows a still window rather than a flash of the
                        // sign-in screen it is about to replace.
                        SessionState.Restoring -> Unit
                        SessionState.SignedOut -> SignInScreen()
                        is SessionState.Paired -> OrdersScreen(store = current.store)
                    }
                }
            }
        }
    }
}
