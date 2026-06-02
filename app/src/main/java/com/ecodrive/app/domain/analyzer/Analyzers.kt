package com.ecodrive.app.domain.analyzer

import android.util.Log
import com.ecodrive.app.data.local.dao.FuelCalibrationDao
import com.ecodrive.app.data.local.entity.FuelCalibrationEntity
import com.ecodrive.app.domain.model.*
import com.ecodrive.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Analyzes driving patterns from phone sensor data.
 */
@Singleton
class DrivingPatternAnalyzer @Inject constructor() {

    private var previousMetrics: DrivingMetrics? = null
    private var idleStartTimeMs: Long? = null
    private var speedHistory = mutableListOf<Double>()

    fun analyze(metrics: DrivingMetrics, tripId: Long): List<DrivingEvent> {
        val events = mutableListOf<DrivingEvent>()
        if (metrics.isMoving) {
            speedHistory.add(metrics.speedKmh)
            if (speedHistory.size > 120) speedHistory.removeAt(0)
        }

        if (metrics.longitudinalAccelMps2 < -Constants.HARD_BRAKE_THRESHOLD) {
            events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.HARD_BRAKE, value = metrics.longitudinalAccelMps2, speedAtEvent = metrics.speedKmh, latitude = metrics.latitude, longitude = metrics.longitude, description = "Hard braking: %.1f m/s²".format(metrics.longitudinalAccelMps2)))
        }

        if (metrics.longitudinalAccelMps2 > Constants.HARD_ACCEL_THRESHOLD) {
            events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.HARD_ACCELERATION, value = metrics.longitudinalAccelMps2, speedAtEvent = metrics.speedKmh, latitude = metrics.latitude, longitude = metrics.longitude, description = "Hard acceleration: +%.1f m/s²".format(metrics.longitudinalAccelMps2)))
        }

        if (abs(metrics.lateralAccelMps2) > Constants.SHARP_TURN_THRESHOLD) {
            events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.SHARP_TURN, value = metrics.lateralAccelMps2, speedAtEvent = metrics.speedKmh, latitude = metrics.latitude, longitude = metrics.longitude, description = "Sharp turn: %.1f m/s² lateral".format(metrics.lateralAccelMps2)))
        }

        if (metrics.speedKmh > Constants.SPEED_EXCESSIVE_KMH) {
            events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.EXCESSIVE_SPEED, value = metrics.speedKmh, speedAtEvent = metrics.speedKmh, latitude = metrics.latitude, longitude = metrics.longitude, description = "Excessive speed: %.0f km/h".format(metrics.speedKmh)))
        }

        if (metrics.isIdle) {
            if (idleStartTimeMs == null) {
                idleStartTimeMs = metrics.timestamp.toEpochMilli()
            } else {
                val idleDuration = (metrics.timestamp.toEpochMilli() - idleStartTimeMs!!) / 1000
                if (idleDuration > Constants.IDLE_WARNING_SECONDS && idleDuration % 30 == 0L) {
                    events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.EXCESSIVE_IDLE, value = idleDuration.toDouble(), speedAtEvent = 0.0, latitude = metrics.latitude, longitude = metrics.longitude, description = "Idling for ${idleDuration}s"))
                }
            }
        } else {
            idleStartTimeMs = null
        }

        previousMetrics = metrics
        return events
    }

    fun getSpeedStdDeviation(): Double {
        if (speedHistory.size < 10) return 0.0
        val mean = speedHistory.average()
        val variance = speedHistory.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }

    fun reset() {
        previousMetrics = null
        idleStartTimeMs = null
        speedHistory.clear()
    }
}

/**
 * Universal Power-to-Fuel estimation engine.
 *
 * Uses physics (Vehicle Specific Power) and energy density to estimate
 * fuel consumption across any vehicle model and fuel type.
 */
@Singleton
class FuelEstimationEngine @Inject constructor(
    private val fuelCalibrationDao: FuelCalibrationDao,
) {
    companion object {
        private const val TAG = "FuelEstimationEngine"
    }

    private var calibrationFactor = Constants.DEFAULT_CALIBRATION_FACTOR
    private val calibrationHistory = mutableListOf<FuelCalibrationPoint>()

    init {
        loadCalibrationFactorFromDatabase()
    }

    private fun loadCalibrationFactorFromDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val factor = fuelCalibrationDao.getAverageCorrectionRatio(Constants.CALIBRATION_WINDOW_TRIPS)
                if (factor != null && factor > 0) {
                    calibrationFactor = factor.coerceIn(0.5, 2.0)
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load calibration factor: ${e.message}")
            }
        }
    }

    /**
     * Estimate instantaneous fuel consumption rate in L/h.
     */
    fun estimateFuelRateLPerH(
        speedMps: Double,
        accelerationMps2: Double,
        roadGradePercent: Double,
        vehicle: Vehicle
    ): Double {
        // 1. Idle estimation
        if (speedMps < 0.2) {
            // Idle consumption roughly proportional to displacement: ~0.2 L/h per liter of displacement
            return (vehicle.engineDisplacementCc / 1000.0) * 0.4 * calibrationFactor
        }

        // 2. Physics-based Power (VSP) calculation (Watts per kg)
        // VSP = v * (a(1+eps) + g*sin(theta) + g*Cr) + (0.5*rho*Cd*A*v^3)/m
        val g = Constants.GRAVITY
        val rho = Constants.AIR_DENSITY
        val eps = Constants.ROTATING_MASS_FACTOR
        val theta = atan(roadGradePercent / 100.0)

        val vsp = speedMps * (
            accelerationMps2 * (1 + eps) +
            g * sin(theta) +
            g * vehicle.rollingResistance * cos(theta)
        ) + (0.5 * rho * vehicle.dragCoefficient * vehicle.frontalAreaM2 * speedMps.pow(3)) / vehicle.massKg

        // 3. Convert VSP to Total Power (Watts)
        val powerWatts = (vsp * vehicle.massKg).coerceAtLeast(0.0)

        // 4. Efficiency Factor (eta)
        // Powertrain efficiency varies by type and load.
        var eta = when (vehicle.vehicleType) {
            VehicleType.ICE -> 0.25
            VehicleType.HYBRID -> 0.35
            VehicleType.PLUG_IN_HYBRID -> 0.35
            VehicleType.ELECTRIC -> 0.85
        }

        // Apply low-speed efficiency penalty for ICE, bonus for hybrids
        val speedKmh = speedMps * 3.6
        if (vehicle.vehicleType == VehicleType.ICE && speedKmh < 40) {
            eta *= (0.6 + 0.4 * (speedKmh / 40.0)) // Efficiency drops at low speeds
        } else if ((vehicle.vehicleType == VehicleType.HYBRID || vehicle.vehicleType == VehicleType.PLUG_IN_HYBRID) && speedKmh < 40) {
            eta *= 1.2 // Hybrid advantage in city
        }

        // 5. Fuel Consumption Rate (L/h)
        // Rate = (Power * 3600) / (eta * EnergyDensity_MJ_per_L * 1,000,000)
        val energyDensityJperL = vehicle.fuelType.energyDensityMJperL * 1_000_000.0
        if (energyDensityJperL <= 0) return 0.0

        val fuelRateLPerH = (powerWatts * 3600.0) / (eta * energyDensityJperL)

        // Apply calibration factor and return
        return fuelRateLPerH * calibrationFactor
    }

    fun estimateFuelConsumed(fuelRateLPerH: Double, durationSeconds: Double): Double {
        return fuelRateLPerH * (durationSeconds / 3600.0)
    }

    fun addCalibrationPoint(point: FuelCalibrationPoint) {
        if (point.distanceKm < Constants.CALIBRATION_MIN_DISTANCE_KM) return
        if (point.actualFuelLiters <= 0 || point.estimatedFuelLiters <= 0) return
        calibrationHistory.add(point)
        while (calibrationHistory.size > Constants.CALIBRATION_WINDOW_TRIPS) {
            calibrationHistory.removeAt(0)
        }
        if (calibrationHistory.size >= 3) {
            calibrationFactor = calibrationHistory.takeLast(Constants.CALIBRATION_WINDOW_TRIPS).map { it.correctionRatio }.average().coerceIn(0.5, 2.0)
            persistCalibrationFactorToDatabase()
        }
    }

    private fun persistCalibrationFactorToDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lastPoint = calibrationHistory.lastOrNull()
                val entity = FuelCalibrationEntity(tripId = lastPoint?.tripId ?: 0, correctionRatio = calibrationFactor, distanceKm = lastPoint?.distanceKm ?: 0.0, actualFuelLiters = lastPoint?.actualFuelLiters ?: 0.0, estimatedFuelLiters = lastPoint?.estimatedFuelLiters ?: 0.0, timestampEpochMs = System.currentTimeMillis())
                fuelCalibrationDao.insert(entity)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist calibration factor: ${e.message}")
            }
        }
    }

    fun getCalibrationFactor(): Double = calibrationFactor
}

/**
 * Calculates the Eco Score based on sensor-derived driving behavior.
 */
@Singleton
class EcoScoreCalculator @Inject constructor() {

    fun calculate(
        hardBrakeCount: Int,
        hardAccelCount: Int,
        sharpTurnCount: Int,
        averageSpeedKmh: Double,
        idleTimePercent: Double,
        speedStdDeviation: Double,
        tripDurationMinutes: Double,
    ): EcoScore {
        val per10Min = if (tripDurationMinutes > 0) 10.0 / tripDurationMinutes else 1.0

        val accelFreq = hardAccelCount * per10Min
        val accelScore = when {
            accelFreq < 0.5 -> 100
            accelFreq < 1.0 -> 85
            accelFreq < 2.0 -> 70
            accelFreq < 4.0 -> 50
            accelFreq < 6.0 -> 30
            else -> 10
        }

        val brakeFreq = hardBrakeCount * per10Min
        val brakeScore = when {
            brakeFreq < 0.5 -> 100
            brakeFreq < 1.0 -> 85
            brakeFreq < 2.0 -> 70
            brakeFreq < 4.0 -> 50
            brakeFreq < 6.0 -> 30
            else -> 10
        }

        val speedScore = when {
            averageSpeedKmh in Constants.SPEED_ECO_MIN_KMH..Constants.SPEED_ECO_MAX_KMH -> 100
            averageSpeedKmh in 40.0..100.0 -> 80
            averageSpeedKmh in 30.0..110.0 -> 60
            averageSpeedKmh < 30.0 -> 50
            else -> 40
        }

        val turnFreq = sharpTurnCount * per10Min
        val corneringScore = when {
            turnFreq < 0.3 -> 100
            turnFreq < 0.8 -> 85
            turnFreq < 1.5 -> 70
            turnFreq < 3.0 -> 50
            else -> 25
        }

        val idleScore = when {
            idleTimePercent < 5.0 -> 100
            idleTimePercent < 10.0 -> 85
            idleTimePercent < 20.0 -> 65
            idleTimePercent < 30.0 -> 45
            else -> 20
        }

        val consistencyScore = when {
            speedStdDeviation < Constants.SPEED_CONSISTENCY_GOOD -> 100
            speedStdDeviation < Constants.SPEED_CONSISTENCY_AVERAGE -> 75
            speedStdDeviation < 20.0 -> 50
            else -> 25
        }

        val overall = (
            accelScore * Constants.WEIGHT_ACCELERATION +
            brakeScore * Constants.WEIGHT_BRAKING +
            speedScore * Constants.WEIGHT_SPEED +
            corneringScore * Constants.WEIGHT_CORNERING +
            idleScore * Constants.WEIGHT_IDLE +
            consistencyScore * Constants.WEIGHT_CONSISTENCY
        ).toInt().coerceIn(0, 100)

        return EcoScore(
            overall = overall,
            accelerationScore = accelScore,
            brakingScore = brakeScore,
            speedScore = speedScore,
            corneringScore = corneringScore,
            idleScore = idleScore,
            consistencyScore = consistencyScore,
        )
    }
}
