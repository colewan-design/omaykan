package com.omaykan.storefront.navigation

import android.content.Context
import android.content.Intent
import androidx.core.net.toUri

/**
 * The public page the web serves at /about.
 *
 * A constant rather than BuildConfig.API_BASE_URL: a debug build pointed at a
 * laptop has no marketing site on it, and opening a dead link is a worse answer
 * than opening the real one.
 */
const val ABOUT_URL = "https://omaykan.com/about"

/** Hands a URL to whatever the phone opens links with, or does nothing. */
fun Context.openInBrowser(url: String) {
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
