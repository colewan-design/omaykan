package com.omaykan.storefront.core.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.welcomeDataStore: DataStore<Preferences> by preferencesDataStore("welcome")

/**
 * Whether this phone has been past the welcome screen.
 *
 * One flag, once. The welcome is a front door, not a lobby: a shopper who has
 * pressed Get Started has seen it, and showing it again on the next launch
 * would stand between them and the shelf they came back for.
 */
@Singleton
class WelcomeStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val key = booleanPreferencesKey("seen")

    val seen: Flow<Boolean> = context.welcomeDataStore.data.map { it[key] ?: false }

    suspend fun markSeen() {
        context.welcomeDataStore.edit { it[key] = true }
    }
}
