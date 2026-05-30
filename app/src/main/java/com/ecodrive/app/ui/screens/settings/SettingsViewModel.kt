package com.ecodrive.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.ToyotaApiClient
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.util.PermissionManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 * Manages Toyota API connection, vehicle profile, and app preferences.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val toyotaApiClient: ToyotaApiClient,
    private val fuelEngine: FuelEstimationEngine,
    private val preferenceManager: PreferenceManager,
    val permissionManager: PermissionManager,
) : ViewModel() {

    data class SettingsState(
        val toyotaApiState: ToyotaApiClient.ApiState = ToyotaApiClient.ApiState.NOT_CONFIGURED,
        val smartcarClientId: String = "",
        val smartcarClientSecret: String = "",
        val calibrationFactor: Double = 1.0,
        val useMetric: Boolean = true,
        val hasBluetoothPermissions: Boolean = false,
        val hasBackgroundLocationPermission: Boolean = false,
        val fuelTankPercent: Double? = null,
        val odometerKm: Double? = null,
        val isObdEnabled: Boolean = false,
        val autoRecordEnabled: Boolean = false,
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        observeToyotaState()
        observePreferences()
        viewModelScope.launch {
            com.ecodrive.app.ui.MainActivity.authCodeFlow.collect { code ->
                if (code != null) {
                    handleAuthCallback(code)
                    com.ecodrive.app.ui.MainActivity.authCodeFlow.value = null
                }
            }
        }
    }

    private fun observeToyotaState() {
        viewModelScope.launch {
            toyotaApiClient.state.collect { apiState ->
                _state.update {
                    it.copy(
                        toyotaApiState = apiState,
                        calibrationFactor = fuelEngine.getCalibrationFactor(),
                        hasBluetoothPermissions = permissionManager.hasBluetoothPermissions(),
                        hasBackgroundLocationPermission = permissionManager.hasBackgroundLocationPermission(),
                    )
                }
            }
        }
        viewModelScope.launch {
            toyotaApiClient.vehicleData.collect { data ->
                _state.update {
                    it.copy(
                        fuelTankPercent = data.fuelPercent,
                        odometerKm = data.odometerKm,
                    )
                }
            }
        }
    }

    private fun observePreferences() {
        viewModelScope.launch {
            combine(
                preferenceManager.autoRecordEnabled,
                preferenceManager.useMetricUnits
            ) { autoRecord, useMetric ->
                autoRecord to useMetric
            }.collect { (autoRecord, useMetric) ->
                _state.update {
                    it.copy(
                        autoRecordEnabled = autoRecord,
                        useMetric = useMetric
                    )
                }
            }
        }
    }

    /**
     * Generate the Smartcar Connect OAuth URL for the user to log in.
     */
    fun getAuthUrl(): String? {
        val clientId = _state.value.smartcarClientId
        if (clientId.isBlank()) return null
        return toyotaApiClient.getAuthUrl(clientId)
    }

    /**
     * Handle the OAuth callback with the authorization code.
     */
    fun handleAuthCallback(code: String) {
        val clientId = _state.value.smartcarClientId
        val clientSecret = _state.value.smartcarClientSecret
        viewModelScope.launch {
            toyotaApiClient.exchangeCode(code, clientId, clientSecret)
        }
    }

    fun updateClientId(id: String) {
        _state.update { it.copy(smartcarClientId = id) }
    }

    fun updateClientSecret(secret: String) {
        _state.update { it.copy(smartcarClientSecret = secret) }
    }

    fun disconnectToyota() {
        toyotaApiClient.disconnect()
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

    fun toggleObd() {
        _state.update { it.copy(isObdEnabled = !it.isObdEnabled) }
    }
}
