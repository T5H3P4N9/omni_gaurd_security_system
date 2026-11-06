package com.example.omni_guard.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.omni_guard.utils.Constants

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onNavigateBack: () -> Unit
) {
    val settings = viewModel.getSettings()
    
    var flaskUrl by remember { mutableStateOf(settings[Constants.PREF_FLASK_URL] as String) }
    var interval by remember { mutableStateOf((settings[Constants.PREF_RECONNECT_INTERVAL] as Long).toString()) }
    var darkMode by remember { mutableStateOf(settings[Constants.PREF_DARK_MODE] as Boolean) }
    var vibration by remember { mutableStateOf(settings[Constants.PREF_VIBRATION_ON] as Boolean) }
    var autoConnect by remember { mutableStateOf(settings[Constants.PREF_AUTO_CONNECT] as Boolean) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            OutlinedTextField(
                value = flaskUrl,
                onValueChange = { flaskUrl = it },
                label = { Text("Flask Address") },
                modifier = Modifier.fillMaxWidth()
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            OutlinedTextField(
                value = interval,
                onValueChange = { interval = it },
                label = { Text("Reconnect Interval (ms)") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Dark Mode")
                Switch(checked = darkMode, onCheckedChange = { darkMode = it })
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Vibration On/Off")
                Switch(checked = vibration, onCheckedChange = { vibration = it })
            }
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Auto Connect")
                Switch(checked = autoConnect, onCheckedChange = { autoConnect = it })
            }

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { 
                    viewModel.updateSettings(
                        flaskUrl, 
                        interval.toLongOrNull() ?: 5000L, 
                        darkMode, 
                        vibration, 
                        autoConnect
                    )
                    onNavigateBack()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Save Settings")
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.clearLogs() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Clear Logs")
            }
        }
    }
}
