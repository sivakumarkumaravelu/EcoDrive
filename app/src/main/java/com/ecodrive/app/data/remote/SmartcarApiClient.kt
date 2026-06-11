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

    @Volatile
    private var accessToken: String? = null
    @Volatile
    private var currentUserId: String? = null
    @Volatile
    private var vehicleId: String? = null
    
    @Volatile
    private var savedClientId: String? = null
    @Volatile
    private var savedClientSecret: String? = null
    
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
        val cleanClientId = clientId.trim().removePrefix("client_")
        val makeParam = if (make.isNullOrBlank()) "" else "&make=${make.uppercase()}"
        return "https://connect.smartcar.com/oauth/authorize" +
                "?response_type=code" +
                "&client_id=$cleanClientId" +
                "&redirect_uri=${Constants.SMARTCAR_REDIRECT_URI}" +
                "&scope=read_fuel+read_odometer+read_tires+read_location" +
                makeParam +
                "&mode=live" +
                "&single_select=true"
    }

    /**
     * Exchange the OAuth authorization code for an access token.
     */
    suspend fun exchangeCode(
        code: String,
        userId: String?,
        clientId: String,
        clientSecret: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val fullClientId = if (clientId.trim().startsWith("client_")) clientId.trim() else "client_${clientId.trim()}"
        try {
            _state.value = ApiState.AUTHENTICATING
            if (userId != null) {
                currentUserId = userId
            }
            savedClientId = fullClientId
            savedClientSecret = clientSecret.trim()

            val url = URL("https://iam.smartcar.com/oauth2/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            
            connection.doOutput = true

            val body = "grant_type=client_credentials" +
                    "&client_id=$fullClientId" +
                    "&client_secret=${clientSecret.trim()}"

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
                val errorStream = connection.errorStream
                val errorDetails = if (errorStream != null) {
                    BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
                } else {
                    "No error details"
                }
                
                try {
                    val errorJson = JSONObject(errorDetails)
                    val errorType = errorJson.optString("error", "unknown")
                    val errorDesc = errorJson.optString("error_description", errorDetails)
                    Log.e(TAG, "Smartcar Auth Error: $errorType - $errorDesc")
                    _state.value = ApiState.ERROR
                    Result.failure(Exception("Auth failed: $errorType - $errorDesc"))
                } catch (e: Exception) {
                    Log.e(TAG, "Auth failed with response: $errorDetails")
                    _state.value = ApiState.ERROR
                    Result.failure(Exception("Auth failed: ${connection.responseCode} - $errorDetails"))
                }
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
        // Build the connections URL, filtering by userId if available
        val url = if (currentUserId != null) {
            "https://vehicle.api.smartcar.com/v3/connections?filter[user_id]=$currentUserId"
        } else {
            "https://vehicle.api.smartcar.com/v3/connections"
        }

        val response = apiGet(url)
        val json = JSONObject(response)
        val connections = json.getJSONArray("data")
        if (connections.length() > 0) {
            // Parse JSONAPI relationship structure to get the vehicle ID
            val firstConnection = connections.getJSONObject(0)
            val relationships = firstConnection.getJSONObject("relationships")
            val vehicle = relationships.getJSONObject("vehicle").getJSONObject("data")
            vehicleId = vehicle.getString("id")
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

    private suspend fun apiGet(urlString: String, retryCount: Int = 1): String {
        val finalUrl = if (urlString.startsWith(Constants.SMARTCAR_BASE_URL)) {
            "https://vehicle.api.smartcar.com/v3/" + urlString.removePrefix(Constants.SMARTCAR_BASE_URL)
        } else {
            urlString
        }
        val url = URL(finalUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        currentUserId?.let {
            connection.setRequestProperty("sc-user-id", it)
        }
        connection.setRequestProperty("Content-Type", "application/json")

        if (connection.responseCode == 401 && retryCount > 0) {
            // Token might be expired, try to refresh
            if (refreshToken()) {
                return apiGet(urlString, retryCount - 1)
            }
        }

        if (connection.responseCode != 200) {
            val errorStream = connection.errorStream
            val errorDetails = if (errorStream != null) {
                BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
            } else {
                "No error details"
            }
            
            try {
                val errorJson = JSONObject(errorDetails)
                val errorType = errorJson.optString("error", "unknown")
                val errorMsg = errorJson.optString("message", errorJson.optString("error_description", errorDetails))
                Log.e(TAG, "Smartcar API Error: $errorType - $errorMsg")
                throw Exception("API error ${connection.responseCode}: $errorMsg")
            } catch (e: Exception) {
                throw Exception("API error ${connection.responseCode}: $errorDetails")
            }
        }
        return readResponse(connection)
    }

    private fun readResponse(connection: HttpURLConnection): String {
        return BufferedReader(InputStreamReader(connection.inputStream))
            .use { it.readText() }
    }

    private suspend fun refreshToken(): Boolean = withContext(Dispatchers.IO) {
        val clientId = savedClientId ?: return@withContext false
        val secret = savedClientSecret ?: return@withContext false
        try {
            val url = URL("https://iam.smartcar.com/oauth2/token")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            val body = "grant_type=client_credentials" +
                    "&client_id=$clientId" +
                    "&client_secret=$secret"

            connection.outputStream.write(body.toByteArray())

            if (connection.responseCode == 200) {
                val response = readResponse(connection)
                val json = JSONObject(response)
                accessToken = json.getString("access_token")
                true
            } else {
                Log.e(TAG, "Token refresh failed with response code ${connection.responseCode}")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token refresh failed: ${e.message}")
            false
        }
    }
}
