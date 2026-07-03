package com.safepathbuea.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Delete

@Dao
interface OfflineReportDao {
    @Insert
    suspend fun insert(report: OfflineReport): Long

    @Query("SELECT * FROM offline_reports ORDER BY createdAtMillis ASC")
    suspend fun allReports(): List<OfflineReport>

    @Delete
    suspend fun delete(report: OfflineReport)
}
