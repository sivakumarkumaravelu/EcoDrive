package com.ecodrive.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
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
    private val dataStore = context.dataStore

    companion object {
        private val AUTO_RECORD_ENABLED = booleanPreferencesKey("auto_record_enabled")
        private val CAR_BLUETOOTH_ADDRESS = stringPreferencesKey("car_bluetooth_address")
        private val USE_METRIC_UNITS = booleanPreferencesKey("use_metric_units")
    }

    val autoRecordEnabled: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[AUTO_RECORD_ENABLED] ?: false
    }

    val carBluetoothAddress: Flow<String?> = dataStore.data.map { preferences ->
        preferences[CAR_BLUETOOTH_ADDRESS]
    }

    val useMetricUnits: Flow<Boolean> = dataStore.data.map { preferences ->
        preferences[USE_METRIC_UNITS] ?: true
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
}
