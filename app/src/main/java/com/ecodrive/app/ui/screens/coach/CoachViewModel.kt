package com.ecodrive.app.ui.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.util.AudioFeedbackManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.temporal.ChronoUnit
import javax.inject.Inject

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val tripRepository: TripRepository,
    private val geminiManager: com.ecodrive.app.domain.ai.GeminiManager,
    private val preferenceManager: com.ecodrive.app.data.local.PreferenceManager,
    val audioFeedbackManager: AudioFeedbackManager,
) : ViewModel() {

    data class CoachState(
        val isLoading: Boolean = true,
        val topIssue: DrivingEventType? = null,
        val personalizedTip: String = "",
        val issuesCount: Map<DrivingEventType, Int> = emptyMap(),
        val trends: Map<DrivingEventType, Double> = emptyMap(), // % change vs last week
        val isAudioCoachingEnabled: Boolean = true,
        val recentEcoScore: Int = 0,
        val scoreTrend: Double = 0.0, // change in score vs last week
        val chatHistory: List<ChatMessage> = emptyList(),
        val isAskingAi: Boolean = false,
    )

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val timestamp: Instant = Instant.now()
    )

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _isAskingAi = MutableStateFlow(false)
    private val _aiReport = MutableStateFlow<String?>(null)

    val state: StateFlow<CoachState> = combine(
        tripRepository.getAllTrips(),
        audioFeedbackManager.isAudioEnabled,
        _chatHistory,
        _isAskingAi,
        _aiReport
    ) { allTrips, audioEnabled, chatHistory, isAskingAi, aiReport ->
        val now = Instant.now()
        val oneWeekAgo = now.minus(7, ChronoUnit.DAYS).toEpochMilli()
        val twoWeeksAgo = now.minus(14, ChronoUnit.DAYS).toEpochMilli()

        val recentTrips = allTrips.filter { it.startTime.toEpochMilli() >= oneWeekAgo }
        val previousTrips = allTrips.filter { 
            val start = it.startTime.toEpochMilli()
            start >= twoWeeksAgo && start < oneWeekAgo 
        }
        
        fun calculateAggregates(trips: List<com.ecodrive.app.domain.model.Trip>): Map<DrivingEventType, Int> {
            var totalHardBrakes = 0
            var totalHardAccels = 0
            var totalSharpTurns = 0
            var totalIdleMins = 0L

            trips.forEach { trip ->
                totalHardBrakes += trip.hardBrakeCount
                totalHardAccels += trip.hardAccelCount
                totalSharpTurns += trip.sharpTurnCount
                totalIdleMins += (trip.idleTimeSeconds / 60)
            }

            return mapOf(
                DrivingEventType.HARD_BRAKE to totalHardBrakes,
                DrivingEventType.HARD_ACCELERATION to totalHardAccels,
                DrivingEventType.SHARP_TURN to totalSharpTurns,
                DrivingEventType.EXCESSIVE_IDLE to totalIdleMins.toInt()
            )
        }

        val currentCounts = calculateAggregates(recentTrips)
        val previousCounts = calculateAggregates(previousTrips)

        val trends = currentCounts.mapValues { (type, current) ->
            val prev = previousCounts[type] ?: 0
            if (prev == 0) {
                if (current > 0) 100.0 else 0.0
            } else {
                ((current - prev).toDouble() / prev) * 100.0
            }
        }

        val topIssue = currentCounts.maxByOrNull { it.value }?.takeIf { it.value > 0 }?.key
        
        val recentScore = if (recentTrips.isNotEmpty()) recentTrips.map { it.ecoScore }.average() else 0.0
        val prevScore = if (previousTrips.isNotEmpty()) previousTrips.map { it.ecoScore }.average() else 0.0
        val scoreTrend = recentScore - prevScore

        val tip = aiReport ?: when {
            recentScore > 90 -> "Outstanding driving! You're in the top 5% of efficient drivers. Keep maintaining those steady speeds."
            topIssue == DrivingEventType.HARD_BRAKE -> 
                "You've had ${currentCounts[DrivingEventType.HARD_BRAKE]} hard braking events this week. Try looking further ahead and coasting to a stop."
            topIssue == DrivingEventType.HARD_ACCELERATION -> 
                "Gentle acceleration can save up to 15% on fuel. Try to imagine an egg under your gas pedal!"
            topIssue == DrivingEventType.SHARP_TURN -> 
                "Slow down before entering a turn. It's safer and helps maintain your momentum more efficiently."
            topIssue == DrivingEventType.EXCESSIVE_IDLE -> 
                "Idling for more than 30 seconds wastes more fuel than restarting. Consider turning off the engine during long waits."
            recentTrips.isNotEmpty() -> "Good job this week. Try to focus on even smoother braking to boost your score further."
            else -> "Welcome to EcoDrive! Complete your first trip to receive personalized coaching tips."
        }

        CoachState(
            isLoading = false,
            topIssue = topIssue,
            personalizedTip = tip,
            issuesCount = currentCounts,
            trends = trends,
            isAudioCoachingEnabled = audioEnabled,
            recentEcoScore = recentScore.toInt(),
            scoreTrend = scoreTrend,
            chatHistory = chatHistory,
            isAskingAi = isAskingAi
        ).also {
            // Trigger AI report if data changed and we don't have one yet
            if (recentTrips.isNotEmpty() && aiReport == null) {
                generateWeeklyAiReport(it)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CoachState()
    )

    private fun generateWeeklyAiReport(s: CoachState) {
        viewModelScope.launch {
            val apiKey = preferenceManager.geminiApiKey.first()
            if (apiKey.isBlank()) return@launch

            val prompt = """
                You are an expert Eco-Driving Coach. Analyze the driver's performance this week and provide a personalized coaching report (max 3 sentences).
                
                Performance:
                - Recent Eco Score: ${s.recentEcoScore}/100 (Trend: ${"%.1f".format(s.scoreTrend)} pts)
                - Top Issue: ${s.topIssue?.name ?: "None"}
                - Issue Counts: ${s.issuesCount}
                - Trends: ${s.trends.mapValues { "%.1f%%".format(it.value) }}
                
                Provide encouraging, specific advice. If there are improvements, celebrate them.
            """.trimIndent()

            val response = geminiManager.generateWeeklyReport(apiKey, prompt)
            _aiReport.value = response
        }
    }

    fun toggleAudioCoaching() {
        audioFeedbackManager.setAudioEnabled(!state.value.isAudioCoachingEnabled)
    }

    fun askQuestion(question: String) {
        if (question.isBlank()) return
        
        viewModelScope.launch {
            val userMsg = ChatMessage(question, isUser = true)
            _chatHistory.update { it + userMsg }
            _isAskingAi.value = true

            val apiKey = preferenceManager.geminiApiKey.first()
            if (apiKey.isBlank()) {
                _chatHistory.update { it + ChatMessage("Please configure your Gemini API key in Settings to use the AI Coach.", isUser = false) }
                _isAskingAi.value = false
                return@launch
            }

            val contextPrompt = """
                You are an expert Eco-Driving Coach. The user has a question about driving efficiency or their recent performance.
                
                Recent Performance Summary:
                ${state.value.personalizedTip}
                
                User Question: $question
                
                Provide a helpful, concise, and encouraging response.
            """.trimIndent()

            val response = geminiManager.generateTripInsight(apiKey, contextPrompt)
            val aiMsg = ChatMessage(response ?: "I'm sorry, I'm having trouble connecting right now. Please try again later.", isUser = false)
            
            _chatHistory.update { it + aiMsg }
            _isAskingAi.value = false
        }
    }

    fun refresh() {
        // Data refreshes automatically via tripRepository.getAllTrips() Flow
    }
}
