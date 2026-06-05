package com.ecodrive.app.ui.screens.tripdetail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.local.dao.DataPointDao
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.model.DrivingEvent
import com.ecodrive.app.domain.model.Trip
import com.ecodrive.app.ui.components.ChartPoint
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * ViewModel for the Trip Detail screen.
 * Loads data points and events for a specific trip and prepares chart data.
 */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val dataPointDao: DataPointDao,
    private val preferenceManager: PreferenceManager,
) : ViewModel() {

    private val tripId: Long = savedStateHandle.get<Long>("tripId") ?: 0L

    data class TripDetailState(
        val trip: Trip? = null,
        val isLoading: Boolean = true,
        val speedPoints: List<ChartPoint> = emptyList(),
        val accelPoints: List<ChartPoint> = emptyList(),
        val fuelPoints: List<ChartPoint> = emptyList(),
        val altitudePoints: List<ChartPoint> = emptyList(),
        val routePoints: List<LatLng> = emptyList(),
        val events: List<DrivingEvent> = emptyList(),
        val scoreBreakdown: List<Pair<String, Int>> = emptyList(),
        val useMetric: Boolean = true,
    )

    private val _state = MutableStateFlow(TripDetailState())
    val state: StateFlow<TripDetailState> = _state.asStateFlow()

    init {
        if (tripId > 0) {
            loadTrip()
            loadDataPoints()
            loadEvents()
            observePreferences()
        }
    }

    private fun observePreferences() {
        preferenceManager.useMetricUnits
            .onEach { useMetric ->
                _state.update { it.copy(useMetric = useMetric) }
            }
            .launchIn(viewModelScope)
    }

    private fun loadTrip() {
        viewModelScope.launch {
            val trip = tripRepository.getTripById(tripId)
            _state.update { it.copy(trip = trip) }
        }
    }

    private fun loadDataPoints() {
        viewModelScope.launch {
            dataPointDao.getDataPointsForTrip(tripId).collect { allPoints ->
                if (allPoints.isEmpty()) {
                    _state.update { it.copy(isLoading = false) }
                    return@collect
                }

                // Sample points if too many (max 500 for charts/map)
                val points = if (allPoints.size > 500) {
                    val step = allPoints.size / 500
                    allPoints.filterIndexed { index, _ -> index % step == 0 }
                } else {
                    allPoints
                }

                val startTime = points.first().timestampEpochMs

                val speedData = points.map { dp ->
                    val minutesElapsed = (dp.timestampEpochMs - startTime) / 60000.0
                    ChartPoint(
                        x = minutesElapsed.toFloat(),
                        y = dp.speedKmh.toFloat(),
                        label = "%.0f".format(minutesElapsed),
                    )
                }

                val accelData = points.map { dp ->
                    val minutesElapsed = (dp.timestampEpochMs - startTime) / 60000.0
                    ChartPoint(
                        x = minutesElapsed.toFloat(),
                        y = dp.longitudinalAccelMps2.toFloat(),
                    )
                }

                val fuelData = points.filter { it.fuelConsumptionLPer100Km > 0 }
                    .map { dp ->
                        val minutesElapsed = (dp.timestampEpochMs - startTime) / 60000.0
                        ChartPoint(
                            x = minutesElapsed.toFloat(),
                            y = dp.fuelConsumptionLPer100Km.toFloat(),
                        )
                    }

                val altData = points.filter { it.altitudeM != 0.0 }.map { dp ->
                    val minutesElapsed = (dp.timestampEpochMs - startTime) / 60000.0
                    ChartPoint(
                        x = minutesElapsed.toFloat(),
                        y = dp.altitudeM.toFloat(),
                    )
                }

                val routeData = points.filter { it.latitude != 0.0 && it.longitude != 0.0 }.map { dp ->
                    LatLng(dp.latitude, dp.longitude)
                }

                _state.update {
                    it.copy(
                        isLoading = false,
                        speedPoints = speedData,
                        accelPoints = accelData,
                        fuelPoints = fuelData,
                        altitudePoints = altData,
                        routePoints = routeData,
                    )
                }
            }
        }
    }

    /**
     * Exports the trip data points to a CSV file in the cache directory
     * and returns the absolute path of the file.
     */
    suspend fun exportTripDataToCsv(cacheDir: File): String? {
        val trip = state.value.trip ?: return null
        
        // Fetch all points synchronously for export
        var allPoints = emptyList<com.ecodrive.app.data.local.entity.DataPointEntity>()
        dataPointDao.getDataPointsForTrip(tripId).take(1).collect { allPoints = it }
        
        if (allPoints.isEmpty()) return null

        val fileName = "EcoDrive_Trip_${tripId}_${trip.startTime.toEpochMilli()}.csv"
        val file = File(cacheDir, fileName)
        
        val formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME.withZone(ZoneId.systemDefault())

        FileWriter(file).use { writer ->
            writer.append("Timestamp,Speed(km/h),Latitude,Longitude,Altitude(m),Accel(m/s2),LateralAccel(m/s2),Grade(%),FuelConsump(L/100km)\n")
            for (dp in allPoints) {
                val timeStr = formatter.format(Instant.ofEpochMilli(dp.timestampEpochMs))
                writer.append("$timeStr,${dp.speedKmh},${dp.latitude},${dp.longitude},${dp.altitudeM},${dp.longitudinalAccelMps2},${dp.lateralAccelMps2},${dp.roadGradePercent},${dp.fuelConsumptionLPer100Km}\n")
            }
        }
        
        return file.absolutePath
    }

    private fun loadEvents() {
        viewModelScope.launch {
            tripRepository.getEventsForTrip(tripId).collect { events ->
                _state.update { it.copy(events = events) }
            }
        }
    }
}
