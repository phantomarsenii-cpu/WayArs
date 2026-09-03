package com.wayars.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.wayars.app.data.local.entity.OrderEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OrderDao {

    @Insert
    suspend fun insert(order: OrderEntity): Long

    @Query("UPDATE orders SET accepted = :accepted WHERE id = :id")
    suspend fun setAccepted(id: Long, accepted: Boolean)

    @Query("SELECT * FROM orders ORDER BY timestampMillis DESC")
    fun observeAll(): Flow<List<OrderEntity>>

    @Query("SELECT * FROM orders WHERE timestampMillis >= :sinceMillis ORDER BY timestampMillis DESC")
    fun observeSince(sinceMillis: Long): Flow<List<OrderEntity>>

    @Query("DELETE FROM orders")
    suspend fun clearAll()
}
