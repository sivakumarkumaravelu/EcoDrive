package com.ecodrive.app.ui.screens.settings

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.util.PermissionManager
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppTheme
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
        val useGoogleMaps: Boolean = false,
        val selectedAiProvider: String = "GEMINI",
        val selectedModel: String = "",
        val availableModels: List<String> = emptyList(),
        val isLoadingModels: Boolean = false,
        val validProviders: List<String> = listOf("LOCAL"),
        val isValidatingProviders: Boolean = false,
    )

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    private val providerModelsCache = ConcurrentHashMap<String, List<String>>()

    init {
        // Load initial client credentials from preferences
        viewModelScope.launch {
            val appId = preferenceManager.smartcarApplicationId.first()
            val id = preferenceManager.smartcarClientId.first()
            val secret = preferenceManager.smartcarClientSecret.first()
            _state.update {
                it.copy(
                    smartcarApplicationId = appId,
                    smartcarClientId = id,
                    smartcarClientSecret = secret
                )
            }
        }
        observeSmartcarState()
        observeGeneralPreferences()
        validateProvidersAndObserveAiPrefs()
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
                preferenceManager.autoRecordEnabled,
                preferenceManager.useMetricUnits,
                preferenceManager.appTheme,
                preferenceManager.colorPalette,
                preferenceManager.useGoogleMaps
            ) { autoRecord, useMetric, theme, palette, useGoogleMaps ->
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
                        useGoogleMaps = useGoogleMaps
                    )
                }
            }.collect()
        }
    }

    private fun validateProvidersAndObserveAiPrefs() {
        viewModelScope.launch {
            _state.update { it.copy(isValidatingProviders = true) }
            val allProviders = aiManager.getAllProviders()
            
            // Validate each non-LOCAL provider in parallel
            val jobs = allProviders.filter { it.name != "LOCAL" }.map { provider ->
                async {
                    try {
                        val models = provider.getAvailableModels()
                        if (!models.isNullOrEmpty()) {
                            provider.name to models
                        } else {
                            null
                        }
                    } catch (e: Exception) {
                        Log.e("SettingsViewModel", "Error validating provider ${provider.name}", e)
                        null
                    }
                }
            }
            
            val results = jobs.awaitAll().filterNotNull()
            
            // Populate cache
            results.forEach { (name, models) ->
                providerModelsCache[name] = models
            }
            
            val workingProviderNames = results.map { it.first }
            val validList = listOf("LOCAL") + workingProviderNames
            
            _state.update {
                it.copy(
                    validProviders = validList,
                    isValidatingProviders = false
                )
            }
            
            // Start observing AI preferences after validation
            observeAiPreferences(validList)
        }
    }

    private fun observeAiPreferences(validList: List<String>) {
        viewModelScope.launch {
            preferenceManager.selectedAiProvider.collectLatest { providerName ->
                // Check if selected provider is still valid, fallback if not
                if (!validList.contains(providerName)) {
                    val fallback = if (validList.contains("GEMINI")) "GEMINI" else "LOCAL"
                    setSelectedAiProvider(fallback)
                    return@collectLatest
                }

                _state.update { it.copy(selectedAiProvider = providerName) }
                fetchModelsForProvider(providerName)
                
                preferenceManager.getSelectedModel(providerName).collect { modelName ->
                    val defaultModel = aiManager.getProviderByName(providerName).defaultModel
                    _state.update { it.copy(selectedModel = modelName ?: defaultModel) }
                }
            }
        }
    }

    private fun fetchModelsForProvider(providerName: String) {
        if (providerName == "LOCAL") {
            _state.update { it.copy(availableModels = emptyList(), isLoadingModels = false) }
            return
        }
        val cachedModels = providerModelsCache[providerName]
        if (cachedModels != null) {
            _state.update { it.copy(availableModels = cachedModels, isLoadingModels = false) }
            return
        }
        
        viewModelScope.launch {
            _state.update { it.copy(isLoadingModels = true, availableModels = emptyList()) }
            val provider = aiManager.getProviderByName(providerName)
            val models = try {
                provider.getAvailableModels() ?: emptyList()
            } catch (e: Exception) {
                emptyList()
            }
            if (models.isNotEmpty()) {
                providerModelsCache[providerName] = models
            }
            _state.update { it.copy(isLoadingModels = false, availableModels = models) }
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

        _state.update {
            it.copy(
                smartcarApplicationId = appId,
                smartcarClientId = clientId,
                smartcarClientSecret = clientSecret
            )
        }

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
            smartcarApiClient.exchangeCode(code, userId, clientId, clientSecret)
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

    fun disconnectSmartcar() {
        smartcarApiClient.disconnect()
        viewModelScope.launch {
            preferenceManager.setSmartcarClientId("")
            preferenceManager.setSmartcarClientSecret("")
        }
        _state.update {
            it.copy(
                smartcarClientId = "",
                smartcarClientSecret = ""
            )
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

    fun setSelectedAiProvider(provider: String) {
        viewModelScope.launch {
            preferenceManager.setSelectedAiProvider(provider)
        }
    }

    fun setSelectedModel(model: String) {
        viewModelScope.launch {
            preferenceManager.setSelectedModel(_state.value.selectedAiProvider, model)
        }
    }
}
