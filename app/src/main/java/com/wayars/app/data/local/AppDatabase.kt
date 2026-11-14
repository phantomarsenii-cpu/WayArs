package com.wayars.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.wayars.app.data.local.dao.OrderDao
import com.wayars.app.data.local.entity.OrderEntity

@Database(entities = [OrderEntity::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE orders ADD COLUMN sourcePackage TEXT")
                db.execSQL("ALTER TABLE orders ADD COLUMN takeHome REAL")
            }
        }

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
                    // v2 -> v3 keeps the driver's history: two new nullable columns.
                    .addMigrations(MIGRATION_2_3)
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
