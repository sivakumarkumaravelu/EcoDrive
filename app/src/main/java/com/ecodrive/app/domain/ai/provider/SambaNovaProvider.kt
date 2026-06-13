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
class SambaNovaProvider @Inject constructor() : AiProvider {
    override val name: String = "SAMBANOVA"
    override val defaultModel: String = "Meta-Llama-3.3-70B-Instruct"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val TAG = "SambaNovaProvider"
        private const val API_URL = "https://api.sambanova.ai/v1/chat/completions"
        private const val MODELS_URL = "https://api.sambanova.ai/v1/models"
    }

    override suspend fun getAvailableModels(): List<String>? = withContext(Dispatchers.IO) {
        val apiKey = AiConfig.SAMBANOVA_API_KEY
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
                jsonResponse["data"]?.jsonArray?.mapNotNull { 
                    it.jsonObject["id"]?.jsonPrimitive?.content 
                }?.sorted()
            }
        } catch (e: Exception) {
            Log.w(TAG, "SambaNova models fetch failed: ${e.message}")
            null
        }
    }

    override suspend fun generateRealTimeTip(prompt: String, model: String?): String? = generate(prompt, 0.7f, 512, model)
    override suspend fun generateTripInsight(prompt: String, model: String?): String? = generate(prompt, 0.4f, 1024, model)
    override suspend fun generateWeeklyReport(prompt: String, model: String?): String? = generate(prompt, 0.4f, 1024, model)
    override suspend fun generateAnalyticsSummary(prompt: String, model: String?): String? = generate(prompt, 0.4f, 1024, model)

    private suspend fun generate(prompt: String, temp: Float, maxTokens: Int, model: String?): String? = withContext(Dispatchers.IO) {
        val apiKey = AiConfig.SAMBANOVA_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) return@withContext null

        val availableModels = getAvailableModels()
        val targetModel = if (availableModels.isNullOrEmpty()) {
            model ?: defaultModel
        } else {
            if (model != null && availableModels.contains(model)) model else availableModels.first()
        }

        val requestBodyJson = buildJsonObject {
            put("model", targetModel)
            put("messages", buildJsonArray {
                add(buildJsonObject { put("role", "user"); put("content", prompt) })
            })
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
                jsonResponse["choices"]?.jsonArray?.get(0)
                    ?.jsonObject?.get("message")?.jsonObject?.get("content")
                    ?.jsonPrimitive?.content?.trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "SambaNova failed: ${e.message}")
            null
        }
    }
}
