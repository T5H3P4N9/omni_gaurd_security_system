package com.example.omni_guard.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [EventEntity::class], version = 2, exportSchema = false)
abstract class OmniDatabase : RoomDatabase() {
    abstract fun eventDao(): EventDao
}
