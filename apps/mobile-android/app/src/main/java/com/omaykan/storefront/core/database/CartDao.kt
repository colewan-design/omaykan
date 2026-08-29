package com.omaykan.storefront.core.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CartDao {

    @Query(
        "SELECT * FROM cart_line WHERE storeKey = :storeKey " +
            "ORDER BY addedAtEpochMs, productId",
    )
    fun observeLines(storeKey: String): Flow<List<CartLineEntity>>

    /** Every line this device is holding, across shops — drives the cart badge. */
    @Query("SELECT * FROM cart_line ORDER BY addedAtEpochMs")
    fun observeAll(): Flow<List<CartLineEntity>>

    @Query("SELECT quantity FROM cart_line WHERE storeKey = :storeKey AND productId = :productId")
    suspend fun quantityOf(storeKey: String, productId: String): Double?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(line: CartLineEntity)

    /**
     * Changes an existing line's quantity in place, and answers how many rows
     * that was — 0 when the basket has no such line yet.
     *
     * The point of it is what it does not touch: `addedAtEpochMs`, which is
     * what every read of this table sorts on. Re-inserting the whole row to
     * change a number would stamp it with the current time and send the line
     * to the bottom of the basket.
     */
    @Query(
        "UPDATE cart_line SET quantity = :quantity " +
            "WHERE storeKey = :storeKey AND productId = :productId",
    )
    suspend fun updateQuantity(storeKey: String, productId: String, quantity: Double): Int

    @Query("DELETE FROM cart_line WHERE storeKey = :storeKey AND productId = :productId")
    suspend fun remove(storeKey: String, productId: String)

    @Query("DELETE FROM cart_line WHERE storeKey = :storeKey")
    suspend fun clear(storeKey: String)
}
