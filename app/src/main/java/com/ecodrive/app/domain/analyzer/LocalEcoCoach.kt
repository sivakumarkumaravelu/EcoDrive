package com.ecodrive.app.domain.analyzer

import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.domain.model.Trip
import com.ecodrive.app.domain.model.DrivingEvent
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * A rich rule-based coaching engine that provides driving insights locally.
 * Used as a fallback when AI services are unavailable or as supplementary advice.
 *
 * Unlike the previous static-string version, this engine:
 * - Parses actual driving data to generate context-aware tips
 * - Adapts to time-of-day and season
 * - Rotates through a variety of tip variants to prevent repetition
 */
@Singleton
class LocalEcoCoach @Inject constructor() {

    private var lastTipIndex = -1

    // ── Real-Time Tips ───────────────────────────────────────────

    /**
     * Returns a context-aware real-time driving tip based on current driving state.
     */
    fun getRealTimeTip(
        speedKmh: Double,
        longitudinalAccelMps2: Double,
        lateralAccelMps2: Double,
        ecoScore: Int,
        isIdle: Boolean,
        useMetric: Boolean = true,
    ): String {
        // Prepare display speed and unit label once
        val displaySpeed = if (useMetric) speedKmh else com.ecodrive.app.util.UnitConverter.kmhToMph(speedKmh)
        val speedUnit = if (useMetric) "km/h" else "mph"

        // Speed thresholds converted to km/h internally; display values in user's unit
        return when {
            isIdle ->
                "Engine idling burns fuel. If stopped for >30s, consider switching off."

            speedKmh > 115 ->
                "Above ${"%.0f".format(if (useMetric) 115.0 else 71.0)} $speedUnit, aerodynamic drag doubles fuel use. Ease back."

            speedKmh > 100 ->
                "Highway efficiency peaks at ${if (useMetric) "80-100 km/h" else "50-62 mph"}. You're slightly above the sweet spot."

            longitudinalAccelMps2 > 2.5 ->
                "Gentle acceleration — aim for 0-${if (useMetric) "100 km/h" else "60 mph"} in 15+ seconds for best efficiency."

            longitudinalAccelMps2 > 1.5 ->
                "Smooth starts save up to 25% fuel. Imagine an egg under the pedal."

            longitudinalAccelMps2 < -3.0 ->
                "Hard braking wastes kinetic energy. Look further ahead and coast to red lights."

            longitudinalAccelMps2 < -2.0 ->
                "Anticipate stops — start coasting before junctions."

            abs(lateralAccelMps2) > 2.5 ->
                "Sharp cornering uses extra fuel. Slow before the turn, not during it."

            ecoScore >= 90 ->
                getPositiveTip()

            speedKmh in 70.0..90.0 ->
                "You're in the efficiency sweet spot. Maintain this steady cruise."

            speedKmh in 50.0..70.0 ->
                "Smooth city driving. Anticipate traffic flow to keep momentum."

            ecoScore < 60 ->
                "Focus on smooth transitions — no sudden pedal changes for 2 minutes."

            else ->
                getDayTimeTip()
        }
    }

    // ── Post-Trip Insight ────────────────────────────────────────

    /**
     * Generates a rich multi-factor insight after a trip completes.
     */
    fun getInsight(trip: Trip, events: List<DrivingEvent>, useMetric: Boolean = true): String {
        val hardBrakes = events.count { it.type == DrivingEventType.HARD_BRAKE }
        val hardAccels = events.count { it.type == DrivingEventType.HARD_ACCELERATION }
        val sharpTurns = events.count { it.type == DrivingEventType.SHARP_TURN }
        val idleSeconds = events.filter { it.type == DrivingEventType.EXCESSIVE_IDLE }
            .sumOf { it.value.toLong() }
        val tripMinutes = (trip.durationSeconds / 60.0).toInt().coerceAtLeast(1)

        return when {
            trip.ecoScore >= 95 ->
                buildExcellentInsight(trip, useMetric)

            trip.ecoScore >= 85 ->
                buildGoodInsight(trip, hardBrakes, hardAccels, useMetric)

            hardBrakes > 4 ->
                buildBrakingInsight(trip, hardBrakes, tripMinutes)

            hardAccels > 4 ->
                buildAccelInsight(trip, hardAccels, tripMinutes)

            idleSeconds > 120 ->
                buildIdleInsight(trip, idleSeconds)

            sharpTurns > 4 ->
                buildCorneringInsight(trip, sharpTurns)

            trip.fuelEfficiencyLPer100Km > 10.0 ->
                buildFuelInsight(trip, useMetric)

            trip.ecoScore < 60 ->
                buildLowScoreInsight(trip, hardBrakes, hardAccels, idleSeconds)

            else ->
                buildAverageInsight(trip)
        }
    }

    // ── Weekly Summary ───────────────────────────────────────────

    /**
     * Generates a contextual weekly coaching summary from aggregated stats.
     */
    fun getWeeklySummary(
        avgScore: Int,
        scoreTrend: Double,
        hardBrakes: Int,
        hardAccels: Int,
        trips: Int,
    ): String {
        val trendWord = when {
            scoreTrend > 5 -> "significantly improved"
            scoreTrend > 0 -> "slightly improved"
            scoreTrend < -5 -> "declined noticeably"
            scoreTrend < 0 -> "slightly dipped"
            else -> "stayed consistent"
        }
        val worstBehavior = when {
            hardBrakes > hardAccels && hardBrakes > 5 -> "hard braking ($hardBrakes events)"
            hardAccels > 5 -> "aggressive acceleration ($hardAccels events)"
            else -> null
        }
        val scoreLabel = when {
            avgScore >= 85 -> "excellent"
            avgScore >= 70 -> "good"
            avgScore >= 55 -> "average"
            else -> "low"
        }

        return buildString {
            append("Your $scoreLabel weekly average of $avgScore/100 has $trendWord. ")
            if (worstBehavior != null) {
                append("Your biggest opportunity is reducing $worstBehavior. ")
                append("Try to anticipate stops and use gentle inputs to improve. ")
            } else {
                append("Your driving is well-rounded — keep maintaining smooth habits. ")
            }
            append("You completed $trips trips this week. ")
            if (scoreTrend > 3) append("Great momentum — keep it going!") else append("Every trip is a chance to improve.")
        }
    }

    // ── Private Insight Builders ─────────────────────────────────

    private fun buildExcellentInsight(trip: Trip, useMetric: Boolean = true): String {
        val effStr = com.ecodrive.app.util.UnitConverter.formatFuelEfficiency(trip.fuelEfficiencyLPer100Km, useMetric)
        return "🌟 Exceptional trip! Your ${trip.ecoScore}/100 score puts you in the elite tier of eco-drivers. " +
        "Fuel efficiency of $effStr is outstanding. " +
        "You're already at the top — focus on consistency to keep this standard every trip."
    }

    private fun buildGoodInsight(trip: Trip, hardBrakes: Int, hardAccels: Int, useMetric: Boolean = true): String {
        val minor = when {
            hardBrakes > 2 -> "Reducing the $hardBrakes hard brake${if (hardBrakes > 1) "s" else ""} could push you into the 90+ club."
            hardAccels > 2 -> "Smoother starts — you had $hardAccels rapid acceleration${if (hardAccels > 1) "s" else ""}."
            else -> "Maintain this level and you'll consistently hit 90+ scores."
        }
        val effStr = com.ecodrive.app.util.UnitConverter.formatFuelEfficiency(trip.fuelEfficiencyLPer100Km, useMetric)
        return "✅ Great trip at ${trip.ecoScore}/100! $minor Your efficiency of $effStr is above average."
    }

    private fun buildBrakingInsight(trip: Trip, hardBrakes: Int, tripMinutes: Int): String =
        "Your score of ${trip.ecoScore}/100 was pulled down by $hardBrakes hard braking event${if (hardBrakes > 1) "s" else ""} " +
        "across $tripMinutes minutes. Each hard brake wastes momentum you paid for with fuel during acceleration. " +
        "Try the '3-second rule': when you see a potential stop, start coasting 3 seconds earlier. " +
        "This single habit can add 5-10 points to your score."

    private fun buildAccelInsight(trip: Trip, hardAccels: Int, tripMinutes: Int): String =
        "Score: ${trip.ecoScore}/100. You had $hardAccels rapid acceleration${if (hardAccels > 1) "s" else ""} over $tripMinutes minutes. " +
        "Aggressive starts can use 3x more fuel than smooth acceleration. " +
        "Try counting to 5 every time you pull away from a stop. Smooth = efficient = less fuel."

    private fun buildIdleInsight(trip: Trip, idleSeconds: Long): String {
        val idleMinutes = idleSeconds / 60
        return "Trip score: ${trip.ecoScore}/100. You idled for ~${idleMinutes} minute${if (idleMinutes > 1) "s" else ""} total. " +
               "Modern engines use less fuel to restart than 30 seconds of idling. " +
               "If you're parked or stuck in traffic for more than 30 seconds, switching off saves real fuel."
    }

    private fun buildCorneringInsight(trip: Trip, sharpTurns: Int): String =
        "Score: ${trip.ecoScore}/100 — $sharpTurns sharp turn${if (sharpTurns > 1) "s" else ""} detected. " +
        "The key to smooth cornering is 'slow in, fast out': brake before the apex, not during the turn. " +
        "This also reduces tire wear and is safer in wet conditions."

    private fun buildFuelInsight(trip: Trip, useMetric: Boolean = true): String {
        val effStr = com.ecodrive.app.util.UnitConverter.formatFuelEfficiency(trip.fuelEfficiencyLPer100Km, useMetric)
        val speedThreshold = if (useMetric) "100 km/h" else "62 mph"
        return "Your efficiency was $effStr this trip (score: ${trip.ecoScore}/100). " +
        "High fuel use often points to excessive speed or aggressive driving. " +
        "Try staying below $speedThreshold on highways — every 10 ${if (useMetric) "km/h" else "mph"} over that adds ~8% fuel consumption."
    }

    private fun buildLowScoreInsight(trip: Trip, brakes: Int, accels: Int, idleSeconds: Long): String {
        val issues = buildList {
            if (brakes > 3) add("$brakes hard brakes")
            if (accels > 3) add("$accels rapid accelerations")
            if (idleSeconds > 120) add("${idleSeconds / 60} minutes of idling")
        }
        val issueText = if (issues.isEmpty()) "multiple factors" else issues.joinToString(", ")
        return "Score: ${trip.ecoScore}/100. This trip was challenging due to $issueText. " +
               "Focus on one improvement at a time — start with smoother braking. " +
               "Even a 5-point improvement next trip is real progress."
    }

    private fun buildAverageInsight(trip: Trip): String =
        "Solid trip at ${trip.ecoScore}/100. Your driving is consistent — to break into the next tier, " +
        "try to keep speed variance low on the highway (use cruise control if available) " +
        "and aim to anticipate traffic flow rather than reacting to it."

    // ── Utility ──────────────────────────────────────────────────

    private val positiveTips = listOf(
        "🌿 Peak efficiency! You're in eco-driving elite territory.",
        "✨ Brilliant smoothness — your technique is textbook eco-driving.",
        "🏆 Top performance! Keep this momentum going.",
        "⭐ Your consistency is saving real fuel. Excellent work!",
    )

    private fun getPositiveTip(): String {
        lastTipIndex = (lastTipIndex + 1) % positiveTips.size
        return positiveTips[lastTipIndex]
    }

    private fun getDayTimeTip(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 6..9 -> "Morning rush: anticipate light changes early to avoid unnecessary stops."
            in 10..15 -> "Good driving window — moderate traffic. Maintain steady speed."
            in 16..19 -> "Peak hours. Keep 3+ seconds following distance to coast more."
            in 20..23 -> "Night driving: visibility is lower. Smooth, predictable inputs keep you safe."
            else -> "Late-night driving: quiet roads — maintain a steady cruise for peak efficiency."
        }
    }
}
