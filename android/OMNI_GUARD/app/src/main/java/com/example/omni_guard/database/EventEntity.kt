package com.example.omni_guard.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "events")
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val type: String,
    val description: String,
    val threatLevel: String,
    val distance: Int,
    val source: String = "APP",
    val severity: String = "INFO"
)
