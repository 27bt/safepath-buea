package com.safepathbuea.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "offline_reports")
data class OfflineReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,
    val latitude: Double,
    val longitude: Double,
    val hashedUid: String,
    val severity: String = "medium",
    val createdAtMillis: Long = System.currentTimeMillis(),
)
