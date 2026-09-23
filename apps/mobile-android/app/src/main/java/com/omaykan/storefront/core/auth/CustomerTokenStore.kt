package com.omaykan.storefront.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The signed-in shopper's Sanctum bearer token.
 *
 * EncryptedSharedPreferences rather than DataStore, which is where the checkout
 * prefs live: DataStore writes plaintext to disk, and a bearer token that never
 * expires on its own is not a setting. The key comes from the Android keystore,
 * so the file is unreadable off a rooted backup of the device.
 *
 * The value is mirrored in memory because the OkHttp interceptor reads it on
 * every request and must not touch the keystore on the network thread each
 * time, and exposed as a StateFlow because signing out has to move the UI on
 * every screen at once rather than only the one that asked.
 */
@Singleton
class CustomerTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val FILE = "omaykan.session"
        const val KEY_TOKEN = "customer_token"
    }

    /**
     * Null when the keystore will not open.
     *
     * That happens for real — a restored backup, a changed lock screen, a
     * device whose keystore has been reset — and it is not a reason to crash
     * the app on launch. Losing the token means signing in again, which is a
     * far better outcome than an unstartable storefront, and the cart is
     * untouched either way.
     */
    private val prefs: SharedPreferences? by lazy {
        runCatching {
            EncryptedSharedPreferences.create(
                context,
                FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.recoverCatching {
            // Second chance: a file encrypted under a key that no longer exists
            // can never be read again, so it is deleted and started over.
            context.deleteSharedPreferences(FILE)
            EncryptedSharedPreferences.create(
                context,
                FILE,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }.getOrNull()
    }

    private val state = MutableStateFlow(runCatching { prefs?.getString(KEY_TOKEN, null) }.getOrNull())

    val token: StateFlow<String?> = state.asStateFlow()

    /** Read on the network thread by the interceptor; never blocks on disk. */
    fun current(): String? = state.value

    fun save(token: String) {
        state.value = token
        runCatching { prefs?.edit()?.putString(KEY_TOKEN, token)?.apply() }
    }

    /**
     * Local only. Revoking the token server-side is POST /api/customer/logout,
     * which the repository tries first — but a shopper who pressed sign out has
     * been signed out whatever the network said.
     */
    fun clear() {
        state.value = null
        runCatching { prefs?.edit()?.remove(KEY_TOKEN)?.apply() }
    }
}
