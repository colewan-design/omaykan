package com.omaykan.storefront.core.data

import android.content.Context
import androidx.core.content.pm.PackageInfoCompat
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.omaykan.storefront.core.model.AppUpdate
import com.omaykan.storefront.core.network.ApiCaller
import com.omaykan.storefront.core.network.OmaykanApi
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore: DataStore<Preferences> by preferencesDataStore("updates")

/**
 * Whether there is a newer build, and whether the shopper wants to hear it.
 *
 * The whole comparison lives on this side. The server publishes one fact —
 * "this is the current build" — and cannot know what is installed on the phone
 * asking, so a server that decided whether an update was needed would be wrong
 * on every sideloaded downgrade and every phone that skipped a release.
 *
 * Nothing here ever downloads or installs anything. The APK opens in the
 * browser and Android's own installer takes it from there, which keeps this out
 * of REQUEST_INSTALL_PACKAGES and out of the business of verifying a download.
 */
@Singleton
class UpdateRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: OmaykanApi,
    private val caller: ApiCaller,
) {
    private val keyDismissed = longPreferencesKey("dismissed_version_code")

    /**
     * The build running right now.
     *
     * longVersionCode rather than the deprecated int: the server column is an
     * unsigned big int and a date-derived scheme runs past 32 bits.
     */
    val installedVersionCode: Long by lazy {
        runCatching {
            PackageInfoCompat.getLongVersionCode(
                context.packageManager.getPackageInfo(context.packageName, 0),
            )
        }.getOrDefault(0L)
    }

    val installedVersionName: String by lazy {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    /**
     * What is published, newer or not.
     *
     * Null for "nothing published" *and* for every failure: an update check is
     * the least important request the app makes, and a launch that showed an
     * error because the release endpoint was unreachable would be a worse app
     * than one that quietly stayed on the build it has.
     */
    suspend fun published(): AppUpdate? {
        val dto = runCatching { caller.callOrNull { api.appRelease(SLUG) } }.getOrNull() ?: return null

        return AppUpdate(
            versionCode = dto.versionCode,
            versionName = dto.versionName,
            apkUrl = dto.apkUrl?.takeIf { it.isNotBlank() },
            notes = dto.notes?.takeIf { it.isNotBlank() },
        )
    }

    /** What to actually offer, or null to say nothing. */
    suspend fun pendingUpdate(): AppUpdate? = chooseUpdate(
        published = published(),
        installedVersionCode = installedVersionCode,
        dismissedVersionCode = context.updateDataStore.data.first()[keyDismissed] ?: 0L,
    )

    suspend fun dismiss(versionCode: Long) {
        context.updateDataStore.edit { it[keyDismissed] = versionCode }
    }

    private companion object {
        /** This app. The rider and merchant builds would be their own slugs. */
        const val SLUG = "storefront-android"
    }
}

/**
 * Whether a published build is worth interrupting for.
 *
 * A free function so the rule can be tested without a Context: everything it
 * needs is three values, and every one of them is awkward to fake through
 * PackageManager and DataStore.
 *
 * Dismissal is remembered per version rather than as a boolean, so "later"
 * silences this build and not the next one — a shopper who waved off 2.0 still
 * hears about 2.1. Equal codes are not an update: a phone running exactly what
 * is published is current, and re-offering it is how an update prompt teaches
 * people to ignore update prompts.
 */
internal fun chooseUpdate(
    published: AppUpdate?,
    installedVersionCode: Long,
    dismissedVersionCode: Long,
): AppUpdate? {
    if (published == null) return null
    if (published.versionCode <= installedVersionCode) return null

    // Published but with nowhere to get it: a real state while a build is
    // recorded ahead of its upload, and nothing a shopper can act on.
    if (!published.installable) return null

    return if (published.versionCode <= dismissedVersionCode) null else published
}
