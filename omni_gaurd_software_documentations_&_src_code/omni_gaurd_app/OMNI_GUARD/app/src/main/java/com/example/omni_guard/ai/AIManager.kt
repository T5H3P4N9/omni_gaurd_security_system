package com.example.omni_guard.ai

import com.example.omni_guard.data.*
import com.example.omni_guard.database.EventDao
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AIManager @Inject constructor(
    private val eventDao: EventDao,
    private val openAIProvider: OpenAIProvider
) {
    suspend fun chat(status: SystemStatus, userText: String): String {
        val recentEvents = eventDao.getRecentEvents().first()
        val history = recentEvents.take(5).joinToString("\\n") { 
            "[${it.threatLevel}] ${it.description}" 
        }

        return try {
            openAIProvider.chat(status, history, userText)
        } catch (e: Exception) {
            "Analysis Error: ${e.localizedMessage}"
        }
    }
}
