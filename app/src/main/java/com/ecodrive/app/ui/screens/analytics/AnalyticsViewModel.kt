package com.ecodrive.app.ui.screens.analytics

import com.ecodrive.app.domain.ai.analyzer.AnalyticsInsightGenerator

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.model.Trip
import com.ecodrive.app.ui.components.ChartPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * ViewModel for the Analytics screen.
 * Aggregates trip data into chart-ready formats for trends,
 * comparisons, and behavior breakdowns.
 */
@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
@HiltViewModel
class AnalyticsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val preferenceManager: PreferenceManager,
    private val analyticsInsightGenerator: com.ecodrive.app.domain.ai.analyzer.AnalyticsInsightGenerator,
) : ViewModel() {

    data class AnalyticsState(
        val isLoading: Boolean = true,
        // Eco Score trend (last 20 trips)
        val ecoScoreTrend: List<ChartPoint> = emptyList(),
        val avgEcoScore: Int = 0,
        val bestTrip: Trip? = null,
        val worstTrip: Trip? = null,
        // Fuel efficiency trend
        val fuelEfficiencyTrend: List<ChartPoint> = emptyList(),
        val avgFuelEfficiency: Double = 0.0,
        // Weekly bar chart data
        val weeklyScores: List<Pair<String, Float>> = emptyList(),
        val weeklyDistances: List<Pair<String, Float>> = emptyList(),
        // Behavior breakdown
        val totalHardBrakes: Int = 0,
        val totalHardAccels: Int = 0,
        val totalSharpTurns: Int = 0,
        val totalIdleMinutes: Long = 0,
        // Summary stats
        val totalTrips: Int = 0,
        val totalDistanceKm: Double = 0.0,
        val totalFuelLiters: Double = 0.0,
        val fuelSavedEstimate: Double = 0.0,
        val useMetric: Boolean = true,
        // Time range
        val selectedRange: TimeRange = TimeRange.MONTH,
        // AI Narrative
        val aiSummary: String? = null,
        val isAiLoading: Boolean = false,
    )

    enum class TimeRange(val label: String, val days: Long) {
        WEEK("7 Days", 7),
        MONTH("30 Days", 30),
        ALL("All Time", 365 * 10),
    }

    private val _selectedRange = MutableStateFlow(TimeRange.MONTH)

    private val _state = MutableStateFlow(AnalyticsState())
    val state: StateFlow<AnalyticsState> = _state.asStateFlow()

    init {
        combine(
            _selectedRange,
            tripRepository.getAllTrips(),
            preferenceManager.useMetricUnits
        ) { range, allTrips, useMetric ->
            val sinceMs = Instant.now().minus(range.days, ChronoUnit.DAYS).toEpochMilli()
            val trips = allTrips.filter {
                it.startTime.toEpochMilli() >= sinceMs && !it.isActive
            }.sortedBy { it.startTime }

            if (trips.isEmpty()) {
                _state.update { 
                    AnalyticsState(isLoading = false, totalTrips = 0, selectedRange = range, useMetric = useMetric) 
                }
                return@combine
            }

            // Eco score trend
            val ecoTrend = trips.mapIndexed { i, trip ->
                ChartPoint(
                    x = i.toFloat(),
                    y = trip.ecoScore.toFloat(),
                    label = trip.startTime.atZone(ZoneId.systemDefault())
                        .format(dateFormatter),
                )
            }

            // Fuel efficiency trend
            val fuelTrend = trips.filter { it.distanceKm > 0 }.mapIndexed { i, trip ->
                ChartPoint(
                    x = i.toFloat(),
                    y = trip.fuelEfficiencyLPer100Km.toFloat(),
                    label = trip.startTime.atZone(ZoneId.systemDefault())
                        .format(dateFormatter),
                )
            }

            // Weekly aggregation for bar charts
            val weeklyData = aggregateWeekly(trips)

            // Behavior totals
            val totalBrakes = trips.sumOf { it.hardBrakeCount }
            val totalAccels = trips.sumOf { it.hardAccelCount }
            val totalTurns = trips.sumOf { it.sharpTurnCount }
            val totalIdle = trips.sumOf { it.idleTimeSeconds } / 60

            // Summary
            val totalDist = trips.sumOf { it.distanceKm }
            val totalFuel = trips.sumOf { it.fuelConsumedLiters }
            val avgEfficiency = if (totalDist > 0) (totalFuel / totalDist) * 100 else 0.0

            // Fuel saved estimate: compare avg vs EPA 6.4 L/100km
            val epaFuel = totalDist * 6.4 / 100.0
            val saved = epaFuel - totalFuel

            val newState = AnalyticsState(
                isLoading = false,
                ecoScoreTrend = ecoTrend,
                avgEcoScore = trips.map { t -> t.ecoScore }.average().toInt(),
                bestTrip = trips.maxByOrNull { t -> t.ecoScore },
                worstTrip = trips.minByOrNull { t -> t.ecoScore },
                fuelEfficiencyTrend = fuelTrend,
                avgFuelEfficiency = avgEfficiency,
                weeklyScores = weeklyData.first,
                weeklyDistances = weeklyData.second,
                totalHardBrakes = totalBrakes,
                totalHardAccels = totalAccels,
                totalSharpTurns = totalTurns,
                totalIdleMinutes = totalIdle,
                totalTrips = trips.size,
                totalDistanceKm = totalDist,
                totalFuelLiters = totalFuel,
                fuelSavedEstimate = saved,
                selectedRange = range,
                useMetric = useMetric,
                aiSummary = _state.value.aiSummary,
                isAiLoading = _state.value.isAiLoading
            )
            
            _state.update { newState }
            
            // Trigger AI Summary if data changed significantly or is first load
            viewModelScope.launch {
                generateAiSummary(newState)
            }

        }.launchIn(viewModelScope)
    }

    private suspend fun generateAiSummary(s: AnalyticsState) {
        _state.update { it.copy(isAiLoading = true) }
        val response = analyticsInsightGenerator.generateSummary(s)
        _state.update { it.copy(aiSummary = response, isAiLoading = false) }
    }

    private val dateFormatter = DateTimeFormatter.ofPattern("M/d")

    fun selectTimeRange(range: TimeRange) {
        _selectedRange.value = range
    }

    private fun aggregateWeekly(
        trips: List<Trip>,
    ): Pair<List<Pair<String, Float>>, List<Pair<String, Float>>> {
        val zone = ZoneId.systemDefault()
        val grouped = trips.groupBy { trip ->
            trip.startTime.atZone(zone).toLocalDate().let { date ->
                // Group by week start (Monday)
                date.minusDays(date.dayOfWeek.value.toLong() - 1)
            }
        }

        val scores = grouped.entries.sortedBy { it.key }.takeLast(8).map { (week, weekTrips) ->
            val label = week.format(DateTimeFormatter.ofPattern("M/d"))
            val avgScore = weekTrips.map { it.ecoScore }.average().toFloat()
            label to avgScore
        }

        val distances = grouped.entries.sortedBy { it.key }.takeLast(8).map { (week, weekTrips) ->
            val label = week.format(DateTimeFormatter.ofPattern("M/d"))
            val totalDist = weekTrips.sumOf { it.distanceKm }.toFloat()
            label to totalDist
        }

        return scores to distances
    }
}
