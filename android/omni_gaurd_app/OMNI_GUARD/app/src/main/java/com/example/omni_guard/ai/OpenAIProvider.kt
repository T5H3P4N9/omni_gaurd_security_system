package com.example.omni_guard.ai

import com.example.omni_guard.data.SystemStatus
import com.example.omni_guard.utils.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

class OpenAIProvider @Inject constructor() : AIProvider {
    private val client = OkHttpClient()

    override suspend fun chat(status: SystemStatus, history: String, userText: String): String = withContext(Dispatchers.IO) {
        val systemContext = """
            You are OMNI_GUARD SYSTEM, an advanced AI-powered security and monitoring OS.

            CURRENT SYSTEM STATE:
            - Power: ${status.power}
            - Security Mode: ${status.security}
            - Current Threat: ${status.threat}
            - Last Distance: ${status.distance} cm
            - Target Coords: X:${status.x} Y:${status.y}

            RECENT LOG ENTRIES:
            $history

            Rules:
            - Be concise, intelligent, and professional.
            - Respond like a futuristic OS (similar to JARVIS/HAL).
            - Prioritize security and system status reporting.
            - If the user asks about system state or history, use the data provided above.
            - Speak confidently. You are a man's voice.
            - The application logic handles hardware commands. If the user input mentions a command (ARM, DISARM, etc.), the command has ALREADY been executed. Simply confirm the action professionally.
            
            System Name: OMNI_GUARD SYSTEM
            Creator: Tshepang Oliver
        """.trimIndent()

        val json = JSONObject().apply {
            put("model", "gpt-4o-mini")
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", systemContext)
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", userText)
                })
            })
        }

        val request = Request.Builder()
            .url("https://api.openai.com/v1/chat/completions")
            .header("Authorization", "Bearer ${Constants.OPENAI_API_KEY}")
            .post(json.toString().toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@withContext "Cloud AI Offline (HTTP ${response.code})"
            val body = response.body?.string() ?: return@withContext "Empty response"
            JSONObject(body).getJSONArray("choices")
                .getJSONObject(0)
                .getJSONObject("message")
                .getString("content")
        }
    }
}
