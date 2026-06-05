package com.ecodrive.app.sensor

import android.util.Log
import com.ecodrive.app.data.repository.VehicleRepository
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.domain.model.Vehicle
import com.ecodrive.app.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combines data from all phone sensors (GPS + IMU) and Vehicle API
 * into a unified [DrivingMetrics] stream.
 */
@Singleton
class SensorDataManager @Inject constructor(
    private val locationTracker: LocationTracker,
    private val phoneSensorManager: PhoneSensorManager,
    private val fuelEngine: FuelEstimationEngine,
    private val vehicleRepository: VehicleRepository,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    companion object {
        private const val TAG = "SensorDataManager"
    }

    // ── State ───────────────────────────────────────────────────

    enum class CollectionState {
        IDLE,
        COLLECTING,
        ERROR,
    }

    private val _state = MutableStateFlow(CollectionState.IDLE)
    val state: StateFlow<CollectionState> = _state.asStateFlow()

    private val _metrics = MutableStateFlow(DrivingMetrics())
    val metrics: StateFlow<DrivingMetrics> = _metrics.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var collectionJob: Job? = null

    // Latest Vehicle API values
    @Volatile
    private var vehicleFuelPercent: Double? = null
    @Volatile
    private var vehicleOdometerKm: Double? = null

    // Road grade calculation state
    private var previousAltitude: Double? = null
    private var previousDistance: Double = 0.0
    private var lastTimestampMs: Long = 0L

    // IMU readings buffer
    private val imuReadingsBuffer = java.util.Collections.synchronizedList(mutableListOf<Pair<ImuReading, Long>>())

    // ── Public API ──────────────────────────────────────────────

    fun startCollection() {
        if (_state.value == CollectionState.COLLECTING) return

        collectionJob?.cancel()
        collectionJob = CoroutineScope(defaultDispatcher + SupervisorJob()).launch {
            _state.value = CollectionState.COLLECTING
            Log.i(TAG, "Starting sensor data collection")

            // Get active vehicle profile (fallback to default if none)
            val activeVehicle = vehicleRepository.getDefaultVehicle() ?: Vehicle()
            Log.i(TAG, "Using vehicle profile: ${activeVehicle.name} (${activeVehicle.make} ${activeVehicle.model})")

            val hasGps = true
            val hasImu = phoneSensorManager.hasAccelerometer
            
            if (!hasGps) {
                _state.value = CollectionState.ERROR
                _errorMessage.value = "GPS sensor not available"
                return@launch
            }

            try {
                if (hasImu) {
                    launch {
                        phoneSensorManager.imuFlow().collect { imu ->
                            val now = System.nanoTime()
                            synchronized(imuReadingsBuffer) {
                                imuReadingsBuffer.add(imu to now)
                                while (imuReadingsBuffer.size > 10) {
                                    imuReadingsBuffer.removeAt(0)
                                }
                            }
                        }
                    }
                }

                locationTracker.locationFlow().collect { gps ->
                    val now = Instant.now()
                    val gpsTimeNs = now.toEpochMilli() * 1_000_000L

                    val (bestImu, imuIsFresh) = selectBestImuReading(gpsTimeNs)
                    val imu = bestImu

                    val deltaTimeSec = if (lastTimestampMs > 0) {
                        (now.toEpochMilli() - lastTimestampMs) / 1000.0
                    } else 0.0
                    
                    val roadGrade = calculateRoadGrade(gps, deltaTimeSec)

                    val speedMps = gps.speedKmh / 3.6
                    val accel = if (imuIsFresh && imu != null) imu.longitudinalAccel else 0.0
                    
                    val fuelRateLPerH = fuelEngine.estimateFuelRateLPerH(
                        speedMps = speedMps,
                        accelerationMps2 = accel,
                        roadGradePercent = roadGrade,
                        vehicle = activeVehicle
                    )
                    
                    val fuelConsumption = if (gps.speedKmh > 1.0) {
                        (fuelRateLPerH / gps.speedKmh) * 100.0
                    } else 0.0

                    val isMoving = gps.speedKmh >= Constants.MOVING_SPEED_THRESHOLD_KMH
                    val isIdle = !isMoving && (imu?.longitudinalAccel?.let {
                        kotlin.math.abs(it) < 0.5
                    } ?: true)

                    lastTimestampMs = now.toEpochMilli()

                    _metrics.value = DrivingMetrics(
                        timestamp = now,
                        speedKmh = gps.speedKmh,
                        latitude = gps.latitude,
                        longitude = gps.longitude,
                        altitudeM = gps.altitudeM,
                        bearingDegrees = gps.bearingDegrees,
                        gpsAccuracyM = gps.accuracyM,
                        longitudinalAccelMps2 = if (imuIsFresh) imu?.longitudinalAccel ?: 0.0 else 0.0,
                        lateralAccelMps2 = if (imuIsFresh) imu?.lateralAccel ?: 0.0 else 0.0,
                        verticalAccelMps2 = if (imuIsFresh) imu?.verticalAccel ?: 0.0 else 0.0,
                        fuelRateLPerH = fuelRateLPerH,
                        fuelConsumptionLPer100Km = fuelConsumption,
                        roadGradePercent = roadGrade,
                        isMoving = isMoving,
                        isIdle = isIdle,
                        fuelTankPercent = vehicleFuelPercent,
                        odometerKm = vehicleOdometerKm,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sensor collection error: ${e.message}", e)
                _state.value = CollectionState.ERROR
                _errorMessage.value = e.message
            }
        }
    }

    fun stopCollection() {
        collectionJob?.cancel()
        collectionJob = null
        _state.value = CollectionState.IDLE
        previousAltitude = null
        previousDistance = 0.0
        lastTimestampMs = 0L
        imuReadingsBuffer.clear()
        Log.i(TAG, "Sensor data collection stopped")
    }

    fun updateSmartcarData(fuelPercent: Double?, odometerKm: Double?) {
        vehicleFuelPercent = fuelPercent
        vehicleOdometerKm = odometerKm
    }

    // ── Internals ───────────────────────────────────────────────

    private fun selectBestImuReading(gpsTimeNs: Long): Pair<ImuReading?, Boolean> {
        return synchronized(imuReadingsBuffer) {
            if (imuReadingsBuffer.isEmpty()) return@synchronized null to false

            var bestReading: ImuReading? = null
            var bestTimeDiffNs = Long.MAX_VALUE
            var bestIsFresh = false

            for ((reading, readingTimeNs) in imuReadingsBuffer) {
                val timeDiffNs = kotlin.math.abs(gpsTimeNs - readingTimeNs)
                val isFresh = timeDiffNs < 100_000_000L

                if (timeDiffNs < bestTimeDiffNs && isFresh) {
                    bestReading = reading
                    bestTimeDiffNs = timeDiffNs
                    bestIsFresh = true
                }
            }

            if (bestReading == null && imuReadingsBuffer.isNotEmpty()) {
                val (latest, latestTimeNs) = imuReadingsBuffer.last()
                val timeDiffNs = kotlin.math.abs(gpsTimeNs - latestTimeNs)
                bestReading = latest
                bestIsFresh = timeDiffNs < 100_000_000L
            }

            bestReading to bestIsFresh
        }
    }

    private fun calculateRoadGrade(gps: GpsReading, deltaTimeSec: Double): Double {
        val prevAlt = previousAltitude
        if (gps.altitudeM <= 0.0) {
            previousAltitude = null
            return 0.0
        }
        if (prevAlt == null || prevAlt <= 0.0) {
            previousAltitude = gps.altitudeM
            return 0.0
        }

        if (deltaTimeSec <= 0.0) return 0.0

        val distanceIncrement = gps.speedKmh / 3.6 * deltaTimeSec
        previousDistance += distanceIncrement

        return if (previousDistance >= 20.0) {
            val elevationChange = gps.altitudeM - prevAlt
            val maxReasonableChange = (previousDistance / 10.0)
            if (kotlin.math.abs(elevationChange) > maxReasonableChange) {
                previousAltitude = gps.altitudeM
                previousDistance = 0.0
                return 0.0
            }
            val grade = (elevationChange / previousDistance) * 100.0
            previousAltitude = gps.altitudeM
            previousDistance = 0.0
            grade.coerceIn(-15.0, 15.0)
        } else {
            0.0
        }
    }
}
