package com.ecodrive.app.domain.ai.service

import com.ecodrive.app.domain.ai.config.AiUtils

import android.util.Log
import com.ecodrive.app.domain.ai.provider.AiProvider
import com.ecodrive.app.domain.ai.provider.DeepSeekProvider
import com.ecodrive.app.domain.ai.provider.GeminiProvider
import com.ecodrive.app.domain.ai.provider.LocalProvider
import com.ecodrive.app.domain.ai.provider.OpenRouterProvider
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiManager @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards AiProvider>
) {
    companion object {
        private const val TAG = "AiManager"
        private const val COACHING_RATE_LIMIT_MS = 15_000L
    }

    private var lastCoachingCallMs = 0L

    private suspend fun <T> executeWithFallback(action: suspend (AiProvider, String?) -> T?): T? {
        // 1. Try Free Providers (Shuffled for load balancing)
        val freeProviders = providers.filter { 
            it !is DeepSeekProvider && it !is LocalProvider && it !is OpenRouterProvider 
        }.shuffled()

        for (provider in freeProviders) {
            try {
                val result = action(provider, provider.defaultModel)
                if (result != null) return result
            } catch (e: Exception) {
                Log.w(TAG, "Provider ${provider.name} failed: ${e.message}")
            }
        }

        // 2. Try Secondary/Paid Provider
        val deepSeek = providers.find { it is DeepSeekProvider }
        if (deepSeek != null) {
            try {
                val result = action(deepSeek, deepSeek.defaultModel)
                if (result != null) return result
            } catch (e: Exception) {
                Log.e(TAG, "DeepSeek fallback failed", e)
            }
        }

        // 3. Ultimate Fallback
        val local = getLocalProvider()
        return try {
            action(local, null)
        } catch (e: Exception) {
            Log.e(TAG, "Local fallback failed", e)
            null
        }
    }

    suspend fun generateRealTimeTip(prompt: String): String? {
        val now = System.currentTimeMillis()
        if (now - lastCoachingCallMs < COACHING_RATE_LIMIT_MS) return null

        val tip = executeWithFallback { provider, model ->
            provider.generateRealTimeTip(prompt, model)
        }

        if (tip != null) lastCoachingCallMs = System.currentTimeMillis()
        return tip
    }

    suspend fun generateTripInsight(prompt: String): String? {
        return executeWithFallback { provider, model ->
            provider.generateTripInsight(prompt, model)
        }
    }

    suspend fun generateWeeklyReport(prompt: String): String? {
        return executeWithFallback { provider, model ->
            provider.generateWeeklyReport(prompt, model)
        }
    }

    suspend fun generateAnalyticsSummary(prompt: String): String? {
        return executeWithFallback { provider, model ->
            provider.generateAnalyticsSummary(prompt, model)
        }
    }

    /**
     * Generates an eco-score prediction using lower-temperature settings.
     */
    suspend fun generatePrediction(prompt: String): String? {
        return executeWithFallback { provider, model ->
            provider.generatePrediction(prompt, model)
        }
    }

    /**
     * Generates a multi-turn conversational response for the AI Coach chat.
     * Falls back to the default (history-as-context) approach on failure.
     *
     * @param messages Ordered list of (text, isUser) message pairs.
     * @param systemPrompt System-level coaching context injected at the start.
     * @return A Triple of (response text, provider name, model name) or null if all providers fail.
     */
    suspend fun generateConversationalResponse(
        messages: List<Pair<String, Boolean>>,
        systemPrompt: String,
    ): Triple<String, String, String?>? {
        return executeWithFallback { provider, model ->
            val response = provider.generateConversationalResponse(messages, systemPrompt, model)
            if (response != null) Triple(response, provider.name, model ?: provider.defaultModel) else null
        }
    }

    /**
     * Gets all registered AI providers.
     */
    fun getAllProviders(): List<AiProvider> = providers.toList()

    /**
     * Maps a string name to a provider instance.
     */
    fun getProviderByName(name: String): AiProvider {
        val provider = providers.find { it.name.equals(name, ignoreCase = true) }
        return provider ?: getFallbackProvider()
    }

    private fun getFallbackProvider(): AiProvider {
        return providers.find { it is GeminiProvider } ?: getLocalProvider()
    }

    private fun getLocalProvider(): AiProvider {
        return providers.find { it is LocalProvider } 
            ?: throw IllegalStateException("LocalProvider not found in providers set. Available: ${providers.map { it::class.java.simpleName }}")
    }

    suspend fun invalidateCache() {
        (providers.find { it is GeminiProvider } as? GeminiProvider)?.invalidateCache()
    }

    fun isCoachingRateLimited(): Boolean {
        return System.currentTimeMillis() - lastCoachingCallMs < COACHING_RATE_LIMIT_MS
    }
}
