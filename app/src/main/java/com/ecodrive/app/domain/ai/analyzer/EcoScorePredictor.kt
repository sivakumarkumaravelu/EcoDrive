package com.ecodrive.app.domain.ai.analyzer

import android.util.Log
import com.ecodrive.app.data.local.dao.TripDao
import com.ecodrive.app.domain.ai.service.AiManager
import com.ecodrive.app.domain.model.PredictedScore
import com.ecodrive.app.domain.model.Trip
import java.time.Instant
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Predicts the expected eco-score for an upcoming trip before recording starts.
 *
 * Uses:
 * - Historical trips at the same time-of-day (±1 hour)
 * - Same day-of-week patterns
 * - Recent trend (last 5 trips)
 * - AI refinement for a natural-language explanation
 */
@Singleton
class EcoScorePredictor @Inject constructor(
    private val tripDao: TripDao,
    private val aiManager: AiManager,
) {
    companion object {
        private const val TAG = "EcoScorePredictor"
        private const val HISTORY_LIMIT = 50
    }

    /**
     * Generates a [PredictedScore] for the current moment.
     *
     * @param lat Current latitude (used for weather context in future expansion)
     * @param lon Current longitude
     * @return A [PredictedScore] or null if insufficient data
     */
    suspend fun predictForNow(lat: Double = 0.0, lon: Double = 0.0): PredictedScore? {
        return try {
            val recentTrips = tripDao.getRecentCompletedTrips(HISTORY_LIMIT).map { it.toDomain() }
            if (recentTrips.size < 3) return null

            val cal = Calendar.getInstance()
            val currentHour = cal.get(Calendar.HOUR_OF_DAY)
            val currentDow = cal.get(Calendar.DAY_OF_WEEK)

            // 1. Filter trips at same time-of-day (±2h) and same day-of-week
            val sameDowTrips = recentTrips.filter {
                val tripCal = Calendar.getInstance().apply { timeInMillis = it.startTime.toEpochMilli() }
                tripCal.get(Calendar.DAY_OF_WEEK) == currentDow
            }
            val sameTimeTrips = recentTrips.filter {
                val tripCal = Calendar.getInstance().apply { timeInMillis = it.startTime.toEpochMilli() }
                abs(tripCal.get(Calendar.HOUR_OF_DAY) - currentHour) <= 2
            }

            // Weighted average: same-time trips count 2x, same-dow 1x, recent trend 1.5x
            val recentTrend = recentTrips.take(5).map { it.ecoScore }.average()
            val sameTimeAvg = if (sameTimeTrips.isNotEmpty()) sameTimeTrips.map { it.ecoScore }.average() else recentTrend
            val sameDowAvg = if (sameDowTrips.isNotEmpty()) sameDowTrips.map { it.ecoScore }.average() else recentTrend

            val weightedExpected = (
                sameTimeAvg * 2.0 +
                sameDowAvg * 1.0 +
                recentTrend * 1.5
            ) / 4.5

            // Compute confidence range using standard deviation of similar trips
            val referenceScores = (sameTimeTrips + sameDowTrips).distinctBy { it.id }.map { it.ecoScore.toDouble() }
            val stdDev = if (referenceScores.size >= 3) {
                val mean = referenceScores.average()
                Math.sqrt(referenceScores.sumOf { (it - mean) * (it - mean) } / referenceScores.size)
            } else 8.0

            val expected = weightedExpected.roundToInt().coerceIn(0, 100)
            val low = (weightedExpected - stdDev).roundToInt().coerceIn(0, 100)
            val high = (weightedExpected + stdDev).roundToInt().coerceIn(0, 100)

            // Ask AI for a brief explanation
            val explanation = generateExplanation(
                expected = expected,
                sameTimeAvg = sameTimeAvg.roundToInt(),
                recentTrend = recentTrend.roundToInt(),
                currentHour = currentHour,
                tripCount = referenceScores.size,
            ) ?: buildFallbackExplanation(expected, currentHour, referenceScores.size)

            PredictedScore(
                expected = expected,
                low = low,
                high = high,
                explanation = explanation,
                basedOnTripCount = referenceScores.size,
            )
        } catch (e: Exception) {
            Log.w(TAG, "Prediction failed: ${e.message}")
            null
        }
    }

    private suspend fun generateExplanation(
        expected: Int,
        sameTimeAvg: Int,
        recentTrend: Int,
        currentHour: Int,
        tripCount: Int,
    ): String? {
        val timeLabel = when (currentHour) {
            in 6..9 -> "morning rush hour"
            in 10..11 -> "mid-morning"
            in 12..13 -> "lunch time"
            in 14..16 -> "afternoon"
            in 17..19 -> "evening rush hour"
            in 20..22 -> "evening"
            else -> "late night"
        }

        val prompt = """
            You are an Eco-Driving Predictor. Explain in ONE short sentence (max 20 words) why a driver is 
            predicted to score $expected/100 on their next trip.
            
            Context:
            - Time: $timeLabel
            - Recent average score: $recentTrend/100
            - Same-time-of-day average: $sameTimeAvg/100  
            - Based on $tripCount historical trips
            
            Focus on the most relevant factor. Be encouraging and specific.
            Output ONLY the explanation sentence.
        """.trimIndent()

        return aiManager.generateRealTimeTip(prompt)
    }

    private fun buildFallbackExplanation(expected: Int, currentHour: Int, tripCount: Int): String {
        val timeLabel = when (currentHour) {
            in 7..9 -> "rush hour patterns"
            in 17..19 -> "evening traffic patterns"
            else -> "your recent driving history"
        }
        val quality = when {
            expected >= 85 -> "excellent performance"
            expected >= 70 -> "good performance"
            expected >= 55 -> "average performance"
            else -> "challenging conditions"
        }
        return "Based on $timeLabel across $tripCount trips, expect $quality."
    }

    /**
     * Compares an actual score to its pre-trip prediction and returns
     * a celebratory or encouraging message.
     */
    fun buildOutcomeMessage(predicted: PredictedScore, actualScore: Int): String {
        val diff = actualScore - predicted.expected
        return when {
            diff >= 10 -> "🎉 You crushed it! +$diff pts above forecast (${predicted.expected} → $actualScore)."
            diff >= 3 -> "✅ Slightly above forecast! +$diff pts (${predicted.expected} → $actualScore)."
            diff in -2..2 -> "📊 Right on target — $actualScore vs predicted ${predicted.expected}."
            diff in -9..-3 -> "📉 Just below forecast. You predicted ${predicted.expected}, scored $actualScore. Try smoother braking next time."
            else -> "⚠️ Harder trip than expected (${predicted.expected} → $actualScore). Check traffic or conditions."
        }
    }

    private fun com.ecodrive.app.data.local.entity.TripEntity.toDomain() = Trip(
        id = id,
        vehicleId = vehicleId,
        startTime = Instant.ofEpochMilli(startTimeEpochMs),
        endTime = endTimeEpochMs?.let { Instant.ofEpochMilli(it) },
        distanceKm = distanceKm,
        durationSeconds = durationSeconds,
        averageSpeedKmh = averageSpeedKmh,
        maxSpeedKmh = maxSpeedKmh,
        fuelConsumedLiters = fuelConsumedLiters,
        fuelEfficiencyLPer100Km = fuelEfficiencyLPer100Km,
        ecoScore = ecoScore,
        hardBrakeCount = hardBrakeCount,
        hardAccelCount = hardAccelCount,
        sharpTurnCount = sharpTurnCount,
        idleTimeSeconds = idleTimeSeconds,
        isActive = isActive,
    )
}
