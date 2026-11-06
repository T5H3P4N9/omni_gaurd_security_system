package com.example.omni_guard.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.util.Log
import com.example.omni_guard.data.BluetoothStatus
import com.example.omni_guard.data.SystemStateManager
import com.example.omni_guard.utils.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OmniBluetoothManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val stateManager: SystemStateManager
) {
    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager.adapter

    private val _status = MutableStateFlow(BluetoothStatus.DISCONNECTED)
    val status = _status.asStateFlow()

    private val _pairedDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val pairedDevices = _pairedDevices.asStateFlow()

    private var socket: BluetoothSocket? = null
    
    @Volatile
    private var lastConnectedAddress: String? = null
    
    @Volatile
    private var connectionJob: Job? = null
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val socketMutex = Mutex()
    private val writeMutex = Mutex()

    private var onDataReceived: ((String) -> Unit)? = null
    
    // V3.0 Diagnostics
    private var sentPackets = 0
    private var lostPackets = 0

    fun setOnDataReceivedListener(listener: (String) -> Unit) {
        onDataReceived = listener
    }

    @SuppressLint("MissingPermission")
    fun updatePairedDevices() {
        try {
            bluetoothAdapter?.let {
                val devices = it.bondedDevices.toList()
                _pairedDevices.value = devices
            }
        } catch (e: SecurityException) {
            Log.e("BluetoothManager", "Missing permissions: ${e.message}")
        }
    }

    @SuppressLint("MissingPermission")
    fun connect(address: String) {
        val currentJob = connectionJob
        if (address == lastConnectedAddress && currentJob != null && currentJob.isActive) {
            return
        }
        lastConnectedAddress = address
        if (currentJob == null || !currentJob.isActive) {
            startConnectionLoop()
        }
    }

    @SuppressLint("MissingPermission")
    private fun startConnectionLoop() {
        connectionJob = scope.launch {
            var isFirstAttempt = true
            while (isActive) {
                val address = lastConnectedAddress ?: break
                _status.value = if (isFirstAttempt) BluetoothStatus.CONNECTING else BluetoothStatus.RECONNECTING

                val success = try {
                    socketMutex.withLock {
                        internalCloseSocket()
                        val device = bluetoothAdapter?.getRemoteDevice(address)
                        if (device == null) {
                            false
                        } else {
                            socket = device.createRfcommSocketToServiceRecord(UUID.fromString(Constants.BLUETOOTH_UUID))
                            bluetoothAdapter?.cancelDiscovery()
                            val startTime = System.currentTimeMillis()
                            socket?.connect()
                            val latency = System.currentTimeMillis() - startTime
                            stateManager.updateState { it.copy(bluetoothLatency = latency) }
                            true
                        }
                    }
                } catch (e: Exception) {
                    false
                }

                if (success && socket?.isConnected == true) {
                    _status.value = BluetoothStatus.CONNECTED
                    isFirstAttempt = false 
                    stateManager.updateState { it.copy(connectionQuality = 100) }
                    try {
                        val inputStream = socket!!.inputStream
                        val buffer = StringBuilder()
                        while (isActive && lastConnectedAddress == address) {
                            val byte = withContext(Dispatchers.IO) { inputStream.read() }
                            if (byte == -1) break
                            val char = byte.toChar()
                            if (char == '\n' || char == '\r') {
                                if (buffer.isNotEmpty()) {
                                    val msg = buffer.toString()
                                    onDataReceived?.invoke(msg)
                                    buffer.setLength(0)
                                }
                            } else {
                                buffer.append(char)
                            }
                        }
                    } catch (e: IOException) {
                        Log.e("BluetoothManager", "Link broken: ${e.message}")
                    }
                }

                socketMutex.withLock { internalCloseSocket() }
                if (lastConnectedAddress == null) break
                _status.value = BluetoothStatus.CONNECTION_LOST
                stateManager.updateState { it.copy(connectionQuality = 0) }
                delay(Constants.DEFAULT_RECONNECT_INTERVAL)
            }
            withContext(NonCancellable) {
                _status.value = BluetoothStatus.DISCONNECTED
            }
        }
    }

    private fun internalCloseSocket() {
        try {
            socket?.close()
            socket = null
        } catch (e: IOException) {}
    }

    fun disconnect() {
        lastConnectedAddress = null
        connectionJob?.cancel()
    }

    suspend fun write(data: String) {
        writeMutex.withLock {
            if (_status.value != BluetoothStatus.CONNECTED) {
                lostPackets++
                updateDiagnostics()
                return@withLock
            }
            val s = socketMutex.withLock { socket } ?: return@withLock
            try {
                val message = if (data.endsWith("\n")) data else "$data\n"
                val outputStream = s.outputStream
                
                // V7.1: Add a small delay between writes to prevent buffer overflow on Arduino
                delay(50) 
                
                val startTime = System.currentTimeMillis()
                withContext(Dispatchers.IO) {
                    outputStream.write(message.toByteArray())
                    outputStream.flush()
                }
                val latency = System.currentTimeMillis() - startTime
                sentPackets++
                stateManager.updateState { it.copy(bluetoothLatency = latency, lastArduinoCmd = message.trim()) }
                updateDiagnostics()
            } catch (e: Exception) {
                lostPackets++
                _status.value = BluetoothStatus.CONNECTION_LOST
                socketMutex.withLock { internalCloseSocket() }
                updateDiagnostics()
            }
        }
    }

    private fun updateDiagnostics() {
        val total = sentPackets + lostPackets
        val loss = if (total > 0) (lostPackets * 100 / total) else 0
        val quality = 100 - loss
        stateManager.updateState { it.copy(
            packetLoss = loss,
            connectionQuality = quality,
            sentPackets = sentPackets,
            lostPackets = lostPackets
        ) }
    }
}
