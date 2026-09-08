package com.omaykan.seller.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omaykan.seller.core.model.PairedStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pairingDataStore: DataStore<Preferences> by preferencesDataStore("seller_pairing")

/**
 * Which shop this phone is signed in to, minus the credential.
 *
 * Plain DataStore Preferences, not EncryptedSharedPreferences: a store's name
 * and its code are printed on the tarpaulin outside. The token that actually
 * opens the door lives in [com.omaykan.seller.core.auth.StaffTokenStore], and
 * the split is the point — this file is readable, that one is not.
 */
@Singleton
class PairedStoreStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyId = stringPreferencesKey("store_id")
    private val keyName = stringPreferencesKey("store_name")
    private val keyCode = stringPreferencesKey("store_code")
    private val keyOrg = stringPreferencesKey("organization_slug")

    val paired: Flow<PairedStore?> = context.pairingDataStore.data.map { prefs ->
        PairedStore(
            id = prefs[keyId] ?: return@map null,
            name = prefs[keyName].orEmpty(),
            code = prefs[keyCode].orEmpty(),
            organizationSlug = prefs[keyOrg].orEmpty(),
        )
    }

    suspend fun save(store: PairedStore) {
        context.pairingDataStore.edit { prefs ->
            prefs[keyId] = store.id
            prefs[keyName] = store.name
            prefs[keyCode] = store.code
            prefs[keyOrg] = store.organizationSlug
        }
    }

    suspend fun clear() {
        context.pairingDataStore.edit { it.clear() }
    }
}
