package com.ecodrive.app.data.repository

import com.ecodrive.app.data.local.dao.*
import com.ecodrive.app.data.local.entity.*
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.domain.ai.engine.AdaptiveScoreWeights
import com.ecodrive.app.domain.ai.engine.AdaptiveThresholdEngine
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
    private val adaptiveScoreWeights: AdaptiveScoreWeights,
    private val adaptiveThresholdEngine: AdaptiveThresholdEngine,
    private val anomalyDao: AnomalyDao,
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

    /**
     * Save detected anomalies.
     */
    suspend fun saveAnomalies(tripId: Long, anomalies: List<VehicleAnomaly>) {
        if (anomalies.isEmpty()) return
        val entities = anomalies.map { anomaly ->
            com.ecodrive.app.data.local.entity.AnomalyEntity(
                tripId = tripId,
                type = anomaly.type.name,
                severity = anomaly.severity.name,
                description = anomaly.description,
                detectedAtSpeedKmh = anomaly.detectedAtSpeedKmh,
                aiDiagnosis = anomaly.aiDiagnosis,
            )
        }
        anomalyDao.insertAnomalies(entities)
    }

    /**
     * Get anomalies for a trip.
     */
    fun getAnomaliesForTrip(tripId: Long): Flow<List<VehicleAnomaly>> {
        return anomalyDao.getAnomaliesForTrip(tripId).map { entities ->
            entities.map { 
                VehicleAnomaly(
                    type = com.ecodrive.app.domain.model.AnomalyType.valueOf(it.type),
                    severity = com.ecodrive.app.domain.model.AnomalySeverity.valueOf(it.severity),
                    description = it.description,
                    detectedAtSpeedKmh = it.detectedAtSpeedKmh,
                    aiDiagnosis = it.aiDiagnosis,
                ) 
            }
        }.flowOn(Dispatchers.IO)
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

    /**
     * Returns the total number of completed trips. Used to schedule
     * periodic AI model refinement cycles.
     */
    suspend fun getCompletedTripCount(): Int {
        return tripDao.getCompletedTripCount()
    }

    /**
     * Returns the N most recent completed trips as domain objects.
     * Used for AI refinement and challenge/badge checks.
     */
    suspend fun getRecentCompletedTrips(limit: Int): List<Trip> {
        return tripDao.getRecentCompletedTrips(limit).map { it.toDomain() }
    }

    /**
     * Finds trips that started and ended near the given coordinates.
     * Used for AI trip comparison (Feature 1).
     *
     * @param radiusKm Approximate match radius in km (default 1 km)
     */
    suspend fun findSimilarTrips(
        startLat: Double,
        startLon: Double,
        endLat: Double,
        endLon: Double,
        radiusKm: Double = 1.0,
        limit: Int = 5,
    ): List<Trip> {
        val allTrips = tripDao.getRecentCompletedTrips(50)
        val radiusDeg = radiusKm / 111.0  // 1 degree lat ≈ 111km

        return allTrips.filter { trip ->
            val startPoint = dataPointDao.getFirstDataPoint(trip.id)
            val endPoint = dataPointDao.getLastDataPoint(trip.id)
            if (startPoint == null || endPoint == null) return@filter false

            val startMatch = Math.abs(startPoint.latitude - startLat) < radiusDeg &&
                             Math.abs(startPoint.longitude - startLon) < radiusDeg
            val endMatch = Math.abs(endPoint.latitude - endLat) < radiusDeg &&
                           Math.abs(endPoint.longitude - endLon) < radiusDeg
            startMatch && endMatch
        }.take(limit).map { it.toDomain() }
    }

    /**
     * Triggers periodic AI model refinement after every Nth trip.
     * Call after [endTrip] to maintain up-to-date adaptive models.
     *
     * Schedule:
     * - Every 5th trip: fuel estimation AI refinement
     * - Every 10th trip: threshold and score weight refinement
     */
    suspend fun triggerPeriodicAiRefinement() {
        val count = getCompletedTripCount()
        val recentTrips = getRecentCompletedTrips(10)
        if (recentTrips.isEmpty()) return

        // Fuel estimation refinement every 5 trips
        if (count % 5 == 0) {
            android.util.Log.i("TripRepository", "Triggering fuel estimation AI refinement (trip #$count)")
            fuelEngine.performAiRefinement(recentTrips)
        }

        // Threshold and weight refinement every 10 trips
        if (count % 10 == 0) {
            android.util.Log.i("TripRepository", "Triggering adaptive AI refinement (trip #$count)")
            val history = recentTrips.joinToString("\n") {
                "Trip: score=${it.ecoScore}, brakes=${it.hardBrakeCount}, accels=${it.hardAccelCount}, " +
                "avgSpeed=${it.averageSpeedKmh}km/h, dist=${it.distanceKm}km"
            }
            adaptiveThresholdEngine.analyzeAndRefineThresholds(history)

            val vehicle = vehicleRepository.getDefaultVehicle()
            if (vehicle != null) {
                adaptiveScoreWeights.refineWeightsWithAi(vehicle.vehicleType, history)
            }
        }
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
