package com.ecodrive.app.domain.analyzer

import com.ecodrive.app.domain.ai.engine.DrivingThresholds
import com.ecodrive.app.domain.ai.analyzer.FuelPredictionModel
import com.ecodrive.app.domain.ai.engine.ScoreWeights
import com.ecodrive.app.domain.ai.config.AiUtils

import android.util.Log
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.local.dao.FuelCalibrationDao
import com.ecodrive.app.data.local.entity.FuelCalibrationEntity
import com.ecodrive.app.domain.ai.service.AiManager
import com.ecodrive.app.domain.model.DrivingEvent
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.EcoScore
import com.ecodrive.app.domain.model.FuelCalibrationPoint
import com.ecodrive.app.domain.model.Trip
import com.ecodrive.app.domain.model.Vehicle
import com.ecodrive.app.domain.model.VehicleType
import com.ecodrive.app.util.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    private var lastIdleEventDuration: Long = 0
    private val speedHistory = java.util.Collections.synchronizedList(mutableListOf<Double>())
    private var thresholds = com.ecodrive.app.domain.ai.engine.DrivingThresholds()

    fun updateThresholds(newThresholds: com.ecodrive.app.domain.ai.engine.DrivingThresholds) {
        thresholds = newThresholds
    }

    fun analyze(metrics: DrivingMetrics, tripId: Long): List<DrivingEvent> {
        val events = mutableListOf<DrivingEvent>()
        if (metrics.isMoving) {
            speedHistory.add(metrics.speedKmh)
            synchronized(speedHistory) {
                while (speedHistory.size > 120) speedHistory.removeAt(0)
            }
        }

        if (metrics.longitudinalAccelMps2 < -thresholds.hardBrake) {
            events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.HARD_BRAKE, value = metrics.longitudinalAccelMps2, speedAtEvent = metrics.speedKmh, latitude = metrics.latitude, longitude = metrics.longitude, description = "Hard braking: %.1f m/s²".format(metrics.longitudinalAccelMps2)))
        }

        if (metrics.longitudinalAccelMps2 > thresholds.hardAccel) {
            events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.HARD_ACCELERATION, value = metrics.longitudinalAccelMps2, speedAtEvent = metrics.speedKmh, latitude = metrics.latitude, longitude = metrics.longitude, description = "Hard acceleration: +%.1f m/s²".format(metrics.longitudinalAccelMps2)))
        }

        if (abs(metrics.lateralAccelMps2) > thresholds.sharpTurn) {
            events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.SHARP_TURN, value = metrics.lateralAccelMps2, speedAtEvent = metrics.speedKmh, latitude = metrics.latitude, longitude = metrics.longitude, description = "Sharp turn: %.1f m/s² lateral".format(metrics.lateralAccelMps2)))
        }

        if (metrics.speedKmh > Constants.SPEED_EXCESSIVE_KMH) {
            events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.EXCESSIVE_SPEED, value = metrics.speedKmh, speedAtEvent = metrics.speedKmh, latitude = metrics.latitude, longitude = metrics.longitude, description = "Excessive speed"))
        }

        if (metrics.isIdle) {
            if (idleStartTimeMs == null) {
                idleStartTimeMs = metrics.timestamp.toEpochMilli()
                lastIdleEventDuration = 0
            } else {
                val idleDuration = (metrics.timestamp.toEpochMilli() - idleStartTimeMs!!) / 1000
                if (idleDuration > Constants.IDLE_WARNING_SECONDS) {
                    if (lastIdleEventDuration == 0L) {
                        lastIdleEventDuration = Constants.IDLE_WARNING_SECONDS.toLong()
                        events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.EXCESSIVE_IDLE, value = idleDuration.toDouble(), speedAtEvent = 0.0, latitude = metrics.latitude, longitude = metrics.longitude, description = "Idling for ${idleDuration}s"))
                    } else if (idleDuration - lastIdleEventDuration >= 30) {
                        lastIdleEventDuration += 30
                        events.add(DrivingEvent(tripId = tripId, type = DrivingEventType.EXCESSIVE_IDLE, value = idleDuration.toDouble(), speedAtEvent = 0.0, latitude = metrics.latitude, longitude = metrics.longitude, description = "Idling for ${idleDuration}s"))
                    }
                }
            }
        } else {
            idleStartTimeMs = null
            lastIdleEventDuration = 0
        }

        previousMetrics = metrics
        return events
    }

    fun getSpeedStdDeviation(): Double {
        return synchronized(speedHistory) {
            if (speedHistory.size < 10) return@synchronized 0.0
            val mean = speedHistory.average()
            val variance = speedHistory.map { (it - mean) * (it - mean) }.average()
            kotlin.math.sqrt(variance)
        }
    }

    fun reset() {
        previousMetrics = null
        idleStartTimeMs = null
        lastIdleEventDuration = 0
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
    private val aiManager: AiManager,
    private val preferenceManager: PreferenceManager,
    private val mlModel: com.ecodrive.app.domain.ai.analyzer.FuelPredictionModel,
) {
    companion object {
        private const val TAG = "FuelEstimationEngine"
    }

    private var calibrationFactor = Constants.DEFAULT_CALIBRATION_FACTOR
    private var aiCorrectionFactor = 1.0
    private var mlCorrectionFactor = 1.0
    private val calibrationHistory = java.util.Collections.synchronizedList(mutableListOf<FuelCalibrationPoint>())

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

    fun estimateFuelRateLPerH(
        speedMps: Double,
        accelerationMps2: Double,
        roadGradePercent: Double,
        vehicle: Vehicle
    ): Double {
        val speedKmh = speedMps * 3.6
        
        // Update ML correction factor
        mlCorrectionFactor = mlModel.predictCorrectionFactor(
            speedKmh = speedKmh,
            accelMps2 = accelerationMps2,
            gradePercent = roadGradePercent,
            vehicle = vehicle
        )

        // 1. Idle estimation — engine always burns fuel when running
        // Minimum floor: ~0.4 L/h per liter of displacement at idle (e.g. 0.8 L/h for a 2.0L engine)
        val displacementL = vehicle.engineDisplacementCc / 1000.0
        val idleFloorLPerH = displacementL * 0.4

        if (speedMps < 0.2) {
            return idleFloorLPerH * calibrationFactor * mlCorrectionFactor
        }

        // 2. Physics-based Power (VSP) calculation (Watts)
        val g = Constants.GRAVITY
        val rho = Constants.AIR_DENSITY
        val eps = Constants.ROTATING_MASS_FACTOR
        val theta = atan(roadGradePercent / 100.0)

        val vsp = speedMps * (
            accelerationMps2 * (1 + eps) +
            g * sin(theta) +
            g * vehicle.rollingResistance * cos(theta)
        ) + (0.5 * rho * vehicle.dragCoefficient * vehicle.frontalAreaM2 * speedMps.pow(3)) / vehicle.massKg

        // 3. Convert VSP to Total Power (Watts).
        // Add a fixed auxiliary / friction load that is always present when engine runs.
        // This represents alternator, HVAC, power steering, and internal friction — typically 1–3 kW.
        val auxLoadWatts = displacementL * 500.0  // ~1000W for a 2.0L engine
        val powerWatts = (vsp * vehicle.massKg).coerceAtLeast(0.0) + auxLoadWatts

        // 4. Speed/load-dependent efficiency factor (eta).
        // Real ICE engines are least efficient at light loads (city coasting = ~15% thermal efficiency)
        // and approach their best efficiency (~25%) only at moderate-to-high cruise loads.
        // Using a fixed eta=0.25 at all loads was the key bug — it made light-load fuel look too low.
        val eta = when (vehicle.vehicleType) {
            VehicleType.ICE -> when {
                speedKmh < 20  -> 0.14  // Very low load — worst efficiency, most fuel per kWh
                speedKmh < 40  -> 0.17  // City stop-and-go
                speedKmh < 70  -> 0.20  // Mixed/suburban
                speedKmh < 100 -> 0.24  // Highway — approaching peak efficiency
                else           -> 0.26  // High-speed cruise
            }
            VehicleType.HYBRID -> when {
                speedKmh < 40  -> 0.38  // Hybrid advantage: uses motor more in city
                speedKmh < 90  -> 0.33
                else           -> 0.30
            }
            VehicleType.PLUG_IN_HYBRID -> when {
                speedKmh < 40  -> 0.40
                speedKmh < 90  -> 0.34
                else           -> 0.30
            }
            VehicleType.ELECTRIC -> 0.88  // Battery-to-wheel efficiency
        }

        // 5. Fuel Consumption Rate (L/h)
        // Rate = (Power * 3600) / (eta * EnergyDensity_J_per_L)
        val energyDensityJperL = vehicle.fuelType.energyDensityMJperL * 1_000_000.0
        if (energyDensityJperL <= 0) return 0.0

        val fuelRateLPerH = (powerWatts * 3600.0) / (eta * energyDensityJperL)

        // 6. Apply calibration, AI and ML corrections, then enforce the idle floor.
        // The floor ensures the model never reports less fuel than an idling engine burns,
        // which was the primary cause of 1000+ MPG readings on flat constant-speed segments.
        val rawRate = fuelRateLPerH * calibrationFactor * aiCorrectionFactor * mlCorrectionFactor
        return rawRate.coerceAtLeast(idleFloorLPerH)
    }

    fun updateAiCorrection(newFactor: Double) {
        aiCorrectionFactor = newFactor.coerceIn(0.8, 1.2)
        Log.d(TAG, "AI Correction Factor updated to: $aiCorrectionFactor")
    }

    /**
     * Periodically analyze recent trips with AI to refine the correction factor.
     */
    fun performAiRefinement(trips: List<Trip>) {
        CoroutineScope(Dispatchers.IO).launch {
            if (trips.isEmpty()) return@launch

            val prompt = """
                You are a Vehicle Efficiency Analyst. Analyze the following trip records where we estimated fuel consumption vs physics model defaults.
                
                Data:
                ${trips.joinToString("\n") { "- Trip ${it.id}: Dist=${it.distanceKm}km, Estimated=${it.fuelConsumedLiters}L, Score=${it.ecoScore}" }}
                
                Current AI Correction Factor: $aiCorrectionFactor
                
                Based on the consistency of the scores and estimated consumption, should we adjust our physics-based estimation factor?
                Return ONLY a JSON object: {"suggested_factor": float, "reason": "string"}. 
                Keep the factor between 0.8 and 1.2.
            """.trimIndent()

            val response = aiManager.generateTripInsight(prompt)
            response?.let { raw ->
                try {
                    val jsonStr = com.ecodrive.app.domain.ai.config.AiUtils.extractJson(raw) ?: return@let
                    val json = Json.parseToJsonElement(jsonStr).jsonObject
                    val factor = json["suggested_factor"]?.jsonPrimitive?.doubleOrNull ?: 1.0
                    updateAiCorrection(factor)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse AI refinement response: ${e.message}")
                }
            }
        }
    }

    fun estimateFuelConsumed(fuelRateLPerH: Double, durationSeconds: Double): Double {
        return fuelRateLPerH * (durationSeconds / 3600.0)
    }

    fun addCalibrationPoint(point: FuelCalibrationPoint) {
        if (point.distanceKm < Constants.CALIBRATION_MIN_DISTANCE_KM) return
        if (point.actualFuelLiters <= 0 || point.estimatedFuelLiters <= 0) return
        
        synchronized(calibrationHistory) {
            calibrationHistory.add(point)
            while (calibrationHistory.size > Constants.CALIBRATION_WINDOW_TRIPS) {
                calibrationHistory.removeAt(0)
            }
            if (calibrationHistory.size >= 3) {
                calibrationFactor = calibrationHistory.takeLast(Constants.CALIBRATION_WINDOW_TRIPS)
                    .map { it.correctionRatio }.average().coerceIn(0.5, 2.0)
                persistCalibrationFactorToDatabase()
            }
        }
    }

    private fun persistCalibrationFactorToDatabase() {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val lastPoint = synchronized(calibrationHistory) { calibrationHistory.lastOrNull() }
                val entity = FuelCalibrationEntity(
                    tripId = lastPoint?.tripId ?: 0,
                    correctionRatio = calibrationFactor,
                    distanceKm = lastPoint?.distanceKm ?: 0.0,
                    actualFuelLiters = lastPoint?.actualFuelLiters ?: 0.0,
                    estimatedFuelLiters = lastPoint?.estimatedFuelLiters ?: 0.0,
                    timestampEpochMs = System.currentTimeMillis()
                )
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
        weights: com.ecodrive.app.domain.ai.engine.ScoreWeights = com.ecodrive.app.domain.ai.engine.ScoreWeights(),
    ): EcoScore {
        val effectiveDuration = kotlin.math.max(tripDurationMinutes, 5.0)
        val per10Min = 10.0 / effectiveDuration

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
            accelScore * weights.acceleration +
            brakeScore * weights.braking +
            speedScore * weights.speed +
            corneringScore * weights.cornering +
            idleScore * weights.idle +
            consistencyScore * weights.consistency
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
