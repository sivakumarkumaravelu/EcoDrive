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
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gemini-based implementation of AiProvider using hardcoded API key.
 */
@Singleton
class GeminiProvider @Inject constructor() : AiProvider {

    override val name: String = "GEMINI"
    override val defaultModel: String = "gemini-2.0-flash"

    companion object {
        private const val TAG = "GeminiProvider"
    }

    private val modelCache = mutableMapOf<String, GenerativeModel>()
    private val cacheMutex = Mutex()

    private suspend fun getModel(config: GenerationConfig, modelName: String?): GenerativeModel? {
        val apiKey = AiConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) return null
        
        val actualModelName = modelName ?: defaultModel
        return cacheMutex.withLock {
            val cacheKey = "$actualModelName:${config.temperature}:${config.maxOutputTokens}"
            modelCache.getOrPut(cacheKey) {
                GenerativeModel(
                    modelName = actualModelName,
                    apiKey = apiKey,
                    generationConfig = config
                )
            }
        }
    }

    override suspend fun getAvailableModels(): List<String>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val apiKey = AiConfig.GEMINI_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) return@withContext null

        val client = okhttp3.OkHttpClient()
        val request = okhttp3.Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models?key=$apiKey")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val jsonResponse = Json { ignoreUnknownKeys = true }.parseToJsonElement(body).jsonObject
                jsonResponse["models"]?.jsonArray?.mapNotNull { 
                    it.jsonObject["name"]?.jsonPrimitive?.content?.removePrefix("models/")
                }?.sorted()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Gemini models fetch failed: ${e.message}")
            null
        }
    }

    override suspend fun generateRealTimeTip(prompt: String, model: String?): String? {
        return generate(prompt, 0.7f, 512, model)
    }

    override suspend fun generateTripInsight(prompt: String, model: String?): String? {
        return generate(prompt, 0.4f, 1024, model)
    }

    override suspend fun generateWeeklyReport(prompt: String, model: String?): String? {
        return generate(prompt, 0.4f, 1024, model)
    }

    override suspend fun generateAnalyticsSummary(prompt: String, model: String?): String? {
        return generate(prompt, 0.4f, 1024, model)
    }

    override suspend fun generatePrediction(prompt: String, model: String?): String? {
        // Lower temperature for more deterministic predictions
        return generate(prompt, 0.2f, 256, model)
    }

    /**
     * Native multi-turn chat using Gemini's Chat API.
     * Provides true conversational context rather than concatenated prompts.
     */
    override suspend fun generateConversationalResponse(
        messages: List<Pair<String, Boolean>>,
        systemPrompt: String,
        model: String?
    ): String? {
        return try {
            val generativeModel = getModel(generationConfig {
                temperature = 0.5f
                maxOutputTokens = 1024
            }, model) ?: return null

            // Build Gemini chat history from all messages except the last (current user message)
            val history = messages.dropLast(1).map { (text, isUser) ->
                content(role = if (isUser) "user" else "model") { text(text) }
            }

            val chat = generativeModel.startChat(history = history)
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
            super.generateConversationalResponse(messages, systemPrompt, model)
        }
    }

    private suspend fun generate(prompt: String, temp: Float, maxTokens: Int, modelName: String?): String? {
        return try {
            val generativeModel = getModel(generationConfig {
                temperature = temp
                maxOutputTokens = maxTokens
            }, modelName) ?: return null
            
            val response = generativeModel.generateContent(content { text(prompt) })
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
