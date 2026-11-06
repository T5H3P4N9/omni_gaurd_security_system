package com.example.omni_guard.repository

import com.example.omni_guard.bluetooth.OmniBluetoothManager
import com.example.omni_guard.data.*
import com.example.omni_guard.database.EventDao
import com.example.omni_guard.database.EventEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityRepository @Inject constructor(
    private val bluetoothManager: OmniBluetoothManager,
    private val stateManager: SystemStateManager,
    private val eventDao: EventDao
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val commandMutex = Mutex()
    private val commandQueue = Channel<CommandRequest>(Channel.UNLIMITED)
    
    private val _commandAcks = MutableSharedFlow<String>(extraBufferCapacity = 64)
    val commandAcks = _commandAcks.asSharedFlow()

    data class CommandRequest(
        val command: SystemCommand,
        val retryCount: Int = 0,
        val timestamp: Long = System.currentTimeMillis()
    )

    init {
        observeBluetoothData()
        observeBluetoothStatus()
        startCommandProcessor()
    }

    private fun startCommandProcessor() {
        scope.launch {
            for (request in commandQueue) {
                processCommandRequest(request)
            }
        }
    }

    private suspend fun processCommandRequest(request: CommandRequest) {
        var currentRetry = request.retryCount
        val cmdStr = "CMD:${request.command.command}\n"
        
        while (currentRetry < 3) {
            stateManager.updateState { it.copy(queueSize = 1, retryCount = currentRetry) }
            
            // Start collecting before writing to avoid race conditions
            val ackReceived = scope.async {
                commandAcks.first { it == request.command.command || it == "BUTTON_${request.command.command}" || it == "PONG" || it.startsWith("VERSION") }
            }
            
            bluetoothManager.write(cmdStr)
            
            val success = withTimeoutOrNull(2500) {
                ackReceived.await()
            }
            
            if (success != null) {
                stateManager.updateState { it.copy(queueSize = 0, retryCount = 0) }
                logAction(request.command.name + " (SUCCESS)")
                return
            }
            
            currentRetry++
            delay(1000)
        }
        
        // Failed after 3 retries
        stateManager.updateState { it.copy(queueSize = 0, lastError = "House Unit Not Responding: ${request.command.name}") }
        scope.launch { logEvent("ERROR", "Command Failed: ${request.command.name} after 3 retries", severity = "CRITICAL") }
        
        // Trigger reconnection if appropriate
        if (stateManager.state.value.bluetooth == BluetoothStatus.CONNECTED) {
            bluetoothManager.disconnect()
            val addr = stateManager.state.value.lastDeviceAddress
            if (addr.isNotEmpty()) {
                delay(2000)
                bluetoothManager.connect(addr)
            }
        }
    }

    private fun observeBluetoothData() {
        bluetoothManager.setOnDataReceivedListener { message ->
            handleIncomingData(message)
        }
    }

    private fun observeBluetoothStatus() {
        scope.launch {
            bluetoothManager.status.collectLatest { status ->
                stateManager.updateState { it.copy(bluetooth = status) }
                // syncHardwareState will be triggered by HOUSE_UNIT_READY/ONLINE packets
            }
        }
    }

    private fun handleIncomingData(rawMessage: String) {
        val data = rawMessage.trim()
        if (data.isEmpty()) return

        when {
            data.startsWith("STATUS:") -> handleStatusPacket(data.substring(7))
            data.startsWith("ACK:") -> {
                val cmd = data.substring(4)
                scope.launch { _commandAcks.emit(cmd) }
                stateManager.updateState { it.copy(lastAck = cmd) }
                if (cmd.startsWith("BUTTON_")) {
                    scope.launch { logEvent("HARDWARE", "Button Ack: ${cmd.substring(7)}", source = "HARDWARE", severity = "INFO") }
                }
            }
            data.startsWith("EVENT:") -> {
                val event = data.substring(6)
                stateManager.updateState { it.copy(lastEvent = event) }
                scope.launch { logEvent("HARDWARE_EVENT", event, source = "HARDWARE", severity = "WARNING") }
                handleHardwareEvent(event)
            }
            data.startsWith("VERSION:") -> {
                val version = data.substring(8)
                stateManager.updateState { it.copy(firmwareVersion = version) }
                scope.launch { _commandAcks.emit("VERSION") }
            }
            data.startsWith("MODEL:") -> {
                stateManager.updateState { it.copy(arduinoModel = data.substring(6)) }
            }
            data.startsWith("PROTOCOL:") -> {
                stateManager.updateState { it.copy(protocolVersion = data.substring(9)) }
            }
            data.startsWith("ERROR:") -> {
                val error = data.substring(6)
                stateManager.updateState { it.copy(lastError = error) }
                scope.launch { 
                    _commandAcks.emit("ERROR")
                    logEvent("ERROR", error, severity = "ERROR") 
                }
            }
            data == "PONG" -> {
                stateManager.updateState { it.copy(lastCommunicationTime = System.currentTimeMillis()) }
                scope.launch { _commandAcks.emit("PONG") }
            }
            data == "HOUSE_UNIT_READY" || data == "HOUSE_UNIT_ONLINE" || data == "HOUSE_UNIT_RECONNECTED" -> {
                stateManager.updateState { it.copy(houseUnitOnline = true) }
                scope.launch { 
                    _commandAcks.emit(data)
                    logEvent("SYSTEM", data, severity = "INFO") 
                }
                syncHardwareState()
            }
            else -> handleLegacyOrSensorData(data)
        }
    }

    private fun handleStatusPacket(packet: String) {
        // Format: POWER=1,ARMED=1,MUTED=0,AUTOSCAN=1,STATE=READY,DISTANCE=35,REDLED=0,GREENLED=1,BUZZER=0,VERSION=7.0
        try {
            val pairs = packet.split(",")
            stateManager.updateState { current ->
                var next = current
                pairs.forEach { pair ->
                    val (key, value) = pair.split("=")
                    next = when (key) {
                        "POWER" -> next.copy(power = if (value == "1") PowerState.ON else PowerState.OFF)
                        "ARMED" -> next.copy(security = if (value == "1") SecurityMode.ARMED else SecurityMode.DISARMED)
                        "MUTED" -> next.copy(buzzer = value == "0") // buzzer true means SOUND ON
                        "AUTOSCAN" -> next.copy(autoScan = value == "1")
                        "STATE" -> next.copy(threat = parseThreatLevel(value))
                        "DISTANCE" -> next.copy(distance = value.toInt())
                        "REDLED" -> next.copy(redLed = value == "1")
                        "GREENLED" -> next.copy(greenLed = value == "1")
                        "BUZZER" -> next.copy(alarmActive = value == "1") // alarmActive means physical buzzer is sounding
                        "VERSION" -> next.copy(firmwareVersion = value)
                        else -> next
                    }
                }
                next.copy(lastCommunicationTime = System.currentTimeMillis(), houseUnitOnline = true)
            }
        } catch (e: Exception) {
            scope.launch { logEvent("ERROR", "Malformed Status Packet: $packet", severity = "ERROR") }
        }
    }

    private fun handleHardwareEvent(event: String) {
        when(event) {
            "BUTTON_POWER" -> {} // Hardware already updated its state and will send STATUS
            "BUTTON_ARM" -> {}
            "BUTTON_DISARM" -> {}
            "BUTTON_SILENCE" -> {}
        }
    }

    private fun parseThreatLevel(value: String): ThreatLevel = when(value) {
        "READY" -> ThreatLevel.READY
        "WARNING" -> ThreatLevel.WARNING
        "INTRUDER" -> ThreatLevel.INTRUDER
        "FINAL" -> ThreatLevel.FINAL
        else -> ThreatLevel.CLEAR
    }

    private fun handleLegacyOrSensorData(data: String) {
        try {
            val json = JSONObject(data)
            if (json.optString("event") == "sensor_update") {
                handleSensorData(
                    dist = json.optInt("distance"),
                    angle = json.optInt("angle"),
                    threatStr = json.optString("threat"),
                    temp = json.optDouble("temperature").toFloat(),
                    batt = json.optInt("battery")
                )
                return
            }
        } catch (e: Exception) {}

        if (data.startsWith("SENSOR:")) {
            val parts = data.substringAfter("SENSOR:").split(",")
            if (parts.size >= 3) {
                handleSensorData(parts[0].toIntOrNull() ?: 0, parts[1].toIntOrNull() ?: 0, parts[2])
            }
        }
    }

    fun armSystem() = executeCommand(SystemCommand.ARM)
    fun disarmSystem() = executeCommand(SystemCommand.DISARM)
    fun silenceAlarm() = executeCommand(SystemCommand.SILENCE)
    fun unmuteSystem() = executeCommand(SystemCommand.SOUND)
    fun powerOff() = executeCommand(SystemCommand.POWER_OFF)
    fun autoScan() = executeCommand(SystemCommand.AUTO_SCAN)
    fun stopScan() = executeCommand(SystemCommand.STOP_SCAN)
    fun resetSystem() = executeCommand(SystemCommand.RESET)
    fun emergency() = executeCommand(SystemCommand.EMERGENCY)
    fun lightOn() = executeCommand(SystemCommand.LIGHT_ON)
    fun lightOff() = executeCommand(SystemCommand.LIGHT_OFF)
    fun ping() = executeCommand(SystemCommand.PING)
    fun getVersion() = executeCommand(SystemCommand.VERSION)

    fun executeCommand(command: SystemCommand, originHardware: Boolean = false) {
        if (originHardware) return
        scope.launch {
            commandQueue.send(CommandRequest(command))
        }
    }

    private fun syncHardwareState() {
        getVersion()
        ping()
    }

    fun handleSensorData(dist: Int, angle: Int, threatStr: String, temp: Float? = null, batt: Int? = null) {
        val threat = when (threatStr.uppercase()) {
            "WARNING" -> ThreatLevel.WARNING
            "INTRUDER" -> ThreatLevel.INTRUDER
            "FINAL", "FINAL_WARNING" -> ThreatLevel.FINAL
            "READY" -> ThreatLevel.READY
            else -> ThreatLevel.CLEAR
        }

        stateManager.updateState { current ->
            if (!current.sensorAiEnabled && threat != ThreatLevel.FINAL) {
                return@updateState current.copy(distance = dist, angle = angle, lastCommunicationTime = System.currentTimeMillis())
            }

            var nextBuzzer = current.buzzer
            var nextAlarm = current.alarmActive
            
            if (current.security == SecurityMode.ARMED) {
                if (threat == ThreatLevel.INTRUDER || threat == ThreatLevel.FINAL) {
                    if (!current.buzzer) {
                        nextBuzzer = true
                        scope.launch { logEvent("SECURITY", "Breach at ${dist}cm", threat.name, severity = "CRITICAL") }
                    }
                    if (threat == ThreatLevel.FINAL) nextAlarm = true
                }
            }

            val rad = Math.toRadians(angle.toDouble())
            current.copy(
                distance = dist, angle = angle,
                x = (dist * kotlin.math.cos(rad)).toInt(),
                y = (dist * kotlin.math.sin(rad)).toInt(),
                threat = threat, buzzer = nextBuzzer, alarmActive = nextAlarm,
                temperature = temp ?: current.temperature,
                batteryLevel = batt ?: current.batteryLevel,
                lastCommunicationTime = System.currentTimeMillis()
            )
        }
        
        syncHardwareBuzzer()
        
        // V7.1: Only push sensor state to House Unit if threat level is critical AND changed
        if (threat != ThreatLevel.CLEAR) {
            val currentState = stateManager.state.value
            if (currentState.threat != threat || currentState.distance != dist) {
                scope.launch {
                    bluetoothManager.write("STATE:${threat.name},$dist\n")
                }
            }
        }
    }

    fun toggleSensorAI() {
        stateManager.updateState { it.copy(sensorAiEnabled = !it.sensorAiEnabled) }
    }

    fun toggleEventVoice() {
        stateManager.updateState { it.copy(eventVoiceEnabled = !it.eventVoiceEnabled) }
    }

    private fun syncHardwareBuzzer() {
        scope.launch {
            val isBuzzerOn = stateManager.state.value.buzzer
            bluetoothManager.write("BUZZER:${if (isBuzzerOn) "ON" else "OFF"}\n")
        }
    }

    private fun logAction(action: String) {
        scope.launch { logEvent("ACTION", "Command processed: $action", source = "APP", severity = "INFO") }
    }

    private suspend fun logEvent(
        type: String,
        desc: String,
        threat: String = "CLEAR",
        source: String = "APP",
        severity: String = "INFO"
    ) {
        eventDao.insertEvent(EventEntity(
            timestamp = System.currentTimeMillis(),
            type = type, description = desc,
            threatLevel = threat, distance = stateManager.state.value.distance,
            source = source, severity = severity
        ))
    }

    fun connect(address: String) {
        stateManager.updateState { it.copy(lastDeviceAddress = address) }
        bluetoothManager.connect(address)
    }

    fun disconnect() = bluetoothManager.disconnect()
}
