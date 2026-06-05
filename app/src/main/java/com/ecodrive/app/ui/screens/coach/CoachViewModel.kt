package com.ecodrive.app.ui.screens.coach

import com.ecodrive.app.domain.ai.service.AiManager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ecodrive.app.data.local.dao.ChallengeDao
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.ai.analyzer.ChallengeGenerator
import com.ecodrive.app.domain.model.Badge
import com.ecodrive.app.domain.model.BadgeType
import com.ecodrive.app.domain.model.Challenge
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
    private val aiManager: com.ecodrive.app.domain.ai.service.AiManager,
    private val preferenceManager: com.ecodrive.app.data.local.PreferenceManager,
    private val challengeGenerator: ChallengeGenerator,
    private val challengeDao: ChallengeDao,
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
        val suggestedQuestions: List<String> = emptyList(),
        // Gamification
        val activeChallenge: Challenge? = null,
        val earnedBadges: List<Badge> = emptyList(),
    )

    data class ChatMessage(
        val text: String,
        val isUser: Boolean,
        val timestamp: Instant = Instant.now()
    )

    private val _chatHistory = MutableStateFlow<List<ChatMessage>>(emptyList())
    private val _isAskingAi = MutableStateFlow(false)
    private val _aiReport = MutableStateFlow<String?>(null)
    private val _activeChallenge = MutableStateFlow<Challenge?>(null)
    private val _earnedBadges = MutableStateFlow<List<Badge>>(emptyList())
    private val _suggestedQuestions = MutableStateFlow<List<String>>(emptyList())

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
            isAskingAi = isAskingAi,
            suggestedQuestions = _suggestedQuestions.value,
            activeChallenge = _activeChallenge.value,
            earnedBadges = _earnedBadges.value,
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

    init {
        loadChallengeAndBadges()
        initSuggestedQuestions()
    }

    private fun loadChallengeAndBadges() {
        viewModelScope.launch {
            // Load active challenge
            val challengeEntity = challengeDao.getActiveChallenge()
            if (challengeEntity != null) {
                _activeChallenge.value = Challenge(
                    id = challengeEntity.id,
                    title = challengeEntity.title,
                    description = challengeEntity.description,
                    targetCount = challengeEntity.targetCount,
                    progressCount = challengeEntity.progressCount,
                    metricType = try {
                        DrivingEventType.valueOf(challengeEntity.metricType)
                    } catch (_: Exception) { DrivingEventType.HARD_BRAKE },
                    durationDays = challengeEntity.durationDays,
                    createdAtEpochMs = challengeEntity.createdAtEpochMs,
                    isCompleted = challengeEntity.isCompleted,
                )
            } else {
                // Generate a new challenge if none exists
                viewModelScope.launch {
                    val recentTrips = tripRepository.getRecentCompletedTrips(10)
                    val newChallenge = challengeGenerator.generateChallenge(recentTrips)
                    _activeChallenge.value = newChallenge
                }
            }

            // Load earned badges
            challengeDao.getAllBadges().collect { badgeEntities ->
                _earnedBadges.value = badgeEntities.mapNotNull { entity ->
                    try {
                        Badge(
                            id = entity.id,
                            type = BadgeType.valueOf(entity.type),
                            earnedAtEpochMs = entity.earnedAtEpochMs,
                        )
                    } catch (_: Exception) { null }
                }
            }
        }
    }

    private fun initSuggestedQuestions() {
        _suggestedQuestions.value = listOf(
            "How can I reduce hard braking?",
            "What's the best speed for fuel efficiency?",
            "How is my Eco Score calculated?",
        )
    }

    private fun generateWeeklyAiReport(s: CoachState) {
        viewModelScope.launch {
            val prompt = """
                You are an expert Eco-Driving Coach. Analyze the driver's performance this week and provide a personalized coaching report (max 3 sentences).
                
                Performance:
                - Recent Eco Score: ${s.recentEcoScore}/100 (Trend: ${"%.1f".format(s.scoreTrend)} pts)
                - Top Issue: ${s.topIssue?.name ?: "None"}
                - Issue Counts: HARD_BRAKE=${s.issuesCount[DrivingEventType.HARD_BRAKE] ?: 0}, HARD_ACCELERATION=${s.issuesCount[DrivingEventType.HARD_ACCELERATION] ?: 0}
                - Trends: ${s.trends.mapValues { "%.1f%%".format(it.value) }}
                
                Provide encouraging, specific advice. If there are improvements, celebrate them.
            """.trimIndent()

            val response = aiManager.generateWeeklyReport(prompt)
            _aiReport.value = response
        }
    }

    fun toggleAudioCoaching() {
        audioFeedbackManager.setAudioEnabled(!state.value.isAudioCoachingEnabled)
    }

    /**
     * Sends a question to the AI Coach with full conversation history context.
     * Uses multi-turn conversation via [AiManager.generateConversationalResponse].
     */
    fun askQuestion(question: String) {
        if (question.isBlank()) return

        viewModelScope.launch {
            val userMsg = ChatMessage(question, isUser = true)
            _chatHistory.update { it + userMsg }
            _isAskingAi.value = true

            val systemPrompt = """
                You are an expert Eco-Driving Coach helping a driver improve their fuel efficiency and eco score.
                
                Driver's Recent Context:
                - Recent Eco Score: ${state.value.recentEcoScore}/100 (${if (state.value.scoreTrend > 0) "↑" else "↓"} ${"%.1f".format(state.value.scoreTrend)} vs last week)
                - Top Issue this week: ${state.value.topIssue?.name?.replace("_", " ") ?: "None"}
                - Hard Brakes: ${state.value.issuesCount[DrivingEventType.HARD_BRAKE] ?: 0}
                - Hard Accels: ${state.value.issuesCount[DrivingEventType.HARD_ACCELERATION] ?: 0}
                
                Provide helpful, concise, and encouraging responses. Reference the driver's specific data when relevant.
                After your answer, optionally suggest 2 follow-up questions the driver might want to ask, prefixed with "SUGGESTIONS:".
            """.trimIndent()

            // Build message list for multi-turn context
            val allMessages = _chatHistory.value.map { msg -> msg.text to msg.isUser }

            val response = aiManager.generateConversationalResponse(allMessages, systemPrompt)
                ?: "I'm having trouble connecting right now. Please try again later."

            // Extract suggestions if provided
            val (responseText, suggestions) = extractSuggestions(response)
            if (suggestions.isNotEmpty()) {
                _suggestedQuestions.value = suggestions
            }

            val aiMsg = ChatMessage(responseText, isUser = false)
            _chatHistory.update { it + aiMsg }
            _isAskingAi.value = false
        }
    }

    /**
     * Extracts "SUGGESTIONS: ..." from the AI response and returns
     * (cleaned response text, list of suggestions).
     */
    private fun extractSuggestions(response: String): Pair<String, List<String>> {
        val marker = "SUGGESTIONS:"
        val suggestionIdx = response.indexOf(marker, ignoreCase = true)
        if (suggestionIdx == -1) return response to emptyList()

        val mainText = response.substring(0, suggestionIdx).trim()
        val suggestionBlock = response.substring(suggestionIdx + marker.length).trim()
        val suggestions = suggestionBlock.lines()
            .map { it.trimStart('-', '*', '1', '2', '3', '.', ' ') }
            .filter { it.isNotBlank() && it.endsWith("?") }
            .take(3)
        return mainText to suggestions
    }

    fun refresh() {
        // Data refreshes automatically via tripRepository.getAllTrips() Flow
    }

    fun clearChatHistory() {
        _chatHistory.value = emptyList()
        initSuggestedQuestions()
    }
}
