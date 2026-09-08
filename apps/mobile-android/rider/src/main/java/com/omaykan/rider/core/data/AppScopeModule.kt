package com.omaykan.rider.core.data

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * A scope that outlives every screen.
 *
 * For the one job no ViewModel can own: the work feed's polling loop. It is
 * shared — the board screen and, later, a watcher service both read the same
 * one — and it has to survive the rotation a rider gives the phone every time
 * they take it off the mount.
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
