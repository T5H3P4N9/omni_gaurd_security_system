package com.example.omni_guard.utils

object Constants {
    const val BLUETOOTH_UUID = "00001101-0000-1000-8000-00805F9B34FB"
    const val PREFS_NAME = "OmniGuardPrefs"
    const val PREF_LAST_DEVICE_ADDRESS = "last_device_address"
    const val PREF_FLASK_URL = "flask_url"
    const val PREF_RECONNECT_INTERVAL = "reconnect_interval"
    const val PREF_DARK_MODE = "dark_mode"
    const val PREF_VIBRATION_ON = "vibration_on"
    const val PREF_AUTO_CONNECT = "auto_connect"

    const val DEFAULT_FLASK_URL = "http://127.0.0.1:8000"
    const val DEFAULT_RECONNECT_INTERVAL = 5000L

    const val NOTIFICATION_CHANNEL_ID = "omni_guard_channel"
    const val NOTIFICATION_ID = 101
    const val SERVICE_NOTIFICATION_ID = 100
    
    // Migrated from Flask backend
    const val OPENAI_API_KEY = "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"
}
