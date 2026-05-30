package com.ecodrive.app.sensor

import android.util.Log
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Combines data from all phone sensors (GPS + IMU) and Toyota API
 * into a unified [DrivingMetrics] stream.
 *
 * This is the central data-fusion hub that replaces the OBD-II
 * connection for the hybrid approach. It merges:
 *   1. GPS — speed, position, altitude, bearing
 *   2. Accelerometer — braking/acceleration force, cornering force
 *   3. Gyroscope — turn rate
 *   4. Barometer — road grade
 *   5. Toyota API — fuel level, odometer (periodic)
 */
@Singleton
class SensorDataManager @Inject constructor(
    private val locationTracker: LocationTracker,
    private val phoneSensorManager: PhoneSensorManager,
    private val fuelEngine: FuelEstimationEngine,
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

    // Latest Toyota API values (updated periodically from outside)
    private var toyotaFuelPercent: Double? = null
    private var toyotaOdometerKm: Double? = null

    // Road grade calculation state
    private var previousAltitude: Double? = null
    private var previousDistance: Double = 0.0

    // IMU readings buffer for better sensor fusion timing alignment
    private val imuReadingsBuffer = ArrayDeque<Pair<ImuReading, Long>>()

    // ── Public API ──────────────────────────────────────────────

    /**
     * Start collecting sensor data. Fuses GPS + IMU into DrivingMetrics.
     * Implements graceful degradation if sensors become unavailable.
     */
    fun startCollection() {
        if (_state.value == CollectionState.COLLECTING) return

        collectionJob?.cancel()
        collectionJob = CoroutineScope(Dispatchers.Default + SupervisorJob()).launch {
            _state.value = CollectionState.COLLECTING
            Log.i(TAG, "Starting sensor data collection")

            // Validate sensor availability
            val hasGps = true  // LocationTracker available
            val hasImu = phoneSensorManager.hasAccelerometer
            
            if (!hasGps) {
                Log.e(TAG, "GPS not available - cannot collect data")
                _state.value = CollectionState.ERROR
                _errorMessage.value = "GPS sensor not available"
                return@launch
            }

            Log.i(TAG, "Sensor availability - GPS: $hasGps, IMU: $hasImu")

            try {
                // Collect IMU in background if available, buffering readings for fusion
                val imuJob = if (hasImu) {
                    launch {
                        try {
                            phoneSensorManager.imuFlow().collect { imu ->
                                val now = System.nanoTime()
                                imuReadingsBuffer.addLast(imu to now)
                                // Keep buffer size manageable (max 10 readings = ~200ms at 50Hz)
                                while (imuReadingsBuffer.size > 10) {
                                    imuReadingsBuffer.removeFirst()
                                }
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "IMU sensor error: ${e.message}")
                        }
                    }
                } else {
                    null
                }

                // Main fusion loop — driven by GPS updates (~1Hz)
                try {
                    locationTracker.locationFlow().collect { gps ->
                        // ── Sensor Fusion with Timing Alignment ──────────
                        val now = Instant.now()
                        val gpsTimeNs = now.toEpochMilli() * 1_000_000L

                        // Select best IMU reading from buffer
                        val (bestImu, imuIsFresh) = selectBestImuReading(gpsTimeNs)
                        val imu = bestImu

                        if (imu != null && !imuIsFresh) {
                            Log.w(TAG, "IMU data stale (>100ms old), using zero values")
                        }

                        // Calculate road grade from altitude changes
                        val roadGrade = calculateRoadGrade(gps)

                        // Estimate fuel consumption using VSP model
                        val speedMps = gps.speedKmh / 3.6
                        
                        // Use IMU acceleration only if fresh; otherwise use 0
                        val accel = if (imuIsFresh && imu != null) {
                            imu.longitudinalAccel
                        } else {
                            0.0
                        }
                        
                        val fuelRateLPerH = fuelEngine.estimateFuelRateLPerH(
                            speedMps = speedMps,
                            accelerationMps2 = accel,
                            roadGradePercent = roadGrade,
                        )
                        val fuelConsumption = if (gps.speedKmh > 1.0) {
                            (fuelRateLPerH / gps.speedKmh) * 100.0
                        } else 0.0

                        val isMoving = gps.speedKmh >= Constants.MOVING_SPEED_THRESHOLD_KMH
                        val isIdle = !isMoving && (imu?.longitudinalAccel?.let {
                            kotlin.math.abs(it) < 0.5
                        } ?: true)

                        // Build metrics - use sensor data only if valid
                        _metrics.value = DrivingMetrics(
                            timestamp = now,
                            // GPS
                            speedKmh = gps.speedKmh,
                            latitude = gps.latitude,
                            longitude = gps.longitude,
                            altitudeM = gps.altitudeM,
                            bearingDegrees = gps.bearingDegrees,
                            gpsAccuracyM = gps.accuracyM,
                            // IMU (graceful fallback to 0 if unavailable/stale)
                            longitudinalAccelMps2 = if (imuIsFresh) imu?.longitudinalAccel ?: 0.0 else 0.0,
                            lateralAccelMps2 = if (imuIsFresh) imu?.lateralAccel ?: 0.0 else 0.0,
                            verticalAccelMps2 = if (imuIsFresh) imu?.verticalAccel ?: 0.0 else 0.0,
                            // Derived
                            fuelRateLPerH = fuelRateLPerH,
                            fuelConsumptionLPer100Km = fuelConsumption,
                            roadGradePercent = roadGrade,
                            isMoving = isMoving,
                            isIdle = isIdle,
                            // Toyota API
                            fuelTankPercent = toyotaFuelPercent,
                            odometerKm = toyotaOdometerKm,
                        )
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "GPS collection error: ${e.message}", e)
                    _state.value = CollectionState.ERROR
                    _errorMessage.value = "GPS error: ${e.message}"
                }
            } catch (e: Exception) {
                Log.e(TAG, "Sensor collection error: ${e.message}", e)
                _state.value = CollectionState.ERROR
                _errorMessage.value = e.message
            }
        }
    }

    /**
     * Stop collecting sensor data.
     */
    fun stopCollection() {
        collectionJob?.cancel()
        collectionJob = null
        _state.value = CollectionState.IDLE
        previousAltitude = null
        previousDistance = 0.0
        imuReadingsBuffer.clear()
        Log.i(TAG, "Sensor data collection stopped")
    }

    /**
     * Update Toyota API data (called periodically by ToyotaApiClient).
     */
    fun updateToyotaData(fuelPercent: Double?, odometerKm: Double?) {
        toyotaFuelPercent = fuelPercent
        toyotaOdometerKm = odometerKm
    }

    // ── Internals ───────────────────────────────────────────────

    /**
     * Select the best IMU reading from the buffer.
     * Returns the freshest reading that is within acceptable age threshold.
     * Prefers readings closest to GPS timestamp for optimal fusion.
     */
    private fun selectBestImuReading(gpsTimeNs: Long): Pair<ImuReading?, Boolean> {
        if (imuReadingsBuffer.isEmpty()) {
            return null to false
        }

        // Find IMU reading closest to GPS timestamp (preferring readings slightly before GPS)
        var bestReading: ImuReading? = null
        var bestTimeDiffNs = Long.MAX_VALUE
        var bestIsFresh = false

        for ((reading, readingTimeNs) in imuReadingsBuffer) {
            val timeDiffNs = kotlin.math.abs(gpsTimeNs - readingTimeNs)
            val isFresh = timeDiffNs < 100_000_000L  // 100ms threshold

            if (timeDiffNs < bestTimeDiffNs && isFresh) {
                bestReading = reading
                bestTimeDiffNs = timeDiffNs
                bestIsFresh = true
            }
        }

        // If no fresh reading found, use latest anyway (graceful degradation)
        if (bestReading == null && imuReadingsBuffer.isNotEmpty()) {
            val (latest, latestTimeNs) = imuReadingsBuffer.last()
            val timeDiffNs = kotlin.math.abs(gpsTimeNs - latestTimeNs)
            bestReading = latest
            bestIsFresh = timeDiffNs < 100_000_000L
        }

        return bestReading to bestIsFresh
    }

    /**
     * Calculate road grade (slope %) from GPS altitude changes.
     * Smoothed over distance to reduce GPS altitude noise.
     * Validates altitude data quality before using.
     */
    private fun calculateRoadGrade(gps: GpsReading): Double {
        val prevAlt = previousAltitude
        
        // ── Altitude Validation ────────────────────────────────
        // GPS altitude can be zero or missing - skip if not valid
        if (gps.altitudeM <= 0.0) {
            previousAltitude = null
            return 0.0
        }
        
        if (prevAlt == null || prevAlt <= 0.0) {
            previousAltitude = gps.altitudeM
            return 0.0
        }

        // Accumulate horizontal distance between GPS points
        // (simplified: use speed × time as approximation)
        val distanceIncrement = gps.speedKmh / 3.6 * (Constants.GPS_UPDATE_INTERVAL_MS / 1000.0)
        previousDistance += distanceIncrement

        // Only calculate grade over meaningful distances (reduce noise)
        return if (previousDistance >= 20.0) {  // Every ~20 meters
            val elevationChange = gps.altitudeM - prevAlt
            
            // Validate elevation change is reasonable (< 10m per 20m distance)
            val maxReasonableChange = (previousDistance / 10.0)  // Max 10% grade
            if (kotlin.math.abs(elevationChange) > maxReasonableChange) {
                Log.w(TAG, "Altitude spike detected: ${elevationChange}m over ${previousDistance}m - ignoring")
                previousAltitude = gps.altitudeM
                previousDistance = 0.0
                return 0.0
            }
            
            val grade = (elevationChange / previousDistance) * 100.0
            previousAltitude = gps.altitudeM
            previousDistance = 0.0
            grade.coerceIn(-15.0, 15.0)  // Clamp to reasonable road grades
        } else {
            0.0
        }
    }
}
