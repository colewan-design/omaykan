package com.omaykan.storefront

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.PairedStoreStore
import com.omaykan.storefront.core.data.WelcomeStore
import com.omaykan.storefront.core.model.StoreRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * What the app should show on the very first frame.
 *
 * Resolving the remembered shop takes one disk read — far less time than the
 * splash is held for. The hold is the point: a logo that appears and vanishes
 * inside 80ms reads as a glitch, so the splash stays for a beat whether or not
 * the read was instant, and the read is never what the shopper waits on.
 */
sealed interface LaunchState {
    data object Resolving : LaunchState

    /**
     * [resumeShop] is the shop this phone was last in, or null for a first run.
     * [showWelcome] is true until the welcome screen's button has been pressed.
     */
    data class Ready(val resumeShop: StoreRef?, val showWelcome: Boolean) : LaunchState
}

@HiltViewModel
class LaunchViewModel @Inject constructor(
    pairedStoreStore: PairedStoreStore,
    private val welcomeStore: WelcomeStore,
) : ViewModel() {

    private val _state = MutableStateFlow<LaunchState>(LaunchState.Resolving)
    val state: StateFlow<LaunchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val shop = pairedStoreStore.paired.first()?.ref
            val welcomed = welcomeStore.seen.first()
            delay(SPLASH_HOLD_MS)
            // A phone that has already been inside a shop was using the app
            // before the welcome screen existed. Greeting it now would be a
            // front door in the middle of a house it already lives in.
            _state.value = LaunchState.Ready(shop, showWelcome = !welcomed && shop == null)
        }
    }

    fun onWelcomeDone() {
        _state.update { current ->
            if (current is LaunchState.Ready) current.copy(showWelcome = false) else current
        }
        viewModelScope.launch { welcomeStore.markSeen() }
    }

    private companion object {
        /** Long enough for the wordmark to be read, short enough not to be a wait. */
        const val SPLASH_HOLD_MS = 900L
    }
}
