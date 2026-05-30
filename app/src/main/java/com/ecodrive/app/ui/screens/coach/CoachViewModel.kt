package com.ecodrive.app.ui.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.util.AudioFeedbackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    val audioFeedbackManager: AudioFeedbackManager,
) : ViewModel() {

    data class CoachState(
        val isLoading: Boolean = true,
        val topIssue: DrivingEventType? = null,
        val personalizedTip: String = "",
        val issuesCount: Map<DrivingEventType, Int> = emptyMap(),
        val isAudioCoachingEnabled: Boolean = true,
        val recentEcoScore: Int = 0,
    )

    val state: StateFlow<CoachState> = combine(
        tripRepository.getAllTrips(),
        audioFeedbackManager.isAudioEnabled,
    ) { allTrips, audioEnabled ->
        val oneWeekAgo = Instant.now().minus(7, ChronoUnit.DAYS).toEpochMilli()
        val recentTrips = allTrips.filter { it.startTime.toEpochMilli() >= oneWeekAgo }
        
        var totalHardBrakes = 0
        var totalHardAccels = 0
        var totalSharpTurns = 0
        var totalIdleMins = 0L

        recentTrips.forEach { trip ->
            totalHardBrakes += trip.hardBrakeCount
            totalHardAccels += trip.hardAccelCount
            totalSharpTurns += trip.sharpTurnCount
            totalIdleMins += (trip.idleTimeSeconds / 60)
        }

        val eventCounts = mutableMapOf<DrivingEventType, Int>()
        eventCounts[DrivingEventType.HARD_BRAKE] = totalHardBrakes
        eventCounts[DrivingEventType.HARD_ACCELERATION] = totalHardAccels
        eventCounts[DrivingEventType.SHARP_TURN] = totalSharpTurns
        eventCounts[DrivingEventType.EXCESSIVE_IDLE] = totalIdleMins.toInt()

        val topIssue = eventCounts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
        val tip = when (topIssue) {
            DrivingEventType.HARD_BRAKE -> 
                "You've had $totalHardBrakes hard braking events this week. Try looking further ahead and coasting to a stop to let your hybrid system capture regenerative energy."
            DrivingEventType.HARD_ACCELERATION -> 
                "You've accelerated hard $totalHardAccels times recently. Gentle acceleration keeps the car in EV mode longer, saving significant fuel."
            DrivingEventType.SHARP_TURN -> 
                "Sharp cornering detected $totalSharpTurns times. Slow down before entering a turn, rather than braking during it, to maintain momentum."
            DrivingEventType.EXCESSIVE_IDLE -> 
                "You've spent $totalIdleMins minutes idling. Your hybrid engine handles stops well, but prolonged accessory use drains the battery and forces the gas engine to turn on."
            else -> 
                "Great job! Your driving is very smooth. Keep maintaining steady speeds to maximize your Highlander Hybrid's efficiency."
        }

        CoachState(
            isLoading = false,
            topIssue = topIssue,
            personalizedTip = tip,
            issuesCount = eventCounts,
            isAudioCoachingEnabled = audioEnabled,
            recentEcoScore = recentTrips.map { it.ecoScore }.average().toInt().coerceAtLeast(0)
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CoachState()
    )

    fun toggleAudioCoaching() {
        audioFeedbackManager.setAudioEnabled(!state.value.isAudioCoachingEnabled)
    }

    fun refresh() {
        // Data refreshes automatically via tripRepository.getAllTrips() Flow
    }
}
