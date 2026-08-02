package com.example.omni_guard.data

enum class BluetoothStatus {
    SEARCHING,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    DISCONNECTED,
    CONNECTION_LOST
}

enum class SystemCommand(val command: String) {
    POWER("POWER"),
    POWER_OFF("POWER"),
    ARM("ARM"),
    DISARM("DISARM"),
    SILENCE("SILENCE"),
    SOUND("SOUND"),
    SCAN("SCAN"),
    STOP("STOP"),
    AUTO_SCAN("AUTO_SCAN"),
    STOP_SCAN("STOP_SCAN"),
    TRACK("TRACK"),
    LIGHT_ON("LIGHT_ON"),
    LIGHT_OFF("LIGHT_OFF"),
    CAMERA_ON("CAMERA_ON"),
    CAMERA_OFF("CAMERA_OFF"),
    REBOOT("REBOOT"),
    RESET("RESET"),
    EMERGENCY("EMERGENCY"),
    WARNING("WARNING"),
    INTRUDER("INTRUDER"),
    FINAL("FINAL"),
    READY("READY"),
    PING("PING"),
    VERSION("VERSION"),
    UNKNOWN("UNKNOWN");

    companion object {
        fun fromString(value: String): SystemCommand {
            val normalized = value.trim().uppercase()
            return entries.find { it.command == normalized || it.name == normalized } ?: UNKNOWN
        }
    }
}
