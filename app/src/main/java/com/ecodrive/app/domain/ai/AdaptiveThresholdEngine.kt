package com.ecodrive.app.domain.ai

import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for driving pattern thresholds.
 */
data class DrivingThresholds(
    val hardBrake: Double = Constants.HARD_BRAKE_THRESHOLD,
    val hardAccel: Double = Constants.HARD_ACCEL_THRESHOLD,
    val sharpTurn: Double = Constants.SHARP_TURN_THRESHOLD
)

/**
 * Service that adapts driving event thresholds based on historical driver behavior.
 */
@Singleton
class AdaptiveThresholdEngine @Inject constructor(
    private val geminiManager: GeminiManager,
    private val preferenceManager: PreferenceManager,
) {
    /**
     * Returns thresholds tailored to the driver.
     * In the future, this can be personalized by analyzing historical z-scores.
     */
    fun getPersonalizedThresholds(): DrivingThresholds {
        // For now, return defaults or slight adjustments based on preferences
        return DrivingThresholds()
    }

    /**
     * Uses Gemini to analyze a week of driving data and suggest better thresholds.
     */
    suspend fun analyzeAndRefineThresholds(historySummary: String): DrivingThresholds? {
        val apiKey = preferenceManager.geminiApiKey.first()
        if (apiKey.isBlank()) return null

        val prompt = """
            You are a Driving Data Scientist. Analyze this summary of a driver's behavior over the last week:
            $historySummary
            
            Suggest optimal thresholds for "Hard Brake", "Hard Accel", and "Sharp Turn" in m/s².
            The goal is to be sensitive enough to capture real waste/risk, but not so sensitive that it flags normal safe driving.
            
            Return ONLY a JSON object: {"hard_brake": float, "hard_accel": float, "sharp_turn": float}.
        """.trimIndent()

        val response = geminiManager.generateTripInsight(apiKey, prompt)
        return try {
            val jsonStr = AiUtils.extractJson(response ?: "") ?: return null
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            
            DrivingThresholds(
                hardBrake = json["hard_brake"]?.jsonPrimitive?.doubleOrNull ?: Constants.HARD_BRAKE_THRESHOLD,
                hardAccel = json["hard_accel"]?.jsonPrimitive?.doubleOrNull ?: Constants.HARD_ACCEL_THRESHOLD,
                sharpTurn = json["sharp_turn"]?.jsonPrimitive?.doubleOrNull ?: Constants.SHARP_TURN_THRESHOLD
            )
        } catch (e: Exception) {
            null
        }
    }
}
