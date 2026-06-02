package com.ecodrive.app.data.remote

import android.util.Log
import com.ecodrive.app.util.Constants
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Data returned from the Smartcar API.
 * Supports multiple vehicle brands.
 */
data class SmartcarVehicleData(
    val fuelPercent: Double? = null,
    val odometerKm: Double? = null,
    val tirePressureFrontLeft: Double? = null,
    val tirePressureFrontRight: Double? = null,
    val tirePressureRearLeft: Double? = null,
    val tirePressureRearRight: Double? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val lastUpdated: Long = System.currentTimeMillis(),
)

/**
 * Client for the Smartcar API.
 *
 * Smartcar provides a unified API to access vehicle data across brands
 * including fuel level, odometer, tire pressure, and location.
 *
 * Data is polled periodically and used for:
 *   1. Pre/post-trip fuel level → actual consumption calculation
 *   2. Fuel model calibration
 *   3. Odometer verification
 */
@Singleton
class SmartcarApiClient @Inject constructor() {

    companion object {
        private const val TAG = "SmartcarApiClient"
        private const val BASE_URL = Constants.SMARTCAR_BASE_URL
    }

    enum class ApiState {
        NOT_CONFIGURED,
        AUTHENTICATING,
        CONNECTED,
        ERROR,
    }

    private val _state = MutableStateFlow(ApiState.NOT_CONFIGURED)
    val state: StateFlow<ApiState> = _state.asStateFlow()

    private val _vehicleData = MutableStateFlow(SmartcarVehicleData())
    val vehicleData: StateFlow<SmartcarVehicleData> = _vehicleData.asStateFlow()

    private var accessToken: String? = null
    private var vehicleId: String? = null
    private var pollingJob: Job? = null

    // ── OAuth Flow ──────────────────────────────────────────────

    /**
     * Generate the Smartcar Connect OAuth URL.
     * The user opens this in a browser and logs in with their
     * vehicle's remote connect credentials.
     *
     * @param clientId Your Smartcar app's client ID
     * @param make Optional vehicle brand to pre-filter (e.g., "FORD", "TOYOTA")
     */
    fun getAuthUrl(clientId: String, make: String? = null): String {
        val makeParam = if (make.isNullOrBlank()) "" else "&make=${make.uppercase()}"
        return "https://connect.smartcar.com/oauth/authorize" +
                "?response_type=code" +
                "&client_id=$clientId" +
                "&redirect_uri=${Constants.SMARTCAR_REDIRECT_URI}" +
                "&scope=read_fuel read_odometer read_tires read_location" +
                makeParam +
                "&single_select=true"
    }

    /**
     * Exchange the OAuth authorization code for an access token.
     */
    suspend fun exchangeCode(
        code: String,
        clientId: String,
        clientSecret: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            _state.value = ApiState.AUTHENTICATING

            val url = URL("https://auth.smartcar.com/oauth/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            val body = "grant_type=authorization_code" +
                    "&code=$code" +
                    "&redirect_uri=${Constants.SMARTCAR_REDIRECT_URI}" +
                    "&client_id=$clientId" +
                    "&client_secret=$clientSecret"

            connection.outputStream.write(body.toByteArray())

            if (connection.responseCode == 200) {
                val response = readResponse(connection)
                val json = JSONObject(response)
                accessToken = json.getString("access_token")

                // Get vehicle ID
                fetchVehicleId()

                _state.value = ApiState.CONNECTED
                startPolling()
                Result.success(Unit)
            } else {
                _state.value = ApiState.ERROR
                Result.failure(Exception("Auth failed: ${connection.responseCode}"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed: ${e.message}")
            _state.value = ApiState.ERROR
            Result.failure(e)
        }
    }

    /**
     * Set a pre-existing access token.
     */
    suspend fun setAccessToken(token: String) {
        accessToken = token
        _state.value = ApiState.AUTHENTICATING
        try {
            fetchVehicleId()
            _state.value = ApiState.CONNECTED
            startPolling()
        } catch (e: Exception) {
            _state.value = ApiState.ERROR
        }
    }

    // ── Data Fetching ───────────────────────────────────────────

    suspend fun fetchFuelLevel(): Double? = withContext(Dispatchers.IO) {
        val vid = vehicleId ?: return@withContext null
        try {
            val response = apiGet("${BASE_URL}vehicles/$vid/fuel")
            val json = JSONObject(response)
            json.optDouble("percentRemaining", Double.NaN).takeIf { !it.isNaN() }
        } catch (e: Exception) {
            Log.w(TAG, "Fuel level fetch failed: ${e.message}")
            null
        }
    }

    suspend fun fetchOdometer(): Double? = withContext(Dispatchers.IO) {
        val vid = vehicleId ?: return@withContext null
        try {
            val response = apiGet("${BASE_URL}vehicles/$vid/odometer")
            val json = JSONObject(response)
            json.optDouble("distance", Double.NaN).takeIf { !it.isNaN() }
        } catch (e: Exception) {
            Log.w(TAG, "Odometer fetch failed: ${e.message}")
            null
        }
    }

    suspend fun fetchAll(): SmartcarVehicleData = withContext(Dispatchers.IO) {
        val fuel = fetchFuelLevel()
        val odometer = fetchOdometer()

        SmartcarVehicleData(
            fuelPercent = fuel,
            odometerKm = odometer,
        ).also {
            _vehicleData.value = it
        }
    }

    fun disconnect() {
        pollingJob?.cancel()
        accessToken = null
        vehicleId = null
        _state.value = ApiState.NOT_CONFIGURED
    }

    // ── Internals ───────────────────────────────────────────────

    private suspend fun fetchVehicleId() {
        val response = apiGet("${BASE_URL}vehicles")
        val json = JSONObject(response)
        val vehicles = json.getJSONArray("vehicles")
        if (vehicles.length() > 0) {
            vehicleId = vehicles.getString(0)
            Log.i(TAG, "Vehicle ID: $vehicleId")
        } else {
            throw Exception("No vehicles found in account")
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            while (isActive) {
                try {
                    fetchAll()
                } catch (e: Exception) {
                    Log.w(TAG, "Polling error: ${e.message}")
                }
                delay(Constants.SMARTCAR_POLL_INTERVAL_MS)
            }
        }
    }

    private fun apiGet(urlString: String): String {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        connection.setRequestProperty("Content-Type", "application/json")

        if (connection.responseCode != 200) {
            throw Exception("API error: ${connection.responseCode}")
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        return BufferedReader(InputStreamReader(connection.inputStream))
            .use { it.readText() }
    }
}
