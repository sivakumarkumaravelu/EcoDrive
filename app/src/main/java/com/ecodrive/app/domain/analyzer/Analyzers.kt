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

/**
 * Analyzes driving patterns from phone sensor data.
 * Detects events like hard braking, rapid acceleration,
 * sharp cornering, excessive speed, and idling.
 */
@Singleton
class DrivingPatternAnalyzer @Inject constructor() {

    private var previousMetrics: DrivingMetrics? = null
    private var idleStartTimeMs: Long? = null
    private var speedHistory = mutableListOf<Double>()

    /**
     * Analyze a new metrics snapshot and return any detected events.
     */
    fun analyze(metrics: DrivingMetrics, tripId: Long): List<DrivingEvent> {
        val events = mutableListOf<DrivingEvent>()

        // Track speed history for consistency scoring
        if (metrics.isMoving) {
            speedHistory.add(metrics.speedKmh)
            if (speedHistory.size > 120) speedHistory.removeAt(0) // ~2 min window
        }

        // ── Hard Braking (from accelerometer) ───────────────────
        if (metrics.longitudinalAccelMps2 < -Constants.HARD_BRAKE_THRESHOLD) {
            events.add(
                DrivingEvent(
                    tripId = tripId,
                    type = DrivingEventType.HARD_BRAKE,
                    value = metrics.longitudinalAccelMps2,
                    speedAtEvent = metrics.speedKmh,
                    latitude = metrics.latitude,
                    longitude = metrics.longitude,
                    description = "Hard braking: %.1f m/s²".format(metrics.longitudinalAccelMps2),
                )
            )
        }

        // ── Hard Acceleration (from accelerometer) ──────────────
        if (metrics.longitudinalAccelMps2 > Constants.HARD_ACCEL_THRESHOLD) {
            events.add(
                DrivingEvent(
                    tripId = tripId,
                    type = DrivingEventType.HARD_ACCELERATION,
                    value = metrics.longitudinalAccelMps2,
                    speedAtEvent = metrics.speedKmh,
                    latitude = metrics.latitude,
                    longitude = metrics.longitude,
                    description = "Hard acceleration: +%.1f m/s²".format(metrics.longitudinalAccelMps2),
                )
            )
        }

        // ── Sharp Cornering (from lateral accelerometer) ────────
        if (abs(metrics.lateralAccelMps2) > Constants.SHARP_TURN_THRESHOLD) {
            events.add(
                DrivingEvent(
                    tripId = tripId,
                    type = DrivingEventType.SHARP_TURN,
                    value = metrics.lateralAccelMps2,
                    speedAtEvent = metrics.speedKmh,
                    latitude = metrics.latitude,
                    longitude = metrics.longitude,
                    description = "Sharp turn: %.1f m/s² lateral".format(metrics.lateralAccelMps2),
                )
            )
        }

        // ── Excessive Speed ─────────────────────────────────────
        if (metrics.speedKmh > Constants.SPEED_EXCESSIVE_KMH) {
            events.add(
                DrivingEvent(
                    tripId = tripId,
                    type = DrivingEventType.EXCESSIVE_SPEED,
                    value = metrics.speedKmh,
                    speedAtEvent = metrics.speedKmh,
                    latitude = metrics.latitude,
                    longitude = metrics.longitude,
                    description = "Excessive speed: %.0f km/h".format(metrics.speedKmh),
                )
            )
        }

        // ── Excessive Idle ──────────────────────────────────────
        if (metrics.isIdle) {
            if (idleStartTimeMs == null) {
                idleStartTimeMs = metrics.timestamp.toEpochMilli()
            } else {
                val idleDuration = (metrics.timestamp.toEpochMilli() - idleStartTimeMs!!) / 1000
                if (idleDuration > Constants.IDLE_WARNING_SECONDS && idleDuration % 30 == 0L) {
                    events.add(
                        DrivingEvent(
                            tripId = tripId,
                            type = DrivingEventType.EXCESSIVE_IDLE,
                            value = idleDuration.toDouble(),
                            speedAtEvent = 0.0,
                            latitude = metrics.latitude,
                            longitude = metrics.longitude,
                            description = "Idling for ${idleDuration}s",
                        )
                    )
                }
            }
        } else {
            idleStartTimeMs = null
        }

        previousMetrics = metrics
        return events
    }

    /**
     * Get the current speed standard deviation (for consistency scoring).
     */
    fun getSpeedStdDeviation(): Double {
        if (speedHistory.size < 10) return 0.0
        val mean = speedHistory.average()
        val variance = speedHistory.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }

    /** Reset analyzer state for a new trip. */
    fun reset() {
        previousMetrics = null
        idleStartTimeMs = null
        speedHistory.clear()
    }
}

/**
 * Vehicle Specific Power (VSP) fuel estimation model.
 *
 * Estimates instantaneous fuel consumption using physics:
 *   VSP = v × [a(1+ε) + g×sin(θ) + g×Cr] + ½ρCdAv³/m
 *
 * Pre-loaded with 2023 Toyota Highlander Hybrid specs.
 * Self-calibrates over time using Toyota API fuel level readings.
 * Persists calibration factor across app restarts via database.
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

    // Highlander Hybrid specific: hybrid efficiency bonus at low speeds
    // The electric motor assists significantly below 40 km/h
    private val hybridEfficiencyMap = mapOf(
        0.0 to 0.0,    // Stopped — no fuel
        10.0 to 0.3,   // Very low speed — mostly electric
        20.0 to 0.4,   // Low speed — significant electric assist
        30.0 to 0.55,  // City speed — good hybrid mix
        40.0 to 0.7,   // Transitioning to more gas
        50.0 to 0.85,  // Moderate — mostly gas
        60.0 to 0.92,  // Highway approach — gas dominant
        80.0 to 0.97,  // Highway — almost all gas
        100.0 to 1.0,  // High speed — all gas
        120.0 to 1.05, // Over-speed — worse than rated
    )

    init {
        loadCalibrationFactorFromDatabase()
    }

    private fun loadCalibrationFactorFromDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val factor = fuelCalibrationDao.getAverageCorrectionRatio(
                    Constants.CALIBRATION_WINDOW_TRIPS
                )
                if (factor != null && factor > 0) {
                    calibrationFactor = factor.coerceIn(0.5, 2.0)
                    Log.d(TAG, "Loaded calibration factor from database: $calibrationFactor")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to load calibration factor: ${e.message}")
            }
        }
    }

    /**
     * Estimate instantaneous fuel consumption rate in L/h.
     *
     * Uses the Vehicle Specific Power (VSP) model with
     * Highlander Hybrid-specific adjustments.
     */
    fun estimateFuelRateLPerH(
        speedMps: Double,
        accelerationMps2: Double,
        roadGradePercent: Double,
    ): Double {
        if (speedMps < 0.5) return 0.5  // Idle fuel rate estimate (~0.5 L/h)

        val m = Constants.VEHICLE_MASS_KG
        val g = Constants.GRAVITY
        val cr = Constants.ROLLING_RESISTANCE
        val rho = Constants.AIR_DENSITY
        val cd = Constants.DRAG_COEFFICIENT
        val a = Constants.FRONTAL_AREA_M2
        val epsilon = Constants.ROTATING_MASS_FACTOR

        // Road grade in radians
        val gradeRad = Math.toRadians(Math.atan(roadGradePercent / 100.0))

        // Vehicle Specific Power (W/kg)
        val vsp = speedMps * (
            accelerationMps2 * (1 + epsilon) +
            g * Math.sin(gradeRad) +
            g * cr
        ) + (0.5 * rho * cd * a * speedMps * speedMps * speedMps) / m

        // Convert VSP to fuel rate using empirical relationship
        // Base fuel rate for a 2.5L engine (L/h)
        val baseFuelRate = when {
            vsp < -5.0 -> 0.3    // Deceleration / engine braking (fuel cut-off)
            vsp < 0.0 -> 0.5     // Coasting / light braking
            vsp < 5.0 -> 2.0     // Light load
            vsp < 10.0 -> 4.0    // Moderate load
            vsp < 15.0 -> 6.5    // Medium-heavy load
            vsp < 20.0 -> 9.0    // Heavy load (acceleration)
            vsp < 30.0 -> 12.0   // Very heavy load
            else -> 15.0          // Maximum load
        }

        // Apply hybrid efficiency adjustment
        val speedKmh = speedMps * 3.6
        val hybridFactor = interpolateHybridFactor(speedKmh)

        // Apply calibration factor (learned from Toyota API)
        return baseFuelRate * hybridFactor * calibrationFactor
    }

    /**
     * Estimate fuel consumed over a time period.
     */
    fun estimateFuelConsumed(fuelRateLPerH: Double, durationSeconds: Double): Double {
        return fuelRateLPerH * (durationSeconds / 3600.0)
    }

    /**
     * Add a calibration point from Toyota API fuel level comparison.
     * Adjusts the model to match actual consumption over time.
     * Persists the updated calibration factor to the database.
     */
    fun addCalibrationPoint(point: FuelCalibrationPoint) {
        if (point.distanceKm < Constants.CALIBRATION_MIN_DISTANCE_KM) return
        if (point.actualFuelLiters <= 0 || point.estimatedFuelLiters <= 0) return

        calibrationHistory.add(point)

        // Keep only recent calibration points
        while (calibrationHistory.size > Constants.CALIBRATION_WINDOW_TRIPS) {
            calibrationHistory.removeAt(0)
        }

        // Recalculate calibration factor as weighted average of recent corrections
        if (calibrationHistory.size >= 3) {
            calibrationFactor = calibrationHistory
                .takeLast(Constants.CALIBRATION_WINDOW_TRIPS)
                .map { it.correctionRatio }
                .average()
                .coerceIn(0.5, 2.0)  // Sanity bounds

            persistCalibrationFactorToDatabase()
        }
    }

    private fun persistCalibrationFactorToDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lastPoint = calibrationHistory.lastOrNull()
                val entity = FuelCalibrationEntity(
                    tripId = lastPoint?.tripId ?: 0,
                    correctionRatio = calibrationFactor,
                    distanceKm = lastPoint?.distanceKm ?: 0.0,
                    actualFuelLiters = lastPoint?.actualFuelLiters ?: 0.0,
                    estimatedFuelLiters = lastPoint?.estimatedFuelLiters ?: 0.0,
                    timestampEpochMs = System.currentTimeMillis(),
                )
                fuelCalibrationDao.insert(entity)
                Log.d(TAG, "Persisted calibration factor to database: $calibrationFactor")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist calibration factor: ${e.message}")
            }
        }
    }

    fun getCalibrationFactor(): Double = calibrationFactor

    /**
     * Interpolate hybrid efficiency factor for the given speed.
     */
    private fun interpolateHybridFactor(speedKmh: Double): Double {
        val entries = hybridEfficiencyMap.entries.toList().sortedBy { it.key }

        if (speedKmh <= entries.first().key) return entries.first().value
        if (speedKmh >= entries.last().key) return entries.last().value

        for (i in 0 until entries.size - 1) {
            val lower = entries[i]
            val upper = entries[i + 1]
            if (speedKmh in lower.key..upper.key) {
                val fraction = (speedKmh - lower.key) / (upper.key - lower.key)
                return lower.value + fraction * (upper.value - lower.value)
            }
        }
        return 1.0
    }
}

/**
 * Calculates the Eco Score based on sensor-derived driving behavior.
 * Updated for the hybrid approach: uses acceleration/cornering data
 * from phone sensors instead of RPM/throttle from OBD.
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

        // Acceleration Score: fewer hard accelerations = better
        val accelFreq = hardAccelCount * per10Min
        val accelScore = when {
            accelFreq < 0.5 -> 100
            accelFreq < 1.0 -> 85
            accelFreq < 2.0 -> 70
            accelFreq < 4.0 -> 50
            accelFreq < 6.0 -> 30
            else -> 10
        }

        // Braking Score
        val brakeFreq = hardBrakeCount * per10Min
        val brakeScore = when {
            brakeFreq < 0.5 -> 100
            brakeFreq < 1.0 -> 85
            brakeFreq < 2.0 -> 70
            brakeFreq < 4.0 -> 50
            brakeFreq < 6.0 -> 30
            else -> 10
        }

        // Speed Score: staying in efficient range
        val speedScore = when {
            averageSpeedKmh in Constants.SPEED_ECO_MIN_KMH..Constants.SPEED_ECO_MAX_KMH -> 100
            averageSpeedKmh in 40.0..100.0 -> 80
            averageSpeedKmh in 30.0..110.0 -> 60
            averageSpeedKmh < 30.0 -> 50
            else -> 40
        }

        // Cornering Score: fewer sharp turns = better
        val turnFreq = sharpTurnCount * per10Min
        val corneringScore = when {
            turnFreq < 0.3 -> 100
            turnFreq < 0.8 -> 85
            turnFreq < 1.5 -> 70
            turnFreq < 3.0 -> 50
            else -> 25
        }

        // Idle Score
        val idleScore = when {
            idleTimePercent < 5.0 -> 100
            idleTimePercent < 10.0 -> 85
            idleTimePercent < 20.0 -> 65
            idleTimePercent < 30.0 -> 45
            else -> 20
        }

        // Speed Consistency Score (replaces throttle smoothness)
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
