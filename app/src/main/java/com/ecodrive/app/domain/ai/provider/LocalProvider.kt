package com.ecodrive.app.domain.ai.provider

import com.ecodrive.app.domain.ai.config.AiConfig
import com.ecodrive.app.domain.analyzer.LocalEcoCoach
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Fallback provider using the rich [LocalEcoCoach] rule-based engine.
 *
 * Instead of returning static strings, this provider parses the prompt
 * to extract key metrics and delegates to [LocalEcoCoach] for context-aware
 * responses that are genuinely useful even without a network connection.
 */
@Singleton
class LocalProvider @Inject constructor(
    private val localEcoCoach: LocalEcoCoach
) : AiProvider {

    override val name: String = "LOCAL"

    override suspend fun generateRealTimeTip(prompt: String, model: String?): String? {
        // Parse key metrics from the prompt using simple regex
        // AiCoachService embeds the unit system label in the prompt
        val useMetric = !prompt.contains("Imperial", ignoreCase = true)
        val speed = extractDouble(prompt, "Speed: (\\d+\\.?\\d*) (?:km/h|mph)") ?: 60.0
        // Convert back to km/h if the speed was displayed in mph
        val speedKmh = if (useMetric) speed else com.ecodrive.app.util.UnitConverter.mphToKmh(speed)
        val accel = extractDouble(prompt, "Acceleration: (-?\\d+\\.?\\d*) m/s") ?: 0.0
        val ecoScore = extractInt(prompt, "Eco Score: (\\d+)/100") ?: 70
        val isIdle = prompt.contains("idle", ignoreCase = true) || speedKmh < 2.0

        return localEcoCoach.getRealTimeTip(
            speedKmh = speedKmh,
            longitudinalAccelMps2 = accel,
            lateralAccelMps2 = 0.0,
            ecoScore = ecoScore,
            isIdle = isIdle,
            useMetric = useMetric,
        )
    }

    override suspend fun generateTripInsight(prompt: String, model: String?): String? {
        // For trip insight, extract score and return a structured local response
        val score = extractInt(prompt, "Eco Score: (\\d+)/100") ?: 70
        val brakes = extractInt(prompt, "Hard Brakes: (\\d+)") ?: 0
        val accels = extractInt(prompt, "Hard Accels: (\\d+)") ?: 0
        val idleMins = extractInt(prompt, "Idle: (\\d+) min") ?: 0

        return buildString {
            append("**Summary**: Your score of $score/100 reflects your driving habits this trip.\n\n")
            append("**Key Moments**: ")
            when {
                brakes > 3 -> append("$brakes hard braking events were the main factor — anticipating stops earlier will help most.")
                accels > 3 -> append("$accels rapid acceleration events detected — smoother starts are the biggest win here.")
                idleMins > 2 -> append("$idleMins minutes of idling detected — turning off the engine during long stops saves fuel.")
                score >= 85 -> append("Smooth, consistent driving throughout. Very few inefficiency events.")
                else -> append("Steady driving with room to improve on smoothness and consistency.")
            }
            append("\n\n**Improvement Plan**: ")
            append(
                when {
                    brakes > 3 -> "Increase following distance by 1 car length and start braking earlier. Target: <2 hard brakes next trip."
                    accels > 3 -> "Try the 'count to 5' technique when pulling away from stops. Target: <2 hard accels next trip."
                    score < 70 -> "Focus on one improvement at a time. This week: eliminate excessive speed events."
                    else -> "Maintain your smooth habits. Try using cruise control on the highway for better consistency."
                }
            )
        }
    }

    override suspend fun generateWeeklyReport(prompt: String, model: String?): String? {
        val score = extractInt(prompt, "Recent Eco Score: (\\d+)/100") ?: 70
        val trend = extractDouble(prompt, "Trend: (-?\\d+\\.?\\d*) pts") ?: 0.0
        val brakes = extractInt(prompt, "HARD_BRAKE=(\\d+)") ?: 0
        val accels = extractInt(prompt, "HARD_ACCELERATION=(\\d+)") ?: 0

        return localEcoCoach.getWeeklySummary(
            avgScore = score,
            scoreTrend = trend,
            hardBrakes = brakes,
            hardAccels = accels,
            trips = extractInt(prompt, "(\\d+) trips") ?: 0,
        )
    }

    override suspend fun generateAnalyticsSummary(prompt: String, model: String?): String? {
        val avgScore = extractInt(prompt, "Average Eco Score: (\\d+)/100") ?: 70
        val totalTrips = extractInt(prompt, "Total Trips: (\\d+)") ?: 0
        // AnalyticsInsightGenerator embeds the fuel unit in the prompt
        val useMetric = !prompt.contains("gallons", ignoreCase = true)
        val fuelUnit = if (useMetric) "litres" else "gallons"
        val fuelSaved = extractDouble(prompt, "Fuel Saved.*?: (-?\\d+\\.?\\d*) (?:L|gallons)") ?: 0.0
        val brakes = extractInt(prompt, "Hard Brakes: (\\d+)") ?: 0

        return buildString {
            val label = when {
                avgScore >= 85 -> "excellent"
                avgScore >= 70 -> "good"
                avgScore >= 55 -> "developing"
                else -> "needs attention"
            }
            append("Your $label average eco score of $avgScore/100 across $totalTrips trips shows ")
            if (fuelSaved > 0) {
                append("you've saved approximately ${"%.1f".format(fuelSaved)} $fuelUnit of fuel vs an average vehicle — great progress! ")
            } else {
                append("consistent driving habits. ")
            }
            if (brakes > 10) {
                append("Reducing hard braking events ($brakes total) is your biggest improvement opportunity.")
            } else {
                append("Keep focusing on smooth, consistent throttle control for continued improvements.")
            }
        }
    }

    override suspend fun generateConversationalResponse(
        messages: List<Pair<String, Boolean>>,
        systemPrompt: String,
        model: String?
    ): String? {
        val lastUserMessage = messages.lastOrNull { it.second }?.first ?: return null
        return when {
            lastUserMessage.contains("brake", ignoreCase = true) ->
                "Hard braking is the #1 fuel waster. Try increasing your following distance and looking 12 seconds ahead. Start coasting when you spot a red light — it makes a huge difference."

            lastUserMessage.contains("fuel", ignoreCase = true) || lastUserMessage.contains("mpg", ignoreCase = true) ->
                "Fuel efficiency is most affected by speed, acceleration style, and idling. Highway driving at 80-90 km/h uses far less fuel than 120 km/h. City efficiency improves dramatically with smooth, anticipatory driving."

            lastUserMessage.contains("score", ignoreCase = true) ->
                "Your Eco Score is calculated from 6 factors: acceleration smoothness, braking frequency, speed consistency, cornering intensity, idle time, and average speed. Braking and acceleration typically have the biggest impact."

            lastUserMessage.contains("tip", ignoreCase = true) || lastUserMessage.contains("advice", ignoreCase = true) ->
                "Top 3 eco-driving tips: 1) Keep following distance ≥3 seconds so you can coast more. 2) Accelerate gently — imagine an egg under the pedal. 3) Use engine braking by lifting off early before stops."

            else ->
                "I'm your local eco-driving coach. I can advise on braking technique, fuel efficiency, speed consistency, or how your Eco Score is calculated. What would you like to know?"
        }
    }

    // ── Prompt Parsing Helpers ────────────────────────────────────

    private fun extractDouble(text: String, pattern: String): Double? {
        return Regex(pattern).find(text)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
    }

    private fun extractInt(text: String, pattern: String): Int? {
        return Regex(pattern).find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}
