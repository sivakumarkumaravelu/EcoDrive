package com.ecodrive.app.ui.screens.routeplanner

import com.ecodrive.app.domain.ai.analyzer.RouteInsightGenerator

import android.content.Context
import android.location.Geocoder
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.DirectionsClient
import com.ecodrive.app.data.remote.GoogleMapsServicesClient
import com.ecodrive.app.data.repository.VehicleRepository
import com.ecodrive.app.domain.analyzer.RouteOptimizer
import com.ecodrive.app.domain.model.MapRoute
import com.ecodrive.app.domain.model.Vehicle
import com.ecodrive.app.util.AppConfig
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.URLEncoder
import javax.inject.Inject

/**
 * ViewModel for the Route Planner screen.
 * Handles fetching routes and calculating eco-metrics for them.
 */
@HiltViewModel
class RoutePlannerViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val directionsClient: DirectionsClient,
    private val mapsClient: GoogleMapsServicesClient, // Still needed for elevations if using Google
    private val routeOptimizer: RouteOptimizer,
    private val vehicleRepository: VehicleRepository,
    private val preferenceManager: PreferenceManager,
    private val routeInsightGenerator: com.ecodrive.app.domain.ai.analyzer.RouteInsightGenerator,
) : ViewModel() {

    data class RouteWithMetrics(
        val route: MapRoute,
        val metrics: RouteOptimizer.RouteEcoMetrics
    )

    data class RoutePlannerState(
        val origin: LatLng? = null,
        val destination: String = "",
        val destinationLatLng: LatLng? = null,
        val routes: List<RouteWithMetrics> = emptyList(),
        val selectedRouteIndex: Int = 0,
        val isLoading: Boolean = false,
        val error: String? = null,
        val useMetric: Boolean = true,
        val aiRouteInsight: String? = null,
    )

    private val _state = MutableStateFlow(RoutePlannerState())
    val state: StateFlow<RoutePlannerState> = _state.asStateFlow()

    init {
        observePreferences()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferenceManager.useMetricUnits.collect { useMetric ->
                _state.update { it.copy(useMetric = useMetric) }
            }
        }
    }

    fun updateDestination(dest: String) {
        _state.update { it.copy(destination = dest) }
    }

    fun findRoutes(origin: LatLng, destName: String) {
        if (AppConfig.USE_GOOGLE_MAPS && (AppConfig.MAPS_API_KEY.isBlank() || AppConfig.MAPS_API_KEY == "YOUR_GOOGLE_MAPS_API_KEY_HERE")) {
            _state.update { it.copy(error = "Google Maps API Key not configured in AppConfig") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, origin = origin) }

            // Step 1: Android system Geocoder (fast, no API key)
            // Step 2: Nominatim/OSM  (free, no API key, same ecosystem as OSRM)
            // Step 3: AI model      (last resort, handles fuzzy/colloquial names)
            val destinationLatLng = geocodeWithAndroid(destName)
                ?: geocodeWithNominatim(destName)
                ?: geocodeWithAi(destName, origin)

            if (destinationLatLng == null) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        error = "Could not find \"$destName\". Please check the address and try again."
                    )
                }
                return@launch
            }

            val vehicle = vehicleRepository.getDefaultVehicle() ?: Vehicle()

            directionsClient.getRoutes(origin, destinationLatLng, AppConfig.MAPS_API_KEY).onSuccess { routes ->
                val routesWithMetrics = routes.map { route ->
                    // Elevation is only fetched if using Google Maps for now, 
                    // as it requires a Google API key anyway.
                    val elevations = if (AppConfig.USE_GOOGLE_MAPS) {
                        mapsClient.getElevations(route.points, AppConfig.MAPS_API_KEY).getOrDefault(emptyList())
                    } else {
                        emptyList()
                    }

                    val metrics = routeOptimizer.calculateEcoMetrics(route, elevations, vehicle)
                    RouteWithMetrics(route, metrics)
                }.sortedBy { it.metrics.estimatedFuelLiters } // Sort by greenest first

                _state.update {
                    it.copy(
                        routes = routesWithMetrics,
                        isLoading = false,
                        destinationLatLng = destinationLatLng
                    )
                }

                generateAiRouteInsight(routesWithMetrics)
            }.onFailure { e ->
                _state.update { it.copy(isLoading = false, error = "Routing failed: ${e.message}") }
            }
        }
    }

    /**
     * Geocodes an address using the Android system Geocoder (no API key needed).
     * Returns null if the address cannot be resolved.
     */
    private suspend fun geocodeWithAndroid(address: String): LatLng? = withContext(Dispatchers.IO) {
        try {
            val geocoder = Geocoder(context)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Async API for API 33+
                var result: LatLng? = null
                val latch = java.util.concurrent.CountDownLatch(1)
                geocoder.getFromLocationName(address, 1) { addresses ->
                    result = addresses.firstOrNull()?.let { LatLng(it.latitude, it.longitude) }
                    latch.countDown()
                }
                latch.await(5, java.util.concurrent.TimeUnit.SECONDS)
                result
            } else {
                @Suppress("DEPRECATION")
                geocoder.getFromLocationName(address, 1)?.firstOrNull()?.let {
                    LatLng(it.latitude, it.longitude)
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Geocodes an address using the Nominatim OpenStreetMap API.
     * Free, no API key required. Nominatim policy requires a descriptive User-Agent.
     * Rate limit: 1 request/second (fine for interactive search).
     * Returns null if the address cannot be resolved or the request fails.
     */
    private suspend fun geocodeWithNominatim(address: String): LatLng? = withContext(Dispatchers.IO) {
        try {
            val encoded = URLEncoder.encode(address, "UTF-8")
            val url = java.net.URL(
                "https://nominatim.openstreetmap.org/search?q=$encoded&format=json&limit=1"
            )
            val connection = url.openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 8000
            connection.readTimeout = 8000
            // Nominatim ToS requires a meaningful User-Agent identifying the app
            connection.setRequestProperty("User-Agent", "EcoDrive-Android/1.0")
            connection.setRequestProperty("Accept-Language", "en")

            if (connection.responseCode != 200) return@withContext null

            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val arr = JSONArray(body)
            if (arr.length() == 0) return@withContext null

            val first = arr.getJSONObject(0)
            LatLng(first.getDouble("lat"), first.getDouble("lon"))
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Geocodes an address using the AI model as a last-resort fallback.
     * Handles fuzzy, colloquial, or abbreviated place names that structured
     * geocoders may not recognise.
     * Returns null if parsing fails.
     */
    private suspend fun geocodeWithAi(destName: String, origin: LatLng): LatLng? {
        return try {
            val latLngStr = routeInsightGenerator.resolveDestination(
                destName,
                "${origin.latitude},${origin.longitude}"
            )?.trim()?.removeSurrounding("\"")?.trim()

            if (latLngStr.isNullOrBlank() || latLngStr == "null") return null

            val parts = latLngStr.split(",")
            if (parts.size < 2) return null

            LatLng(parts[0].trim().toDouble(), parts[1].trim().toDouble())
        } catch (e: Exception) {
            null
        }
    }

    fun selectRoute(index: Int) {
        _state.update { it.copy(selectedRouteIndex = index) }
    }

    private fun generateAiRouteInsight(routes: List<RouteWithMetrics>) {
        viewModelScope.launch {
            val insight = routeInsightGenerator.generateComparison(routes, state.value.useMetric)
            _state.update { it.copy(aiRouteInsight = insight) }
        }
    }
}
