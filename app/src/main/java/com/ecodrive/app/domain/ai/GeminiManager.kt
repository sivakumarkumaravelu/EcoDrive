package com.ecodrive.app.domain.ai

import android.util.Log
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Central singleton for managing all Gemini AI interactions in EcoDrive.
 *
 * Responsibilities:
 * - Caches a single [GenerativeModel] instance per API key (avoids recreating on every call).
 * - Provides typed generation methods for different coaching contexts.
 * - Implements graceful degradation: returns null on any error so callers can
 *   fall back to rule-based coaches without crashing.
 * - Enforces a minimum interval between requests to avoid excessive API calls
 *   during active recording sessions.
 */
@Singleton
class GeminiManager @Inject constructor() {

    companion object {
        private const val TAG = "GeminiManager"

        // Flash model: fast, cost-effective for frequent coaching calls
        private const val MODEL_FLASH = "gemini-2.0-flash"

        // Pro model: richer reasoning for post-trip analysis
        private const val MODEL_PRO = "gemini-2.0-flash"

        // Minimum ms between real-time coaching API calls (15 seconds)
        private const val COACHING_RATE_LIMIT_MS = 15_000L
    }

    // Cached model instances keyed by "apiKey:modelName"
    private val modelCache = mutableMapOf<String, GenerativeModel>()
    private val cacheMutex = Mutex()

    // Rate limiting for real-time coaching
    private var lastCoachingCallMs = 0L

    // ── Model Access ─────────────────────────────────────────────

    /**
     * Returns the flash model for frequent / real-time calls.
     * Returns null if [apiKey] is blank.
     */
    private suspend fun getFlashModel(apiKey: String): GenerativeModel? {
        if (apiKey.isBlank()) return null
        return getOrCreateModel(apiKey, MODEL_FLASH, generationConfig {
            temperature = 0.7f
            maxOutputTokens = 512
        })
    }

    /**
     * Returns the model for rich post-trip analysis (more tokens, lower temperature).
     * Returns null if [apiKey] is blank.
     */
    private suspend fun getAnalysisModel(apiKey: String): GenerativeModel? {
        if (apiKey.isBlank()) return null
        return getOrCreateModel(apiKey, MODEL_PRO, generationConfig {
            temperature = 0.4f
            maxOutputTokens = 1024
        })
    }

    private suspend fun getOrCreateModel(
        apiKey: String,
        modelName: String,
        config: GenerationConfig,
    ): GenerativeModel = cacheMutex.withLock {
        val cacheKey = "$apiKey:$modelName"
        modelCache.getOrPut(cacheKey) {
            GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                generationConfig = config,
            )
        }
    }

    // ── Generation Methods ───────────────────────────────────────

    /**
     * Generates a real-time driving tip during active trip recording.
     *
     * Rate-limited to [COACHING_RATE_LIMIT_MS] ms between calls.
     * Returns null when rate-limited, API key missing, or on any error.
     *
     * @param apiKey   User's Gemini API key from preferences.
     * @param prompt   Structured prompt describing current driving context.
     */
    suspend fun generateRealTimeTip(apiKey: String, prompt: String): String? {
        val now = System.currentTimeMillis()
        if (now - lastCoachingCallMs < COACHING_RATE_LIMIT_MS) {
            return null // Rate-limited — caller should use cached tip
        }

        return try {
            val model = getFlashModel(apiKey) ?: return null
            val response = model.generateContent(
                content { text(prompt) }
            )
            lastCoachingCallMs = System.currentTimeMillis()
            response.text?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Real-time tip generation failed: ${e.message}")
            null
        }
    }

    /**
     * Generates a rich post-trip insight for the Trip Detail screen.
     *
     * Uses a higher token budget and lower temperature for analytical depth.
     * Returns null on error so the caller can fall back to [LocalEcoCoach].
     *
     * @param apiKey User's Gemini API key from preferences.
     * @param prompt Structured prompt with full trip telemetry context.
     */
    suspend fun generateTripInsight(apiKey: String, prompt: String): String? {
        return try {
            val model = getAnalysisModel(apiKey) ?: return null
            val response = model.generateContent(
                content { text(prompt) }
            )
            response.text?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Trip insight generation failed: ${e.message}")
            null
        }
    }

    /**
     * Generates a weekly coaching report for the Coach screen.
     * Uses analysis model for structured, multi-paragraph output.
     */
    suspend fun generateWeeklyReport(apiKey: String, prompt: String): String? {
        return try {
            val model = getAnalysisModel(apiKey) ?: return null
            val response = model.generateContent(
                content { text(prompt) }
            )
            response.text?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Weekly report generation failed: ${e.message}")
            null
        }
    }

    /**
     * Generates a natural language analytics summary (trends, forecasts, goals).
     */
    suspend fun generateAnalyticsSummary(apiKey: String, prompt: String): String? {
        return try {
            val model = getAnalysisModel(apiKey) ?: return null
            val response = model.generateContent(
                content { text(prompt) }
            )
            response.text?.trim()
        } catch (e: Exception) {
            Log.w(TAG, "Analytics summary generation failed: ${e.message}")
            null
        }
    }

    /**
     * Clears cached models when the API key changes.
     * Call this from the Settings screen after saving a new key.
     */
    suspend fun invalidateCache() = cacheMutex.withLock {
        modelCache.clear()
        Log.d(TAG, "Model cache cleared")
    }

    /**
     * Returns true if a Gemini API call is currently rate-limited.
     * Useful for UI to show a "cooling down" indicator.
     */
    fun isCoachingRateLimited(): Boolean {
        return System.currentTimeMillis() - lastCoachingCallMs < COACHING_RATE_LIMIT_MS
    }
}
