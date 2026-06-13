package com.ecodrive.app.domain.ai.provider

import com.ecodrive.app.domain.ai.config.AiConfig

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CohereProvider @Inject constructor() : AiProvider {
    override val name: String = "COHERE"
    override val defaultModel: String = "command-r-08-2024"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val TAG = "CohereProvider"
        private const val API_URL = "https://api.cohere.ai/v1/chat"
        private const val MODELS_URL = "https://api.cohere.ai/v1/models"
    }

    override suspend fun getAvailableModels(): List<String>? = withContext(Dispatchers.IO) {
        val apiKey = AiConfig.COHERE_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) return@withContext null

        val request = Request.Builder()
            .url(MODELS_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .get()
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val jsonResponse = json.parseToJsonElement(body).jsonObject
                jsonResponse["models"]?.jsonArray?.mapNotNull { 
                    it.jsonObject["name"]?.jsonPrimitive?.content 
                }?.sorted()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cohere models fetch failed: ${e.message}")
            null
        }
    }

    override suspend fun generateRealTimeTip(prompt: String, model: String?): String? = generate(prompt, 0.7f, 512, model)
    override suspend fun generateTripInsight(prompt: String, model: String?): String? = generate(prompt, 0.4f, 1024, model)
    override suspend fun generateWeeklyReport(prompt: String, model: String?): String? = generate(prompt, 0.4f, 1024, model)
    override suspend fun generateAnalyticsSummary(prompt: String, model: String?): String? = generate(prompt, 0.4f, 1024, model)

    private suspend fun generate(prompt: String, temp: Float, maxTokens: Int, model: String?): String? = withContext(Dispatchers.IO) {
        val apiKey = AiConfig.COHERE_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) return@withContext null

        val availableModels = getAvailableModels()
        val targetModel = if (availableModels.isNullOrEmpty()) {
            model ?: defaultModel
        } else {
            if (model != null && availableModels.contains(model)) model 
            else availableModels.firstOrNull { it.contains("command") } ?: availableModels.first()
        }

        val requestBodyJson = buildJsonObject {
            put("message", prompt)
            put("model", targetModel)
            put("temperature", temp)
            put("max_tokens", maxTokens)
        }

        val request = Request.Builder()
            .url(API_URL)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val jsonResponse = json.parseToJsonElement(body).jsonObject
                jsonResponse["text"]?.jsonPrimitive?.content?.trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cohere failed: ${e.message}")
            null
        }
    }
}
