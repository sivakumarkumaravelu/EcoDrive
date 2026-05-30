package com.ecodrive.app.domain.recorder

import android.content.Context
import android.content.Intent
import android.util.Log
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.analyzer.DrivingPatternAnalyzer
import com.ecodrive.app.domain.analyzer.EcoScoreCalculator
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.EcoScore
import com.ecodrive.app.sensor.SensorDataManager
import com.ecodrive.app.service.SensorForegroundService
import com.ecodrive.app.util.AudioFeedbackManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
    private val sensorDataManager: SensorDataManager,
    private val tripRepository: TripRepository,
    private val analyzer: DrivingPatternAnalyzer,
    private val ecoScoreCalculator: EcoScoreCalculator,
    private val audioFeedbackManager: AudioFeedbackManager,
) {
    companion object {
        private const val TAG = "TripRecorder"
    }

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentMetrics = MutableStateFlow(DrivingMetrics())
    val currentMetrics: StateFlow<DrivingMetrics> = _currentMetrics.asStateFlow()

    private val _currentEcoScore = MutableStateFlow(EcoScore(overall = 0))
    val currentEcoScore: StateFlow<EcoScore> = _currentEcoScore.asStateFlow()

    // Trip State
    private var activeTripId: Long? = null
    private var hardBrakes = 0
    private var hardAccels = 0
    private var sharpTurns = 0
    private var tripStartTimeMs = 0L
    private var totalDistanceKm = 0.0
    private var totalFuelConsumed = 0.0
    private var maxSpeed = 0.0
    private var lastSpeedKmh = 0.0
    private var lastTimestampMs = 0L
    private var idleTimeMs = 0L
    private var dataPointCounter = 0

    init {
        observeMetrics()
    }

    private fun observeMetrics() {
        scope.launch {
            sensorDataManager.metrics.collect { metrics ->
                if (metrics.timestamp.toEpochMilli() > 0 && _isRecording.value) {
                    processMetrics(metrics)
                }
            }
        }
    }

    fun startRecording() {
        if (_isRecording.value) return
        
        Log.i(TAG, "Starting trip recording")
        resetCounters()

        // Start foreground service
        val serviceIntent = Intent(context, SensorForegroundService::class.java)
        context.startForegroundService(serviceIntent)

        // Start sensor collection
        sensorDataManager.startCollection()

        // Create trip in database
        scope.launch {
            activeTripId = tripRepository.startTrip()
        }

        _isRecording.value = true
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        
        Log.i(TAG, "Stopping trip recording")
        
        // Stop sensor collection
        sensorDataManager.stopCollection()

        // Stop foreground service
        val serviceIntent = Intent(context, SensorForegroundService::class.java)
        context.stopService(serviceIntent)

        // Save trip to database
        val tripId = activeTripId
        if (tripId != null) {
            val tripDurationSeconds = if (tripStartTimeMs > 0) (System.currentTimeMillis() - tripStartTimeMs) / 1000 else 0L
            val ecoScore = _currentEcoScore.value
            
            scope.launch {
                val avgSpeed = if (tripDurationSeconds > 0) {
                    totalDistanceKm / (tripDurationSeconds / 3600.0)
                } else 0.0

                tripRepository.endTrip(
                    tripId = tripId,
                    distanceKm = totalDistanceKm,
                    durationSeconds = tripDurationSeconds,
                    averageSpeedKmh = avgSpeed,
                    maxSpeedKmh = maxSpeed,
                    fuelConsumedEstimate = totalFuelConsumed,
                    ecoScore = ecoScore.overall,
                    hardBrakeCount = hardBrakes,
                    hardAccelCount = hardAccels,
                    sharpTurnCount = sharpTurns,
                    idleTimeSeconds = idleTimeMs / 1000,
                )
                activeTripId = null
            }
        }

        _isRecording.value = false
        audioFeedbackManager.playTip("Trip saved.")
    }

    private fun resetCounters() {
        hardBrakes = 0
        hardAccels = 0
        sharpTurns = 0
        tripStartTimeMs = 0L
        totalDistanceKm = 0.0
        totalFuelConsumed = 0.0
        maxSpeed = 0.0
        lastSpeedKmh = 0.0
        lastTimestampMs = 0L
        idleTimeMs = 0L
        dataPointCounter = 0
        analyzer.reset()
        _currentEcoScore.value = EcoScore(overall = 0)
        _currentMetrics.value = DrivingMetrics()
    }

    private suspend fun processMetrics(metrics: DrivingMetrics) {
        val now = metrics.timestamp.toEpochMilli()

        if (lastTimestampMs > 0) {
            val deltaTimeSec = (now - lastTimestampMs) / 1000.0
            val avgSpeed = (metrics.speedKmh + lastSpeedKmh) / 2.0
            totalDistanceKm += (avgSpeed * deltaTimeSec) / 3600.0
            totalFuelConsumed += metrics.fuelRateLPerH * (deltaTimeSec / 3600.0)
            if (metrics.isIdle) idleTimeMs += (deltaTimeSec * 1000).toLong()
        } else {
            tripStartTimeMs = now
        }

        if (metrics.speedKmh > maxSpeed) maxSpeed = metrics.speedKmh
        lastSpeedKmh = metrics.speedKmh
        lastTimestampMs = now

        val tripId = activeTripId ?: 0
        val events = analyzer.analyze(metrics, tripId)
        for (event in events) {
            audioFeedbackManager.playEventFeedback(event)
            when (event.type) {
                DrivingEventType.HARD_BRAKE -> hardBrakes++
                DrivingEventType.HARD_ACCELERATION -> hardAccels++
                DrivingEventType.SHARP_TURN -> sharpTurns++
                else -> {}
            }
        }

        if (events.isNotEmpty() && tripId > 0) {
            tripRepository.saveEvents(events)
        }

        dataPointCounter++
        if (dataPointCounter % 5 == 0 && tripId > 0) {
            tripRepository.saveDataPoint(tripId, metrics)
        }

        val tripDuration = if (tripStartTimeMs > 0) (now - tripStartTimeMs) / 1000 else 0L
        val idlePercent = if (tripDuration > 0) {
            (idleTimeMs / 1000.0 / tripDuration) * 100.0
        } else 0.0

        val ecoScore = ecoScoreCalculator.calculate(
            hardBrakeCount = hardBrakes,
            hardAccelCount = hardAccels,
            sharpTurnCount = sharpTurns,
            averageSpeedKmh = if (totalDistanceKm > 0 && tripDuration > 0) {
                totalDistanceKm / (tripDuration / 3600.0)
            } else metrics.speedKmh,
            idleTimePercent = idlePercent,
            speedStdDeviation = analyzer.getSpeedStdDeviation(),
            tripDurationMinutes = tripDuration / 60.0,
        )

        _currentMetrics.value = metrics
        _currentEcoScore.value = ecoScore
    }
}
