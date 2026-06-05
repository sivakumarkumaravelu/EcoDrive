package com.ecodrive.app.domain.ai

import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.domain.model.VehicleType
import com.ecodrive.app.util.Constants
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data class for Eco Score weights.
 */
data class ScoreWeights(
    val acceleration: Double = Constants.WEIGHT_ACCELERATION,
    val braking: Double = Constants.WEIGHT_BRAKING,
    val speed: Double = Constants.WEIGHT_SPEED,
    val cornering: Double = Constants.WEIGHT_CORNERING,
    val idle: Double = Constants.WEIGHT_IDLE,
    val consistency: Double = Constants.WEIGHT_CONSISTENCY
)

/**
 * Service that provides adaptive weights for Eco Score calculation
 * based on vehicle type and historical performance.
 */
@Singleton
class AdaptiveScoreWeights @Inject constructor(
    private val geminiManager: GeminiManager,
    private val preferenceManager: PreferenceManager,
) {
    /**
     * Returns the recommended weights for a given vehicle type.
     * In the future, this can be refined by Gemini based on historical data.
     */
    fun getWeightsForVehicle(vehicleType: VehicleType): ScoreWeights {
        return when (vehicleType) {
            VehicleType.ELECTRIC -> ScoreWeights(
                acceleration = 0.25,
                braking = 0.15, // Regen braking is less "bad" in EVs
                speed = 0.20,
                cornering = 0.15,
                idle = 0.05, // Idling is zero fuel in EV, but still inefficient use of time/energy
                consistency = 0.20
            )
            VehicleType.HYBRID, VehicleType.PLUG_IN_HYBRID -> ScoreWeights(
                acceleration = 0.20,
                braking = 0.15,
                speed = 0.20,
                cornering = 0.15,
                idle = 0.15, // Idle management is key for hybrids
                consistency = 0.15
            )
            VehicleType.ICE -> ScoreWeights() // Default constants
        }
    }

    /**
     * Refines weights using Gemini by analyzing recent trip performance.
     */
    suspend fun refineWeightsWithAi(vehicleType: VehicleType, tripSummary: String): ScoreWeights? {
        val apiKey = preferenceManager.geminiApiKey.first()
        if (apiKey.isBlank()) return null

        val prompt = """
            You are a Vehicle Performance Engineer. Suggest optimal Eco Score weights for a $vehicleType vehicle.
            
            Current context/recent performance:
            $tripSummary
            
            Return ONLY a JSON object with these keys: 
            "acceleration", "braking", "speed", "cornering", "idle", "consistency".
            The sum of all values MUST be exactly 1.0.
        """.trimIndent()

        val response = geminiManager.generateTripInsight(apiKey, prompt)
        return try {
            val jsonStr = AiUtils.extractJson(response ?: "") ?: return null
            val json = Json.parseToJsonElement(jsonStr).jsonObject
            
            ScoreWeights(
                acceleration = json["acceleration"]?.jsonPrimitive?.doubleOrNull ?: Constants.WEIGHT_ACCELERATION,
                braking = json["braking"]?.jsonPrimitive?.doubleOrNull ?: Constants.WEIGHT_BRAKING,
                speed = json["speed"]?.jsonPrimitive?.doubleOrNull ?: Constants.WEIGHT_SPEED,
                cornering = json["cornering"]?.jsonPrimitive?.doubleOrNull ?: Constants.WEIGHT_CORNERING,
                idle = json["idle"]?.jsonPrimitive?.doubleOrNull ?: Constants.WEIGHT_IDLE,
                consistency = json["consistency"]?.jsonPrimitive?.doubleOrNull ?: Constants.WEIGHT_CONSISTENCY
            )
        } catch (e: Exception) {
            null
        }
    }
}
