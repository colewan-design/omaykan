package com.omaykan.storefront.core.auth

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import com.omaykan.storefront.MainActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Where Google's redirect lands.
 *
 * Its own activity rather than an intent filter on MainActivity, because a
 * custom-scheme filter is a public entry point: anything on the phone can fire
 * an intent at `com.omaykan.storefront:/oauth2redirect`, and none of that should
 * be able to reach the shop through MainActivity's own launch path. Everything
 * arriving here is treated as unverified until GoogleAuthFlow has matched the
 * `state` it issued.
 *
 * `singleTask` is what dismisses the Custom Tab. The tab was launched into this
 * app's task, so bringing this activity to the front clears it — otherwise the
 * shopper lands back on a browser page showing a URL they cannot read.
 *
 * It draws nothing. It reads the intent, hands it over, opens MainActivity and
 * finishes, all before a frame is composed. ComponentActivity rather than the
 * plain platform Activity only because @AndroidEntryPoint requires one.
 */
@AndroidEntryPoint
class GoogleAuthRedirectActivity : ComponentActivity() {

    @Inject
    lateinit var flow: GoogleAuthFlow

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handle(intent)
    }

    /** `singleTask` means a second redirect reuses this instance rather than a new one. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        intent?.data?.let(flow::onRedirect)

        // CLEAR_TOP rather than a plain launch: MainActivity is already down
        // the stack with the shopper's cart and their place in the shop on it,
        // and starting a second copy would lose both.
        startActivity(
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )

        finish()
    }
}
