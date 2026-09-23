package com.omaykan.seller.feature.shell

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.omaykan.seller.BuildConfig
import com.omaykan.seller.core.model.PairedStore

/*
 * The web pages this app hands off to.
 *
 * Always the public site, whatever API a debug build points at: these are
 * pages a merchant reads, and a laptop's `php artisan serve` is not where
 * their storefront lives.
 */

private const val WEB_ORIGIN = "https://omaykan.com"

/** Self-serve signup: an organization, a shop and an owner account in one go. */
const val SIGNUP_URL = "$WEB_ORIGIN/seller/signup"

/** The register PWA, where the shop photo, address, staff and categories are kept. */
const val REGISTER_URL = "$WEB_ORIGIN/app"

/**
 * The shop's page as customers see it — the landing page opened on this
 * organization, the same `?shop=<orgSlug>` link the shop directory uses.
 * A record paired before the slug was stored has none; the front page is the
 * honest fallback.
 */
fun storefrontUrl(store: PairedStore): String =
    if (store.organizationSlug.isBlank()) {
        WEB_ORIGIN
    } else {
        "$WEB_ORIGIN/?shop=${Uri.encode(store.organizationSlug)}"
    }

/**
 * The shop's own photo, from the API this build talks to. Public, and a 404
 * when the owner never uploaded one — RemoteThumb shows the storefront icon
 * then, which is the right picture of a shop with no picture.
 */
fun storeImageUrl(storeId: String): String =
    BuildConfig.API_BASE_URL.trimEnd('/') + "/api/stores/$storeId/image"

/**
 * Open the shop's location in the phone's maps app — the pin when there is
 * one, a search for the address when there is not. Nothing to show, or no
 * maps app, does nothing.
 */
fun Context.openMap(lat: Double?, lng: Double?, label: String, address: String?) {
    val uri = when {
        lat != null && lng != null -> Uri.parse("geo:$lat,$lng?q=$lat,$lng(${Uri.encode(label)})")
        !address.isNullOrBlank() -> Uri.parse("geo:0,0?q=${Uri.encode(address)}")
        else -> return
    }
    runCatching {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

/** Open a page in the phone's browser. A phone with none simply does nothing. */
fun Context.openInBrowser(url: String) {
    runCatching {
        startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }
}
