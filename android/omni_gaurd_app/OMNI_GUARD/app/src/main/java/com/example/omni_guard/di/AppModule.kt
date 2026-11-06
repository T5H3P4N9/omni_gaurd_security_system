package com.example.omni_guard.di

import android.content.Context
import com.example.omni_guard.bluetooth.OmniBluetoothManager
import com.example.omni_guard.data.SystemStateManager
import com.example.omni_guard.notifications.OmniNotificationManager
import com.example.omni_guard.repository.SecurityRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideBluetoothManager(
        @ApplicationContext context: Context,
        stateManager: SystemStateManager
    ): OmniBluetoothManager {
        return OmniBluetoothManager(context, stateManager)
    }

    @Provides
    @Singleton
    fun provideSystemStateManager(): SystemStateManager {
        return SystemStateManager()
    }
}
