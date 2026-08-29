package com.omaykan.storefront.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.savedDataStore: DataStore<Preferences> by preferencesDataStore("saved_products")

/**
 * The heart on a product card.
 *
 * Local to the device and nothing else — there is no wishlist table behind the
 * API, and the mobile plan leaves it an open question whether one should exist.
 * Keeping it on the phone means the feature works today and owes the server
 * nothing; if it ever becomes an account feature, this is one repository to
 * repoint rather than a UI to rebuild.
 *
 * Ids are stored fully qualified ("orgSlug/storeCode/productId") because a
 * product id is only unique inside its own shop's catalog.
 */
@Singleton
class SavedProductsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = stringSetPreferencesKey("saved_ids")

    val saved: Flow<Set<String>> = context.savedDataStore.data.map { it[key] ?: emptySet() }

    /**
     * @return true if the product is now saved, false if this removed it —
     * which is the only way a caller can tell the shopper which one just
     * happened. The flow above says what the set holds, not what changed.
     */
    suspend fun toggle(id: String): Boolean {
        var nowSaved = false
        context.savedDataStore.edit { prefs ->
            val current = prefs[key] ?: emptySet()
            nowSaved = id !in current
            prefs[key] = if (nowSaved) current + id else current - id
        }
        return nowSaved
    }
}
