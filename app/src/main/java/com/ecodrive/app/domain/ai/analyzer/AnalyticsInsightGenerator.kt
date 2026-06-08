package com.ecodrive.app.domain.ai.analyzer

import com.ecodrive.app.domain.ai.service.AiManager

import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.ui.screens.analytics.AnalyticsViewModel.AnalyticsState
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that generates natural language analytics summaries,
 * trends, and forecasts based on aggregated trip data.
 */
@Singleton
class AnalyticsInsightGenerator @Inject constructor(
    private val aiManager: AiManager,
    private val preferenceManager: PreferenceManager,
) {
    /**
     * Generates a narrative summary of driving trends.
     */
    suspend fun generateSummary(state: AnalyticsState, useMetric: Boolean): String? {
        if (state.totalTrips == 0) return null

        val distanceUnit = if (useMetric) "km" else "miles"
        val fuelEfficiencyUnit = if (useMetric) "L/100km" else "mpg"
        val fuelUnit = if (useMetric) "L" else "gallons"

        val displayDistance = if (useMetric) state.totalDistanceKm else com.ecodrive.app.util.UnitConverter.kmToMiles(state.totalDistanceKm)
        val displayEfficiency = if (useMetric) state.avgFuelEfficiency else com.ecodrive.app.util.UnitConverter.l100kmToMpg(state.avgFuelEfficiency)
        val displayFuelSaved = if (useMetric) state.fuelSavedEstimate else com.ecodrive.app.util.UnitConverter.litersToGallons(state.fuelSavedEstimate)

        val prompt = """
            You are an Eco-Driving Analytics Expert. Analyze the following driving trends for the last ${state.selectedRange.label} and provide a insightful narrative summary (max 4 sentences).
            
            CRITICAL: Use $distanceUnit, $fuelEfficiencyUnit, and $fuelUnit for all mentions of distance, efficiency, and fuel volume.
            
            Stats:
            - Average Eco Score: ${state.avgEcoScore}/100
            - Total Trips: ${state.totalTrips}
            - Total Distance: ${"%.1f".format(displayDistance)} $distanceUnit
            - Avg Fuel Efficiency: ${"%.1f".format(displayEfficiency)} $fuelEfficiencyUnit
            - Fuel Saved (vs average vehicle): ${"%.1f".format(displayFuelSaved)} $fuelUnit
            
            Behaviors:
            - Hard Brakes: ${state.totalHardBrakes}
            - Hard Accels: ${state.totalHardAccels}
            - Sharp Turns: ${state.totalSharpTurns}
            - Total Idle: ${state.totalIdleMinutes} minutes
            
            Identify the biggest area for improvement and celebrate any major wins (like high fuel savings or consistent scores).
        """.trimIndent()

        return aiManager.generateAnalyticsSummary(prompt)
    }
}
