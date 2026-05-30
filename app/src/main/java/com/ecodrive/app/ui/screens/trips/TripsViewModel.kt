package com.ecodrive.app.ui.screens.trips

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.model.Trip
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

/**
 * ViewModel for the Trip History screen.
 * Loads trips from the database and provides summary statistics.
 */
@HiltViewModel
class TripsViewModel @Inject constructor(
    private val tripRepository: TripRepository,
) : ViewModel() {

    data class TripsState(
        val trips: List<Trip> = emptyList(),
        val isLoading: Boolean = true,
        val weeklyAvgScore: Int = 0,
        val weeklyDistance: Double = 0.0,
        val weeklyFuel: Double = 0.0,
        val totalTrips: Int = 0,
    )

    private val _state = MutableStateFlow(TripsState())
    val state: StateFlow<TripsState> = _state.asStateFlow()

    init {
        loadTrips()
        loadWeeklyStats()
    }

    private fun loadTrips() {
        viewModelScope.launch {
            tripRepository.getRecentTrips(50).collect { trips ->
                _state.update {
                    it.copy(
                        trips = trips,
                        isLoading = false,
                        totalTrips = trips.size,
                    )
                }
            }
        }
    }

    private fun loadWeeklyStats() {
        viewModelScope.launch {
            val weekAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli()
            val avgScore = tripRepository.getAverageEcoScore(weekAgo) ?: 0.0
            val totalDist = tripRepository.getTotalDistance(weekAgo) ?: 0.0
            val totalFuel = tripRepository.getTotalFuelConsumed(weekAgo) ?: 0.0

            _state.update {
                it.copy(
                    weeklyAvgScore = avgScore.toInt(),
                    weeklyDistance = totalDist,
                    weeklyFuel = totalFuel,
                )
            }
        }
    }

    fun deleteTrip(tripId: Long) {
        viewModelScope.launch {
            tripRepository.deleteTrip(tripId)
        }
    }

    fun refresh() {
        _state.update { it.copy(isLoading = true) }
        loadTrips()
        loadWeeklyStats()
    }
}
