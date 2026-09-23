package com.omaykan.storefront

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.omaykan.storefront.core.push.OrderNotifications
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

@HiltAndroidApp
class OmaykanApp : Application(), SingletonImageLoader.Factory {

    override fun onCreate() {
        super.onCreate()
        // Before any push can arrive: Android files a notification for a
        // channel that does not exist yet under a generic one.
        OrderNotifications.createChannel(this)
    }

    /**
     * The same OkHttp the API uses, so images share its connection pool and its
     * timeouts rather than opening a second stack with its own idea of how long
     * a bad connection is worth waiting on.
     */
    @Inject
    lateinit var okHttpClient: OkHttpClient

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { okHttpClient }))
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.20)
                    .build()
            }
            // Product photos are the bulk of the bytes this app moves and they
            // change rarely. A disk cache is what makes a second visit to a
            // shop feel instant on a slow connection.
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(64L * 1024 * 1024)
                    .build()
            }
            .crossfade(true)
            .build()
}
