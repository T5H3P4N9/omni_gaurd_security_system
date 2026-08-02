package com.example.omni_guard.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.omni_guard.data.*
import com.example.omni_guard.ui.theme.*
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.cos
import kotlin.math.sin

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel: MainViewModel,
    onNavigateToSettings: () -> Unit,
    onNavigateToDiagnostics: () -> Unit
) {
    val state by viewModel.systemStatus.collectAsState()
    val events by viewModel.events.collectAsState()
    val aiResponse by viewModel.aiResponse.collectAsState()

    var time by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        while (true) {
            time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
            delay(1000)
        }
    }

    val isEmergency = state.threat == ThreatLevel.FINAL
    val infiniteTransition = rememberInfiniteTransition(label = "emergency")
    val pulseColor by infiniteTransition.animateColor(
        initialValue = CyberDarkBG,
        targetValue = if (isEmergency) Color(0xFF330000) else CyberDarkBG,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    var userText by remember { mutableStateOf("") }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val data = result.data
            val results = data?.getStringArrayListExtra(android.speech.RecognizerIntent.EXTRA_RESULTS)
            val spokenText = results?.get(0) ?: ""
            if (spokenText.isNotEmpty()) {
                viewModel.askAI(spokenText)
            }
        }
    }

    Scaffold(
        topBar = { Header(time, onNavigateToSettings, onNavigateToDiagnostics) },
        containerColor = pulseColor
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Status & AI Toggles
                item {
                    CyberPanel(title = "📊 SYSTEM HEALTH") {
                        StatusGrid(state)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            CyberButton(
                                text = if (state.sensorAiEnabled) "SENSOR AI: ON" else "SENSOR AI: OFF",
                                color = CyberCyan,
                                modifier = Modifier.weight(1f)
                            ) { viewModel.toggleSensorAI() }
                            
                            CyberButton(
                                text = if (state.eventVoiceEnabled) "VOICE: ON" else "VOICE: OFF",
                                color = CyberCyan,
                                modifier = Modifier.weight(1f)
                            ) { viewModel.toggleEventVoice() }
                        }
                    }
                }

                // AI Intelligence
                item {
                    CyberPanel(title = "🎤 OMNI INTERFACE") {
                        Column {
                            if (aiResponse.isNotEmpty()) {
                                Text(
                                    aiResponse,
                                    color = CyberCyan,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CyberCyan.copy(alpha = 0.05f))
                                        .border(1.dp, CyberCyan.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                        .padding(10.dp)
                                )
                                Spacer(Modifier.height(10.dp))
                            }
                            CyberButton(
                                text = "ACTIVATE VOICE COMMAND", 
                                icon = Icons.Default.Mic,
                                color = CyberCyan,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                val intent = android.content.Intent(android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                    putExtra(android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL, android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                    putExtra(android.speech.RecognizerIntent.EXTRA_PROMPT, "Omni Guard System Listening...")
                                }
                                launcher.launch(intent)
                            }
                        }
                    }
                }

                // Security Control Panel
                item {
                    SecurityControlPanel(state = state, onCommand = { viewModel.sendCommand(it) })
                }

                // Tactical Radar
                item {
                    CyberPanel(title = "📡 TACTICAL RADAR") {
                        RadarScanner(state)
                        Spacer(Modifier.height(12.dp))
                        RadarMetrics(state)
                    }
                }

                // Camera Preview
                item {
                    CyberPanel(title = "📹 LIVE STREAM") {
                        CameraPlaceholder(state)
                    }
                }

                // Mission Log
                item {
                    CyberPanel(title = "📜 MISSION LOG") {
                        MissionLogHeader()
                        HorizontalDivider(color = CyberCyan.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 8.dp))
                        events.take(15).forEach { event ->
                            MissionLogItem(event)
                        }
                    }
                }
            }

            if (isEmergency) EmergencyOverlay()
        }
    }
}

@Composable
fun SecurityControlPanel(state: SystemStatus, onCommand: (SystemCommand) -> Unit) {
    CyberPanel(title = "🟢 SECURITY CONTROLS") {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberControlButton(
                    "ARM SYSTEM", 
                    CyberCyan, 
                    Modifier.weight(1f),
                    active = state.security == SecurityMode.ARMED
                ) { onCommand(SystemCommand.ARM) }
                
                CyberControlButton(
                    "DISARM", 
                    if (state.security == SecurityMode.DISARMED) CyberCyan else Color.Gray, 
                    Modifier.weight(1f),
                    active = state.security == SecurityMode.DISARMED
                ) { onCommand(SystemCommand.DISARM) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberControlButton(
                    if (state.buzzer) "SILENCE" else "SOUND ON", 
                    CyberCyan, 
                    Modifier.weight(1f),
                    active = !state.buzzer
                ) { 
                    if (state.buzzer) onCommand(SystemCommand.SILENCE) 
                    else onCommand(SystemCommand.SOUND)
                }
                
                CyberControlButton(
                    "POWER OFF", 
                    CyberCyan, 
                    Modifier.weight(1f),
                    active = state.power == PowerState.OFF
                ) { onCommand(SystemCommand.POWER_OFF) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberControlButton(
                    "AUTO SCAN", 
                    if (state.autoScan) CyberCyan else Color.Gray, 
                    Modifier.weight(1f),
                    active = state.autoScan
                ) { onCommand(SystemCommand.AUTO_SCAN) }
                
                CyberControlButton(
                    "STOP SCAN", 
                    if (!state.autoScan) CyberCyan else Color.Gray, 
                    Modifier.weight(1f),
                    active = !state.autoScan
                ) { onCommand(SystemCommand.STOP_SCAN) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CyberControlButton("RESET", CyberCyan, Modifier.weight(1f)) { onCommand(SystemCommand.RESET) }
                CyberControlButton("EMERGENCY", CyberCyan, Modifier.weight(1f)) { onCommand(SystemCommand.EMERGENCY) }
            }
        }
    }
}

@Composable
fun RadarScanner(state: SystemStatus) {
    val detections = remember { mutableStateListOf<Detection>() }
    
    LaunchedEffect(state.distance, state.angle) {
        if (state.distance in 1..80) {
            detections.removeAll { it.angle == state.angle }
            detections.add(Detection(state.distance, state.angle, state.threat, System.currentTimeMillis()))
        }
    }

    LaunchedEffect(Unit) {
        while(true) {
            val now = System.currentTimeMillis()
            detections.removeAll { now - it.timestamp > 4000 }
            delay(500)
        }
    }

    val infiniteTransition = rememberInfiniteTransition(label = "radar")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 180f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep"
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.size(280.dp, 180.dp).background(Color.Black)) {
            val center = Offset(size.width / 2, size.height - 10.dp.toPx())
            val radius = size.width / 2

            for (r in 1..4) {
                drawArc(
                    color = CyberCyan.copy(alpha = 0.3f),
                    startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    topLeft = Offset(center.x - radius * (r/4f), center.y - radius * (r/4f)),
                    size = Size(radius * 2 * (r/4f), radius * 2 * (r/4f)),
                    style = Stroke(1.dp.toPx())
                )
            }

            detections.forEach { det ->
                val alpha = 1f - ((System.currentTimeMillis() - det.timestamp) / 4000f)
                val targetRad = Math.toRadians(det.angle.toDouble())
                val d = (det.distance / 100f) * radius
                val tx = center.x + cos(Math.PI + targetRad) * d
                val ty = center.y + sin(Math.PI + targetRad) * d

                val color = when(det.threat) {
                    ThreatLevel.WARNING -> CyberYellow
                    ThreatLevel.INTRUDER -> CyberOrange
                    ThreatLevel.FINAL -> CyberRed
                    else -> Color.Green
                }.copy(alpha = alpha.coerceIn(0f, 1f))

                drawCircle(color, 4.dp.toPx(), Offset(tx.toFloat(), ty.toFloat()))
                drawCircle(color.copy(0.1f), 12.dp.toPx(), Offset(tx.toFloat(), ty.toFloat()))
            }
            
            val sweepRad = Math.toRadians(sweepAngle.toDouble())
            drawLine(
                CyberCyan.copy(0.5f), center,
                Offset((center.x + cos(Math.PI + sweepRad) * radius).toFloat(), (center.y + sin(Math.PI + sweepRad) * radius).toFloat()),
                2.dp.toPx()
            )
        }
    }
}

@Composable
fun StatusGrid(state: SystemStatus) {
    val items = listOf(
        "Power" to state.power.name,
        "Security" to state.security.name,
        "Threat" to state.threat.name,
        "Bluetooth" to state.bluetooth.name,
        "Quality" to "${state.connectionQuality}%",
        "Latency" to "${state.bluetoothLatency}ms",
        "Auto Scan" to if (state.autoScan) "ON" else "OFF",
        "Firmware" to state.firmwareVersion
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (i in items.indices step 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatusItem(items[i].first, items[i].second, Modifier.weight(1f))
                if (i + 1 < items.size) StatusItem(items[i+1].first, items[i+1].second, Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun StatusItem(label: String, value: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(Color.Black.copy(0.2f), RoundedCornerShape(4.dp))
            .drawBehind { drawRect(CyberCyan, Offset.Zero, Size(2.dp.toPx(), size.height)) }
            .padding(8.dp)
    ) {
        Column {
            Text(label.uppercase(), color = CyberTextDim, fontSize = 8.sp)
            Text(value, color = if(value == "ARMED" || value == "INTRUDER" || value == "FINAL") CyberRed else CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun RadarMetrics(state: SystemStatus) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
        MetricItem("DIST", "${state.distance}cm")
        MetricItem("ANG", "${state.angle}°")
        MetricItem("X", "${state.x}")
        MetricItem("Y", "${state.y}")
    }
}

@Composable
fun MetricItem(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = CyberTextDim, fontSize = 9.sp)
        Text(value, color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
    }
}

@Composable
fun CameraPlaceholder(state: SystemStatus) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp).background(Color.Black).border(1.dp, CyberCyan.copy(0.1f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Default.Videocam, null, tint = Color.DarkGray, modifier = Modifier.size(40.dp))
        Text(if(state.cameraActive) "LIVE STREAM ACTIVE" else "CAMERA STANDBY", color = CyberTextDim, fontSize = 9.sp, modifier = Modifier.align(Alignment.BottomCenter).padding(8.dp))
    }
}

@Composable
fun MissionLogItem(event: com.example.omni_guard.database.EventEntity) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(event.timestamp), color = CyberCyan, fontSize = 9.sp, modifier = Modifier.weight(0.8f))
        Text(event.source, color = CyberTextDim, fontSize = 8.sp, modifier = Modifier.weight(0.6f))
        Text(event.threatLevel, color = if(event.threatLevel == "FINAL") CyberRed else CyberCyan, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        Text(event.description, color = Color.White, fontSize = 9.sp, modifier = Modifier.weight(2f))
        Text("${event.distance}cm", color = CyberCyan, fontSize = 9.sp, modifier = Modifier.weight(0.5f))
    }
}

@Composable
fun CyberControlButton(
    text: String, 
    color: Color, 
    modifier: Modifier = Modifier, 
    active: Boolean = false,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick, 
        modifier = modifier, 
        border = androidx.compose.foundation.BorderStroke(
            if (active) 2.dp else 1.dp, 
            if (active) color else color.copy(0.4f)
        ),
        shape = RoundedCornerShape(4.dp), 
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = color,
            containerColor = if (active) color.copy(alpha = 0.1f) else Color.Transparent
        )
    ) { 
        Text(
            text, 
            fontSize = 8.sp, 
            fontWeight = if (active) FontWeight.Black else FontWeight.Bold,
            color = if (active) color else color.copy(alpha = 0.7f)
        ) 
    }
}

@Composable
fun CyberButton(text: String, color: Color, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick, modifier = modifier, shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(0.1f), contentColor = color),
        border = androidx.compose.foundation.BorderStroke(1.dp, color.copy(0.3f))
    ) {
        if (icon != null) { Icon(icon, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
        Text(text.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun MissionLogHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        Text("TIME", color = CyberTextDim, fontSize = 9.sp, modifier = Modifier.weight(0.8f))
        Text("SRC", color = CyberTextDim, fontSize = 9.sp, modifier = Modifier.weight(0.6f))
        Text("THREAT", color = CyberTextDim, fontSize = 9.sp, modifier = Modifier.weight(1f))
        Text("ACTION", color = CyberTextDim, fontSize = 9.sp, modifier = Modifier.weight(2f))
        Text("DIST", color = CyberTextDim, fontSize = 9.sp, modifier = Modifier.weight(0.5f))
    }
}

@Composable
fun Header(time: String, onNavigateToSettings: () -> Unit, onNavigateToDiagnostics: () -> Unit) {
    Surface(color = Color(0xFF0A111A), modifier = Modifier.fillMaxWidth()) {
        Column {
            Row(
                modifier = Modifier.padding(16.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("OMNI_GUARD SYSTEM", color = CyberCyan, fontSize = 18.sp, fontWeight = FontWeight.Black, letterSpacing = 2.sp)
                    Text("V7.1 OS", color = CyberTextDim, fontSize = 9.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(time, color = CyberCyan, fontSize = 16.sp)
                    IconButton(onClick = onNavigateToDiagnostics) { Icon(Icons.Default.Dns, null, tint = CyberCyan) }
                    IconButton(onClick = onNavigateToSettings) { Icon(Icons.Default.Settings, null, tint = CyberCyan) }
                }
            }
            HorizontalDivider(color = CyberCyan, thickness = 1.dp)
        }
    }
}

@Composable
fun CyberPanel(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().background(CyberPanelBG, RoundedCornerShape(8.dp))
            .border(1.dp, CyberCyan.copy(0.1f), RoundedCornerShape(8.dp)).padding(16.dp)
    ) {
        Text(title, color = CyberTextDim, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))
        content()
    }
}

@Composable
fun EmergencyOverlay() {
    var visible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) { while(true) { delay(500); visible = !visible } }
    Box(modifier = Modifier.fillMaxSize().background(if (visible) Color.Red.copy(0.15f) else Color.Transparent), contentAlignment = Alignment.Center) {
        Text("CRITICAL BREACH", color = Color.Red, fontSize = 32.sp, fontWeight = FontWeight.Black)
    }
}

data class Detection(val distance: Int, val angle: Int, val threat: ThreatLevel, val timestamp: Long)
