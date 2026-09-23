package com.omaykan.storefront

import android.content.Intent
import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.core.push.OrderNotifications
import com.omaykan.storefront.feature.welcome.WelcomeScreen
import com.omaykan.storefront.navigation.OmaykanNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Light status-bar icons everywhere: every screen now opens under a
        // forest bar or a darkened photograph, in either theme.
        enableEdgeToEdge(statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT))
        super.onCreate(savedInstanceState)

        // Read once, before anything can consume it: an App Link start means
        // the nav host resolves its own destination from the URL, and the
        // remembered shop must not be pushed on top of it.
        val launchedFromLink = intent?.data != null

        // A tapped order notification — ours, or the one Android drew from the
        // push while the app was closed, which carries the same extra. Not on
        // a recreate: rotating the phone must not open the order again.
        val notifiedOrder = intent?.getStringExtra(OrderNotifications.EXTRA_ORDER_ID)
            .takeIf { savedInstanceState == null }
        var openOrder by mutableStateOf(notifiedOrder)

        setContent {
            OmaykanTheme {
                val viewModel: LaunchViewModel = hiltViewModel()
                val state by viewModel.state.collectAsStateWithLifecycle()
                val navController = rememberNavController()

                /*
                 * A link tapped while the app is already running arrives here
                 * rather than in onCreate — the activity is singleTask, so
                 * there is exactly one of it and Android reuses this one.
                 * Without this the tap would bring the app forward showing
                 * whatever screen it was left on, which reads as the link
                 * having done nothing.
                 */
                DisposableEffect(navController) {
                    val listener = Consumer<Intent> { next ->
                        next.getStringExtra(OrderNotifications.EXTRA_ORDER_ID)
                            ?.let { openOrder = it }
                            ?: navController.handleDeepLink(next)
                    }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    // The launch theme has already painted the forest ground,
                    // so the mark simply appears on it — no handover, and no
                    // basket in between. See values/colors.xml.
                    when (val current = state) {
                        is LaunchState.Resolving -> SplashScreen()

                        // A link is an errand already under way; the welcome
                        // would stand between the shopper and it.
                        is LaunchState.Ready -> if (current.showWelcome && !launchedFromLink && notifiedOrder == null) {
                            WelcomeScreen(onGetStarted = viewModel::onWelcomeDone)
                        } else {
                            OmaykanNavHost(
                                resumeShop = if (launchedFromLink || notifiedOrder != null) null else current.resumeShop,
                                navController = navController,
                                openOrder = openOrder,
                                onOrderOpened = { openOrder = null },
                            )
                        }
                    }
                }
            }
        }
    }
}
