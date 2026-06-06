package com.ecodrive.app.domain.ai.service

import com.ecodrive.app.domain.ai.config.AiUtils

import android.util.Log
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.domain.ai.provider.AiProvider
import com.ecodrive.app.domain.ai.provider.GeminiProvider
import com.ecodrive.app.domain.ai.provider.LocalProvider
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiManager @Inject constructor(
    private val providers: Set<@JvmSuppressWildcards AiProvider>,
    private val preferenceManager: PreferenceManager
) {
    companion object {
        private const val TAG = "AiManager"
        private const val COACHING_RATE_LIMIT_MS = 15_000L
    }

    private var lastCoachingCallMs = 0L

    suspend fun generateRealTimeTip(prompt: String): String? {
        val now = System.currentTimeMillis()
        if (now - lastCoachingCallMs < COACHING_RATE_LIMIT_MS) return null

        val provider = getSelectedProvider()
        val model = getSelectedModel(provider.name)
        var tip = provider.generateRealTimeTip(prompt, model)
        
        if (tip == null && provider !is LocalProvider) {
            Log.d(TAG, "Primary provider failed, falling back to LocalProvider")
            tip = getLocalProvider().generateRealTimeTip(prompt, null)
        }

        if (tip != null) lastCoachingCallMs = System.currentTimeMillis()
        return tip
    }

    suspend fun generateTripInsight(prompt: String): String? {
        val provider = getSelectedProvider()
        val model = getSelectedModel(provider.name)
        return provider.generateTripInsight(prompt, model) ?: getLocalProvider().generateTripInsight(prompt, null)
    }

    suspend fun generateWeeklyReport(prompt: String): String? {
        val provider = getSelectedProvider()
        val model = getSelectedModel(provider.name)
        return provider.generateWeeklyReport(prompt, model) ?: getLocalProvider().generateWeeklyReport(prompt, null)
    }

    suspend fun generateAnalyticsSummary(prompt: String): String? {
        val provider = getSelectedProvider()
        val model = getSelectedModel(provider.name)
        return provider.generateAnalyticsSummary(prompt, model) ?: getLocalProvider().generateAnalyticsSummary(prompt, null)
    }

    /**
     * Generates an eco-score prediction using lower-temperature settings.
     */
    suspend fun generatePrediction(prompt: String): String? {
        val provider = getSelectedProvider()
        val model = getSelectedModel(provider.name)
        return provider.generatePrediction(prompt, model) ?: getLocalProvider().generatePrediction(prompt, null)
    }

    /**
     * Generates a multi-turn conversational response for the AI Coach chat.
     * Falls back to the default (history-as-context) approach on failure.
     *
     * @param messages Ordered list of (text, isUser) message pairs.
     * @param systemPrompt System-level coaching context injected at the start.
     * @return A pair of (response text, provider name) or null if all providers fail.
     */
    suspend fun generateConversationalResponse(
        messages: List<Pair<String, Boolean>>,
        systemPrompt: String,
    ): Pair<String, String>? {
        val provider = getSelectedProvider()
        val model = getSelectedModel(provider.name)
        val response = provider.generateConversationalResponse(messages, systemPrompt, model)
        
        if (response != null) {
            return response to provider.name
        }

        val localProvider = getLocalProvider()
        val localResponse = localProvider.generateConversationalResponse(messages, systemPrompt, null)
        
        return if (localResponse != null) {
            localResponse to localProvider.name
        } else {
            null
        }
    }

    private suspend fun getSelectedProvider(): AiProvider {
        val selected = preferenceManager.selectedAiProvider.first()
        return getProviderByName(selected)
    }

    private suspend fun getSelectedModel(providerName: String): String? {
        return preferenceManager.getSelectedModel(providerName).first()
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
