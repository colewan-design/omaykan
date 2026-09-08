package com.omaykan.rider

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

/**
 * The Hilt root, and one image loader.
 *
 * This app shows names, addresses and money, and for a long time it needed no
 * loader at all. It draws exactly one picture now — the static route map on a
 * job card — and that one picture is enough to need this: Coil's default loader
 * has no network fetcher registered, so every remote image fails without
 * raising anything, which on a card looks identical to "no map for this job".
 *
 * The disk cache is small on purpose. There is one image per job and a rider
 * sees a handful in a shift, so this is sized to hold a day of them and not a
 * catalog — :app's is a hundred times larger because a storefront is mostly
 * photographs.
 */
@HiltAndroidApp
class RiderApp : Application(), SingletonImageLoader.Factory {

    /**
     * The same OkHttp the API uses, so map images share its connection pool and
     * its timeouts rather than opening a second stack with its own idea of how
     * long a bad connection is worth waiting on. Safe for a third-party host:
     * RiderAuthInterceptor only attaches the rider's token to our own.
     */
    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            // A route from this shop to this door does not change while a rider
            // carries it, and every one of these is a metered Mapbox request.
            // The cache is the difference between one request per job and one
            // per glance at the card.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("map_cache").toOkioPath())
                    .maxSizeBytes(8L * 1024 * 1024)
                    .build()
            }
            .build()
}
