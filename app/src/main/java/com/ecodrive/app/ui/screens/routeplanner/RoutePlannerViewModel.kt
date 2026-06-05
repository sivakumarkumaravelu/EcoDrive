package com.ecodrive.app.ui.screens.routeplanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.GoogleMapsServicesClient
import com.ecodrive.app.data.repository.VehicleRepository
import com.ecodrive.app.domain.analyzer.RouteOptimizer
import com.ecodrive.app.domain.model.Vehicle
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the Route Planner screen.
 * Handles fetching routes and calculating eco-metrics for them.
 */
@HiltViewModel
class RoutePlannerViewModel @Inject constructor(
    private val mapsClient: GoogleMapsServicesClient,
    private val routeOptimizer: RouteOptimizer,
    private val vehicleRepository: VehicleRepository,
    private val preferenceManager: PreferenceManager,
    private val routeInsightGenerator: com.ecodrive.app.domain.ai.RouteInsightGenerator,
) : ViewModel() {

    data class RouteWithMetrics(
        val route: GoogleMapsServicesClient.RouteOption,
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
        val mapsApiKey: String = "",
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
        viewModelScope.launch {
            preferenceManager.mapsApiKey.collect { apiKey ->
                _state.update { it.copy(mapsApiKey = apiKey) }
            }
        }
    }

    fun updateDestination(dest: String) {
        _state.update { it.copy(destination = dest) }
    }

    fun findRoutes(origin: LatLng, destName: String) {
        if (_state.value.mapsApiKey.isBlank()) {
            _state.update { it.copy(error = "Google Maps API Key not configured in Settings") }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, origin = origin) }
            
            // AI-assisted Geocoding
            val latLngStr = routeInsightGenerator.resolveDestination(destName, "${origin.latitude},${origin.longitude}")
            val destinationLatLng = if (latLngStr != null && latLngStr != "null") {
                try {
                    val parts = latLngStr.split(",")
                    LatLng(parts[0].toDouble(), parts[1].toDouble())
                } catch (e: Exception) {
                    LatLng(origin.latitude + 0.1, origin.longitude + 0.1) // Fallback to dummy
                }
            } else {
                LatLng(origin.latitude + 0.1, origin.longitude + 0.1) // Fallback to dummy
            }
            
            val vehicle = vehicleRepository.getDefaultVehicle() ?: Vehicle()
            
            mapsClient.getRoutes(origin, destinationLatLng, _state.value.mapsApiKey).onSuccess { routes ->
                val routesWithMetrics = routes.map { route ->
                    val elevations = mapsClient.getElevations(route.points, _state.value.mapsApiKey)
                        .getOrDefault(emptyList())
                    
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
                _state.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun selectRoute(index: Int) {
        _state.update { it.copy(selectedRouteIndex = index) }
    }

    private fun generateAiRouteInsight(routes: List<RouteWithMetrics>) {
        viewModelScope.launch {
            val insight = routeInsightGenerator.generateComparison(routes)
            _state.update { it.copy(aiRouteInsight = insight) }
        }
    }
}
