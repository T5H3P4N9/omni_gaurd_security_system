package com.example.omni_guard.di

import android.content.Context
import androidx.room.Room
import com.example.omni_guard.database.OmniDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): OmniDatabase {
        return Room.databaseBuilder(
            context,
            OmniDatabase::class.java,
            "omni_guard_db"
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideEventDao(database: OmniDatabase) = database.eventDao()
}
