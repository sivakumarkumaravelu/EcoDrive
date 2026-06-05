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
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val TAG = "CohereProvider"
        private const val API_URL = "https://api.cohere.ai/v1/chat"
        private const val MODEL_NAME = "command-r"
    }

    override suspend fun generateRealTimeTip(prompt: String): String? = generate(prompt, 0.7f, 512)
    override suspend fun generateTripInsight(prompt: String): String? = generate(prompt, 0.4f, 1024)
    override suspend fun generateWeeklyReport(prompt: String): String? = generate(prompt, 0.4f, 1024)
    override suspend fun generateAnalyticsSummary(prompt: String): String? = generate(prompt, 0.4f, 1024)

    private suspend fun generate(prompt: String, temp: Float, maxTokens: Int): String? = withContext(Dispatchers.IO) {
        val apiKey = AiConfig.COHERE_API_KEY
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_")) return@withContext null

        val requestBodyJson = buildJsonObject {
            put("message", prompt)
            put("model", MODEL_NAME)
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
