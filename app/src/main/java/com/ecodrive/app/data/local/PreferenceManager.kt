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

    internal fun setTestDataStore(testDataStore: DataStore<Preferences>) {
        dataStore = testDataStore
    }

    companion object {
        private val AUTO_RECORD_ENABLED = booleanPreferencesKey("auto_record_enabled")
        private val CAR_BLUETOOTH_ADDRESS = stringPreferencesKey("car_bluetooth_address")
        private val USE_METRIC_UNITS = booleanPreferencesKey("use_metric_units")
        private val APP_THEME = stringPreferencesKey("app_theme")
        private val COLOR_PALETTE = stringPreferencesKey("color_palette")
    }

    val autoRecordEnabled: Flow<Boolean>
        get() = dataStore.data.map { preferences ->
            preferences[AUTO_RECORD_ENABLED] ?: false
        }

    val carBluetoothAddress: Flow<String?>
        get() = dataStore.data.map { preferences ->
            preferences[CAR_BLUETOOTH_ADDRESS]
        }

    val useMetricUnits: Flow<Boolean>
        get() = dataStore.data.map { preferences ->
            preferences[USE_METRIC_UNITS] ?: true
        }

    val appTheme: Flow<AppTheme>
        get() = dataStore.data.map { preferences ->
            try {
                AppTheme.valueOf(preferences[APP_THEME] ?: AppTheme.DARK.name)
            } catch (e: Exception) {
                AppTheme.DARK
            }
        }

    val colorPalette: Flow<AppColorPalette>
        get() = dataStore.data.map { preferences ->
            try {
                AppColorPalette.valueOf(preferences[COLOR_PALETTE] ?: AppColorPalette.ECO_GREEN.name)
            } catch (e: Exception) {
                AppColorPalette.ECO_GREEN
            }
        }

    suspend fun setAutoRecordEnabled(enabled: Boolean) {
        dataStore.edit { preferences ->
            preferences[AUTO_RECORD_ENABLED] = enabled
        }
    }

    suspend fun setCarBluetoothAddress(address: String?) {
        dataStore.edit { preferences ->
            if (address == null) {
                preferences.remove(CAR_BLUETOOTH_ADDRESS)
            } else {
                preferences[CAR_BLUETOOTH_ADDRESS] = address
            }
        }
    }

    suspend fun setUseMetricUnits(useMetric: Boolean) {
        dataStore.edit { preferences ->
            preferences[USE_METRIC_UNITS] = useMetric
        }
    }

    suspend fun setAppTheme(theme: AppTheme) {
        dataStore.edit { preferences ->
            preferences[APP_THEME] = theme.name
        }
    }

    suspend fun setColorPalette(palette: AppColorPalette) {
        dataStore.edit { preferences ->
            preferences[COLOR_PALETTE] = palette.name
        }
    }
}
