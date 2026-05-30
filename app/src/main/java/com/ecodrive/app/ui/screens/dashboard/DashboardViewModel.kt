package com.ecodrive.app.ui.screens.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.domain.model.*
import com.ecodrive.app.domain.recorder.TripRecorder
import com.ecodrive.app.sensor.SensorDataManager
import com.ecodrive.app.data.remote.ToyotaApiClient
import com.ecodrive.app.util.AudioFeedbackManager
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
    private val toyotaApiClient: ToyotaApiClient,
    private val audioFeedbackManager: AudioFeedbackManager,
    private val tripRecorder: TripRecorder,
    val permissionManager: PermissionManager,
) : ViewModel() {

    // ── UI State ────────────────────────────────────────────────

    data class DashboardState(
        val sensorState: SensorDataManager.CollectionState = SensorDataManager.CollectionState.IDLE,
        val toyotaApiState: ToyotaApiClient.ApiState = ToyotaApiClient.ApiState.NOT_CONFIGURED,
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
    )

    private val _state = MutableStateFlow(DashboardState())
    val state: StateFlow<DashboardState> = _state.asStateFlow()

    init {
        checkPermissions()
        observeSensorState()
        observeToyotaState()
        observeRecorderState()
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

    private fun observeToyotaState() {
        viewModelScope.launch {
            toyotaApiClient.state.collect { apiState ->
                _state.update { it.copy(toyotaApiState = apiState) }
            }
        }
        viewModelScope.launch {
            toyotaApiClient.vehicleData.collect { data ->
                sensorDataManager.updateToyotaData(
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
                if (tip != _state.value.drivingTip && tip != "Tap Start to begin monitoring your drive") {
                    audioFeedbackManager.playTip(tip)
                }

                val source = buildString {
                    append("📱 Sensors")
                    if (metrics.fuelTankPercent != null) append(" + 🌐 Toyota API")
                }

                _state.update {
                    it.copy(
                        metrics = metrics,
                        drivingTip = tip,
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
    }

    // ── User Actions ────────────────────────────────────────────

    fun startRecording() {
        if (!permissionManager.hasRequiredPermissions()) {
            _state.update { it.copy(needsPermissions = true) }
            return
        }

        tripRecorder.startRecording()
    }

    fun stopRecording() {
        tripRecorder.stopRecording()
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
                "🌟 Fantastic driving! Your Highlander Hybrid is in peak efficiency mode."
            ecoScore.brakingScore < 50 ->
                "👀 Look further ahead. Anticipating stops reduces hard braking and saves fuel."
            ecoScore.accelerationScore < 50 ->
                "🚀 Smooth starts can improve fuel economy by 10-20%. Let the hybrid motor help!"
            ecoScore.consistencyScore < 50 ->
                "📏 Maintain steady speed. Use cruise control on the highway when safe."
            metrics.speedKmh in 30.0..50.0 ->
                "⚡ Your hybrid is at peak efficiency! The electric motor is doing heavy lifting."
            else ->
                "🌿 Drive smoothly and let the hybrid system optimize the gas-electric balance."
        }
    }
}
