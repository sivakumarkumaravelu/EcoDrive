package com.ecodrive.app.domain.ai.analyzer

import com.ecodrive.app.domain.ai.service.AiManager

import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.ui.screens.routeplanner.RoutePlannerViewModel.RouteWithMetrics
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service that generates natural language insights for planned routes,
 * explaining why certain options are more eco-friendly.
 */
@Singleton
class RouteInsightGenerator @Inject constructor(
    private val aiManager: AiManager,
    private val preferenceManager: PreferenceManager,
) {
    /**
     * Generates a comparison of routes and why one is better.
     */
    suspend fun generateComparison(routes: List<RouteWithMetrics>, useMetric: Boolean): String? {
        if (routes.isEmpty()) return null

        val distanceUnit = if (useMetric) "km" else "miles"
        val fuelUnit = if (useMetric) "L" else "gallons"

        val prompt = """
            You are an Eco-Driving Route Analyst. Compare these routes and recommend the greenest.
            
            CRITICAL RULES:
            - Use $distanceUnit for distance and $fuelUnit for fuel.
            - Reply with EXACTLY 1-2 SHORT sentences. Maximum 150 characters total.
            - Be specific: mention route name and exact savings.
            
            Routes:
            ${routes.mapIndexed { i, r ->
                val displayDistance = if (useMetric) r.metrics.distanceKm else com.ecodrive.app.util.UnitConverter.kmToMiles(r.metrics.distanceKm)
                val displayFuel = if (useMetric) r.metrics.estimatedFuelLiters else com.ecodrive.app.util.UnitConverter.litersToGallons(r.metrics.estimatedFuelLiters)
                "Route ${i + 1} (${r.route.summary}): ${"%.1f".format(displayDistance)}$distanceUnit, " +
                "${r.metrics.durationMinutes}min, ${"%.2f".format(displayFuel)}$fuelUnit fuel"
            }.joinToString("\n")}
            
            Example output (follow this length): "Route 1 saves 0.3L vs Route 2. It's slightly longer but avoids highway fuel burn."
        """.trimIndent()

        return aiManager.generateTripInsight(prompt)
    }

    /**
     * Resolves a natural language destination to LatLng using Gemini.
     * Useful when no geocoding API is configured or for fuzzy searches.
     */
    suspend fun resolveDestination(query: String, near: String): String? {
        val prompt = """
            Convert the following destination query into a "latitude,longitude" string.
            Query: $query
            Reference Location (near): $near
            
            Return ONLY the "lat,lng" string (e.g., "37.422,-122.084").
            If you cannot resolve it, return "null".
        """.trimIndent()

        return aiManager.generateTripInsight(prompt)
    }
}
