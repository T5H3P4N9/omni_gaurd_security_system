package com.example.omni_guard.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface EventDao {
    @Query("SELECT * FROM events ORDER BY timestamp DESC")
    fun getAllEvents(): Flow<List<EventEntity>>

    @Insert
    suspend fun insertEvent(event: EventEntity)

    @Query("DELETE FROM events")
    suspend fun clearAllEvents()

    @Query("SELECT * FROM events ORDER BY timestamp DESC LIMIT 100")
    fun getRecentEvents(): Flow<List<EventEntity>>
}
