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
    suspend fun generateSummary(state: AnalyticsState): String? {
        if (state.totalTrips == 0) return null

        val prompt = """
            You are an Eco-Driving Analytics Expert. Analyze the following driving trends for the last ${state.selectedRange.label} and provide a insightful narrative summary (max 4 sentences).
            
            Stats:
            - Average Eco Score: ${state.avgEcoScore}/100
            - Total Trips: ${state.totalTrips}
            - Total Distance: ${"%.1f".format(state.totalDistanceKm)} km
            - Avg Fuel Efficiency: ${"%.1f".format(state.avgFuelEfficiency)} L/100km
            - Fuel Saved (vs average vehicle): ${"%.1f".format(state.fuelSavedEstimate)} L
            
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
