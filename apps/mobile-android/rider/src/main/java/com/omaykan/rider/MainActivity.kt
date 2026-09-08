package com.omaykan.rider

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.omaykan.rider.core.designsystem.RiderTheme
import com.omaykan.rider.core.model.SessionState
import com.omaykan.rider.feature.forgot.ForgotPasswordScreen
import com.omaykan.rider.feature.home.RiderShell
import com.omaykan.rider.feature.onboarding.OnboardingScreen
import com.omaykan.rider.feature.onboarding.OnboardingViewModel
import com.omaykan.rider.feature.register.RegisterScreen
import com.omaykan.rider.feature.signin.SignInScreen
import com.omaykan.rider.feature.status.StatusScreen
import dagger.hilt.android.AndroidEntryPoint

/**
 * The whole app, in one activity.
 *
 * There is still no NavHost, and that is still a decision rather than an
 * omission. The top level is not a stack — it is the answers to one question
 * the session already holds: signed out, signed in and waiting, signed in and
 * working. A nav graph would let a rider press Back off their job board onto
 * the sign-in screen they are no longer on, which is a bug waiting to be filed.
 *
 * Two places below it *are* a push, and both are the ones where a back gesture
 * means something: registration and the forgotten-password form, off the
 * sign-in screen. A single `rememberSaveable` enum carries which, restored
 * across rotation and process death, and each screen wires the system back
 * button itself. That is the entire navigation requirement of this app, and it
 * does not need a library.
 *
 * An approved rider gets three tabbed screens rather than one — see
 * [RiderShell], which makes the same argument one level down.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        /**
         * What the location-sharing notification opens.
         *
         * FLAG_IMMUTABLE because nothing may rewrite this intent — it is handed
         * to the system, and on API 31+ a mutable one without a good reason is
         * refused outright. CLEAR_TOP rather than a new task, so tapping the
         * notice brings the running app forward on the work screen instead of
         * stacking a second copy behind it.
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
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        setContent {
            RiderTheme {
                val viewModel: LaunchViewModel = hiltViewModel()
                val session by viewModel.session.collectAsStateWithLifecycle()

                Surface(color = MaterialTheme.colorScheme.background) {
                    when (val current = session) {
                        // The launch theme has already painted this exact
                        // ground, so a phone that reads its keystore slowly
                        // shows a still window rather than a flash of the
                        // sign-in screen it is about to replace.
                        SessionState.Restoring -> Unit
                        SessionState.SignedOut -> SignedOut()
                        is SessionState.Gated -> StatusScreen(rider = current.rider)
                        is SessionState.Working -> RiderShell(rider = current.rider)
                    }
                }
            }
        }
    }
}

/** Which door a signed-out visitor is at. */
private enum class Door { SignIn, Register, Forgot }

/**
 * The introduction, then a door.
 *
 * The introduction is in front of the sign-in screen rather than behind it
 * because it is aimed at somebody who has no account yet — a rider handed a
 * link by a friend at a shop, deciding whether to fill in a form with two
 * photographs in it. A rider who already has an account has seen it once and
 * never sees it again: the flag is written on the last page and on Skip, and
 * is deliberately not cleared by signing out. See OnboardingStore.
 *
 * `collectAsStateWithLifecycle` rather than a one-shot read, so the screen
 * swaps the moment the flag is written instead of waiting for something else
 * to recompose this.
 */
@Composable
private fun SignedOut(onboarding: OnboardingViewModel = hiltViewModel()) {
    val seen by onboarding.seen.collectAsStateWithLifecycle()

    if (!seen) {
        OnboardingScreen(onDone = {})
        return
    }

    Doors()
}

/**
 * Sign in, sign up, or get back in.
 *
 * Neither of the first two navigates away on success: both write a token and a
 * profile, the session flow turns Gated or Working, and the activity swaps the
 * screen. One source of truth for "am I signed in", rather than a screen that
 * thinks it is and a session that disagrees.
 *
 * The third does not sign anybody in at all. A reset link is emailed and it
 * opens the **web portal** — this app registers no deep link for it, and
 * should not: a token only an installed app could spend would strand a rider
 * who read the mail on a laptop, or on the phone they are locked out of and
 * about to reinstall. They come back here afterwards and sign in normally.
 */
@Composable
private fun Doors() {
    var door by rememberSaveable { mutableStateOf(Door.SignIn) }

    when (door) {
        Door.Register -> RegisterScreen(onBack = { door = Door.SignIn })
        Door.Forgot -> ForgotPasswordScreen(onBack = { door = Door.SignIn })
        Door.SignIn -> SignInScreen(
            onRegister = { door = Door.Register },
            onForgotPassword = { door = Door.Forgot },
        )
    }
}
