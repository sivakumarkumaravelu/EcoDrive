package com.ecodrive.app.domain.service

import android.content.Context
import android.speech.tts.TextToSpeech
import com.ecodrive.app.domain.model.DrivingEvent
import com.ecodrive.app.domain.model.DrivingEventType
import io.mockk.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AudioFeedbackManagerTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var audioFeedbackManager: AudioFeedbackManager

    @Before
    fun setup() {
        mockkConstructor(TextToSpeech::class)
        // Simulate TTS init success callback
        every { anyConstructed<TextToSpeech>().setLanguage(any()) } returns TextToSpeech.LANG_COUNTRY_AVAILABLE
        audioFeedbackManager = AudioFeedbackManager(context)
    }

    @Test
    fun `test isAudioEnabled defaults to true`() = runTest {
        assertTrue(audioFeedbackManager.isAudioEnabled.first())
    }

    @Test
    fun `test setAudioEnabled to false emits false`() = runTest {
        audioFeedbackManager.setAudioEnabled(false)
        assertFalse(audioFeedbackManager.isAudioEnabled.first())
    }

    @Test
    fun `test setAudioEnabled to true emits true`() = runTest {
        audioFeedbackManager.setAudioEnabled(false)
        audioFeedbackManager.setAudioEnabled(true)
        assertTrue(audioFeedbackManager.isAudioEnabled.first())
    }

    @Test
    fun `test playEventFeedback does nothing when audio disabled`() {
        audioFeedbackManager.setAudioEnabled(false)
        val event = buildEvent(DrivingEventType.HARD_BRAKE)

        audioFeedbackManager.playEventFeedback(event)

        verify(exactly = 0) { anyConstructed<TextToSpeech>().speak(any(), any(), any(), any()) }
    }

    @Test
    fun `test playTip does nothing when audio disabled`() {
        audioFeedbackManager.setAudioEnabled(false)

        audioFeedbackManager.playTip("slow down")

        verify(exactly = 0) { anyConstructed<TextToSpeech>().speak(any(), any(), any(), any()) }
    }

    private fun buildEvent(type: DrivingEventType) = DrivingEvent(
        id = 1L,
        tripId = 1L,
        type = type,
        timestamp = Instant.now(),
        value = 1.0,
    )
}
