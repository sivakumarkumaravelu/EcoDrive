package com.ecodrive.app.domain.model

import java.time.Instant

/**
 * Fuel types for consumption calculations.
 * Properties:
 *   - energyDensityMJperL: Lower Heating Value (LHV) in Megajoules per Liter.
 *   - densityKgPerL: Mass density in kilograms per Liter.
 */
enum class FuelType(val energyDensityMJperL: Double, val densityKgPerL: Double) {
    GASOLINE(34.2, 0.745),
    DIESEL(38.6, 0.832),
    ETHANOL(21.2, 0.789),
    LPG(25.5, 0.510),
    ELECTRICITY(3.6, 0.0) // 1 kWh = 3.6 MJ. Density is not applicable.
}

/**
 * Categorization of vehicle powertrains.
 */
enum class VehicleType {
    ICE,            // Internal Combustion Engine
    HYBRID,         // Non-pluggable Hybrid (HEV)
    PLUG_IN_HYBRID, // Plug-in Hybrid (PHEV)
    ELECTRIC        // Battery Electric Vehicle (BEV)
}

/**
 * Represents a single driving trip from start to stop.
 */
data class Trip(
    val id: Long = 0,
    val vehicleId: Long = 0,
    val startTime: Instant = Instant.now(),
    val endTime: Instant? = null,
    val distanceKm: Double = 0.0,
    val durationSeconds: Long = 0,
    val averageSpeedKmh: Double = 0.0,
    val maxSpeedKmh: Double = 0.0,
    val fuelConsumedLiters: Double = 0.0,
    val fuelEfficiencyLPer100Km: Double = 0.0,
    val ecoScore: Int = 0,
    val hardBrakeCount: Int = 0,
    val hardAccelCount: Int = 0,
    val sharpTurnCount: Int = 0,
    val idleTimeSeconds: Long = 0,
    val isActive: Boolean = true,
    val startFuelPercent: Double? = null,
    val endFuelPercent: Double? = null,
    val calibrationFactor: Double = 1.0,
)

/**
 * Represents a notable driving event detected during a trip.
 */
data class DrivingEvent(
    val id: Long = 0,
    val tripId: Long,
    val timestamp: Instant = Instant.now(),
    val type: DrivingEventType,
    val value: Double = 0.0,
    val speedAtEvent: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val description: String = "",
)

/**
 * Types of driving events that the analyzer can detect.
 */
enum class DrivingEventType {
    HARD_BRAKE,
    HARD_ACCELERATION,
    SHARP_TURN,
    EXCESSIVE_SPEED,
    EXCESSIVE_IDLE,
    SPEED_INCONSISTENCY,
    ECO_DRIVING,
}

/**
 * Real-time driving metrics snapshot.
 * Combines GPS, accelerometer, and vehicle API data.
 */
data class DrivingMetrics(
    val timestamp: Instant = Instant.now(),

    // ── Movement Data ───────────────────────────────────────
    val speedKmh: Double = 0.0,
    val accelerationMPerS2: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeM: Double = 0.0,
    val bearingDegrees: Float = 0f,
    val gpsAccuracyM: Float = 0f,
    val roadGradePercent: Double = 0.0,

    // ── Vehicle API Engine Data ─────────────────────────────
    val rpm: Int = 0,
    val throttlePercent: Double = 0.0,
    val engineLoadPercent: Double = 0.0,
    val mafGramsPerSec: Double = 0.0,
    val coolantTempC: Int = 0,
    val ambientTempC: Int = 0,
    val batteryVoltage: Double = 0.0,
    val fuelTankPercent: Double? = null,

    // ── Consumption Data ────────────────────────────────────
    val fuelRateLPerH: Double = 0.0,
    val fuelConsumptionLPer100Km: Double = 0.0,

    // ── Phone Sensor Data (Raw-ish) ─────────────────────────
    val longitudinalAccelMps2: Double = 0.0,
    val lateralAccelMps2: Double = 0.0,
    val verticalAccelMps2: Double = 0.0,

    // ── State ───────────────────────────────────────────────
    val isMoving: Boolean = speedKmh > 1.0,
    val isIdle: Boolean = speedKmh <= 1.0,
    
    // ── Smartcar API Data ───────────────────────────────────
    val odometerKm: Double? = null,
    val tirePressure: TirePressure? = null,
)

/**
 * Tire pressure data from Vehicle API.
 */
data class TirePressure(
    val frontLeft: Double = 0.0,
    val frontRight: Double = 0.0,
    val rearLeft: Double = 0.0,
    val rearRight: Double = 0.0,
)

/**
 * Eco Score breakdown with individual category scores.
 */
data class EcoScore(
    val overall: Int = 0,
    val accelerationScore: Int = 0,
    val brakingScore: Int = 0,
    val speedScore: Int = 0,
    val corneringScore: Int = 0,
    val idleScore: Int = 0,
    val consistencyScore: Int = 0,
) {
    val rating: EcoRating
        get() = when {
            overall >= 90 -> EcoRating.EXCELLENT
            overall >= 70 -> EcoRating.GOOD
            overall >= 50 -> EcoRating.AVERAGE
            else -> EcoRating.POOR
        }
}

enum class EcoRating(val label: String, val emoji: String) {
    EXCELLENT("Excellent", "🌟"),
    GOOD("Good", "✅"),
    AVERAGE("Average", "⚠️"),
    POOR("Poor", "🔴"),
}

/**
 * Vehicle profile information.
 */
data class Vehicle(
    val id: Long = 0,
    val name: String = "My Vehicle",
    val make: String = "",
    val model: String = "",
    val year: Int = 2024,
    val vehicleType: VehicleType = VehicleType.ICE,
    val fuelType: FuelType = FuelType.GASOLINE,
    val massKg: Double = 1500.0,
    val dragCoefficient: Double = 0.3,
    val frontalAreaM2: Double = 2.2,
    val rollingResistance: Double = 0.012,
    val tankCapacityLiters: Double = 50.0,
    val engineDisplacementCc: Int = 2000,
    val fuelCalibrationFactor: Double = 1.0,
    val isDefault: Boolean = false,
)

/**
 * Connection state for sensor data collection.
 */
enum class SensorState {
    INACTIVE,
    REQUESTING_PERMISSIONS,
    ACTIVE,
    ERROR,
}

/**
 * Smartcar API connection state.
 */
enum class SmartcarApiState {
    NOT_CONFIGURED,
    AUTHENTICATING,
    CONNECTED,
    ERROR,
}

/**
 * Options for application theme mode.
 */
enum class AppTheme {
    LIGHT,
    DARK,
    FOLLOW_SYSTEM;

    fun getDisplayName(): String {
        return when (this) {
            FOLLOW_SYSTEM -> "System"
            else -> name.lowercase().replaceFirstChar { it.uppercase() }
        }
    }
}

/**
 * Options for application color palette.
 */
enum class AppColorPalette {
    ECO_GREEN,
    MIDNIGHT_BLUE,
    SOLAR_ORANGE,
    DEEP_PURPLE,
    OCEAN_TEAL,
    CRIMSON_RED,
    DYNAMIC;

    fun getDisplayName(): String {
        return name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}

/**
 * Fuel calibration data point from Smartcar API.
 */
data class FuelCalibrationPoint(
    val tripId: Long,
    val estimatedFuelLiters: Double,
    val actualFuelLiters: Double,
    val distanceKm: Double,
    val timestamp: Instant = Instant.now(),
) {
    val correctionRatio: Double
        get() = if (estimatedFuelLiters > 0) actualFuelLiters / estimatedFuelLiters else 1.0
}
