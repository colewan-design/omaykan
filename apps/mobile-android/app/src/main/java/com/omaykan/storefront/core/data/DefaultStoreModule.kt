package com.omaykan.storefront.core.data

import com.omaykan.storefront.BuildConfig
import com.omaykan.storefront.core.model.StoreRef
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

/** The shop whose shelf is the front page. See DEFAULT_ORG_SLUG in build.gradle.kts. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultStore

@Module
@InstallIn(SingletonComponent::class)
object DefaultStoreModule {

    @Provides
    @Singleton
    @DefaultStore
    fun provideDefaultStore(): StoreRef =
        StoreRef(BuildConfig.DEFAULT_ORG_SLUG, BuildConfig.DEFAULT_STORE_CODE)
}
