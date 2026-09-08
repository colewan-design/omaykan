package com.omaykan.rider.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.rider.core.data.SignOut
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The shell owns sign-out, not the account screen that shows the button.
 *
 * Signing out ends the whole session — the sharing service, the polling feed
 * and the token — and it survives the screen that asked for it: the moment the
 * token goes, the session flow turns SignedOut and MainActivity replaces
 * everything below it, account screen included. A view model scoped to that
 * screen would be cancelled mid-sequence.
 */
@HiltViewModel
class RiderShellViewModel @Inject constructor(
    /*
     * Named `endSession`, not `signOut`, and that is not a style choice.
     *
     * With both this property and the function below called `signOut`, the
     * `signOut()` inside the launch resolves to the *member function* rather
     * than to `SignOut.invoke()` — Kotlin prefers a member function over an
     * invoke on a property of the same name. It compiles, and it recurses until
     * the app hangs. It did: the first tap of the sign-out button ANR'd the
     * process.
     */
    private val endSession: SignOut,
) : ViewModel() {

    fun signOut() {
        viewModelScope.launch { endSession() }
    }
}
