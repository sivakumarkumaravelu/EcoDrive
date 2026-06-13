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
    val make: String? = null,
    val model: String? = null,
    val year: Int? = null,
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
 *
 * Auth flow:
 *   1. getAuthUrl() → user opens in browser (Smartcar Connect; client_ prefix stripped)
 *   2. Smartcar redirects back to ecodrive://callback?code=XXX&user_id=YYY
 *   3. exchangeCode() → gets an app-level access token via iam.smartcar.com (client_credentials)
 *   4. fetchVehicleId() → lists connections via vehicle.api.smartcar.com/v3/connections
 *   5. startPolling() → fetches fuel/odometer periodically
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
        AUTH_FAILED,
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
    private var pollingJob: Job? = null

    // ── OAuth Flow ──────────────────────────────────────────────

    /**
     * Generate the Smartcar Connect OAuth URL.
     * Smartcar Connect expects the client_id WITHOUT the "client_" prefix.
     *
     * @param clientId Your Smartcar app's client ID (with or without client_ prefix)
     * @param make Optional vehicle brand to pre-filter (e.g., "FORD", "TOYOTA")
     */
    fun getAuthUrl(clientId: String, make: String? = null): String {
        // Smartcar Connect expects the raw ID without the "client_" prefix
        val cleanClientId = clientId.trim().removePrefix("client_")
        val makeParam = if (make.isNullOrBlank()) "" else "&make=${make.uppercase()}"
        return "https://connect.smartcar.com/oauth/authorize" +
                "?response_type=code" +
                "&client_id=$cleanClientId" +
                "&redirect_uri=${Constants.SMARTCAR_REDIRECT_URI}" +
                "&scope=read_vehicle_info+read_fuel+read_battery+read_odometer+read_tires+read_location" +
                makeParam +
                "&mode=live" +
                "&single_select=true"
    }

    /**
     * Exchange the OAuth callback code for an app-level access token.
     *
     * Note: Smartcar v3 uses the Management API (iam.smartcar.com) with
     * client_credentials grant to get an application-level token — NOT the
     * user-level authorization_code flow. The user_id from the callback is
     * used as the sc-user-id header on subsequent API calls.
     */
    suspend fun exchangeCode(
        code: String,
        userId: String?,
        clientId: String,
        clientSecret: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        // Management API requires the full client_ prefix
        val fullClientId = if (clientId.trim().startsWith("client_")) clientId.trim() else "client_${clientId.trim()}"
        try {
            _state.value = ApiState.AUTHENTICATING
            if (userId != null) {
                currentUserId = userId
            }

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

                // Get vehicle ID from the connections API
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
                    _state.value = ApiState.AUTH_FAILED
                    Result.failure(Exception("Auth failed: $errorType - $errorDesc"))
                } catch (e: Exception) {
                    Log.e(TAG, "Auth failed with response: $errorDetails")
                    _state.value = ApiState.AUTH_FAILED
                    Result.failure(Exception("Auth failed: ${connection.responseCode} - $errorDetails"))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed: ${e.message}")
            _state.value = ApiState.AUTH_FAILED
            Result.failure(e)
        }
    }

    /**
     * Attempt to reconnect using stored credentials (refresh token flow).
     * Re-acquires an app-level token via client_credentials.
     */
    suspend fun authenticate(clientId: String, clientSecret: String, refreshToken: String) {
        val fullClientId = if (clientId.trim().startsWith("client_")) clientId.trim() else "client_${clientId.trim()}"
        try {
            _state.value = ApiState.AUTHENTICATING

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
                fetchVehicleId()
                _state.value = ApiState.CONNECTED
                startPolling()
            } else {
                _state.value = ApiState.AUTH_FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Re-authenticate failed: ${e.message}")
            _state.value = ApiState.AUTH_FAILED
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

    suspend fun fetchVehicleInfo(): Triple<String?, String?, Int?> = withContext(Dispatchers.IO) {
        val vid = vehicleId ?: return@withContext Triple(null, null, null)
        try {
            val response = apiGet("${BASE_URL}vehicles/$vid")
            val json = JSONObject(response)
            val make = json.optString("make").takeIf { it.isNotEmpty() }
            val model = json.optString("model").takeIf { it.isNotEmpty() }
            val year = json.optInt("year", 0).takeIf { it > 0 }
            Triple(make, model, year)
        } catch (e: Exception) {
            Log.w(TAG, "Vehicle info fetch failed: ${e.message}")
            Triple(null, null, null)
        }
    }

    suspend fun fetchAll(): SmartcarVehicleData = withContext(Dispatchers.IO) {
        val fuel = fetchFuelLevel()
        val odometer = fetchOdometer()
        val (make, model, year) = fetchVehicleInfo()

        SmartcarVehicleData(
            make = make,
            model = model,
            year = year,
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
        currentUserId = null
        _state.value = ApiState.NOT_CONFIGURED
    }

    // ── Internals ───────────────────────────────────────────────

    private suspend fun fetchVehicleId() {
        // v3 Connections API — returns JSONAPI format with data[].relationships.vehicle.data.id
        val url = if (currentUserId != null) {
            "https://vehicle.api.smartcar.com/v3/connections?filter[user_id]=$currentUserId"
        } else {
            "https://vehicle.api.smartcar.com/v3/connections"
        }

        val response = apiGet(url)
        val json = JSONObject(response)
        val connections = json.getJSONArray("data")
        if (connections.length() > 0) {
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

    private fun apiGet(urlString: String): String {
        // Route vehicle data API calls through the v3 host
        val finalUrl = if (urlString.startsWith(Constants.SMARTCAR_BASE_URL)) {
            "https://vehicle.api.smartcar.com/v3/" + urlString.removePrefix(Constants.SMARTCAR_BASE_URL)
        } else {
            urlString
        }
        val url = URL(finalUrl)
        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        // v3 requires sc-user-id header to scope the request to the correct user
        currentUserId?.let {
            connection.setRequestProperty("sc-user-id", it)
        }
        connection.setRequestProperty("Content-Type", "application/json")

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
}
