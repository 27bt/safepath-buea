package com.safepathbuea.app.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [OfflineReport::class], version = 1, exportSchema = false)
abstract class OfflineReportDatabase : RoomDatabase() {
    abstract fun offlineReportDao(): OfflineReportDao

    companion object {
        private const val DB_NAME = "safepath_offline_reports.db"

        @Volatile
        private var INSTANCE: OfflineReportDatabase? = null

        fun getInstance(context: Context): OfflineReportDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(context.applicationContext, OfflineReportDatabase::class.java, DB_NAME)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
