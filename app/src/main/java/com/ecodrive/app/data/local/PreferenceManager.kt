package com.ecodrive.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Singleton
class PreferenceManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var dataStore: DataStore<Preferences> = context.dataStore

    // Constructor for testing that allows injecting a custom dataStore
    constructor(context: Context, testDataStore: DataStore<Preferences>) : this(context) {
        dataStore = testDataStore
    }

    companion object {
        private val AUTO_RECORD_ENABLED = booleanPreferencesKey("auto_record_enabled")
        private val CAR_BLUETOOTH_ADDRESS = stringPreferencesKey("car_bluetooth_address")
        private val USE_METRIC_UNITS = booleanPreferencesKey("use_metric_units")
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val COLOR_PALETTE = stringPreferencesKey("color_palette")
        private val AI_PROVIDER = stringPreferencesKey("ai_provider")
    }

    val autoRecordEnabled: Flow<Boolean>
        get() = dataStore.data.map { it[AUTO_RECORD_ENABLED] ?: false }

    val carBluetoothAddress: Flow<String?>
        get() = dataStore.data.map { it[CAR_BLUETOOTH_ADDRESS] }

    val useMetricUnits: Flow<Boolean>
        get() = dataStore.data.map { it[USE_METRIC_UNITS] ?: true }

    val appTheme: Flow<AppTheme>
        get() = dataStore.data.map {
            try {
                AppTheme.valueOf(it[APP_THEME] ?: AppTheme.DARK.name)
            } catch (e: Exception) {
                AppTheme.DARK
            }
        }

    val colorPalette: Flow<AppColorPalette>
        get() = dataStore.data.map {
            try {
                AppColorPalette.valueOf(it[COLOR_PALETTE] ?: AppColorPalette.ECO_GREEN.name)
            } catch (e: Exception) {
                AppColorPalette.ECO_GREEN
            }
        }

    val selectedAiProvider: Flow<String>
        get() = dataStore.data.map { it[AI_PROVIDER] ?: "GEMINI" }

    suspend fun setAutoRecordEnabled(enabled: Boolean) {
        dataStore.edit { it[AUTO_RECORD_ENABLED] = enabled }
    }

    suspend fun setCarBluetoothAddress(address: String?) {
        dataStore.edit {
            if (address == null) it.remove(CAR_BLUETOOTH_ADDRESS)
            else it[CAR_BLUETOOTH_ADDRESS] = address
        }
    }

    suspend fun setUseMetricUnits(useMetric: Boolean) {
        dataStore.edit { it[USE_METRIC_UNITS] = useMetric }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        dataStore.edit { it[APP_THEME] = theme.name }
    }

    suspend fun setColorPalette(palette: AppColorPalette) {
        dataStore.edit { it[COLOR_PALETTE] = palette.name }
    }

    suspend fun setSelectedAiProvider(provider: String) {
        dataStore.edit { it[AI_PROVIDER] = provider }
    }
}
