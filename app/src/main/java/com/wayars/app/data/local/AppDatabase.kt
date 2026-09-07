package com.wayars.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.wayars.app.data.local.dao.OrderDao
import com.wayars.app.data.local.entity.OrderEntity

@Database(entities = [OrderEntity::class], version = 2, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "wayars.db"
                )
                    // App is still in active development/testing, no real
                    // migrations written yet — wiping local order history on
                    // a schema bump (like this one, adding fuelCost/netProfit)
                    // is an acceptable trade-off versus crashing on launch.
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
