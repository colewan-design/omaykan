package com.omaykan.storefront

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.PairedStoreStore
import com.omaykan.storefront.core.model.StoreRef
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    /** [resumeShop] is the shop this phone was last in, or null for a first run. */
    data class Ready(val resumeShop: StoreRef?) : LaunchState
}

@HiltViewModel
class LaunchViewModel @Inject constructor(
    pairedStoreStore: PairedStoreStore,
) : ViewModel() {

    private val _state = MutableStateFlow<LaunchState>(LaunchState.Resolving)
    val state: StateFlow<LaunchState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val shop = pairedStoreStore.paired.first()?.ref
            delay(SPLASH_HOLD_MS)
            _state.value = LaunchState.Ready(shop)
        }
    }

    private companion object {
        /** Long enough for the wordmark to be read, short enough not to be a wait. */
        const val SPLASH_HOLD_MS = 900L
    }
}
