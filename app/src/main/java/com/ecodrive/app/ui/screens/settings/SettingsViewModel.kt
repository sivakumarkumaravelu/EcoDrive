package com.ecodrive.app.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.data.repository.VehicleRepository
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.util.PermissionManager
import com.ecodrive.app.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

import com.ecodrive.app.domain.ai.service.AiManager

/**
 * ViewModel for the Settings screen.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val smartcarApiClient: SmartcarApiClient,
    private val vehicleRepository: VehicleRepository,
    private val fuelEngine: FuelEstimationEngine,
    private val preferenceManager: PreferenceManager,
    private val aiManager: AiManager,
    val permissionManager: PermissionManager,
) : ViewModel() {

    data class SettingsState(
        val smartcarApiState: SmartcarApiClient.ApiState = SmartcarApiClient.ApiState.NOT_CONFIGURED,
        val smartcarApplicationId: String = "",
        val smartcarClientId: String = "",
        val smartcarClientSecret: String = "",
        val smartcarAuthError: String? = null,
        val calibrationFactor: Double = 1.0,
        val useMetric: Boolean = true,
        val appTheme: AppTheme = AppTheme.DARK,
        val appPalette: AppColorPalette = AppColorPalette.ECO_GREEN,
        val appFontScale: AppFontScale = AppFontScale.MEDIUM,
        val hasBluetoothPermissions: Boolean = false,
        val hasBackgroundLocationPermission: Boolean = false,
        // Active/Display data (Smartcar preferred if connected)
        val vehicleMake: String? = null,
        val vehicleModel: String? = null,
        val vehicleYear: Int? = null,
        val fuelTankPercent: Double? = null,
        val odometerKm: Double? = null,
        // Local profile data
        val localVehicle: Vehicle? = null,
        val isObdEnabled: Boolean = false,
        val autoRecordEnabled: Boolean = false,
        val useGoogleMaps: Boolean = false,
        val mapStyle: com.ecodrive.app.util.MapStyle = com.ecodrive.app.util.MapStyle.DEFAULT,
        val liveCoachingEnabled: Boolean = true,
        val coachVoice: String = "DEFAULT",
        val keepDisplayOn: Boolean = true,
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        // Load initial client credentials from preferences
        viewModelScope.launch {
            val appId = preferenceManager.smartcarApplicationId.first()
            val id = preferenceManager.smartcarClientId.first()
            val secret = preferenceManager.smartcarClientSecret.first()
            val userId = preferenceManager.smartcarUserId.first()
            _state.update {
                it.copy(
                    smartcarApplicationId = appId,
                    smartcarClientId = id,
                    smartcarClientSecret = secret
                )
            }
            
            // Auto-reconnect if credentials are present
            if (id.isNotBlank() && secret.isNotBlank() && userId.isNotBlank()) {
                smartcarApiClient.authenticate(id, secret, userId)
            }
        }
        observeSmartcarState()
        observeLocalVehicle()
        observeGeneralPreferences()
    }

    private fun observeLocalVehicle() {
        viewModelScope.launch {
            // Observe vehicle changes using Flow to ensure UI stays in sync
            vehicleRepository.getAllVehicles().collect { vehicles ->
                val vehicle = vehicles.firstOrNull() ?: vehicleRepository.getDefaultVehicle()
                if (vehicle != null) {
                    _state.update { 
                        it.copy(
                            localVehicle = vehicle,
                            // If Smartcar is NOT connected, update display values from local profile
                            vehicleMake = if (it.smartcarApiState != SmartcarApiClient.ApiState.CONNECTED) vehicle.make else it.vehicleMake,
                            vehicleModel = if (it.smartcarApiState != SmartcarApiClient.ApiState.CONNECTED) vehicle.model else it.vehicleModel,
                            vehicleYear = if (it.smartcarApiState != SmartcarApiClient.ApiState.CONNECTED) vehicle.year else it.vehicleYear,
                            fuelTankPercent = if (it.smartcarApiState != SmartcarApiClient.ApiState.CONNECTED) vehicle.fuelLevelPercent else it.fuelTankPercent,
                            odometerKm = if (it.smartcarApiState != SmartcarApiClient.ApiState.CONNECTED) vehicle.odometerKm else it.odometerKm,
                        )
                    }
                }
            }
        }
    }

    private fun observeSmartcarState() {
        viewModelScope.launch {
            smartcarApiClient.state.collect { apiState ->
                // D14: Do NOT call disconnectSmartcar() on AUTH_FAILED.
                // AUTH_FAILED is a transient error (network timeout, 401, 5xx).
                // Wiping the saved userId here permanently destroys the session
                // that the session-persistence fix was meant to keep alive.
                // Only an explicit user-initiated logout should clear credentials.
                if (apiState == SmartcarApiClient.ApiState.AUTH_FAILED) {
                    _state.update {
                        it.copy(smartcarAuthError = "Connection lost. Tap 'Connect' to reconnect.")
                    }
                }

                _state.update {
                    it.copy(
                        smartcarApiState = apiState,
                        calibrationFactor = fuelEngine.getCalibrationFactor(),
                        hasBluetoothPermissions = permissionManager.hasBluetoothPermissions(),
                        hasBackgroundLocationPermission = permissionManager.hasBackgroundLocationPermission(),
                    )
                }
            }
        }
        viewModelScope.launch {
            smartcarApiClient.vehicleData.collect { data ->
                if (data.make != null || data.model != null || data.year != null || data.fuelPercent != null || data.odometerKm != null) {
                    val current = _state.value.localVehicle ?: Vehicle()
                    vehicleRepository.saveVehicle(current.copy(
                        make = data.make ?: current.make,
                        model = data.model ?: current.model,
                        year = data.year ?: current.year,
                        fuelLevelPercent = data.fuelPercent ?: current.fuelLevelPercent,
                        odometerKm = data.odometerKm ?: current.odometerKm
                    ))
                }

                _state.update {
                    it.copy(
                        vehicleMake = data.make,
                        vehicleModel = data.model,
                        vehicleYear = data.year,
                        fuelTankPercent = data.fuelPercent,
                        odometerKm = data.odometerKm,
                    )
                }
            }
        }
        // Collect OAuth error callbacks from MainActivity (e.g. invalid client_id)
        viewModelScope.launch {
            com.ecodrive.app.ui.MainActivity.authErrorFlow.collect { error ->
                if (error != null) {
                    _state.update { it.copy(smartcarAuthError = error) }
                    com.ecodrive.app.ui.MainActivity.authErrorFlow.value = null // reset
                }
            }
        }
        // Collect callback auth code from MainActivity
        viewModelScope.launch {
            com.ecodrive.app.ui.MainActivity.authCodeFlow.collect { authData ->
                if (authData != null) {
                    val code = authData.first
                    val userId = authData.second
                    handleAuthCallback(code, userId)
                    com.ecodrive.app.ui.MainActivity.authCodeFlow.value = null // reset
                }
            }
        }
    }

    private fun observeGeneralPreferences() {
        viewModelScope.launch {
            combine(
                combine(
                    preferenceManager.autoRecordEnabled,
                    preferenceManager.useMetricUnits,
                    preferenceManager.appTheme,
                    ::Triple
                ),
                combine(
                    preferenceManager.colorPalette,
                    preferenceManager.useGoogleMaps,
                    preferenceManager.mapStyle,
                    ::Triple
                ),
                combine(
                    preferenceManager.liveCoachingEnabled,
                    preferenceManager.coachVoice,
                    preferenceManager.appFontScale,
                    ::Triple
                ),
                preferenceManager.keepDisplayOn
            ) { (autoRecord, useMetric, theme), (palette, useGoogleMaps, mapStyle), (liveCoaching, voice, fontScale), keepDisplayOn ->
                // Sync AppConfig map provider dynamically on changes
                com.ecodrive.app.util.AppConfig.ACTIVE_MAP_PROVIDER = if (useGoogleMaps) {
                    com.ecodrive.app.util.MapProvider.GOOGLE_MAPS
                } else {
                    com.ecodrive.app.util.MapProvider.OPEN_STREET_MAP
                }

                _state.update {
                    it.copy(
                        autoRecordEnabled = autoRecord,
                        useMetric = useMetric,
                        appTheme = theme,
                        appPalette = palette,
                        appFontScale = fontScale,
                        useGoogleMaps = useGoogleMaps,
                        mapStyle = mapStyle,
                        liveCoachingEnabled = liveCoaching,
                        coachVoice = voice,
                        keepDisplayOn = keepDisplayOn
                    )
                }
            }.collect()
        }
    }


    /**
     * Generate the Smartcar Connect OAuth URL for the user to log in.
     */
    fun getAuthUrl(): String? {
        val appId = _state.value.smartcarApplicationId.trim()
        val clientId = _state.value.smartcarClientId.trim()
        val clientSecret = _state.value.smartcarClientSecret.trim()
        if (appId.isBlank() || clientId.isBlank()) return null

        viewModelScope.launch {
            preferenceManager.setSmartcarApplicationId(appId)
            preferenceManager.setSmartcarClientId(clientId)
            preferenceManager.setSmartcarClientSecret(clientSecret)
        }
        return smartcarApiClient.getAuthUrl(appId)
    }

    /**
     * Handle the OAuth callback with the authorization code and user ID.
     */
    fun handleAuthCallback(code: String, userId: String?) {
        val clientId = _state.value.smartcarClientId.trim()
        val clientSecret = _state.value.smartcarClientSecret.trim()
        viewModelScope.launch {
            if (userId != null) {
                preferenceManager.setSmartcarUserId(userId)
            }
            // D05: renamed from exchangeCode to authenticateWithCode to match actual behaviour
            val result = smartcarApiClient.authenticateWithCode(code, userId, clientId, clientSecret)
            result.onSuccess {
                // Management API uses app-level credentials
            }
        }
    }

    fun updateApplicationId(id: String) {
        _state.update { it.copy(smartcarApplicationId = id) }
        viewModelScope.launch { preferenceManager.setSmartcarApplicationId(id) }
    }

    fun updateClientId(id: String) {
        _state.update { it.copy(smartcarClientId = id) }
    }

    fun updateClientSecret(secret: String) {
        _state.update { it.copy(smartcarClientSecret = secret) }
    }

    fun clearAuthError() {
        _state.update { it.copy(smartcarAuthError = null) }
    }

    fun disconnectSmartcar() {
        smartcarApiClient.disconnect()
        // D13: Only clear the session token and userId — NOT the client_id/secret.
        // Preserving credentials allows the user to reconnect without re-entering them.
        viewModelScope.launch {
            preferenceManager.setSmartcarRefreshToken("")
            preferenceManager.setSmartcarUserId("")
        }
    }

    fun toggleUnits() {
        viewModelScope.launch {
            preferenceManager.setUseMetricUnits(!_state.value.useMetric)
        }
    }

    fun toggleAutoRecord() {
        viewModelScope.launch {
            preferenceManager.setAutoRecordEnabled(!_state.value.autoRecordEnabled)
        }
    }

    fun toggleUseGoogleMaps() {
        viewModelScope.launch {
            preferenceManager.setUseGoogleMaps(!_state.value.useGoogleMaps)
        }
    }

    fun setMapStyle(style: com.ecodrive.app.util.MapStyle) {
        viewModelScope.launch {
            preferenceManager.setMapStyle(style)
        }
    }

    fun toggleObd() {
        _state.update { it.copy(isObdEnabled = !it.isObdEnabled) }
    }

    fun setAppTheme(theme: AppTheme) {
        viewModelScope.launch {
            preferenceManager.setAppTheme(theme)
        }
    }

    fun setColorPalette(palette: AppColorPalette) {
        viewModelScope.launch {
            preferenceManager.setColorPalette(palette)
        }
    }

    fun setAppFontScale(scale: AppFontScale) {
        viewModelScope.launch {
            preferenceManager.setAppFontScale(scale)
        }
    }


    fun toggleLiveCoaching() {
        viewModelScope.launch {
            preferenceManager.setLiveCoachingEnabled(!_state.value.liveCoachingEnabled)
        }
    }

    fun setKeepDisplayOn(keep: Boolean) {
        viewModelScope.launch {
            preferenceManager.setKeepDisplayOn(keep)
        }
    }

    fun setCoachVoice(voice: String) {
        viewModelScope.launch {
            preferenceManager.setCoachVoice(voice)
        }
    }

    // ── Vehicle Profile Actions ────────────────────────────────

    fun updateVehicleName(name: String) {
        viewModelScope.launch {
            val current = _state.value.localVehicle ?: Vehicle()
            vehicleRepository.saveVehicle(current.copy(name = name))
        }
    }

    fun updateVehicleMake(make: String) {
        viewModelScope.launch {
            val current = _state.value.localVehicle ?: Vehicle()
            vehicleRepository.saveVehicle(current.copy(make = make))
        }
    }

    fun updateVehicleModel(model: String) {
        viewModelScope.launch {
            val current = _state.value.localVehicle ?: Vehicle()
            vehicleRepository.saveVehicle(current.copy(model = model))
        }
    }

    fun updateOdometer(odometerKm: Double) {
        viewModelScope.launch {
            val current = _state.value.localVehicle ?: Vehicle()
            vehicleRepository.saveVehicle(current.copy(odometerKm = odometerKm))
        }
    }

    fun updateFuelLevel(fuelPercent: Double) {
        viewModelScope.launch {
            val current = _state.value.localVehicle ?: Vehicle()
            vehicleRepository.saveVehicle(current.copy(fuelLevelPercent = fuelPercent))
        }
    }
}
