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
    val isMoving: Boolean = speedKmh >= com.ecodrive.app.util.Constants.MOVING_SPEED_THRESHOLD_KMH,
    val isIdle: Boolean = speedKmh < com.ecodrive.app.util.Constants.MOVING_SPEED_THRESHOLD_KMH,
    
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
    val odometerKm: Double? = null,
    val fuelLevelPercent: Double? = null,
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
 * Options for application font scale.
 */
enum class AppFontScale {
    SMALL,
    MEDIUM,
    LARGE;

    fun getDisplayName(): String {
        return name.lowercase().replaceFirstChar { it.uppercase() }
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

// ── AI Gamification Models ──────────────────────────────────────

/**
 * Types of achievement badges the driver can earn.
 */
enum class BadgeType(val title: String, val icon: String, val description: String) {
    SMOOTH_OPERATOR("Smooth Operator", "🧘", "Completed 3 trips with zero hard brakes"),
    ECO_WARRIOR("Eco Warrior", "🌿", "Achieved Eco Score ≥ 90 on 5 consecutive trips"),
    CONSISTENCY_KING("Consistency King", "📍", "Speed std-dev below 5 km/h for an entire trip"),
    FUEL_SAVER("Fuel Saver", "⛽", "Saved 10+ litres vs average fleet this month"),
    NIGHT_OWL("Night Owl", "🦉", "Completed 5 trips after 10 PM with score ≥ 80"),
    HIGHWAY_HERO("Highway Hero", "🛴", "Drove 100+ km at optimal efficiency (70-90 km/h)"),
    FIRST_TRIP("First Journey", "🌟", "Completed your very first EcoDrive trip"),
}

/**
 * Represents an active or completed AI-generated challenge.
 */
data class Challenge(
    val id: Long = 0,
    val title: String,
    val description: String,
    val targetCount: Int,
    val progressCount: Int = 0,
    val metricType: DrivingEventType,
    val durationDays: Int = 7,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val completedAtEpochMs: Long? = null,
    val isCompleted: Boolean = false,
    val rewardBadgeType: BadgeType? = null,
) {
    val progressFraction: Float
        get() = if (targetCount > 0) (progressCount.toFloat() / targetCount).coerceIn(0f, 1f) else 0f

    val daysRemaining: Int
        get() {
            val elapsedMs = System.currentTimeMillis() - createdAtEpochMs
            val remainingMs = (durationDays * 86_400_000L) - elapsedMs
            return (remainingMs / 86_400_000L).toInt().coerceAtLeast(0)
        }
}

/**
 * Represents a badge earned by the driver.
 */
data class Badge(
    val id: Long = 0,
    val type: BadgeType,
    val earnedAtEpochMs: Long = System.currentTimeMillis(),
)

// ── AI Anomaly Detection Models ─────────────────────────────────

enum class AnomalyType {
    HIGH_FUEL_CONSUMPTION,   // Consumption 30%+ above baseline at same speed
    SPEED_OSCILLATION,       // Unexplained rapid speed fluctuations (not traffic)
    LATERAL_PULL,            // Persistent lateral acceleration bias (alignment/tire issue)
    HARSH_VIBRATION,         // Unusually high vertical acceleration (road or suspension)
    COLD_ENGINE_EXCESS,      // Excessive consumption in first 2 minutes (cold start issue)
}

enum class AnomalySeverity { LOW, MEDIUM, HIGH }

/**
 * Represents a detected anomaly in vehicle/driver behavior.
 */
data class VehicleAnomaly(
    val type: AnomalyType,
    val severity: AnomalySeverity,
    val description: String,
    val detectedAtSpeedKmh: Double = 0.0,
    /** AI-generated diagnosis of the likely cause. Null if AI unavailable. */
    val aiDiagnosis: String? = null,
)

// ── Weather Context Model ───────────────────────────────────────

/**
 * Environmental context affecting driving efficiency.
 * Populated from WeatherApiClient or time/season heuristics.
 */
data class WeatherContext(
    val tempC: Double = 20.0,
    val conditionCode: Int = 800, // OpenWeatherMap code; 800 = clear
    val conditionLabel: String = "Clear",
    val windSpeedKmh: Double = 0.0,
    val windBearingDeg: Int = 0,
    val visibilityKm: Double = 10.0,
    val humidity: Int = 50,
    val isRaining: Boolean = false,
    val isSnowing: Boolean = false,
) {
    /** 
     * Computed property to ensure it remains accurate even if visibilityKm is updated via copy().
     */
    val isFoggy: Boolean get() = visibilityKm < 1.0
    /** Fuel consumption penalty factor for current conditions (1.0 = no penalty). */
    val fuelPenaltyFactor: Double get() = when {
        isSnowing -> 1.25            // Snow: 25% more fuel
        isRaining -> 1.10            // Rain: 10% more fuel (rolling resistance + traction)
        tempC < 0 -> 1.18            // Below freezing: cold engine penalty
        tempC < 10 -> 1.08           // Cold: moderate penalty
        windSpeedKmh > 60 -> 1.12   // High wind: aerodynamic penalty
        else -> 1.0
    }

    /** Safety context tag for coaching tips. */
    val safetyContext: String get() = when {
        isSnowing -> "snowy/icy roads"
        isRaining -> "wet roads"
        isFoggy -> "reduced visibility"
        windSpeedKmh > 50 -> "strong crosswinds"
        tempC < -10 -> "extreme cold"
        else -> ""
    }
}

// ── Eco-Score Prediction Model ──────────────────────────────────

/**
 * AI-generated prediction for a trip before it starts.
 */
data class PredictedScore(
    val expected: Int,
    val low: Int,
    val high: Int,
    val explanation: String,
    val basedOnTripCount: Int = 0,
)
