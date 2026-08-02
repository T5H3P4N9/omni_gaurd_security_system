package com.example.omni_guard.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_guard.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.systemStatus.collectAsState()
    
    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            kotlinx.coroutines.delay(1000)
        }
    }

    Scaffold(
        topBar = {
            Surface(color = Color(0xFF0A111A), modifier = Modifier.fillMaxWidth()) {
                Column {
                    Row(
                        modifier = Modifier.padding(16.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween, 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = onNavigateBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = CyberCyan)
                            }
                            Column {
                                Text("SYSTEM DIAGNOSTICS", color = CyberCyan, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
                                Text("V7.1 REAL-TIME TELEMETRY", color = CyberTextDim, fontSize = 9.sp)
                            }
                        }
                        Text(time, color = CyberCyan, fontSize = 16.sp)
                    }
                    HorizontalDivider(color = CyberCyan, thickness = 1.dp)
                }
            }
        },
        containerColor = CyberDarkBG
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Panel 1: Hardware Core
            item {
                DiagPanel(title = "⚙️ HARDWARE CORE") {
                    val items = listOf(
                        "Model" to state.arduinoModel,
                        "Firmware" to state.firmwareVersion,
                        "Protocol" to state.protocolVersion,
                        "House Unit" to if (state.houseUnitOnline) "ONLINE" else "OFFLINE"
                    )
                    DiagGrid(items)
                }
            }

            // Panel 2: Network & Comms
            item {
                DiagPanel(title = "📡 NETWORK & COMMUNICATION") {
                    val items = listOf(
                        "BT State" to state.bluetooth.name,
                        "Quality" to "${state.connectionQuality}%",
                        "Latency" to "${state.bluetoothLatency}ms",
                        "Retry Count" to "${state.retryCount}",
                        "Sent" to "${state.sentPackets}",
                        "Lost" to "${state.lostPackets}"
                    )
                    DiagGrid(items)
                }
            }

            // Panel 3: Engine Metrics
            item {
                DiagPanel(title = "🧠 INTELLIGENCE ENGINE") {
                    val items = listOf(
                        "AI Engine" to "GPT-4o-mini",
                        "Local Server" to "ACTIVE (8000)",
                        "Sensor Unit" to if (state.distance > 0) "ACTIVE" else "WAITING",
                        "Camera" to if (state.cameraActive) "STREAMING" else "STANDBY",
                        "Queue" to "${state.queueSize} pending",
                        "Last Cmd" to state.lastArduinoCmd
                    )
                    DiagGrid(items)
                }
            }

            // Panel 4: System Resources
            item {
                DiagPanel(title = "📊 SYSTEM RESOURCES") {
                    val items = listOf(
                        "App Memory" to "${Runtime.getRuntime().totalMemory() / 1024 / 1024} MB",
                        "Database" to "ROOM ONLINE",
                        "Logs" to "ACTIVE",
                        "Uptime" to "${state.uptimeSeconds}s"
                    )
                    DiagGrid(items)
                }
            }
            
            // Panel 5: Errors & Faults
            item {
                DiagPanel(title = "⚠️ CRITICAL FAULTS") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(0.3f), RoundedCornerShape(4.dp))
                            .padding(12.dp)
                    ) {
                        Text("LAST ERROR REPORTED:", color = CyberTextDim, fontSize = 8.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            state.lastError.ifEmpty { "SYSTEM HEALTHY - NO FAULTS DETECTED" },
                            color = if(state.lastError.isEmpty()) CyberCyan else Color.Red,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagPanel(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(CyberPanelBG, RoundedCornerShape(8.dp))
            .border(1.dp, CyberCyan.copy(0.1f), RoundedCornerShape(8.dp))
            .padding(16.dp)
    ) {
        Text(title, color = CyberTextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
fun DiagGrid(items: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in items.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DiagStatusItem(items[i].first, items[i].second, Modifier.weight(1f))
                if (i + 1 < items.size) {
                    DiagStatusItem(items[i+1].first, items[i+1].second, Modifier.weight(1f))
                } else {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
fun DiagStatusItem(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(0.2f), RoundedCornerShape(4.dp))
            .drawBehind { drawRect(CyberCyan, Offset.Zero, Size(2.dp.toPx(), size.height)) }
            .padding(8.dp)
    ) {
        Column {
            Text(label.uppercase(), color = CyberTextDim, fontSize = 8.sp)
            Text(
                value, 
                color = when(value) {
                    "OFFLINE", "STANDBY" -> Color.Gray
                    "WAITING" -> CyberYellow
                    else -> CyberCyan
                }, 
                fontSize = 11.sp, 
                fontWeight = FontWeight.Bold
            )
        }
    }
}

