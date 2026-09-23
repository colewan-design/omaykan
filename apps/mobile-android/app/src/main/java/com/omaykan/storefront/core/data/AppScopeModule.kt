package com.omaykan.storefront.core.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A scope that outlives every screen.
 *
 * For the one job no ViewModel can own: a singleton watching something for the
 * whole life of the process. Today that is AccountRepository noticing the token
 * has been thrown away by the interceptor after a 401, which has to move the
 * session on every screen at once and cannot be tied to whichever one happened
 * to be visible.
 *
 * SupervisorJob so one failed collector does not take the rest down with it.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

@Module
@InstallIn(SingletonComponent::class)
object AppScopeModule {

    @Provides
    @Singleton
    @AppScope
    fun provideAppScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
