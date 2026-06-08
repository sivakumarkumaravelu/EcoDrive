package com.ecodrive.app.domain.ai.service

import com.ecodrive.app.domain.ai.config.AiConfig

import android.util.Log
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.WeatherApiClient
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.EcoScore
import com.ecodrive.app.domain.model.WeatherContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that generates real-time AI coaching tips during a trip.
 * Maintains a sliding window of recent metrics to provide context-aware advice,
 * now enriched with weather and environmental conditions (Feature 3).
 */
@Singleton
class AiCoachService @Inject constructor(
    private val aiManager: AiManager,
    private val preferenceManager: PreferenceManager,
    private val weatherApiClient: WeatherApiClient,
) {
    private val contextWindow = mutableListOf<DrivingMetrics>()
    private val maxWindowSize = 60 // ~1 minute of data if sampled at 1Hz

    private var lastTip: String? = null
    private var cachedWeather: WeatherContext? = null
    private var lastWeatherFetchMs: Long = 0L
    private val weatherRefreshMs = 15 * 60 * 1000L  // Refresh every 15 minutes

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
     * Generates a personalized coaching tip based on recent driving context
     * and current weather conditions.
     * Returns null if AI is unavailable, rate-limited, or no key is set.
     */
    suspend fun getRealTimeTip(metrics: DrivingMetrics, ecoScore: EcoScore): String? {
        val useMetric = preferenceManager.useMetricUnits.first()
        val avgSpeed = if (contextWindow.isNotEmpty()) contextWindow.map { it.speedKmh }.average() else metrics.speedKmh
        val maxAccel = contextWindow.mapNotNull { it.longitudinalAccelMps2.takeIf { v -> v > 0 } }.maxOrNull() ?: 0.0
        val minAccel = contextWindow.map { it.longitudinalAccelMps2 }.minOrNull() ?: 0.0

        // Weather context — refresh periodically using last known location
        val weather = getWeatherContext(metrics.latitude, metrics.longitude)
        val weatherTag = weather.safetyContext

        val unitLabel = if (useMetric) "km/h" else "mph"
        val displaySpeed = if (useMetric) metrics.speedKmh else com.ecodrive.app.util.UnitConverter.kmhToMph(metrics.speedKmh)
        val displayAvgSpeed = if (useMetric) avgSpeed else com.ecodrive.app.util.UnitConverter.kmhToMph(avgSpeed)

        val weatherSection = if (weatherTag.isNotBlank()) {
            "\nWeather/Environment:\n" +
            "- Conditions: ${weather.conditionLabel} (${weather.tempC.toInt()}°C)\n" +
            "- ${if (weatherTag.isNotBlank()) "⚠️ $weatherTag — adjust advice for these conditions" else ""}\n" +
            "- Fuel penalty factor: ${"%.2f".format(weather.fuelPenaltyFactor)}x (${((weather.fuelPenaltyFactor - 1) * 100).toInt()}% extra fuel expected)\n"
        } else ""

        val prompt = """
            You are a real-time Eco-Driving Coach. Provide a SINGLE, concise (max 15 words) tip 
            for the driver based on their current behavior.
            
            CRITICAL: Use $unitLabel for speed and provide advice in the context of the ${if (useMetric) "Metric" else "Imperial"} system.
            Do NOT include any emojis, markdown, or special symbols in your response. 
            The output will be read aloud by Text-to-Speech.
            
            Current Metrics:
            - Speed: ${"%.1f".format(displaySpeed)} $unitLabel
            - Acceleration: ${"%.2f".format(metrics.longitudinalAccelMps2)} m/s²
            - Eco Score: ${ecoScore.overall}/100
            
            Last 60s Context:
            - Avg Speed: ${"%.1f".format(displayAvgSpeed)} $unitLabel
            - Max Accel: ${"%.2f".format(maxAccel)} m/s²
            - Hardest Brake: ${"%.2f".format(minAccel)} m/s²
            $weatherSection
            Previous Tip Given: ${lastTip ?: "None"}
            
            Give a fresh, relevant tip. If conditions are hazardous (rain/ice/fog), prioritize safety over efficiency.
            If driving is perfect, give an encouraging short praise.
            Focus on actionable advice. Output ONLY the tip text.
        """.trimIndent()

        val tip = aiManager.generateRealTimeTip(prompt)
        if (tip != null) {
            lastTip = tip
        }
        return tip
    }

    /**
     * Returns cached weather context or fetches fresh data.
     */
    private suspend fun getWeatherContext(lat: Double, lon: Double): WeatherContext {
        val now = System.currentTimeMillis()
        if (cachedWeather != null && now - lastWeatherFetchMs < weatherRefreshMs) {
            return cachedWeather!!
        }
        return try {
            val weather = weatherApiClient.getWeatherContext(lat, lon)
            cachedWeather = weather
            lastWeatherFetchMs = now
            weather
        } catch (e: Exception) {
            cachedWeather ?: WeatherContext()
        }
    }

    fun clearContext() {
        contextWindow.clear()
        lastTip = null
        cachedWeather = null
        lastWeatherFetchMs = 0L
    }
}
