package com.omaykan.rider.core.location

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The one binding the location feature needs.
 *
 * [PositionReporter] asks for a [FixSource] rather than a [LocationSource] so
 * its throttling rules can be tested against scripted fixes. This is where the
 * real sensor gets attached, and it is the whole module.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class LocationModule {

    @Binds
    @Singleton
    abstract fun fixSource(source: LocationSource): FixSource
}
