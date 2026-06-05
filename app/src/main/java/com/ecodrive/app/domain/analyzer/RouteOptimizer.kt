package com.ecodrive.app.domain.analyzer

import android.location.Location
import com.ecodrive.app.data.remote.GoogleMapsServicesClient
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
     */
    fun calculateEcoMetrics(
        route: GoogleMapsServicesClient.RouteOption,
        elevations: List<Double>,
        vehicle: Vehicle
    ): RouteEcoMetrics {
        val points = route.points
        if (points.isEmpty() || elevations.isEmpty()) {
            return RouteEcoMetrics(0.0, 0.0, 0.0, 0.0, 0, 0)
        }

        val distanceKm = route.distanceMeters / 1000.0
        val durationHours = route.durationSeconds / 3600.0
        val avgSpeedMps = if (durationHours > 0) (distanceKm * 1000.0) / route.durationSeconds else 0.0

        var totalFuelLiters = 0.0
        var totalGrade = 0.0
        var gradeCount = 0

        // We have 'elevations' which correspond to a subset of 'points'.
        // To simplify, we'll iterate through segments and estimate grade.
        // If elevations.size != points.size, we'll interpolate or sample.
        
        val elevationStep = points.size.toDouble() / elevations.size
        
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            
            val segmentDist = calculateDistance(p1, p2)
            if (segmentDist <= 0) continue

            // Estimate elevations for p1 and p2 from the sampled elevations list
            val e1Idx = (i / elevationStep).toInt().coerceAtMost(elevations.size - 1)
            val e2Idx = ((i + 1) / elevationStep).toInt().coerceAtMost(elevations.size - 1)
            
            val e1 = elevations[e1Idx]
            val e2 = elevations[e2Idx]
            
            val grade = ((e2 - e1) / segmentDist) * 100.0
            val clippedGrade = grade.coerceIn(-15.0, 15.0)
            
            totalGrade += clippedGrade
            gradeCount++

            // Estimate fuel rate for this segment
            // Assume steady speed (avgSpeedMps) and 0 acceleration for planning
            val fuelRateLPerH = fuelEngine.estimateFuelRateLPerH(
                speedMps = avgSpeedMps,
                accelerationMps2 = 0.0,
                roadGradePercent = clippedGrade,
                vehicle = vehicle
            )
            
            val segmentDurationH = segmentDist / avgSpeedMps / 3600.0
            totalFuelLiters += fuelEngine.estimateFuelConsumed(fuelRateLPerH, segmentDurationH * 3600.0)
        }

        // CO2 Estimation: ~2.3 kg CO2 per liter of gasoline
        val co2Factor = when (vehicle.fuelType.name) {
            "DIESEL" -> 2.68
            "ELECTRICITY" -> 0.0 // Simplified: user might care about grid carbon, but 0 at tailpipe
            else -> 2.31
        }
        val totalCo2 = totalFuelLiters * co2Factor

        val avgGrade = if (gradeCount > 0) totalGrade / gradeCount else 0.0
        
        // Calculate a predictive Eco Score
        // Higher grade and higher fuel/km lowers the score
        val fuelPer100Km = if (distanceKm > 0) (totalFuelLiters / distanceKm) * 100.0 else 0.0
        val baseScore = 100.0
        val penalty = (fuelPer100Km * 5.0) + (abs(avgGrade) * 2.0)
        val ecoScore = (baseScore - penalty).toInt().coerceIn(0, 100)

        return RouteEcoMetrics(
            estimatedFuelLiters = totalFuelLiters,
            estimatedCo2Kg = totalCo2,
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
