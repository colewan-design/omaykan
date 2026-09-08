package com.omaykan.rider.core.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.omaykan.rider.core.model.RiderProfile
import com.omaykan.rider.core.model.RiderStatus
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * What this phone knows about the rider using it: the token, and the last
 * account the server described.
 *
 * ## Why both, in one encrypted file
 *
 * :seller keeps its token in EncryptedSharedPreferences and the shop's name in
 * DataStore, because a shop's name is not a secret. Nothing here gets that
 * split. The cached profile is a real person's name, email, phone and driving
 * licence number, so it belongs behind the same keystore key as the token —
 * and keeping them in one file means they are written and cleared together,
 * which removes the half-signed-in state :seller's SessionRepository has to
 * reason about.
 *
 * ## Why the profile is cached at all
 *
 * So the app can decide what to show before the network answers. `GET
 * /api/rider/me` is the truth about an account's status, but a rider opening
 * the app in a basement car park should still see their job screen rather than
 * a spinner, and a pending one should still see their waiting screen rather
 * than a sign-in form they have already filled in. The cached copy picks the
 * screen; the call that follows corrects it.
 *
 * The token is mirrored in memory because the OkHttp interceptor reads it on
 * every request and must not touch the keystore on the network thread each
 * time. Both are StateFlows because a dead token or a suspension has to move
 * every screen at once, not only the one that found out.
 */
@Singleton
class RiderSessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private companion object {
        const val FILE = "omaykan.rider.session"
        const val KEY_TOKEN = "rider_token"
        const val KEY_RIDER = "rider_profile"
    }

    /**
     * The cached profile's on-disk shape, kept separate from [RiderProfile].
     *
     * A stored format and a domain model change for different reasons, and this
     * one has to survive an app update reading a file the previous version
     * wrote. Every field is defaulted for that reason, and [RiderStatus] is
     * stored as its wire string rather than an enum ordinal — an ordinal would
     * silently re-point at a different status the day somebody reorders the
     * enum.
     */
    @Serializable
    private data class StoredRider(
        val id: String = "",
        val name: String = "",
        val email: String = "",
        val phone: String = "",
        val licenseNumber: String = "",
        val plateNumber: String = "",
        val status: String = RiderStatus.Pending.wire,
        val reviewNote: String? = null,
        val reviewedAt: String? = null,
        val createdAt: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Null when the keystore will not open.
     *
     * That happens for real — a restored backup, a changed lock screen, a
     * device whose keystore has been reset — and it is not a reason to crash on
     * launch. Losing this means signing in again with an email and a password
     * the rider already has, which is a far better outcome than an app that
     * will not start.
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

    private val tokenState =
        MutableStateFlow(runCatching { prefs?.getString(KEY_TOKEN, null) }.getOrNull())

    private val riderState = MutableStateFlow(readRider())

    val token: StateFlow<String?> = tokenState.asStateFlow()

    val rider: StateFlow<RiderProfile?> = riderState.asStateFlow()

    /** Read on the network thread by the interceptor; never blocks on disk. */
    fun currentToken(): String? = tokenState.value

    fun save(token: String, rider: RiderProfile) {
        tokenState.value = token
        riderState.value = rider
        runCatching {
            prefs?.edit()
                ?.putString(KEY_TOKEN, token)
                ?.putString(KEY_RIDER, json.encodeToString(rider.stored()))
                ?.apply()
        }
    }

    /**
     * Update the account without touching the token.
     *
     * Two things call this: `GET /api/rider/me`, and the approval gate's 403 —
     * which is how a rider who is suspended mid-shift finds out. A refreshed
     * profile must never disturb the credential that fetched it.
     */
    fun saveRider(rider: RiderProfile) {
        riderState.value = rider
        runCatching {
            prefs?.edit()?.putString(KEY_RIDER, json.encodeToString(rider.stored()))?.apply()
        }
    }

    /**
     * Forget everything about this rider.
     *
     * Cannot fail, and is not allowed to wait on the network: a rider tapping
     * Sign out in a basement must end up signed out. The server-side half —
     * `POST /api/rider/logout`, which retires this one token and leaves their
     * other phones alone — is attempted first by SessionRepository and its
     * failure is ignored.
     */
    fun clear() {
        tokenState.value = null
        riderState.value = null
        runCatching { prefs?.edit()?.remove(KEY_TOKEN)?.remove(KEY_RIDER)?.apply() }
    }

    private fun readRider(): RiderProfile? = runCatching {
        prefs?.getString(KEY_RIDER, null)?.let { json.decodeFromString<StoredRider>(it) }
    }.getOrNull()?.let { stored ->
        RiderProfile(
            id = stored.id,
            name = stored.name,
            email = stored.email,
            phone = stored.phone,
            licenseNumber = stored.licenseNumber,
            plateNumber = stored.plateNumber,
            status = RiderStatus.fromWire(stored.status),
            reviewNote = stored.reviewNote,
            reviewedAt = stored.reviewedAt,
            createdAt = stored.createdAt,
        )
    }

    private fun RiderProfile.stored() = StoredRider(
        id = id,
        name = name,
        email = email,
        phone = phone,
        licenseNumber = licenseNumber,
        plateNumber = plateNumber,
        status = status.wire,
        reviewNote = reviewNote,
        reviewedAt = reviewedAt,
        createdAt = createdAt,
    )
}
