package com.ecodrive.app.domain.ai.provider

import com.ecodrive.app.domain.ai.config.AiConfig

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.Content
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini-based implementation of AiProvider using hardcoded API key.
 */
@Singleton
class GeminiProvider @Inject constructor() : AiProvider {

    override val name: String = "GEMINI"

    companion object {
        private const val TAG = "GeminiProvider"
        private const val MODEL_FLASH = "gemini-2.0-flash"
    }

    private val modelCache = mutableMapOf<String, GenerativeModel>()
    private val cacheMutex = Mutex()

    private suspend fun getModel(config: GenerationConfig): GenerativeModel? {
        val apiKey = AiConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) return null
        
        return cacheMutex.withLock {
            val cacheKey = "${config.temperature}:${config.maxOutputTokens}"
            modelCache.getOrPut(cacheKey) {
                GenerativeModel(
                    modelName = MODEL_FLASH,
                    apiKey = apiKey,
                    generationConfig = config
                )
            }
        }
    }

    override suspend fun generateRealTimeTip(prompt: String): String? {
        return generate(prompt, 0.7f, 512)
    }

    override suspend fun generateTripInsight(prompt: String): String? {
        return generate(prompt, 0.4f, 1024)
    }

    override suspend fun generateWeeklyReport(prompt: String): String? {
        return generate(prompt, 0.4f, 1024)
    }

    override suspend fun generateAnalyticsSummary(prompt: String): String? {
        return generate(prompt, 0.4f, 1024)
    }

    override suspend fun generatePrediction(prompt: String): String? {
        // Lower temperature for more deterministic predictions
        return generate(prompt, 0.2f, 256)
    }

    /**
     * Native multi-turn chat using Gemini's Chat API.
     * Provides true conversational context rather than concatenated prompts.
     */
    override suspend fun generateConversationalResponse(
        messages: List<Pair<String, Boolean>>,
        systemPrompt: String,
    ): String? {
        return try {
            val model = getModel(generationConfig {
                temperature = 0.5f
                maxOutputTokens = 1024
            }) ?: return null

            // Build Gemini chat history from all messages except the last (current user message)
            val history = messages.dropLast(1).map { (text, isUser) ->
                content(role = if (isUser) "user" else "model") { text(text) }
            }

            val chat = model.startChat(history = history)
            val lastMessage = messages.lastOrNull()?.first ?: return null
            // Prepend system context on first message or if it's a fresh chat
            val messageToSend = if (history.isEmpty()) {
                "$systemPrompt\n\n$lastMessage"
            } else {
                lastMessage
            }
            chat.sendMessage(messageToSend).text?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Gemini chat failed: ${e.message}")
            // Fallback to default single-turn implementation
            super.generateConversationalResponse(messages, systemPrompt)
        }
    }

    private suspend fun generate(prompt: String, temp: Float, maxTokens: Int): String? {
        return try {
            val model = getModel(generationConfig {
                temperature = temp
                maxOutputTokens = maxTokens
            }) ?: return null
            
            val response = model.generateContent(content { text(prompt) })
            response.text?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Gemini generation failed: ${e.message}")
            null
        }
    }
    
    suspend fun invalidateCache() = cacheMutex.withLock {
        modelCache.clear()
        Log.d(TAG, "Model cache cleared")
    }
}
