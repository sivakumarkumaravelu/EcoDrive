package com.ecodrive.app.ui.screens.tripdetail

import com.ecodrive.app.domain.ai.service.AiManager

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.local.dao.AiInsightDao
import com.ecodrive.app.data.local.dao.DataPointDao
import com.ecodrive.app.data.local.entity.AiInsightEntity
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.analyzer.LocalEcoCoach
import com.ecodrive.app.domain.model.DrivingEvent
import com.ecodrive.app.domain.model.Trip
import com.ecodrive.app.ui.components.ChartPoint
import com.ecodrive.app.util.Constants
import com.google.ai.client.generativeai.GenerativeModel
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
import kotlin.math.abs

/**
 * ViewModel for the Trip Detail screen.
 * Loads data points and events for a specific trip and prepares chart data.
 */
@HiltViewModel
class TripDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val tripRepository: TripRepository,
    private val dataPointDao: DataPointDao,
    private val aiInsightDao: AiInsightDao,
    private val preferenceManager: PreferenceManager,
    private val localEcoCoach: LocalEcoCoach,
    private val aiManager: com.ecodrive.app.domain.ai.service.AiManager,
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
        val aiInsight: String? = null,
        val isAiLoading: Boolean = false,
        val aiError: String? = null,
        val anomalies: List<com.ecodrive.app.domain.model.VehicleAnomaly> = emptyList(),
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
            if (trip != null) {
                generateAiInsight(trip)
            }
        }
    }

    private fun generateAiInsight(trip: Trip) {
        viewModelScope.launch {
            _state.update { it.copy(isAiLoading = true, aiError = null) }
            
            // Check cache first
            val cached = aiInsightDao.getInsightForTrip(tripId)
            if (cached != null) {
                _state.update { it.copy(aiInsight = cached.insightText, isAiLoading = false) }
                return@launch
            }

            val events = state.value.events

            // Fetch historical averages for context
            val oneMonthAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val avgScore = tripRepository.getAverageEcoScore(oneMonthAgo)
            val avgEfficiency = tripRepository.getAverageFuelEfficiency(oneMonthAgo)

            // Prepare telemetry context (peaks and highlights)
            val maxSpeed = state.value.speedPoints.maxOfOrNull { it.y } ?: 0f
            val maxAccel = state.value.accelPoints.maxOfOrNull { it.y } ?: 0f
            val maxBrake = state.value.accelPoints.minOfOrNull { it.y } ?: 0f

            // Find similar trips for pattern comparison (Feature 1)
            val startPoint = state.value.routePoints.firstOrNull()
            val endPoint = state.value.routePoints.lastOrNull()
            val similarTripsContext = if (startPoint != null && endPoint != null) {
                val similarTrips = tripRepository.findSimilarTrips(
                    startLat = startPoint.latitude,
                    startLon = startPoint.longitude,
                    endLat = endPoint.latitude,
                    endLon = endPoint.longitude,
                    limit = 5
                ).filter { it.id != tripId }
                
                if (similarTrips.isNotEmpty()) {
                    val avgSimilarScore = similarTrips.map { it.ecoScore }.average()
                    val scoreDiff = trip.ecoScore - avgSimilarScore
                    val comparison = if (scoreDiff > 0) "better" else "worse"
                    "\nSimilar Route Comparison:\n" +
                    "- You've driven this route ${similarTrips.size} other time(s) recently.\n" +
                    "- This trip scored ${"%.1f".format(abs(scoreDiff))} points $comparison than your average (${"%.1f".format(avgSimilarScore)}) for this exact route."
                } else ""
            } else ""

            val useMetric = state.value.useMetric
            val distanceUnit = if (useMetric) "km" else "miles"
            val speedUnit = if (useMetric) "km/h" else "mph"
            val fuelEfficiencyUnit = if (useMetric) "L/100km" else "mpg"
            val fuelUnit = if (useMetric) "L" else "gallons"

            val displayDistance = if (useMetric) trip.distanceKm else com.ecodrive.app.util.UnitConverter.kmToMiles(trip.distanceKm)
            val displayFuel = if (useMetric) trip.fuelConsumedLiters else com.ecodrive.app.util.UnitConverter.litersToGallons(trip.fuelConsumedLiters)
            val displayEfficiency = if (useMetric) trip.fuelEfficiencyLPer100Km else com.ecodrive.app.util.UnitConverter.l100kmToMpg(trip.fuelEfficiencyLPer100Km)
            val displayAvgEfficiency = if (useMetric) (avgEfficiency ?: trip.fuelEfficiencyLPer100Km) else com.ecodrive.app.util.UnitConverter.l100kmToMpg(avgEfficiency ?: trip.fuelEfficiencyLPer100Km)
            val displayMaxSpeed = if (useMetric) maxSpeed else com.ecodrive.app.util.UnitConverter.kmhToMph(maxSpeed.toDouble()).toFloat()

            val prompt = """
                You are an expert Eco-Driving Coach. Analyze the following trip data and provide a rich, 
                encouraging, and actionable insight for the driver.
                
                CRITICAL: Use $distanceUnit, $speedUnit, $fuelEfficiencyUnit, and $fuelUnit for all mentions of distance, speed, efficiency, and fuel volume.
                
                Trip Summary:
                - Eco Score: ${trip.ecoScore}/100 (Your 30-day average: ${"%.1f".format(avgScore ?: trip.ecoScore.toDouble())})
                - Distance: ${"%.1f".format(displayDistance) } $distanceUnit
                - Duration: ${trip.durationSeconds / 60} minutes
                - Fuel Consumed: ${"%.2f".format(displayFuel)} $fuelUnit
                - Efficiency: ${"%.2f".format(displayEfficiency)} $fuelEfficiencyUnit (Avg: ${"%.2f".format(displayAvgEfficiency)} $fuelEfficiencyUnit)
                $similarTripsContext
                
                Telemetry Highlights:
                - Max Speed: ${"%.1f".format(displayMaxSpeed)} $speedUnit
                - Max Acceleration: ${"%.2f".format(maxAccel)} m/s²
                - Strongest Braking: ${"%.2f".format(maxBrake)} m/s²
                
                Driving Events:
                ${events.joinToString("\n") { 
                    val eventSpeed = if (useMetric) it.speedAtEvent else com.ecodrive.app.util.UnitConverter.kmhToMph(it.speedAtEvent)
                    "- ${it.type.name} at ${"%.1f".format(eventSpeed)} $speedUnit: ${it.description}" 
                }}
                
                Context:
                The trip was taken on ${trip.startTime.atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("EEEE, MMMM d 'at' HH:mm"))}.
                
                Focus on why the score was high or low and what specific behavior to improve or maintain.
                Compare with historical averages if relevant.
                Structure your response into these sections:
                1. Summary (A concise overview of the trip's efficiency)
                2. Key Moments (Mention specific events or telemetry highlights)
                3. Improvement Plan (Concrete, actionable steps for the next drive)
            """.trimIndent()

            val response = aiManager.generateTripInsight(prompt)
            
            if (response != null) {
                // Save to cache
                aiInsightDao.insertInsight(
                    AiInsightEntity(
                        tripId = tripId,
                        insightText = response,
                        isAiGenerated = true
                    )
                )
                _state.update { it.copy(aiInsight = response, isAiLoading = false) }
            } else {
                // Fallback to local insight on error or null response
                val localInsight = localEcoCoach.getInsight(trip, events)
                _state.update { 
                    it.copy(
                        aiInsight = localInsight, 
                        isAiLoading = false,
                        aiError = "Using local coach (AI unavailable)"
                    ) 
                }
            }
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
        viewModelScope.launch {
            tripRepository.getAnomaliesForTrip(tripId).collect { anomalies ->
                _state.update { it.copy(anomalies = anomalies) }
            }
        }
    }
}
