package com.example.omni_guard.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.example.omni_guard.MainActivity
import com.example.omni_guard.data.SystemStatus
import com.example.omni_guard.data.SystemStateManager
import com.example.omni_guard.data.ThreatLevel
import com.example.omni_guard.repository.SecurityRepository
import com.example.omni_guard.utils.Constants
import com.example.omni_guard.notifications.OmniNotificationManager
import com.example.omni_guard.network.InternalServer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class OmniGuardService : LifecycleService() {

    @Inject lateinit var securityRepository: SecurityRepository
    @Inject lateinit var stateManager: SystemStateManager
    @Inject lateinit var notificationManager: OmniNotificationManager
    @Inject lateinit var internalServer: InternalServer

    override fun onCreate() {
        super.onCreate()
        
        // Start the internal backend natively
        internalServer.start()
        
        startForegroundService()
        observeSystemState()
        
        // Handle Auto-connect
        val sharedPrefs = getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)
        if (sharedPrefs.getBoolean(Constants.PREF_AUTO_CONNECT, true)) {
            sharedPrefs.getString(Constants.PREF_LAST_DEVICE_ADDRESS, null)?.let {
                securityRepository.connect(it)
            }
        }
    }

    private fun startForegroundService() {
        val channelId = "omni_guard_service"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "OMNI_GUARD Core Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }

        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("OMNI_GUARD Active")
            .setContentText("System monitoring in progress...")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setContentIntent(pendingIntent)
            .build()

        startForeground(Constants.SERVICE_NOTIFICATION_ID, notification)
    }

    private fun observeSystemState() {
        lifecycleScope.launch {
            stateManager.state.collectLatest { state ->
                handleCriticalState(state)
            }
        }
    }

    private fun handleCriticalState(state: SystemStatus) {
        if (state.threat == ThreatLevel.INTRUDER || state.threat == ThreatLevel.FINAL) {
            notificationManager.showThreatAlert(state.threat)
        } else {
            notificationManager.stopAlarm()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        super.onBind(intent)
        return null
    }

    override fun onDestroy() {
        internalServer.stop()
        super.onDestroy()
    }
}
