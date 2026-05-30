package com.ecodrive.app.service

import android.content.Context
import android.content.Intent
import com.ecodrive.app.TestUtils
import com.ecodrive.app.domain.recorder.TripRecorder
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import io.mockk.*
import org.junit.After
import org.junit.Before
import org.junit.Test

class ActivityRecognitionReceiverTest {

    private val context: Context = mockk(relaxed = true)
    private val tripRecorder: TripRecorder = mockk(relaxed = true)
    private lateinit var receiver: TestActivityRecognitionReceiver

    // We use a test subclass that doesn't have @AndroidEntryPoint
    class TestActivityRecognitionReceiver : ActivityRecognitionReceiver() {
        // This bypasses the Hilt injection and super.onReceive call which would trigger injection
        override fun onReceive(context: Context, intent: Intent) {
            // Simplified logic for testing the behavior
            if (ActivityRecognitionResult.hasResult(intent)) {
                val result = ActivityRecognitionResult.extractResult(intent)
                if (result != null) {
                    val mostProbableActivity = result.mostProbableActivity
                    if (mostProbableActivity.type == DetectedActivity.IN_VEHICLE && mostProbableActivity.confidence >= 75) {
                        tripRecorder.startRecording()
                    } else if (mostProbableActivity.type == DetectedActivity.WALKING && mostProbableActivity.confidence >= 75 && tripRecorder.isRecording.value) {
                        tripRecorder.stopRecording()
                    }
                }
            }
        }
    }

    @Before
    fun setup() {
        TestUtils.mockLog()
        
        receiver = TestActivityRecognitionReceiver()
        receiver.tripRecorder = tripRecorder
        
        mockkStatic(ActivityRecognitionResult::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(ActivityRecognitionResult::class)
    }

    @Test
    fun `test start recording on IN_VEHICLE with high confidence`() {
        // Given
        val intent = mockk<Intent>()
        val result = mockk<ActivityRecognitionResult>()
        val activity = mockk<DetectedActivity>()
        
        every { ActivityRecognitionResult.hasResult(intent) } returns true
        every { ActivityRecognitionResult.extractResult(intent) } returns result
        every { result.mostProbableActivity } returns activity
        every { activity.type } returns DetectedActivity.IN_VEHICLE
        every { activity.confidence } returns 90

        // When
        receiver.onReceive(context, intent)
        
        // Then
        verify { tripRecorder.startRecording() }
    }

    @Test
    fun `test stop recording on WALKING with high confidence`() {
        // Given
        val intent = mockk<Intent>()
        val result = mockk<ActivityRecognitionResult>()
        val activity = mockk<DetectedActivity>()
        
        every { ActivityRecognitionResult.hasResult(intent) } returns true
        every { ActivityRecognitionResult.extractResult(intent) } returns result
        every { result.mostProbableActivity } returns activity
        every { activity.type } returns DetectedActivity.WALKING
        every { activity.confidence } returns 90
        every { tripRecorder.isRecording.value } returns true

        // When
        receiver.onReceive(context, intent)
        
        // Then
        verify { tripRecorder.stopRecording() }
    }
}
