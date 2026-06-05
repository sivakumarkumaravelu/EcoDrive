package com.ecodrive.app.data.repository

import com.ecodrive.app.data.local.dao.*
import com.ecodrive.app.data.local.entity.*
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.domain.model.*
import com.ecodrive.app.util.Constants
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository managing trip lifecycle: creation, updates, persistence,
 * and fuel calibration after trip completion.
 */
@Singleton
class TripRepository @Inject constructor(
    private val tripDao: TripDao,
    private val drivingEventDao: DrivingEventDao,
    private val dataPointDao: DataPointDao,
    private val smartcarApiClient: SmartcarApiClient,
    private val fuelEngine: FuelEstimationEngine,
    private val vehicleRepository: VehicleRepository,
) {
    // ── Trip Lifecycle ──────────────────────────────────────────

    /**
     * Create a new trip and return its ID.
     * Optionally captures fuel level from Smartcar API as the starting baseline.
     */
    suspend fun startTrip(vehicleId: Long = 1): Long {
        val startFuel = smartcarApiClient.fetchFuelLevel()
        
        // Use provided vehicleId or fetch default
        val targetVehicleId = if (vehicleId > 0) vehicleId else {
            vehicleRepository.getDefaultVehicle()?.id ?: 1
        }

        val trip = TripEntity(
            vehicleId = targetVehicleId,
            startTimeEpochMs = System.currentTimeMillis(),
            isActive = true,
            startFuelPercent = startFuel,
            dataSource = "SENSORS",
        )
        return tripDao.insertTrip(trip)
    }

    /**
     * Finalize a trip: fetch end fuel level, calculate calibration,
     * and save final statistics.
     */
    suspend fun endTrip(
        tripId: Long,
        distanceKm: Double,
        durationSeconds: Long,
        averageSpeedKmh: Double,
        maxSpeedKmh: Double,
        fuelConsumedEstimate: Double,
        ecoScore: Int,
        hardBrakeCount: Int,
        hardAccelCount: Int,
        sharpTurnCount: Int,
        idleTimeSeconds: Long,
    ) {
        val endFuel = smartcarApiClient.fetchFuelLevel()
        val trip = tripDao.getTripById(tripId) ?: return
        val vehicle = vehicleRepository.getVehicleById(trip.vehicleId) ?: Vehicle()

        val fuelEfficiency = if (distanceKm > 0) {
            (fuelConsumedEstimate / distanceKm) * 100.0
        } else 0.0

        // Calculate calibration if we have both start and end fuel levels
        var calibrationFactor = trip.calibrationFactor
        if (trip.startFuelPercent != null && endFuel != null) {
            val fuelDeltaPercent = trip.startFuelPercent - endFuel
            if (fuelDeltaPercent >= Constants.CALIBRATION_MIN_FUEL_CHANGE_PERCENT &&
                distanceKm >= Constants.CALIBRATION_MIN_DISTANCE_KM
            ) {
                val actualFuelLiters = (fuelDeltaPercent / 100.0) * vehicle.tankCapacityLiters
                val calibrationPoint = FuelCalibrationPoint(
                    tripId = tripId,
                    estimatedFuelLiters = fuelConsumedEstimate,
                    actualFuelLiters = actualFuelLiters,
                    distanceKm = distanceKm,
                )
                fuelEngine.addCalibrationPoint(calibrationPoint)
                calibrationFactor = fuelEngine.getCalibrationFactor()
            }
        }

        val updatedTrip = trip.copy(
            endTimeEpochMs = System.currentTimeMillis(),
            distanceKm = distanceKm,
            durationSeconds = durationSeconds,
            averageSpeedKmh = averageSpeedKmh,
            maxSpeedKmh = maxSpeedKmh,
            fuelConsumedLiters = fuelConsumedEstimate,
            fuelEfficiencyLPer100Km = fuelEfficiency,
            ecoScore = ecoScore,
            hardBrakeCount = hardBrakeCount,
            hardAccelCount = hardAccelCount,
            sharpTurnCount = sharpTurnCount,
            idleTimeSeconds = idleTimeSeconds,
            isActive = false,
            endFuelPercent = endFuel,
            calibrationFactor = calibrationFactor,
        )
        tripDao.updateTrip(updatedTrip)
    }

    // ── Data Point Storage ──────────────────────────────────────

    /**
     * Save a driving metrics snapshot as a data point for later analysis.
     */
    suspend fun saveDataPoint(tripId: Long, metrics: DrivingMetrics) {
        val dataPoint = DataPointEntity(
            tripId = tripId,
            timestampEpochMs = metrics.timestamp.toEpochMilli(),
            speedKmh = metrics.speedKmh,
            latitude = metrics.latitude,
            longitude = metrics.longitude,
            altitudeM = metrics.altitudeM,
            longitudinalAccelMps2 = metrics.longitudinalAccelMps2,
            lateralAccelMps2 = metrics.lateralAccelMps2,
            fuelRateEstimateLPerH = metrics.fuelRateLPerH,
            fuelConsumptionLPer100Km = metrics.fuelConsumptionLPer100Km,
            roadGradePercent = metrics.roadGradePercent,
        )
        dataPointDao.insertDataPoint(dataPoint)
    }

    /**
     * Save detected driving events.
     */
    suspend fun saveEvents(events: List<DrivingEvent>) {
        if (events.isEmpty()) return
        val entities = events.map { event ->
            DrivingEventEntity(
                tripId = event.tripId,
                timestampEpochMs = event.timestamp.toEpochMilli(),
                type = event.type.name,
                value = event.value,
                speedAtEvent = event.speedAtEvent,
                latitude = event.latitude,
                longitude = event.longitude,
                description = event.description,
            )
        }
        drivingEventDao.insertEvents(entities)
    }

    // ── Queries ─────────────────────────────────────────────────

    /**
     * Get all trips ordered by most recent first.
     */
    fun getAllTrips(): Flow<List<Trip>> {
        return tripDao.getAllTrips().map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get the N most recent trips.
     */
    fun getRecentTrips(limit: Int = 20): Flow<List<Trip>> {
        return tripDao.getRecentTrips(limit).map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get a single trip by ID.
     */
    suspend fun getTripById(tripId: Long): Trip? {
        return tripDao.getTripById(tripId)?.toDomain()
    }

    /**
     * Get the currently active trip (if any).
     */
    suspend fun getActiveTrip(): Trip? {
        return tripDao.getActiveTrip()?.toDomain()
    }

    /**
     * Get events for a specific trip.
     */
    fun getEventsForTrip(tripId: Long): Flow<List<DrivingEvent>> {
        return drivingEventDao.getEventsForTrip(tripId).map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get route points for a list of trips.
     */
    fun getRoutePointsForTrips(tripIds: List<Long>): Flow<Map<Long, List<LatLng>>> {
        return dataPointDao.getRoutePointsForTrips(tripIds).map { points ->
            points.groupBy { it.tripId }
                .mapValues { entry ->
                    entry.value.map { LatLng(it.latitude, it.longitude) }
                }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get average eco score over a time period.
     */
    suspend fun getAverageEcoScore(sinceEpochMs: Long): Double? {
        return tripDao.getAverageEcoScore(sinceEpochMs)
    }

    /**
     * Get average fuel efficiency (L/100km) over a time period.
     */
    suspend fun getAverageFuelEfficiency(sinceEpochMs: Long): Double? {
        return tripDao.getAverageFuelEfficiency(sinceEpochMs)
    }

    /**
     * Get total fuel consumed over a time period.
     */
    suspend fun getTotalFuelConsumed(sinceEpochMs: Long): Double? {
        return tripDao.getTotalFuelConsumed(sinceEpochMs)
    }

    /**
     * Get total distance driven over a time period.
     */
    suspend fun getTotalDistance(sinceEpochMs: Long): Double? {
        return tripDao.getTotalDistance(sinceEpochMs)
    }

    /**
     * Delete a trip and its associated data.
     */
    suspend fun deleteTrip(tripId: Long) {
        val trip = tripDao.getTripById(tripId) ?: return
        dataPointDao.deleteDataPointsForTrip(tripId)
        drivingEventDao.deleteEventsForTrip(tripId)
        tripDao.deleteTrip(trip)
    }
}

// ── Entity ↔ Domain Mappers ─────────────────────────────────────

private fun TripEntity.toDomain() = Trip(
    id = id,
    vehicleId = vehicleId,
    startTime = Instant.ofEpochMilli(startTimeEpochMs),
    endTime = endTimeEpochMs?.let { Instant.ofEpochMilli(it) },
    distanceKm = distanceKm,
    durationSeconds = durationSeconds,
    averageSpeedKmh = averageSpeedKmh,
    maxSpeedKmh = maxSpeedKmh,
    fuelConsumedLiters = fuelConsumedLiters,
    fuelEfficiencyLPer100Km = fuelEfficiencyLPer100Km,
    ecoScore = ecoScore,
    hardBrakeCount = hardBrakeCount,
    hardAccelCount = hardAccelCount,
    sharpTurnCount = sharpTurnCount,
    idleTimeSeconds = idleTimeSeconds,
    isActive = isActive,
    startFuelPercent = startFuelPercent,
    endFuelPercent = endFuelPercent,
    calibrationFactor = calibrationFactor,
)

private fun DrivingEventEntity.toDomain() = DrivingEvent(
    id = id,
    tripId = tripId,
    timestamp = Instant.ofEpochMilli(timestampEpochMs),
    type = try {
        DrivingEventType.valueOf(type)
    } catch (_: Exception) {
        DrivingEventType.ECO_DRIVING
    },
    value = value,
    speedAtEvent = speedAtEvent,
    latitude = latitude,
    longitude = longitude,
    description = description,
)
