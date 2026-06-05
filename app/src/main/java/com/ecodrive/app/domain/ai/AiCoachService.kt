package com.ecodrive.app.domain.ai

import android.util.Log
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.EcoScore
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that generates real-time AI coaching tips during a trip.
 * Maintains a sliding window of recent metrics to provide context-aware advice.
 */
@Singleton
class AiCoachService @Inject constructor(
    private val geminiManager: GeminiManager,
    private val preferenceManager: PreferenceManager,
) {
    private val contextWindow = mutableListOf<DrivingMetrics>()
    private val maxWindowSize = 60 // ~1 minute of data if sampled at 1Hz

    private var lastTip: String? = null

    /**
     * Adds a metric point to the context window.
     */
    fun updateContext(metrics: DrivingMetrics) {
        contextWindow.add(metrics)
        if (contextWindow.size > maxWindowSize) {
            contextWindow.removeAt(0)
        }
    }

    /**
     * Generates a personalized coaching tip based on recent driving context.
     * Returns null if Gemini is unavailable, rate-limited, or no key is set.
     */
    suspend fun getRealTimeTip(metrics: DrivingMetrics, ecoScore: EcoScore): String? {
        val apiKey = preferenceManager.geminiApiKey.first()
        if (apiKey.isBlank()) return null

        // Rate limiting is handled inside GeminiManager.generateRealTimeTip
        
        val avgSpeed = contextWindow.map { it.speedKmh }.average()
        val maxAccel = contextWindow.map { it.longitudinalAccelMps2 }.maxOrNull() ?: 0.0
        val minAccel = contextWindow.map { it.longitudinalAccelMps2 }.minOrNull() ?: 0.0
        
        val prompt = """
            You are a real-time Eco-Driving Coach. Provide a SINGLE, concise (max 15 words) tip 
            for the driver based on their current behavior.
            
            Current Metrics:
            - Speed: ${"%.1f".format(metrics.speedKmh)} km/h
            - Acceleration: ${"%.2f".format(metrics.longitudinalAccelMps2)} m/s²
            - Eco Score: ${ecoScore.overall}/100
            
            Last 60s Context:
            - Avg Speed: ${"%.1f".format(avgSpeed)} km/h
            - Max Accel: ${"%.2f".format(maxAccel)} m/s²
            - Hardest Brake: ${"%.2f".format(minAccel)} m/s²
            
            Previous Tip Given: ${lastTip ?: "None"}
            
            Give a fresh, relevant tip. If driving is perfect, give an encouraging short praise.
            Focus on actionable advice (e.g., "Ease off the pedal", "Great consistency").
            Output ONLY the tip text.
        """.trimIndent()

        val tip = geminiManager.generateRealTimeTip(apiKey, prompt)
        if (tip != null) {
            lastTip = tip
        }
        return tip
    }

    fun clearContext() {
        contextWindow.clear()
        lastTip = null
    }
}
