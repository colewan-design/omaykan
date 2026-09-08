package com.omaykan.seller.core.auth

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
 * The signed-in staff token — the credential that reaches a shop's live orders.
 *
 * EncryptedSharedPreferences rather than DataStore, where the shop's name and
 * code live: a bearer token that does not expire on its own is not a setting.
 * The key comes from the Android keystore, so the file is unreadable off a
 * rooted backup of the device.
 *
 * This token is worth more than the shopper app's. It can settle payments and
 * mark orders complete, and it now carries a *person's* name into everything it
 * does. Hence the encryption, the backup exclusions in
 * data_extraction_rules.xml, and the fact that signing out clears it locally
 * rather than trusting a network call to finish first.
 *
 * The value is mirrored in memory because the OkHttp interceptor reads it on
 * every request and must not touch the keystore on the network thread each
 * time, and exposed as a StateFlow because an expired token has to move every
 * screen at once rather than only the one that found out.
 */
@Singleton
class StaffTokenStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val FILE = "omaykan.seller.session"
        const val KEY_TOKEN = "staff_token"
    }

    /**
     * Null when the keystore will not open.
     *
     * That happens for real — a restored backup, a changed lock screen, a
     * device whose keystore has been reset — and it is not a reason to crash on
     * launch. Losing the token means signing in again, which is a far better
     * outcome than an app that will not start.
     */
    private val prefs: SharedPreferences? by lazy {
        runCatching { open() }
            .recoverCatching {
                // A file encrypted under a key that no longer exists can never
                // be read again, so it is deleted and started over.
                context.deleteSharedPreferences(FILE)
                open()
            }
            .getOrNull()
    }

    private fun open(): SharedPreferences = EncryptedSharedPreferences.create(
        context,
        FILE,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private val state = MutableStateFlow(runCatching { prefs?.getString(KEY_TOKEN, null) }.getOrNull())

    val token: StateFlow<String?> = state.asStateFlow()

    /** Read on the network thread by the interceptor; never blocks on disk. */
    fun current(): String? = state.value

    fun save(token: String) {
        state.value = token
        runCatching { prefs?.edit()?.putString(KEY_TOKEN, token)?.apply() }
    }

    /**
     * Local only, and always safe to call.
     *
     * SessionRepository asks the server to retire the token first, which is
     * something the device era could not do at all. This half must still work
     * when that call fails: a phone in a merchant's hand has to stop showing
     * the next shift's orders whether or not the network agreed.
     */
    fun clear() {
        state.value = null
        runCatching { prefs?.edit()?.remove(KEY_TOKEN)?.apply() }
    }
}
