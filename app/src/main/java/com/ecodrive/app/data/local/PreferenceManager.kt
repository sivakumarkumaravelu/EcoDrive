package com.ecodrive.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppFontScale
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

        private val USE_GOOGLE_MAPS = booleanPreferencesKey("use_google_maps")
        private val SMARTCAR_APPLICATION_ID_KEY = stringPreferencesKey("smartcar_application_id")
        private val SMARTCAR_CLIENT_ID = stringPreferencesKey("smartcar_client_id")
        private val SMARTCAR_CLIENT_SECRET = stringPreferencesKey("smartcar_client_secret")
        private val SMARTCAR_REFRESH_TOKEN = stringPreferencesKey("smartcar_refresh_token")
        private val SMARTCAR_USER_ID = stringPreferencesKey("smartcar_user_id")
        private val MAP_STYLE = stringPreferencesKey("map_style")
        private val LIVE_COACHING_ENABLED = booleanPreferencesKey("live_coaching_enabled")
        private val COACH_VOICE = stringPreferencesKey("coach_voice")
        private val APP_FONT_SCALE = stringPreferencesKey("app_font_scale")
        private val KEEP_DISPLAY_ON = booleanPreferencesKey("keep_display_on")
    }

    val autoRecordEnabled: Flow<Boolean>
        get() = dataStore.data.map { it[AUTO_RECORD_ENABLED] ?: false }

    val keepDisplayOn: Flow<Boolean>
        get() = dataStore.data.map { it[KEEP_DISPLAY_ON] ?: true }

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

    val appFontScale: Flow<AppFontScale>
        get() = dataStore.data.map {
            try {
                AppFontScale.valueOf(it[APP_FONT_SCALE] ?: AppFontScale.MEDIUM.name)
            } catch (e: Exception) {
                AppFontScale.MEDIUM
            }
        }

    val useGoogleMaps: Flow<Boolean>
        get() = dataStore.data.map { it[USE_GOOGLE_MAPS] ?: false }

    val mapStyle: Flow<com.ecodrive.app.util.MapStyle>
        get() = dataStore.data.map {
            try {
                com.ecodrive.app.util.MapStyle.valueOf(it[MAP_STYLE] ?: com.ecodrive.app.util.MapStyle.DEFAULT.name)
            } catch (e: Exception) {
                com.ecodrive.app.util.MapStyle.DEFAULT
            }
        }

    val liveCoachingEnabled: Flow<Boolean>
        get() = dataStore.data.map { it[LIVE_COACHING_ENABLED] ?: true }

    val coachVoice: Flow<String>
        get() = dataStore.data.map { it[COACH_VOICE] ?: "DEFAULT" }

    val smartcarApplicationId: Flow<String>
        get() = dataStore.data.map { it[SMARTCAR_APPLICATION_ID_KEY] ?: "" }

    val smartcarClientId: Flow<String>
        get() = dataStore.data.map { it[SMARTCAR_CLIENT_ID] ?: "client_01KRQFQKMXYQK09HJ9TCRGDTE3" }

    val smartcarClientSecret: Flow<String>
        get() = dataStore.data.map { it[SMARTCAR_CLIENT_SECRET] ?: "9782e5a0bc2a85fa2c18d298b983fdebc5e0d3b041c7cc82cbac9fa28a5cad34" }

    val smartcarRefreshToken: Flow<String>
        get() = dataStore.data.map { it[SMARTCAR_REFRESH_TOKEN] ?: "" }

    val smartcarUserId: Flow<String>
        get() = dataStore.data.map { it[SMARTCAR_USER_ID] ?: "" }

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

    suspend fun setAppFontScale(scale: AppFontScale) {
        dataStore.edit { it[APP_FONT_SCALE] = scale.name }
    }

    suspend fun setUseGoogleMaps(enabled: Boolean) {
        dataStore.edit { it[USE_GOOGLE_MAPS] = enabled }
    }

    suspend fun setMapStyle(style: com.ecodrive.app.util.MapStyle) {
        dataStore.edit { it[MAP_STYLE] = style.name }
    }

    suspend fun setSmartcarApplicationId(id: String) {
        dataStore.edit { it[SMARTCAR_APPLICATION_ID_KEY] = id }
    }

    suspend fun setSmartcarClientId(clientId: String) {
        dataStore.edit { it[SMARTCAR_CLIENT_ID] = clientId }
    }

    suspend fun setSmartcarClientSecret(clientSecret: String) {
        dataStore.edit { it[SMARTCAR_CLIENT_SECRET] = clientSecret }
    }

    suspend fun setSmartcarRefreshToken(token: String) {
        dataStore.edit { it[SMARTCAR_REFRESH_TOKEN] = token }
    }

    suspend fun setSmartcarUserId(userId: String) {
        dataStore.edit { it[SMARTCAR_USER_ID] = userId }
    }

    suspend fun setLiveCoachingEnabled(enabled: Boolean) {
        dataStore.edit { it[LIVE_COACHING_ENABLED] = enabled }
    }

    suspend fun setCoachVoice(voice: String) {
        dataStore.edit { it[COACH_VOICE] = voice }
    }

    suspend fun setKeepDisplayOn(keep: Boolean) {
        dataStore.edit { it[KEEP_DISPLAY_ON] = keep }
    }


}
