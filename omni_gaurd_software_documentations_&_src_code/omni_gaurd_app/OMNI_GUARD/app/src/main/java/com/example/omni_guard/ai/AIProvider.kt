package com.example.omni_guard.ai

import com.example.omni_guard.data.SystemStatus

interface AIProvider {
    suspend fun chat(status: SystemStatus, history: String, userText: String): String
}
