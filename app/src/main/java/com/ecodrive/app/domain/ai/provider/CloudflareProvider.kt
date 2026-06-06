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
class CloudflareProvider @Inject constructor() : AiProvider {
    override val name: String = "CLOUDFLARE"
    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }
    
    companion object {
        private const val TAG = "CloudflareProvider"
        private const val MODEL_ID = "@cf/meta/llama-3-8b-instruct"
    }

    override suspend fun generateRealTimeTip(prompt: String, model: String?): String? = generate(prompt, 0.7f, 512, model)
    override suspend fun generateTripInsight(prompt: String, model: String?): String? = generate(prompt, 0.4f, 1024, model)
    override suspend fun generateWeeklyReport(prompt: String, model: String?): String? = generate(prompt, 0.4f, 1024, model)
    override suspend fun generateAnalyticsSummary(prompt: String, model: String?): String? = generate(prompt, 0.4f, 1024, model)

    private suspend fun generate(prompt: String, temp: Float, maxTokens: Int, modelName: String? = null): String? = withContext(Dispatchers.IO) {
        val apiKey = AiConfig.CLOUDFLARE_API_KEY
        val accountId = AiConfig.CLOUDFLARE_ACCOUNT_ID
        if (apiKey.isBlank() || apiKey.startsWith("YOUR_") || accountId.isBlank() || accountId.startsWith("YOUR_")) return@withContext null

        val apiUrl = "https://api.cloudflare.com/client/v4/accounts/$accountId/ai/run/$MODEL_ID"
        val requestBodyJson = buildJsonObject {
            put("messages", buildJsonArray { add(buildJsonObject { put("role", "user"); put("content", prompt) }) })
        }

        val request = Request.Builder()
            .url(apiUrl)
            .addHeader("Authorization", "Bearer $apiKey")
            .post(requestBodyJson.toString().toRequestBody("application/json".toMediaType()))
            .build()

        try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body?.string() ?: return@withContext null
                val jsonResponse = json.parseToJsonElement(body).jsonObject
                jsonResponse["result"]?.jsonObject?.get("response")?.jsonPrimitive?.content?.trim()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Cloudflare failed: ${e.message}")
            null
        }
    }
}
