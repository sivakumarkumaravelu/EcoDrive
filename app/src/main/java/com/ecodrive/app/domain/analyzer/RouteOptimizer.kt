package com.ecodrive.app.domain.analyzer

import android.location.Location
import com.ecodrive.app.domain.model.MapRoute
import com.ecodrive.app.domain.model.Vehicle
import com.google.android.gms.maps.model.LatLng
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Calculates eco-metrics for predicted routes.
 * Compares alternative routes based on estimated fuel consumption and CO2 emissions.
 */
@Singleton
class RouteOptimizer @Inject constructor(
    private val fuelEngine: FuelEstimationEngine
) {

    data class RouteEcoMetrics(
        val estimatedFuelLiters: Double,
        val estimatedCo2Kg: Double,
        val averageGradePercent: Double,
        val distanceKm: Double,
        val durationMinutes: Int,
        val ecoScore: Int
    )

    /**
     * Estimates eco-metrics for a given route option.
     *
     * Distance and duration come directly from the routing API (OSRM or Google).
     * Fuel consumption is estimated from vehicle parameters and road grade.
     * When no elevation data is available (i.e. using OSRM without a Google API key),
     * grade is assumed to be 0% (flat terrain) — still produces valid fuel estimates.
     */
    fun calculateEcoMetrics(
        route: MapRoute,
        elevations: List<Double>,
        vehicle: Vehicle
    ): RouteEcoMetrics {
        val points = route.points

        // Guard only on missing route geometry — elevation is optional.
        // Without points we have nothing to work with.
        if (points.isEmpty()) {
            return RouteEcoMetrics(0.0, 0.0, 0.0, 0.0, 0, 0)
        }

        val distanceKm = route.distanceMeters / 1000.0
        val durationHours = route.durationSeconds / 3600.0
        val avgSpeedMps = if (route.durationSeconds > 0) {
            (distanceKm * 1000.0) / route.durationSeconds
        } else {
            0.0
        }

        var totalFuelLiters = 0.0
        var totalGrade = 0.0
        var gradeCount = 0

        val hasElevations = elevations.isNotEmpty()
        val elevationStep = if (hasElevations) points.size.toDouble() / elevations.size else 1.0

        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]

            val segmentDist = calculateDistance(p1, p2)
            if (segmentDist <= 0) continue

            // Use elevation data when available (Google Maps API).
            // Fall back to 0% grade (flat terrain) for OSRM routes — still
            // produces a meaningful fuel estimate, just without hilliness adjustment.
            val clippedGrade = if (hasElevations) {
                val e1Idx = (i / elevationStep).toInt().coerceAtMost(elevations.size - 1)
                val e2Idx = ((i + 1) / elevationStep).toInt().coerceAtMost(elevations.size - 1)
                val grade = ((elevations[e2Idx] - elevations[e1Idx]) / segmentDist) * 100.0
                grade.coerceIn(-15.0, 15.0)
            } else {
                0.0
            }

            totalGrade += clippedGrade
            gradeCount++

            // Estimate fuel rate for this segment at steady average speed, 0 acceleration
            val fuelRateLPerH = fuelEngine.estimateFuelRateLPerH(
                speedMps = avgSpeedMps,
                accelerationMps2 = 0.0,
                roadGradePercent = clippedGrade,
                vehicle = vehicle
            )

            val segmentDurationH = if (avgSpeedMps > 0) segmentDist / avgSpeedMps / 3600.0 else 0.0
            totalFuelLiters += fuelEngine.estimateFuelConsumed(fuelRateLPerH, segmentDurationH * 3600.0)
        }

        // CO2 Estimation: ~2.3 kg CO2 per liter of gasoline
        val co2Factor = when (vehicle.fuelType.name) {
            "DIESEL" -> 2.68
            "ELECTRICITY" -> 0.0 // Simplified: 0 at tailpipe; grid carbon ignored
            else -> 2.31
        }

        val avgGrade = if (gradeCount > 0) totalGrade / gradeCount else 0.0
        val fuelPer100Km = if (distanceKm > 0) (totalFuelLiters / distanceKm) * 100.0 else 0.0
        val penalty = (fuelPer100Km * 5.0) + (abs(avgGrade) * 2.0)
        val ecoScore = (100.0 - penalty).toInt().coerceIn(0, 100)

        return RouteEcoMetrics(
            estimatedFuelLiters = totalFuelLiters,
            estimatedCo2Kg = totalFuelLiters * co2Factor,
            averageGradePercent = avgGrade,
            distanceKm = distanceKm,
            durationMinutes = route.durationSeconds / 60,
            ecoScore = ecoScore
        )
    }

    private fun calculateDistance(p1: LatLng, p2: LatLng): Double {
        val results = FloatArray(1)
        Location.distanceBetween(p1.latitude, p1.longitude, p2.latitude, p2.longitude, results)
        return results[0].toDouble()
    }
}
