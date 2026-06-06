package com.ecodrive.app.ui.screens.coach

import com.ecodrive.app.TestUtils
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.ai.service.AiManager
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.domain.model.Trip
import com.ecodrive.app.domain.service.AudioFeedbackManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

@OptIn(ExperimentalCoroutinesApi::class)
class CoachViewModelTest {

    private val tripRepository: TripRepository = mockk()
    private val aiManager: AiManager = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val audioFeedbackManager: AudioFeedbackManager = mockk(relaxed = true)
    private lateinit var viewModel: CoachViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val tripsFlow = MutableStateFlow<List<Trip>>(emptyList())

    private val challengeGenerator: com.ecodrive.app.domain.ai.analyzer.ChallengeGenerator = mockk(relaxed = true)
    private val challengeDao: com.ecodrive.app.data.local.dao.ChallengeDao = mockk(relaxed = true)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()
        
        every { tripRepository.getAllTrips() } returns tripsFlow
        every { audioFeedbackManager.isAudioEnabled } returns MutableStateFlow(true)
        
        viewModel = CoachViewModel(
            tripRepository, 
            aiManager, 
            preferenceManager, 
            challengeGenerator,
            challengeDao,
            audioFeedbackManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test state reflects recent trip data and trends`() = runTest {
        // Given: One trip today, one trip 10 days ago
        val now = Instant.now()
        val recentTrip = Trip(
            id = 1,
            startTime = now.minus(1, ChronoUnit.HOURS),
            hardBrakeCount = 2,
            ecoScore = 80,
            isActive = false
        )
        
        // And: One trip in the "previous week" window (8-14 days ago) for trend calculation
        val previousWeekTrip = Trip(
            id = 3,
            startTime = now.minus(10, ChronoUnit.DAYS),
            hardBrakeCount = 4,
            ecoScore = 70,
            isActive = false
        )

        tripsFlow.value = listOf(recentTrip, previousWeekTrip)
        
        // When
        advanceUntilIdle()
        val state = viewModel.state.first { !it.isLoading }
        
        // Then
        assertEquals(2, state.issuesCount[DrivingEventType.HARD_BRAKE])
        assertEquals(80, state.recentEcoScore)
        // Trend: current=2, previous=4 -> -50% (improvement)
        assertEquals(-50.0, state.trends[DrivingEventType.HARD_BRAKE]!!, 0.1)
        assertEquals(10.0, state.scoreTrend, 0.1)
    }

    @Test
    fun `test askQuestion updates chat history with model attribution`() = runTest {
        // Given
        val question = "How can I drive better?"
        val aiResponse = "Drive smoother."
        val providerName = "GEMINI"
        
        coEvery { 
            aiManager.generateConversationalResponse(any(), any()) 
        } returns (aiResponse to providerName)
        
        val collectJob = launch { viewModel.state.collect {} }
        
        // When
        viewModel.askQuestion(question)
        advanceUntilIdle()
        
        // Then
        val history = viewModel.state.value.chatHistory
        assertEquals("History size mismatch", 2, history.size)
        
        // User message
        assertEquals(question, history[0].text)
        assertTrue(history[0].isUser)
        
        // AI message
        assertEquals(aiResponse, history[1].text)
        assertTrue(!history[1].isUser)
        assertEquals(providerName, history[1].providerName)
        
        collectJob.cancel()
    }

    @Test
    fun `test askQuestion handles error with no attribution`() = runTest {
        // Given
        coEvery { 
            aiManager.generateConversationalResponse(any(), any()) 
        } returns null
        
        val collectJob = launch { viewModel.state.collect {} }
        
        // When
        viewModel.askQuestion("Test")
        advanceUntilIdle()
        
        // Then
        val history = viewModel.state.value.chatHistory
        assertEquals("History size mismatch on error", 2, history.size)
        assertTrue("Error message text missing", history[1].text.contains("trouble connecting"))
        assertEquals(null, history[1].providerName)
        
        collectJob.cancel()
    }
}
