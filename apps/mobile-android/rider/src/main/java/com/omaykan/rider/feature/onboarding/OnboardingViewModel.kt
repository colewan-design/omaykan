package com.omaykan.rider.feature.onboarding

import androidx.lifecycle.ViewModel
import com.omaykan.rider.core.data.OnboardingStore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * One boolean and one way to set it.
 *
 * Thin to the point of looking unnecessary, and it is not: the store is a
 * singleton that has to be injected, and injecting it into a composable is what
 * a view model is for. Keeping it here also means the screen never touches
 * `SharedPreferences` on the composition thread, however small the read.
 */
@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val store: OnboardingStore,
) : ViewModel() {

    /** True once the introduction has been finished or skipped, on this phone. */
    val seen: StateFlow<Boolean> = store.seen

    fun markSeen() = store.markSeen()
}
