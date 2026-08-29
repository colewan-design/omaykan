package com.omaykan.storefront.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omaykan.storefront.core.model.Contact
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.checkoutDataStore: DataStore<Preferences> by preferencesDataStore("checkout")

/**
 * What checkout should already know the second time.
 *
 * A name, a number and an address, remembered on this device so a returning
 * shopper is not made to type them again — the same convenience an account
 * would give, without needing one. Nothing here is a credential; when the
 * Sanctum token arrives it does not belong in this file.
 */
@Singleton
class CheckoutPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyName = stringPreferencesKey("name")
    private val keyPhone = stringPreferencesKey("phone")
    private val keyEmail = stringPreferencesKey("email")
    private val keyAddress = stringPreferencesKey("address")
    private val keyLandmark = stringPreferencesKey("landmark")
    private val keyLastOrder = stringPreferencesKey("recent_order_ids")

    val contact: Flow<Contact> = context.checkoutDataStore.data.map { prefs ->
        Contact(
            name = prefs[keyName].orEmpty(),
            phone = prefs[keyPhone].orEmpty(),
            email = prefs[keyEmail].orEmpty(),
        )
    }

    val address: Flow<String> = context.checkoutDataStore.data.map { it[keyAddress].orEmpty() }

    /**
     * The landmark, kept apart from the address on purpose.
     *
     * "The green gate past the basketball court" does not change when someone
     * moves from unit 4 to unit 7, and a rider who has been once needs it more
     * than the street name. Stored separately so it survives an edit to the
     * address, and so it can be prefilled on its own.
     */
    val landmark: Flow<String> = context.checkoutDataStore.data.map { it[keyLandmark].orEmpty() }

    /**
     * Orders placed on this device, newest first.
     *
     * The device's own record, kept because a guest order belongs to nobody:
     * the server can only find it by its id, and without this list a shopper
     * who did not sign in would lose sight of an order the moment they left
     * the confirmation screen. Signing in later does not backfill these —
     * see CustomerOrderController — so both lists are shown.
     *
     * A delimited string rather than a preferences string-set, because a set
     * has no order and "newest first" is the whole point.
     */
    val recentOrderIds: Flow<List<String>> = context.checkoutDataStore.data.map { prefs ->
        prefs[keyLastOrder].orEmpty()
            .split(SEPARATOR)
            .filter { it.isNotBlank() }
    }

    /**
     * What this device should already know next time.
     *
     * Blanks are not written. Someone who ordered for pickup has no address to
     * remember, and letting that empty field overwrite the one saved from a
     * delivery last week would lose it for no reason. The landmark follows the
     * same rule and for the same reason.
     */
    suspend fun remember(contact: Contact, address: String, landmark: String = "") {
        context.checkoutDataStore.edit { prefs ->
            prefs[keyName] = contact.name.trim()
            prefs[keyPhone] = contact.phone.trim()
            prefs[keyEmail] = contact.email.trim()
            if (address.isNotBlank()) prefs[keyAddress] = address.trim()
            if (landmark.isNotBlank()) prefs[keyLandmark] = landmark.trim()
        }
    }

    suspend fun rememberOrder(orderId: String) {
        context.checkoutDataStore.edit { prefs ->
            val existing = prefs[keyLastOrder].orEmpty()
                .split(SEPARATOR)
                .filter { it.isNotBlank() && it != orderId }
            prefs[keyLastOrder] = (listOf(orderId) + existing)
                .take(MAX_REMEMBERED)
                .joinToString(SEPARATOR)
        }
    }

    private companion object {
        const val SEPARATOR = ","

        /** Enough to be a history; short enough that the list is one screenful of calls. */
        const val MAX_REMEMBERED = 10
    }
}
