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
    private val testDispatcher = UnconfinedTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()
        
        tripRecorder = TripRecorder(
            context
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test startRecording calls context`() = runTest {
        // When
        tripRecorder.startRecording()
        
        // Then
        verify { context.startForegroundService(any()) }
    }

    @Test
    fun `test stopRecording calls context`() = runTest {
        // When
        tripRecorder.stopRecording()
        
        // Then
        verify { context.startForegroundService(any()) }
    }
}
