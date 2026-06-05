package com.ecodrive.app.domain.ai.analyzer

import android.util.Log
import com.ecodrive.app.data.local.dao.ChallengeDao
import com.ecodrive.app.data.local.entity.BadgeEntity
import com.ecodrive.app.data.local.entity.ChallengeEntity
import com.ecodrive.app.domain.ai.config.AiUtils
import com.ecodrive.app.domain.ai.service.AiManager
import com.ecodrive.app.domain.model.Badge
import com.ecodrive.app.domain.model.BadgeType
import com.ecodrive.app.domain.model.Challenge
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.domain.model.Trip
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Generates personalized weekly driving challenges using AI and awards
 * badges when challenges are completed.
 *
 * Challenge lifecycle:
 * 1. [generateChallenge] — AI creates a challenge based on the weakest behavior area.
 * 2. [updateProgress] — Called after each trip to check if the challenge was met.
 * 3. [checkAndAwardBadges] — Automatically awards badges for milestone achievements.
 */
@Singleton
class ChallengeGenerator @Inject constructor(
    private val aiManager: AiManager,
    private val challengeDao: ChallengeDao,
) {
    companion object {
        private const val TAG = "ChallengeGenerator"
    }

    /**
     * Generates a new challenge based on the driver's weakest area.
     * Only creates one if no active challenge exists.
     *
     * @param recentTrips Last 7-10 trips for analysis
     * @return The newly created [Challenge] or null if one already exists
     */
    suspend fun generateChallenge(recentTrips: List<Trip>): Challenge? {
        val existing = challengeDao.getActiveChallenge()
        if (existing != null) return existing.toDomain()
        if (recentTrips.isEmpty()) return buildFirstTripChallenge()

        val totalBrakes = recentTrips.sumOf { it.hardBrakeCount }
        val totalAccels = recentTrips.sumOf { it.hardAccelCount }
        val totalTurns = recentTrips.sumOf { it.sharpTurnCount }
        val avgScore = recentTrips.map { it.ecoScore }.average()

        val weakestArea = when {
            totalBrakes >= totalAccels && totalBrakes >= totalTurns -> "HARD_BRAKE"
            totalAccels >= totalTurns -> "HARD_ACCELERATION"
            else -> "SHARP_TURN"
        }

        val prompt = """
            You are an Eco-Driving Coach generating a personalized weekly challenge.
            
            Driver's recent performance (last ${recentTrips.size} trips):
            - Average Eco Score: ${avgScore.toInt()}/100
            - Hard Brakes: $totalBrakes
            - Hard Accelerations: $totalAccels
            - Sharp Turns: $totalTurns
            - Weakest area: $weakestArea
            
            Generate ONE specific, achievable weekly challenge targeting the weakest area.
            
            Return ONLY a JSON object:
            {
              "title": "Short catchy title (max 5 words)",
              "description": "Detailed description of the challenge goal (1-2 sentences)",
              "target_count": <integer: how many 'events' to aim for (use 0 for zero-tolerance challenges)>,
              "duration_days": <integer: 5 or 7>,
              "badge_type": "<one of: SMOOTH_OPERATOR, ECO_WARRIOR, CONSISTENCY_KING, FUEL_SAVER>"
            }
        """.trimIndent()

        return try {
            val response = aiManager.generateTripInsight(prompt)
            val jsonStr = AiUtils.extractJson(response ?: "") ?: return buildFallbackChallenge(weakestArea, avgScore)
            val json = Json.parseToJsonElement(jsonStr).jsonObject

            val title = json["title"]?.jsonPrimitive?.content ?: "Smooth Driver"
            val description = json["description"]?.jsonPrimitive?.content ?: "Reduce $weakestArea events this week."
            val targetCount = json["target_count"]?.jsonPrimitive?.intOrNull ?: 2
            val durationDays = json["duration_days"]?.jsonPrimitive?.intOrNull ?: 7
            val badgeTypeStr = json["badge_type"]?.jsonPrimitive?.content
            val badgeType = try { BadgeType.valueOf(badgeTypeStr ?: "") } catch (_: Exception) { BadgeType.SMOOTH_OPERATOR }

            val entity = ChallengeEntity(
                title = title,
                description = description,
                targetCount = targetCount,
                metricType = weakestArea,
                durationDays = durationDays,
                rewardBadgeType = badgeType.name,
            )
            challengeDao.insertChallenge(entity)
            entity.toDomain()
        } catch (e: Exception) {
            Log.w(TAG, "AI challenge generation failed: ${e.message}")
            buildFallbackChallenge(weakestArea, avgScore)
        }
    }

    /**
     * Updates challenge progress after a trip.
     * Marks complete and awards badge if target is met.
     */
    suspend fun updateProgress(completedTrip: Trip) {
        val entity = challengeDao.getActiveChallenge() ?: return
        if (entity.isCompleted) return

        // Count relevant events from the completed trip
        val tripMetricCount = when (entity.metricType) {
            "HARD_BRAKE" -> completedTrip.hardBrakeCount
            "HARD_ACCELERATION" -> completedTrip.hardAccelCount
            "SHARP_TURN" -> completedTrip.sharpTurnCount
            else -> 0
        }

        val newProgress = entity.progressCount + tripMetricCount
        val isComplete = newProgress <= entity.targetCount && entity.targetCount == 0
                || (entity.targetCount > 0 && newProgress <= entity.targetCount)

        val updatedEntity = entity.copy(
            progressCount = newProgress,
            isCompleted = isComplete,
            completedAtEpochMs = if (isComplete) System.currentTimeMillis() else null,
        )
        challengeDao.updateChallenge(updatedEntity)

        if (isComplete && entity.rewardBadgeType != null) {
            awardBadge(entity.rewardBadgeType)
        }

        Log.d(TAG, "Challenge progress: $newProgress/${entity.targetCount} — completed=$isComplete")
    }

    /**
     * Evaluates all badge criteria against the driver's lifetime stats.
     * Call after each trip ends.
     */
    suspend fun checkAndAwardBadges(
        allTrips: List<Trip>,
        recentTrips: List<Trip>,
    ) {
        if (allTrips.isEmpty()) return

        // FIRST_TRIP
        if (allTrips.size == 1) {
            awardBadge(BadgeType.FIRST_TRIP.name)
        }

        // ECO_WARRIOR: 5 consecutive trips ≥ 90
        val hasEcoWarrior = allTrips.take(5).all { it.ecoScore >= 90 }
        if (hasEcoWarrior) awardBadge(BadgeType.ECO_WARRIOR.name)

        // SMOOTH_OPERATOR: last 3 trips with 0 hard brakes
        val hasSmoothOp = allTrips.take(3).all { it.hardBrakeCount == 0 }
        if (hasSmoothOp) awardBadge(BadgeType.SMOOTH_OPERATOR.name)

        // FUEL_SAVER: saved 10+ litres vs EPA average (6.4 L/100km) this month
        val monthMs = 30L * 24 * 60 * 60 * 1000
        val monthTrips = allTrips.filter {
            System.currentTimeMillis() - it.startTime.toEpochMilli() < monthMs
        }
        val monthDist = monthTrips.sumOf { it.distanceKm }
        val monthFuel = monthTrips.sumOf { it.fuelConsumedLiters }
        val epaFuel = monthDist * 6.4 / 100.0
        if (epaFuel - monthFuel >= 10.0) awardBadge(BadgeType.FUEL_SAVER.name)
    }

    private suspend fun awardBadge(badgeTypeName: String) {
        val alreadyHas = challengeDao.hasBadge(badgeTypeName) > 0
        if (alreadyHas) return
        val type = try { BadgeType.valueOf(badgeTypeName) } catch (_: Exception) { return }
        challengeDao.insertBadge(
            BadgeEntity(
                type = badgeTypeName,
                title = type.title,
                description = type.description,
                icon = type.icon,
            )
        )
        Log.i(TAG, "Badge awarded: ${type.title}")
    }

    private suspend fun buildFirstTripChallenge(): Challenge? {
        val entity = ChallengeEntity(
            title = "First Steps",
            description = "Complete your first full trip with EcoDrive and get your baseline score!",
            targetCount = 1,
            metricType = "FIRST_TRIP",
            durationDays = 7,
            rewardBadgeType = BadgeType.FIRST_TRIP.name,
        )
        challengeDao.insertChallenge(entity)
        return entity.toDomain()
    }

    private suspend fun buildFallbackChallenge(weakestArea: String, avgScore: Double): Challenge? {
        val (title, description, target) = when (weakestArea) {
            "HARD_BRAKE" -> Triple("Smooth Stopper", "Complete 3 trips with fewer than 2 hard braking events each.", 6)
            "HARD_ACCELERATION" -> Triple("Gentle Starter", "Keep total hard accelerations under 5 across your next 3 trips.", 5)
            else -> Triple("Corner Smoother", "Complete your next 3 trips with fewer than 3 sharp turns each.", 9)
        }
        val entity = ChallengeEntity(
            title = title,
            description = description,
            targetCount = target,
            metricType = weakestArea,
            durationDays = 7,
        )
        challengeDao.insertChallenge(entity)
        return entity.toDomain()
    }

    private fun ChallengeEntity.toDomain() = Challenge(
        id = id,
        title = title,
        description = description,
        targetCount = targetCount,
        progressCount = progressCount,
        metricType = try { DrivingEventType.valueOf(metricType) } catch (_: Exception) { DrivingEventType.HARD_BRAKE },
        durationDays = durationDays,
        createdAtEpochMs = createdAtEpochMs,
        completedAtEpochMs = completedAtEpochMs,
        isCompleted = isCompleted,
        rewardBadgeType = try { rewardBadgeType?.let { BadgeType.valueOf(it) } } catch (_: Exception) { null },
    )
}
