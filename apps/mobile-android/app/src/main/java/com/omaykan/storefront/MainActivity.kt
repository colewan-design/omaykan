package com.omaykan.storefront

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.core.util.Consumer
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.omaykan.storefront.core.designsystem.OmaykanTheme
import com.omaykan.storefront.navigation.OmaykanNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Read once, before anything can consume it: an App Link start means
        // the nav host resolves its own destination from the URL, and the
        // remembered shop must not be pushed on top of it.
        val launchedFromLink = intent?.data != null

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
                    val listener = Consumer<Intent> { navController.handleDeepLink(it) }
                    addOnNewIntentListener(listener)
                    onDispose { removeOnNewIntentListener(listener) }
                }

                Surface(color = MaterialTheme.colorScheme.background) {
                    // The launch theme has already painted this exact ground,
                    // so the wordmark simply appears on it — no handover, and
                    // no basket in between. See values-v31/themes.xml.
                    when (val current = state) {
                        is LaunchState.Resolving -> SplashScreen()
                        is LaunchState.Ready -> OmaykanNavHost(
                            resumeShop = if (launchedFromLink) null else current.resumeShop,
                            navController = navController,
                        )
                    }
                }
            }
        }
    }
}
