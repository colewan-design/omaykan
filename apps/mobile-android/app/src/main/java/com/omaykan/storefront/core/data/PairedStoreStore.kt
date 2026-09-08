package com.omaykan.storefront.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omaykan.storefront.core.model.BusinessMode
import com.omaykan.storefront.core.model.StoreRef
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.pairingDataStore: DataStore<Preferences> by preferencesDataStore("pairing")

/**
 * Which shop this phone is currently in.
 *
 * Plain DataStore Preferences, not EncryptedSharedPreferences: it holds a shop's
 * public handle and its name, both of which are on the shop's own signage. A
 * bearer token would not belong here — that is not a setting.
 */
@Singleton
class PairedStoreStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val keyStore = stringPreferencesKey("store_key")
    private val keyName = stringPreferencesKey("store_name")
    private val keyMode = stringPreferencesKey("business_mode")

    val paired: Flow<PairedShopRecord?> = context.pairingDataStore.data.map { prefs ->
        val ref = prefs[keyStore]?.let(StoreRef::fromKey) ?: return@map null
        PairedShopRecord(
            ref = ref,
            name = prefs[keyName].orEmpty(),
            businessMode = BusinessMode.fromWire(prefs[keyMode]),
        )
    }

    suspend fun pair(record: PairedShopRecord) {
        context.pairingDataStore.edit { prefs ->
            prefs[keyStore] = record.ref.key
            prefs[keyName] = record.name
            prefs[keyMode] = record.businessMode.wire
        }
    }

    suspend fun unpair() {
        context.pairingDataStore.edit { it.clear() }
    }
}

data class PairedShopRecord(
    val ref: StoreRef,
    val name: String,
    val businessMode: BusinessMode,
)
