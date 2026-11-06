package com.example.omni_guard.ui

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.speech.tts.TextToSpeech
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.omni_guard.data.*
import com.example.omni_guard.database.EventDao
import com.example.omni_guard.database.EventEntity
import com.example.omni_guard.repository.SecurityRepository
import com.example.omni_guard.data.SystemStateManager
import com.example.omni_guard.data.SystemCommand
import com.example.omni_guard.data.OmniCommand
import com.example.omni_guard.utils.Constants
import com.example.omni_guard.ai.AIManager
import com.example.omni_guard.ai.VoiceCommandInterpreter
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.*
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(
    private val securityRepository: SecurityRepository,
    private val stateManager: SystemStateManager,
    private val eventDao: EventDao,
    private val aiManager: AIManager,
    private val commandInterpreter: VoiceCommandInterpreter,
    @ApplicationContext private val context: Context
) : ViewModel(), TextToSpeech.OnInitListener {
    
    val systemStatus: StateFlow<SystemStatus> = stateManager.state
    val events: StateFlow<List<EventEntity>> = eventDao.getRecentEvents()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _aiResponse = MutableStateFlow("")
    val aiResponse: StateFlow<String> = _aiResponse.asStateFlow()

    private var tts: TextToSpeech? = TextToSpeech(context, this)
    private val sharedPrefs = context.getSharedPreferences(Constants.PREFS_NAME, Context.MODE_PRIVATE)

    init {
        // Observe status changes for intelligent voice alerts
        viewModelScope.launch {
            systemStatus.map { it.threat }.distinctUntilChanged().collect { threat ->
                handleThreatVoicePrompt(threat)
            }
        }
        
        viewModelScope.launch {
            systemStatus.map { it.security }.distinctUntilChanged().collect { mode ->
                if (mode == SecurityMode.ARMED) speak("Security system armed.")
                else if (mode == SecurityMode.DISARMED) speak("Security system disarmed.")
            }
        }

        viewModelScope.launch {
            systemStatus.map { it.power }.distinctUntilChanged().collect { power ->
                if (power == PowerState.ON) speak("OMNI_GUARD system online.")
            }
        }
    }

    private fun handleThreatVoicePrompt(threat: ThreatLevel) {
        if (!systemStatus.value.eventVoiceEnabled) return

        when(threat) {
            ThreatLevel.WARNING -> speak("Warning. Motion detected.")
            ThreatLevel.INTRUDER -> speak("Intruder detected.")
            ThreatLevel.FINAL -> speak("Critical security breach detected.")
            ThreatLevel.READY -> speak("Area secure.")
            else -> {}
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            tts?.setPitch(0.8f)
            tts?.setSpeechRate(0.9f)
        }
    }

    private fun speak(message: String) {
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
    }

    fun connectToDevice(device: BluetoothDevice) {
        sharedPrefs.edit().putString(Constants.PREF_LAST_DEVICE_ADDRESS, device.address).apply()
        securityRepository.connect(device.address)
    }

    fun disconnect() {
        securityRepository.disconnect()
    }

    fun sendCommand(cmd: SystemCommand) {
        securityRepository.executeCommand(cmd)
    }

    fun toggleSensorAI() {
        securityRepository.toggleSensorAI()
    }

    fun toggleEventVoice() {
        securityRepository.toggleEventVoice()
    }

    fun askAI(userText: String = "Brief me on the system status.") {
        viewModelScope.launch {
            // 1. Intent Detection
            val command = commandInterpreter.interpret(userText)
            
            // 2. Execute Structured Command if known
            var commandExecuted = false
            if (command != OmniCommand.Unknown) {
                if (command == OmniCommand.Reconnect) {
                    sharedPrefs.getString(Constants.PREF_LAST_DEVICE_ADDRESS, null)?.let {
                        securityRepository.connect(it)
                    }
                    speak("Attempting to reconnect to House Unit.")
                    return@launch
                }
                executeOmniCommand(command)
                commandExecuted = true
            }

            // 3. Wait for ACK if a command was sent (V7.1 Reliability)
            if (commandExecuted) {
                _aiResponse.value = "Executing command: ${command::class.simpleName}..."
                val success = withTimeoutOrNull(3000) {
                    securityRepository.commandAcks.first { ack ->
                        isMatchingAck(ack, command)
                    }
                }
                if (success == null) {
                    val errorMsg = "Hardware is not responding to the ${command::class.simpleName} command."
                    _aiResponse.value = errorMsg
                    speak(errorMsg)
                    return@launch
                }
            }

            // 4. Conversation
            _aiResponse.value = "Processing request..."
            val contextText = if (commandExecuted) {
                "USER COMMAND DETECTED: ${command::class.simpleName}. I have confirmed execution with the House Unit. $userText"
            } else {
                userText
            }
            
            val response = aiManager.chat(systemStatus.value, contextText)
            _aiResponse.value = response
            speak(response)
        }
    }

    private fun isMatchingAck(ack: String, command: OmniCommand): Boolean {
        val cmdStr = when(command) {
            OmniCommand.Arm -> "ARM"
            OmniCommand.Disarm -> "DISARM"
            OmniCommand.Silence -> "SILENCE"
            OmniCommand.Emergency -> "EMERGENCY"
            OmniCommand.PowerOff -> "POWER"
            OmniCommand.AutoScan -> "AUTO_SCAN"
            OmniCommand.StopScan -> "STOP_SCAN"
            OmniCommand.Reset -> "RESET"
            OmniCommand.Ping -> "PONG"
            OmniCommand.Version -> "VERSION"
            OmniCommand.Status -> "STATUS"
            else -> ""
        }
        return ack.contains(cmdStr) || (command == OmniCommand.Ping && ack == "PONG")
    }

    private fun executeOmniCommand(command: OmniCommand) {
        when (command) {
            OmniCommand.Arm -> securityRepository.armSystem()
            OmniCommand.Disarm -> securityRepository.disarmSystem()
            OmniCommand.Silence -> securityRepository.silenceAlarm()
            OmniCommand.Emergency -> securityRepository.emergency()
            OmniCommand.PowerOff -> securityRepository.powerOff()
            OmniCommand.AutoScan -> securityRepository.autoScan()
            OmniCommand.StopScan -> securityRepository.stopScan()
            OmniCommand.Reset -> securityRepository.resetSystem()
            OmniCommand.LightOn -> securityRepository.lightOn()
            OmniCommand.LightOff -> securityRepository.lightOff()
            OmniCommand.Ping -> securityRepository.ping()
            OmniCommand.Version -> securityRepository.getVersion()
            OmniCommand.Status -> securityRepository.ping() // Ping triggers status update indirectly or use explicit status if added
            OmniCommand.Reconnect -> {} // Handled above
            OmniCommand.Unknown -> {}
        }
    }

    fun clearLogs() {
        viewModelScope.launch {
            eventDao.clearAllEvents()
        }
    }

    fun updateSettings(flaskUrl: String, interval: Long, darkMode: Boolean, vibration: Boolean, autoConnect: Boolean) {
        sharedPrefs.edit().apply {
            putString(Constants.PREF_FLASK_URL, flaskUrl)
            putLong(Constants.PREF_RECONNECT_INTERVAL, interval)
            putBoolean(Constants.PREF_DARK_MODE, darkMode)
            putBoolean(Constants.PREF_VIBRATION_ON, vibration)
            putBoolean(Constants.PREF_AUTO_CONNECT, autoConnect)
            apply()
        }
    }

    fun getSettings() = mapOf(
        Constants.PREF_FLASK_URL to (sharedPrefs.getString(Constants.PREF_FLASK_URL, Constants.DEFAULT_FLASK_URL) ?: Constants.DEFAULT_FLASK_URL),
        Constants.PREF_RECONNECT_INTERVAL to sharedPrefs.getLong(Constants.PREF_RECONNECT_INTERVAL, Constants.DEFAULT_RECONNECT_INTERVAL),
        Constants.PREF_DARK_MODE to sharedPrefs.getBoolean(Constants.PREF_DARK_MODE, false),
        Constants.PREF_VIBRATION_ON to sharedPrefs.getBoolean(Constants.PREF_VIBRATION_ON, true),
        Constants.PREF_AUTO_CONNECT to sharedPrefs.getBoolean(Constants.PREF_AUTO_CONNECT, true)
    )

    override fun onCleared() {
        tts?.stop()
        tts?.shutdown()
        super.onCleared()
    }
}
