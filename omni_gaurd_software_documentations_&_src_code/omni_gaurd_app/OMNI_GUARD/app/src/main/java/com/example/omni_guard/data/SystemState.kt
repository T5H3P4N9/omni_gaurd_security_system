package com.example.omni_guard.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

enum class SecurityMode { ARMED, DISARMED }
enum class ThreatLevel { CLEAR, WARNING, INTRUDER, FINAL, READY }
enum class PowerState { ON, OFF }

data class SystemStatus(
    val power: PowerState = PowerState.OFF,
    val security: SecurityMode = SecurityMode.DISARMED,
    val threat: ThreatLevel = ThreatLevel.READY,
    val bluetooth: BluetoothStatus = BluetoothStatus.DISCONNECTED,
    val lastArduinoCmd: String = "N/A",
    val distance: Int = 0,
    val angle: Int = 0,
    val x: Int = 0,
    val y: Int = 0,
    val lastMessage: String = "",
    val alarmActive: Boolean = false,
    val buzzer: Boolean = false,
    val sensorAiEnabled: Boolean = true,
    val eventVoiceEnabled: Boolean = true,
    
    // Telemetry & Diagnostics (V7.0 Expanded)
    val batteryLevel: Int = 0,
    val signalStrength: Int = 0,
    val temperature: Float = 0f,
    val uptimeSeconds: Long = 0,
    val cameraActive: Boolean = false,
    val lastCommunicationTime: Long = 0,
    val bluetoothLatency: Long = 0,
    val packetLoss: Int = 0,
    
    // House Unit V7.0 States
    val houseUnitOnline: Boolean = false,
    val firmwareVersion: String = "N/A",
    val redLed: Boolean = false,
    val greenLed: Boolean = false,
    val autoScan: Boolean = false,
    val lastAck: String = "N/A",
    val lastEvent: String = "N/A",
    val lastDeviceAddress: String = "",
    val protocolVersion: String = "V7.1",
    val arduinoModel: String = "Arduino UNO R3",
    val lastError: String = "",
    val queueSize: Int = 0,
    val retryCount: Int = 0,
    val connectionQuality: Int = 100,
    val sentPackets: Int = 0,
    val lostPackets: Int = 0
)

@Singleton
class SystemStateManager @Inject constructor() {
    private val _state = MutableStateFlow(SystemStatus())
    val state: StateFlow<SystemStatus> = _state.asStateFlow()

    fun updateState(transform: (SystemStatus) -> SystemStatus) {
        _state.value = transform(_state.value)
    }
}
