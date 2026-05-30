package com.ecodrive.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Room entity representing a driving trip stored in the local database.
 * Updated for hybrid approach (phone sensors + Toyota API + optional OBD).
 */
@Entity(tableName = "trips")
data class TripEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val vehicleId: Long = 0,
    val startTimeEpochMs: Long = 0,
    val endTimeEpochMs: Long? = null,
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
    // Toyota API fuel calibration data
    val startFuelPercent: Double? = null,
    val endFuelPercent: Double? = null,
    val calibrationFactor: Double = 1.0,
    // Data source used for this trip
    val dataSource: String = "SENSORS", // SENSORS, OBD, HYBRID
)

/**
 * Room entity representing a driving event within a trip.
 */
@Entity(tableName = "driving_events")
data class DrivingEventEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val timestampEpochMs: Long,
    val type: String,        // DrivingEventType enum name
    val value: Double = 0.0,
    val speedAtEvent: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val description: String = "",
)

/**
 * Room entity for storing raw data points at intervals during a trip.
 * Used for charting and detailed post-trip analysis.
 * Updated for sensor-based data collection.
 */
@Entity(tableName = "data_points")
data class DataPointEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val timestampEpochMs: Long,
    // GPS data
    val speedKmh: Double = 0.0,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val altitudeM: Double = 0.0,
    // Accelerometer data
    val longitudinalAccelMps2: Double = 0.0,
    val lateralAccelMps2: Double = 0.0,
    // Derived
    val fuelRateEstimateLPerH: Double = 0.0,
    val fuelConsumptionLPer100Km: Double = 0.0,
    val roadGradePercent: Double = 0.0,
    // OBD data (optional, null when using sensors only)
    val rpm: Int? = null,
    val throttlePercent: Double? = null,
    val engineLoadPercent: Double? = null,
)

/**
 * Room entity for storing vehicle profile information.
 * Pre-loaded with 2023 Toyota Highlander Hybrid specs.
 */
@Entity(tableName = "vehicles")
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String = "My Highlander",
    val make: String = "Toyota",
    val model: String = "Highlander Hybrid",
    val year: Int = 2023,
    val massKg: Double = 2090.0,
    val dragCoefficient: Double = 0.35,
    val frontalAreaM2: Double = 2.83,
    val rollingResistance: Double = 0.012,
    val tankCapacityLiters: Double = 65.0,
    val engineDisplacementCc: Int = 2487,
    val isHybrid: Boolean = true,
    val fuelCalibrationFactor: Double = 1.0,
)

/**
 * Room entity for fuel calibration history.
 * Stores comparison between estimated and actual fuel consumption
 * for self-improving the VSP model.
 */
@Entity(tableName = "fuel_calibration")
data class FuelCalibrationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val tripId: Long,
    val estimatedFuelLiters: Double,
    val actualFuelLiters: Double,
    val distanceKm: Double,
    val correctionRatio: Double,
    val timestampEpochMs: Long,
)
