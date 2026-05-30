package com.ecodrive.app.domain.recorder

import android.content.Context
import com.ecodrive.app.TestUtils
import com.ecodrive.app.data.repository.TripRepository
import com.ecodrive.app.domain.analyzer.DrivingPatternAnalyzer
import com.ecodrive.app.domain.analyzer.EcoScoreCalculator
import com.ecodrive.app.sensor.SensorDataManager
import com.ecodrive.app.util.AudioFeedbackManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripRecorderTest {

    private val context: Context = mockk(relaxed = true)
    private val sensorDataManager: SensorDataManager = mockk(relaxed = true)
    private val tripRepository: TripRepository = mockk(relaxed = true)
    private val analyzer: DrivingPatternAnalyzer = mockk(relaxed = true)
    private val ecoScoreCalculator: EcoScoreCalculator = mockk(relaxed = true)
    private val audioFeedbackManager: AudioFeedbackManager = mockk(relaxed = true)

    private lateinit var tripRecorder: TripRecorder
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()
        
        every { sensorDataManager.metrics } returns MutableStateFlow(mockk(relaxed = true))
        
        tripRecorder = TripRecorder(
            context,
            sensorDataManager,
            tripRepository,
            analyzer,
            ecoScoreCalculator,
            audioFeedbackManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test startRecording transitions state to true`() = runTest {
        // When
        tripRecorder.startRecording()
        advanceUntilIdle()
        
        // Then
        assertTrue(tripRecorder.isRecording.value)
        verify { sensorDataManager.startCollection() }
        coVerify { tripRepository.startTrip(any()) }
    }

    @Test
    fun `test stopRecording transitions state to false`() = runTest {
        // Given
        tripRecorder.startRecording()
        advanceUntilIdle()
        assertTrue(tripRecorder.isRecording.value)

        // When
        tripRecorder.stopRecording()
        advanceUntilIdle()
        
        // Then
        assertFalse(tripRecorder.isRecording.value)
        verify { sensorDataManager.stopCollection() }
        coVerify { tripRepository.endTrip(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `test startRecording does nothing if already recording`() = runTest {
        // Given
        tripRecorder.startRecording()
        advanceUntilIdle()
        
        // Use a new spy or just reset recording state carefully.
        // Actually, we can just verify that after the second call, the count is still 1.
        
        // When
        tripRecorder.startRecording()
        advanceUntilIdle()
        
        // Then
        assertTrue(tripRecorder.isRecording.value)
        // verify(exactly = 1) checks total calls since the beginning of the test
        verify(exactly = 1) { sensorDataManager.startCollection() }
        coVerify(exactly = 1) { tripRepository.startTrip(any()) }
    }
}
