package com.omaykan.storefront.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Database(
    entities = [
        CachedShopEntity::class,
        CachedCategoryEntity::class,
        CachedProductEntity::class,
        CartLineEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class OmaykanDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun cartDao(): CartDao
}

/**
 * 1 → 2: the cart arrives.
 *
 * Written out rather than left to the destructive fallback, because from this
 * version on the database holds something that is not a cache. The catalog can
 * be thrown away and refetched; a basket someone filled cannot be, and an
 * upgrade that silently empties it is an upgrade that costs them their order.
 */
internal val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `cart_line` (
                `storeKey` TEXT NOT NULL,
                `productId` TEXT NOT NULL,
                `quantity` REAL NOT NULL,
                `addedAtEpochMs` INTEGER NOT NULL,
                PRIMARY KEY(`storeKey`, `productId`)
            )
            """.trimIndent(),
        )
    }
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OmaykanDatabase =
        Room.databaseBuilder(context, OmaykanDatabase::class.java, "omaykan.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideCatalogDao(database: OmaykanDatabase): CatalogDao = database.catalogDao()

    @Provides
    fun provideCartDao(database: OmaykanDatabase): CartDao = database.cartDao()
}
