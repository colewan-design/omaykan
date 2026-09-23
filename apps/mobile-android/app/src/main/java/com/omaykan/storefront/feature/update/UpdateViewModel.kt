package com.omaykan.storefront.feature.update

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.omaykan.storefront.core.data.UpdateRepository
import com.omaykan.storefront.core.model.AppUpdate
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The update prompt, asked once per launch.
 *
 * Checked from the market rather than from a settings screen nobody opens: the
 * app is sideloaded, so this is the only way a shopper ever learns there is a
 * newer build. It is still the quietest thing on screen — one dialog, offered
 * once per version, and silent about every failure.
 */
@HiltViewModel
class UpdateViewModel @Inject constructor(
    private val repository: UpdateRepository,
) : ViewModel() {

    private val _offer = MutableStateFlow<AppUpdate?>(null)
    val offer: StateFlow<AppUpdate?> = _offer.asStateFlow()

    init {
        viewModelScope.launch { _offer.value = repository.pendingUpdate() }
    }

    /**
     * "Later" — and it means later for *this* build only, so the next release
     * is still announced.
     */
    fun dismiss() {
        val offered = _offer.value ?: return
        _offer.value = null
        viewModelScope.launch { repository.dismiss(offered.versionCode) }
    }

    /**
     * The browser has the APK. The prompt closes without being marked
     * dismissed, because a download that the shopper abandons should still be
     * offered again next launch.
     */
    fun onOpened() {
        _offer.value = null
    }
}
