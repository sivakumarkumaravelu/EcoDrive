package com.ecodrive.app.data.remote

import android.util.Log
import com.ecodrive.app.di.ApplicationScope
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
 * This implementation uses the Management API approach (client_credentials)
 * to maintain compatibility with multi-brand vehicle integrations and MFA flows.
 */
@Singleton
class SmartcarApiClient @Inject constructor(
    @ApplicationScope private val applicationScope: CoroutineScope,
) {

    companion object {
        private const val TAG = "SmartcarApiClient"
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
     */
    fun getAuthUrl(applicationId: String, make: String? = null): String {
        val cleanAppId = applicationId.trim()
        val makeParam = if (make.isNullOrBlank()) "" else "&make=${make.uppercase()}"
        return "https://connect.smartcar.com/oauth/authorize" +
                "?response_type=code" +
                "&client_id=$cleanAppId" +
                "&redirect_uri=${Constants.SMARTCAR_REDIRECT_URI}" +
                "&scope=read_vehicle_info+read_fuel+read_battery+read_odometer+read_tires+read_location" +
                makeParam +
                "&mode=live" +
                "&single_select=true"
    }

    /**
     * Authenticates using client_credentials and (if provided) a userId to scope requests.
     *
     * NOTE: This implementation intentionally uses the client_credentials grant to
     * obtain a Management API token. The `code` parameter from the OAuth callback
     * is used to identify the connected user (via [userId]) but not exchanged —
     * Smartcar's Management API requires a per-app token rather than a per-user token
     * for the vehicle data endpoints used here.
     *
     * If your Smartcar plan supports per-user access tokens, replace the body
     * with grant_type=authorization_code&code=$code&redirect_uri=...
     */
    suspend fun authenticateWithCode(
        code: String,
        userId: String?,
        clientId: String,
        clientSecret: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val fullClientId = if (clientId.trim().startsWith("client_")) clientId.trim() else "client_${clientId.trim()}"
        var connection: HttpURLConnection? = null
        try {
            _state.value = ApiState.AUTHENTICATING
            if (userId != null) {
                currentUserId = userId
            }

            connection = URL("https://iam.smartcar.com/oauth2/token").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            val body = "grant_type=client_credentials" +
                    "&client_id=$fullClientId" +
                    "&client_secret=${clientSecret.trim()}"

            connection.outputStream.use { it.write(body.toByteArray()) }

            if (connection.responseCode == 200) {
                val response = readResponse(connection)
                val json = JSONObject(response)
                accessToken = json.getString("access_token")

                // Get vehicle ID from the connections API.
                // fetchVehicleId() returns false and sets AUTH_FAILED itself
                // if no connections exist — do not override state in that case.
                if (fetchVehicleId()) {
                    _state.value = ApiState.CONNECTED
                    startPolling()
                }
                Result.success(Unit)
            } else {
                handleAuthError(connection)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Token exchange failed: ${e.message}")
            _state.value = ApiState.AUTH_FAILED
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Re-acquires an app-level token via client_credentials.
     */
    suspend fun authenticate(clientId: String, clientSecret: String, userId: String?) = withContext(Dispatchers.IO) {
        val fullClientId = if (clientId.trim().startsWith("client_")) clientId.trim() else "client_${clientId.trim()}"
        var connection: HttpURLConnection? = null
        try {
            _state.value = ApiState.AUTHENTICATING
            currentUserId = userId

            connection = URL("https://iam.smartcar.com/oauth2/token").openConnection() as HttpURLConnection
            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.doOutput = true

            val body = "grant_type=client_credentials" +
                    "&client_id=$fullClientId" +
                    "&client_secret=${clientSecret.trim()}"

            connection.outputStream.use { it.write(body.toByteArray()) }

            if (connection.responseCode == 200) {
                val response = readResponse(connection)
                val json = JSONObject(response)
                accessToken = json.getString("access_token")
                // fetchVehicleId() returns false and sets AUTH_FAILED itself
                // if no connections exist — do not override state in that case.
                if (fetchVehicleId()) {
                    _state.value = ApiState.CONNECTED
                    startPolling()
                }
            } else {
                _state.value = ApiState.AUTH_FAILED
            }
        } catch (e: Exception) {
            Log.e(TAG, "Re-authenticate failed: ${e.message}")
            _state.value = ApiState.AUTH_FAILED
        } finally {
            connection?.disconnect()
        }
    }

    // ── Data Fetching ───────────────────────────────────────────

    suspend fun fetchFuelLevel(): Double? = withContext(Dispatchers.IO) {
        val vid = vehicleId ?: return@withContext null
        try {
            val response = apiGet("${Constants.SMARTCAR_VEHICLE_URL}vehicles/$vid/fuel")
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
            val response = apiGet("${Constants.SMARTCAR_VEHICLE_URL}vehicles/$vid/odometer")
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
            val response = apiGet("${Constants.SMARTCAR_VEHICLE_URL}vehicles/$vid")
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

    /**
     * Fetches the vehicle ID for the current user from the Smartcar connections API.
     *
     * Returns `true` if a vehicle was found and [vehicleId] was set.
     * Returns `false` (and sets state to [ApiState.AUTH_FAILED]) if no connections
     * exist for this user — this is a non-throwing, recoverable failure.
     */
    private suspend fun fetchVehicleId(): Boolean {
        val url = if (currentUserId != null) {
            "${Constants.SMARTCAR_CONNECTIONS_URL}connections?filter[user_id]=$currentUserId"
        } else {
            "${Constants.SMARTCAR_CONNECTIONS_URL}connections"
        }

        val response = apiGet(url)
        val json = JSONObject(response)
        val data = json.getJSONArray("data")
        return if (data.length() > 0) {
            val first = data.getJSONObject(0)
            val vehicle = first.getJSONObject("relationships").getJSONObject("vehicle").getJSONObject("data")
            vehicleId = vehicle.getString("id")
            Log.i(TAG, "Found Vehicle ID: $vehicleId")
            true
        } else {
            Log.w(TAG, "No vehicle connections found for this user")
            _state.value = ApiState.AUTH_FAILED
            false
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        // D04: Use the injected application-scoped coroutine scope instead of creating
        // an orphan scope that outlives its usefulness and cannot be cancelled.
        pollingJob = applicationScope.launch(Dispatchers.IO) {
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

    private suspend fun apiGet(urlString: String): String = withContext(Dispatchers.IO) {
        val connection = URL(urlString).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.setRequestProperty("Authorization", "Bearer $accessToken")
            currentUserId?.let {
                connection.setRequestProperty("sc-user-id", it)
            }
            connection.setRequestProperty("Content-Type", "application/json")

            if (connection.responseCode != 200) {
                val errorDetails = connection.errorStream
                    ?.bufferedReader()?.use { it.readText() } ?: "No error details"
                throw Exception("API Error ${connection.responseCode}: $errorDetails")
            }
            readResponse(connection)
        } finally {
            connection.disconnect()
        }
    }

    private fun handleAuthError(connection: HttpURLConnection): Result<Unit> {
        val errorStream = connection.errorStream
        val errorDetails = if (errorStream != null) {
            BufferedReader(InputStreamReader(errorStream)).use { it.readText() }
        } else {
            "No error details"
        }
        Log.e(TAG, "Auth Error: $errorDetails")
        _state.value = ApiState.AUTH_FAILED
        return Result.failure(Exception("Auth failed: $errorDetails"))
    }

    private fun readResponse(connection: HttpURLConnection): String {
        return BufferedReader(InputStreamReader(connection.inputStream))
            .use { it.readText() }
    }
}
