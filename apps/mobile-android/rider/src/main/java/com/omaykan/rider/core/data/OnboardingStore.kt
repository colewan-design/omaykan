package com.omaykan.rider.core.data

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Whether this phone has been shown what the app is for.
 *
 * ## Why this is not in the session file
 *
 * [com.omaykan.rider.core.auth.RiderSessionStore] holds a token and a real
 * person's licence number behind a keystore key, and it is cleared on sign-out.
 * Both are wrong for this flag. It is not a secret — an attacker learning that
 * somebody has opened an app twice has learned nothing — and clearing it on
 * sign-out would walk a rider through three introduction screens every time
 * they signed out on a shared phone.
 *
 * So: a plain preferences file of its own, written once and never cleared.
 *
 * ## Why it is read on construction
 *
 * The value has to be known before the first frame, or the app shows a sign-in
 * screen and then replaces it with an introduction — which is the flash this
 * whole session model exists to avoid. It is one boolean out of one small file,
 * which is a cheaper main-thread read than the keystore unlock the session
 * store already does on the same launch.
 */
@Singleton
class OnboardingStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private companion object {
        const val FILE = "omaykan.rider.onboarding"
        const val KEY_SEEN = "seen"
    }

    private val preferences = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _seen = MutableStateFlow(preferences.getBoolean(KEY_SEEN, false))

    /** True once the rider has reached the end of the introduction, or skipped it. */
    val seen: StateFlow<Boolean> = _seen.asStateFlow()

    fun markSeen() {
        preferences.edit().putBoolean(KEY_SEEN, true).apply()
        _seen.value = true
    }
}
