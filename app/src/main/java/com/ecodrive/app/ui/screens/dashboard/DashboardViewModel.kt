package com.ecodrive.app.ui.screens.dashboard

import com.ecodrive.app.domain.ai.service.AiCoachService

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.domain.model.*
import com.ecodrive.app.domain.recorder.TripRecorder
import com.ecodrive.app.data.sensor.SensorDataManager
import com.ecodrive.app.util.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the main Dashboard screen.
 * Manages sensor collection state and UI updates by observing TripRecorder.
 */
@HiltViewModel
class DashboardViewModel @Inject constructor(
    private val sensorDataManager: SensorDataManager,
    private val smartcarApiClient: SmartcarApiClient,
    private val tripRecorder: TripRecorder,
    private val preferenceManager: PreferenceManager,
    private val aiCoachService: com.ecodrive.app.domain.ai.service.AiCoachService,
    private val ecoScorePredictor: com.ecodrive.app.domain.ai.analyzer.EcoScorePredictor,
    val permissionManager: PermissionManager,
) : ViewModel() {

    // ── UI State ────────────────────────────────────────────────

    data class DashboardState(
        val sensorState: SensorDataManager.CollectionState = SensorDataManager.CollectionState.IDLE,
        val smartcarApiState: SmartcarApiClient.ApiState = SmartcarApiClient.ApiState.NOT_CONFIGURED,
        val metrics: DrivingMetrics = DrivingMetrics(),
        val ecoScore: EcoScore = EcoScore(overall = 0),
        val hardBrakeCount: Int = 0,
        val hardAccelCount: Int = 0,
        val sharpTurnCount: Int = 0,
        val tripDurationSeconds: Long = 0,
        val tripDistanceKm: Double = 0.0,
        val fuelConsumedEstimate: Double = 0.0,
        val errorMessage: String? = null,
        val drivingTip: String = "Tap Start to begin monitoring your drive",
        val isRecording: Boolean = false,
        val dataSource: String = "Phone Sensors",
        val needsPermissions: Boolean = false,
        val maxSpeedKmh: Double = 0.0,
        val useMetric: Boolean = true,
        val predictedScore: PredictedScore? = null,
    )

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        checkPermissions()
        observeSensorState()
        observeSmartcarState()
        observeRecorderState()
        observePreferences()
        fetchPrediction()
    }

    private fun fetchPrediction() {
        viewModelScope.launch {
            val prediction = ecoScorePredictor.predictForNow()
            if (prediction != null) {
                _state.update {
                    it.copy(
                        predictedScore = prediction,
                        drivingTip = "Predicted Score: ${prediction.expected}/100. ${prediction.explanation}"
                    )
                }
            }
        }
    }

    private fun observePreferences() {
        preferenceManager.useMetricUnits
            .onEach { useMetric ->
                _state.update { it.copy(useMetric = useMetric) }
            }
            .launchIn(viewModelScope)
    }

    // ── Permission Handling ─────────────────────────────────────

    fun checkPermissions() {
        _state.update {
            it.copy(needsPermissions = !permissionManager.hasRequiredPermissions())
        }
    }

    fun onPermissionsGranted() {
        _state.update { it.copy(needsPermissions = false) }
    }

    // ── Observation ─────────────────────────────────────────────

    private fun observeSensorState() {
        viewModelScope.launch {
            sensorDataManager.state.collect { sensorState ->
                _state.update { it.copy(sensorState = sensorState) }
            }
        }
        viewModelScope.launch {
            sensorDataManager.errorMessage.collect { error ->
                _state.update { it.copy(errorMessage = error) }
            }
        }
    }

    private fun observeSmartcarState() {
        viewModelScope.launch {
            smartcarApiClient.state.collect { apiState ->
                _state.update { it.copy(smartcarApiState = apiState) }
            }
        }
        viewModelScope.launch {
            smartcarApiClient.vehicleData.collect { data ->
                sensorDataManager.updateSmartcarData(
                    fuelPercent = data.fuelPercent,
                    odometerKm = data.odometerKm,
                )
            }
        }
    }

    private fun observeRecorderState() {
        viewModelScope.launch {
            tripRecorder.isRecording.collect { isRecording ->
                _state.update { it.copy(isRecording = isRecording) }
            }
        }
        viewModelScope.launch {
            tripRecorder.currentMetrics.collect { metrics ->
                val tip = generateDrivingTip(metrics, _state.value.ecoScore)
                
                val source = buildString {
                    append("📱 Sensors")
                    if (metrics.fuelTankPercent != null) append(" + 🌐 Vehicle API")
                }

                _state.update {
                    it.copy(
                        metrics = metrics,
                        drivingTip = if (it.drivingTip.startsWith("🤖")) it.drivingTip else tip,
                        dataSource = source,
                    )
                }
            }
        }
        viewModelScope.launch {
            tripRecorder.currentEcoScore.collect { ecoScore ->
                _state.update {
                    it.copy(ecoScore = ecoScore)
                }
            }
        }
        viewModelScope.launch {
            tripRecorder.latestTip.collect { tip ->
                if (tip != null) {
                    _state.update { it.copy(drivingTip = "🤖 $tip") }
                }
            }
        }
    }

    // ── User Actions ────────────────────────────────────────────

    fun startRecording() {
        if (!permissionManager.hasRequiredPermissions()) {
            _state.update { it.copy(needsPermissions = true) }
            return
        }

        aiCoachService.clearContext()
        tripRecorder.startRecording()
    }

    fun stopRecording() {
        tripRecorder.stopRecording()
        aiCoachService.clearContext()
        _state.update {
            it.copy(
                drivingTip = "Trip saved! Check your trip history for details.",
            )
        }
    }

    private fun generateDrivingTip(metrics: DrivingMetrics, ecoScore: EcoScore): String {
        return when {
            !_state.value.isRecording ->
                "Tap Start to begin monitoring your drive"
            metrics.speedKmh > 110 ->
                "🐢 Slow down! Fuel consumption increases exponentially above 110 km/h."
            metrics.longitudinalAccelMps2 > 2.5 ->
                "🦶 Ease off! Gentle acceleration saves up to 30% fuel."
            metrics.longitudinalAccelMps2 < -2.5 ->
                "👀 Try to anticipate stops — coast when you can see a red light ahead."
            kotlin.math.abs(metrics.lateralAccelMps2) > 3.0 ->
                "🔄 Smooth cornering saves tires and fuel. Slow before the turn, not during."
            metrics.isIdle ->
                "⏱️ Idling wastes fuel. Consider stopping the engine if parked for over 30 seconds."
            ecoScore.overall >= 85 ->
                "🌟 Fantastic driving! You are in peak efficiency mode."
            ecoScore.brakingScore < 50 ->
                "👀 Look further ahead. Anticipating stops reduces hard braking and saves fuel."
            ecoScore.accelerationScore < 50 ->
                "🚀 Smooth starts can improve fuel economy by 10-20%."
            ecoScore.consistencyScore < 50 ->
                "📏 Maintain steady speed. Use cruise control on the highway when safe."
            else ->
                "🌿 Drive smoothly and optimize your vehicle's efficiency."
        }
    }
}
