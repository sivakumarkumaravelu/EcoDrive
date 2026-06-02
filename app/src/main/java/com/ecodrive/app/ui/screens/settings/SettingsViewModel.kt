package com.ecodrive.app.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.util.PermissionManager
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Settings screen.
 * Manages Smartcar API connection, vehicle profile, and app preferences.
 */
@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val smartcarApiClient: SmartcarApiClient,
    private val fuelEngine: FuelEstimationEngine,
    private val preferenceManager: PreferenceManager,
    val permissionManager: PermissionManager,
) : ViewModel() {

    data class SettingsState(
        val smartcarApiState: SmartcarApiClient.ApiState = SmartcarApiClient.ApiState.NOT_CONFIGURED,
        val smartcarClientId: String = "",
        val smartcarClientSecret: String = "",
        val calibrationFactor: Double = 1.0,
        val useMetric: Boolean = true,
        val appTheme: AppTheme = AppTheme.DARK,
        val appPalette: AppColorPalette = AppColorPalette.ECO_GREEN,
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
        observeSmartcarState()
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

    private fun observeSmartcarState() {
        viewModelScope.launch {
            smartcarApiClient.state.collect { apiState ->
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
                preferenceManager.useMetricUnits,
                preferenceManager.appTheme,
                preferenceManager.colorPalette
            ) { autoRecord, useMetric, appTheme, appPalette ->
                _state.update {
                    it.copy(
                        autoRecordEnabled = autoRecord,
                        useMetric = useMetric,
                        appTheme = appTheme,
                        appPalette = appPalette
                    )
                }
            }.collect()
        }
    }

    /**
     * Generate the Smartcar Connect OAuth URL for the user to log in.
     */
    fun getAuthUrl(): String? {
        val clientId = _state.value.smartcarClientId
        if (clientId.isBlank()) return null
        return smartcarApiClient.getAuthUrl(clientId)
    }

    /**
     * Handle the OAuth callback with the authorization code.
     */
    fun handleAuthCallback(code: String) {
        val clientId = _state.value.smartcarClientId
        val clientSecret = _state.value.smartcarClientSecret
        viewModelScope.launch {
            smartcarApiClient.exchangeCode(code, clientId, clientSecret)
        }
    }

    fun updateClientId(id: String) {
        _state.update { it.copy(smartcarClientId = id) }
    }

    fun updateClientSecret(secret: String) {
        _state.update { it.copy(smartcarClientSecret = secret) }
    }

    fun disconnectSmartcar() {
        smartcarApiClient.disconnect()
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
}
