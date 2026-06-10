package com.ecodrive.app.service

import com.ecodrive.app.domain.ai.service.AiCoachService
import com.ecodrive.app.domain.ai.analyzer.FatigueDetector
import com.ecodrive.app.domain.ai.analyzer.FatigueStatus
import com.ecodrive.app.domain.ai.engine.AdaptiveScoreWeights
import com.ecodrive.app.domain.ai.engine.AdaptiveThresholdEngine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.analyzer.DrivingPatternAnalyzer
import com.ecodrive.app.domain.analyzer.EcoScoreCalculator
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.EcoScore
import com.ecodrive.app.data.sensor.SensorDataManager
import com.ecodrive.app.ui.MainActivity
import com.ecodrive.app.domain.service.AudioFeedbackManager
import com.ecodrive.app.util.Constants
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Foreground service to keep sensor data collection running
 * while the app is in the background or the screen is off.
 */
@AndroidEntryPoint
class SensorForegroundService : Service() {

    @Inject
    lateinit var sensorDataManager: SensorDataManager

    @Inject
    lateinit var tripRepository: TripRepository

    @Inject
    lateinit var analyzer: DrivingPatternAnalyzer

    @Inject
    lateinit var ecoScoreCalculator: EcoScoreCalculator

    @Inject
    lateinit var adaptiveScoreWeights: com.ecodrive.app.domain.ai.engine.AdaptiveScoreWeights

    @Inject
    lateinit var adaptiveThresholdEngine: com.ecodrive.app.domain.ai.engine.AdaptiveThresholdEngine

    @Inject
    lateinit var aiCoachService: com.ecodrive.app.domain.ai.service.AiCoachService

    @Inject
    lateinit var fatigueDetector: com.ecodrive.app.domain.ai.analyzer.FatigueDetector

    @Inject
    lateinit var vehicleRepository: com.ecodrive.app.data.repository.VehicleRepository

    @Inject
    lateinit var audioFeedbackManager: AudioFeedbackManager

    @Inject
    lateinit var anomalyDetector: com.ecodrive.app.domain.ai.analyzer.AnomalyDetector

    @Inject
    @com.ecodrive.app.di.ApplicationScope
    lateinit var applicationScope: CoroutineScope

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val binder = LocalBinder()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentMetrics = MutableStateFlow(DrivingMetrics())
    val currentMetrics: StateFlow<DrivingMetrics> = _currentMetrics.asStateFlow()

    private val _currentEcoScore = MutableStateFlow(EcoScore(overall = 0))
    val currentEcoScore: StateFlow<EcoScore> = _currentEcoScore.asStateFlow()

    private val _latestTip = MutableStateFlow<String?>(null)
    val latestTip: StateFlow<String?> = _latestTip.asStateFlow()

    // Live trip stat flows
    private val _tripDurationSeconds = MutableStateFlow(0L)
    val tripDurationSeconds: StateFlow<Long> = _tripDurationSeconds.asStateFlow()

    private val _tripDistanceKm = MutableStateFlow(0.0)
    val tripDistanceKm: StateFlow<Double> = _tripDistanceKm.asStateFlow()

    private val _fuelConsumedEstimate = MutableStateFlow(0.0)
    val fuelConsumedEstimate: StateFlow<Double> = _fuelConsumedEstimate.asStateFlow()

    private val _hardBrakeCount = MutableStateFlow(0)
    val hardBrakeCount: StateFlow<Int> = _hardBrakeCount.asStateFlow()

    private val _hardAccelCount = MutableStateFlow(0)
    val hardAccelCount: StateFlow<Int> = _hardAccelCount.asStateFlow()

    private val _sharpTurnCount = MutableStateFlow(0)
    val sharpTurnCount: StateFlow<Int> = _sharpTurnCount.asStateFlow()

    // Trip State
    private var activeTripId: Long? = null
    private var vehicleType: com.ecodrive.app.domain.model.VehicleType = com.ecodrive.app.domain.model.VehicleType.ICE
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
    private var lastFatigueAlertMs = 0L

    // Performance Optimization: Batching data points
    private val dataPointBatch = mutableListOf<DrivingMetrics>()
    private val BATCH_SIZE = 20

    // Fuel rate smoothing: rolling average over last 5 samples prevents single-spike inflation.
    private val fuelRateBuffer = ArrayDeque<Double>(5)

    inner class LocalBinder : Binder() {
        fun getService(): SensorForegroundService = this@SensorForegroundService
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        observeMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START_RECORDING -> startRecording()
            ACTION_STOP_RECORDING -> stopRecording()
        }

        startForeground(Constants.NOTIFICATION_ID, createNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
        serviceScope.cancel()
    }

    private fun observeMetrics() {
        serviceScope.launch {
            sensorDataManager.metrics.collect { metrics ->
                if (metrics.timestamp.toEpochMilli() > 0 && _isRecording.value) {
                    processMetrics(metrics)
                }
            }
        }
    }

    fun startRecording() {
        if (_isRecording.value) return
        
        Log.i(TAG, "Starting trip recording in service")
        resetCounters()
        aiCoachService.clearContext()
        sensorDataManager.startCollection()

        serviceScope.launch {
            val vehicle = vehicleRepository.getDefaultVehicle()
            vehicleType = vehicle?.vehicleType ?: com.ecodrive.app.domain.model.VehicleType.ICE
            
            // Set adaptive thresholds
            val thresholds = adaptiveThresholdEngine.getPersonalizedThresholds()
            analyzer.updateThresholds(thresholds)

            activeTripId = tripRepository.startTrip(vehicle?.id ?: 1)
            anomalyDetector.reset()
            _isRecording.value = true
        }
    }

    fun stopRecording() {
        if (!_isRecording.value) return
        
        Log.i(TAG, "Stopping trip recording in service")
        sensorDataManager.stopCollection()

        val tripId = activeTripId
        if (tripId != null) {
            // Save remaining batched points
            if (dataPointBatch.isNotEmpty()) {
                val finalBatch = ArrayList(dataPointBatch)
                dataPointBatch.clear()
                applicationScope.launch(Dispatchers.IO) {
                    tripRepository.saveDataPointBatch(tripId, finalBatch)
                }
            }

            val tripDurationSeconds = if (tripStartTimeMs > 0) (System.currentTimeMillis() - tripStartTimeMs) / 1000 else 0L
            val ecoScore = _currentEcoScore.value
            val avgSpeed = if (tripDurationSeconds > 0) {
                totalDistanceKm / (tripDurationSeconds / 3600.0)
            } else 0.0

            // Use applicationScope to ensure trip is saved even if service is being destroyed
            applicationScope.launch {
                try {
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
                    Log.i(TAG, "Trip $tripId ended successfully")

                    // Activate dormant AI: trigger periodic refinement after every trip
                    tripRepository.triggerPeriodicAiRefinement()

                    // Enrich any detected anomalies with AI diagnosis
                    val rawAnomalies = anomalyDetector.getDetectedAnomalies()
                    if (rawAnomalies.isNotEmpty()) {
                        Log.i(TAG, "Enriching ${rawAnomalies.size} anomalies with AI diagnosis")
                        val enrichedAnomalies = anomalyDetector.enrichWithAiDiagnosis(rawAnomalies)
                        tripRepository.saveAnomalies(tripId, enrichedAnomalies)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to end trip $tripId: ${e.message}")
                } finally {
                    activeTripId = null
                    _isRecording.value = false
                    withContext(Dispatchers.Main) {
                        audioFeedbackManager.playTip("Trip saved.")
                    }
                    stopSelf()
                }
            }
        } else {
            _isRecording.value = false
            stopSelf()
        }
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
        fuelRateBuffer.clear()
        analyzer.reset()
        _currentEcoScore.value = EcoScore(overall = 0)
        _currentMetrics.value = DrivingMetrics()
        // Reset live stat flows
        _tripDurationSeconds.value = 0L
        _tripDistanceKm.value = 0.0
        _fuelConsumedEstimate.value = 0.0
        _hardBrakeCount.value = 0
        _hardAccelCount.value = 0
        _sharpTurnCount.value = 0
    }

    private suspend fun processMetrics(metrics: DrivingMetrics) {
        val now = metrics.timestamp.toEpochMilli()

        if (lastTimestampMs > 0) {
            // Cap delta time at 10 seconds to guard against stale-timer spikes
            // (e.g. when the app is paused/resumed or GPS lags).
            val deltaTimeSec = ((now - lastTimestampMs) / 1000.0).coerceAtMost(10.0)
            val avgSpeed = (metrics.speedKmh + lastSpeedKmh) / 2.0
            totalDistanceKm += (avgSpeed * deltaTimeSec) / 3600.0

            // Use a 5-sample rolling average of fuelRateLPerH before accumulating,
            // so a single high-spike reading does not materially inflate the trip total.
            fuelRateBuffer.addLast(metrics.fuelRateLPerH)
            if (fuelRateBuffer.size > 5) fuelRateBuffer.removeFirst()
            val smoothedFuelRate = fuelRateBuffer.average()
            totalFuelConsumed += smoothedFuelRate * (deltaTimeSec / 3600.0)

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
                DrivingEventType.HARD_BRAKE -> { hardBrakes++; _hardBrakeCount.value = hardBrakes }
                DrivingEventType.HARD_ACCELERATION -> { hardAccels++; _hardAccelCount.value = hardAccels }
                DrivingEventType.SHARP_TURN -> { sharpTurns++; _sharpTurnCount.value = sharpTurns }
                else -> {}
            }
        }

        if (events.isNotEmpty() && tripId > 0) {
            tripRepository.saveEvents(events)
        }

        dataPointCounter++
        
        // Performance Optimization: Batch database writes
        if (tripId > 0) {
            dataPointBatch.add(metrics)
            if (dataPointBatch.size >= BATCH_SIZE) {
                val batchToSave = ArrayList(dataPointBatch)
                dataPointBatch.clear()
                serviceScope.launch(Dispatchers.IO) {
                    tripRepository.saveDataPointBatch(tripId, batchToSave)
                }
            }
        }

        val tripDuration = if (tripStartTimeMs > 0) (now - tripStartTimeMs) / 1000 else 0L
        val idlePercent = if (tripDuration > 0) {
            (idleTimeMs / 1000.0 / tripDuration) * 100.0
        } else 0.0

        // Emit live stat updates
        _tripDurationSeconds.value = tripDuration
        _tripDistanceKm.value = totalDistanceKm
        _fuelConsumedEstimate.value = totalFuelConsumed

        val weights = adaptiveScoreWeights.getWeightsForVehicle(vehicleType)
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
            weights = weights
        )

        _currentMetrics.value = metrics
        _currentEcoScore.value = ecoScore

        // Fatigue Detection
        val fatigueStatus = fatigueDetector.analyze(metrics)
        if (fatigueStatus != com.ecodrive.app.domain.ai.analyzer.FatigueStatus.NORMAL) {
            handleFatigue(fatigueStatus)
        }

        // Vehicle Anomaly Detection
        val anomalies = anomalyDetector.analyze(metrics)
        for (anomaly in anomalies) {
            if (anomaly.severity == com.ecodrive.app.domain.model.AnomalySeverity.HIGH) {
                serviceScope.launch(Dispatchers.Main) {
                    // Anomaly warnings are safety-critical — use playSafetyAlert for QUEUE_FLUSH priority.
                    audioFeedbackManager.playSafetyAlert(anomaly.description)
                }
            }
        }

        // Real-time AI Coaching
        aiCoachService.updateContext(metrics)
        maybeFetchAiTip(metrics, ecoScore)
    }

    private fun handleFatigue(status: com.ecodrive.app.domain.ai.analyzer.FatigueStatus) {
        val now = System.currentTimeMillis()
        if (now - lastFatigueAlertMs < 60_000L) return // Don't annoy too much
        
        lastFatigueAlertMs = now
        val message = when (status) {
            com.ecodrive.app.domain.ai.analyzer.FatigueStatus.HIGH_RISK -> 
                "High fatigue risk detected. Please consider taking a break."
            com.ecodrive.app.domain.ai.analyzer.FatigueStatus.MODERATE_RISK -> 
                "Your driving patterns suggest reduced focus. Stay alert."
            else -> ""
        }
        
        if (message.isNotBlank()) {
            serviceScope.launch(Dispatchers.Main) {
                // Fatigue alerts are safety-critical — use playSafetyAlert for QUEUE_FLUSH priority.
                audioFeedbackManager.playSafetyAlert(message)
            }
        }
    }

    private suspend fun maybeFetchAiTip(
        metrics: DrivingMetrics,
        ecoScore: EcoScore
    ) {
        val tip = aiCoachService.getRealTimeTip(metrics, ecoScore)
        if (tip != null) {
            _latestTip.value = tip
            withContext(Dispatchers.Main) {
                // AI coaching tips are non-urgent — play via QUEUE_ADD so they don't interrupt alerts.
                audioFeedbackManager.playTip(tip)
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            Constants.NOTIFICATION_CHANNEL_ID,
            "EcoDrive Recording",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "Shows driving monitoring status"
            setShowBadge(false)
        }

        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(channel)
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, Constants.NOTIFICATION_CHANNEL_ID)
            .setContentTitle("EcoDrive Recording")
            .setContentText("Monitoring your driving with phone sensors…")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        private const val TAG = "SensorForegroundService"
        const val ACTION_START_RECORDING = "com.ecodrive.app.action.START_RECORDING"
        const val ACTION_STOP_RECORDING = "com.ecodrive.app.action.STOP_RECORDING"
    }
}
