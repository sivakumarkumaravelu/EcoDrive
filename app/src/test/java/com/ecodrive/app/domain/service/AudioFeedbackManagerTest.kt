package com.ecodrive.app.domain.service

import android.content.Context
import android.speech.tts.TextToSpeech
import com.ecodrive.app.domain.model.DrivingEvent
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.data.local.PreferenceManager
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.cancel
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AudioFeedbackManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val liveCoachingFlow = MutableStateFlow(true)
    private lateinit var audioFeedbackManager: AudioFeedbackManager

    @Before
    fun setup() {
        mockkConstructor(TextToSpeech::class)
        every { anyConstructed<TextToSpeech>().setLanguage(any()) } returns TextToSpeech.LANG_COUNTRY_AVAILABLE
        every { anyConstructed<TextToSpeech>().setSpeechRate(any()) } returns TextToSpeech.SUCCESS
        every { anyConstructed<TextToSpeech>().setPitch(any()) } returns TextToSpeech.SUCCESS
        every { anyConstructed<TextToSpeech>().setAudioAttributes(any()) } returns TextToSpeech.SUCCESS
        every { anyConstructed<TextToSpeech>().voices } returns null
        every { anyConstructed<TextToSpeech>().setOnUtteranceProgressListener(any()) } returns 0
        every {
            anyConstructed<TextToSpeech>().speak(any<String>(), any<Int>(), any(), any<String>())
        } returns TextToSpeech.SUCCESS

        every { preferenceManager.liveCoachingEnabled } returns liveCoachingFlow
        every { preferenceManager.coachVoice } returns MutableStateFlow("DEFAULT")
        coEvery { preferenceManager.setLiveCoachingEnabled(any()) } answers {
            liveCoachingFlow.value = firstArg()
        }
        
        audioFeedbackManager = AudioFeedbackManager(context, preferenceManager, kotlinx.coroutines.test.TestScope())
    }

    private fun initManager() {
        audioFeedbackManager.onInit(TextToSpeech.SUCCESS)
    }

    @Test
    fun `test isAudioEnabled defaults to true`() = runTest {
        liveCoachingFlow.value = true
        val manager = AudioFeedbackManager(context, preferenceManager, this)
        manager.onInit(TextToSpeech.SUCCESS)
        runCurrent()
        assertTrue(manager.isAudioEnabled.first())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `test setAudioEnabled to false emits false`() = runTest {
        liveCoachingFlow.value = true
        val manager = AudioFeedbackManager(context, preferenceManager, this)
        manager.onInit(TextToSpeech.SUCCESS)
        runCurrent()
        manager.setAudioEnabled(false)
        runCurrent()
        assertFalse(manager.isAudioEnabled.first())
        coroutineContext.cancelChildren()
    }

    @Test
    fun `test setAudioEnabled to true emits true`() = runTest {
        liveCoachingFlow.value = true
        val manager = AudioFeedbackManager(context, preferenceManager, this)
        manager.onInit(TextToSpeech.SUCCESS)
        runCurrent()
        manager.setAudioEnabled(false)
        runCurrent()
        manager.setAudioEnabled(true)
        runCurrent()
        assertTrue(manager.isAudioEnabled.first())
        coroutineContext.cancelChildren()
    }

    // ── Audio-disabled guards ─────────────────────────────────────────────

    @Test
    fun `test playEventFeedback does nothing when audio disabled`() = runTest {
        liveCoachingFlow.value = true
        val manager = AudioFeedbackManager(context, preferenceManager, this)
        manager.onInit(TextToSpeech.SUCCESS)
        runCurrent()
        manager.setAudioEnabled(false)
        runCurrent()
        manager.playEventFeedback(buildEvent(DrivingEventType.HARD_BRAKE))
        verify(exactly = 0) { anyConstructed<TextToSpeech>().speak(any(), any(), any(), any()) }
        coroutineContext.cancelChildren()
    }

    @Test
    fun `test playTip does nothing when audio disabled`() = runTest {
        liveCoachingFlow.value = true
        val manager = AudioFeedbackManager(context, preferenceManager, this)
        manager.onInit(TextToSpeech.SUCCESS)
        runCurrent()
        manager.setAudioEnabled(false)
        runCurrent()
        manager.playTip("slow down")
        verify(exactly = 0) { anyConstructed<TextToSpeech>().speak(any(), any(), any(), any()) }
        coroutineContext.cancelChildren()
    }

    @Test
    fun `test playSafetyAlert does nothing when audio disabled`() = runTest {
        liveCoachingFlow.value = true
        val manager = AudioFeedbackManager(context, preferenceManager, this)
        manager.onInit(TextToSpeech.SUCCESS)
        runCurrent()
        manager.setAudioEnabled(false)
        runCurrent()
        manager.playSafetyAlert("High fatigue risk detected.")
        verify(exactly = 0) { anyConstructed<TextToSpeech>().speak(any(), any(), any(), any()) }
        coroutineContext.cancelChildren()
    }

    // ── sanitizeForTts ────────────────────────────────────────────────────

    @Test
    fun `sanitizeForTts strips simple emoji`() {
        val result = audioFeedbackManager.sanitizeForTts("\uD83E\uDD16 Slow down! \uD83D\uDC22")
        assertEquals("Slow down!", result)
    }

    @Test
    fun `sanitizeForTts strips warning sign variation selector emoji`() {
        // U+26A0 (warning sign) + U+FE0F (variation selector-16) form the ⚠️ emoji
        val result = audioFeedbackManager.sanitizeForTts("\u26A0\uFE0F Watch out!")
        assertEquals("Watch out!", result)
    }

    @Test
    fun `sanitizeForTts expands km per h unit`() {
        val result = audioFeedbackManager.sanitizeForTts("Reduce speed to 50 km/h")
        assertEquals("Reduce speed to 50 kilometers per hour", result)
    }

    @Test
    fun `sanitizeForTts expands mph unit`() {
        val result = audioFeedbackManager.sanitizeForTts("You are doing 30 mph")
        assertEquals("You are doing 30 miles per hour", result)
    }

    @Test
    fun `sanitizeForTts expands m per s squared written as m over s2`() {
        val result = audioFeedbackManager.sanitizeForTts("Acceleration: 3.2 m/s2")
        assertEquals("Acceleration: 3.2 meters per second squared", result)
    }

    @Test
    fun `sanitizeForTts strips markdown bold and italic markers`() {
        val result = audioFeedbackManager.sanitizeForTts("**Tip**: Ease off the *gas*")
        assertEquals("Tip: Ease off the gas", result)
    }

    @Test
    fun `sanitizeForTts returns blank for emoji-only input`() {
        val result = audioFeedbackManager.sanitizeForTts("\uD83E\uDD16\uD83D\uDC22")
        assertTrue("Expected blank but got: '$result'", result.isBlank())
    }

    @Test
    fun `sanitizeForTts collapses multiple spaces`() {
        val result = audioFeedbackManager.sanitizeForTts("Slow   down   please")
        assertEquals("Slow down please", result)
    }

    // ── Queue behaviour ───────────────────────────────────────────────────

    @Test
    fun `playTip speaks via QUEUE_ADD`() {
        initManager()
        audioFeedbackManager.playTip("Ease off the accelerator")

        verify(exactly = 1) {
            anyConstructed<TextToSpeech>().speak(
                eq("Ease off the accelerator"),
                eq(TextToSpeech.QUEUE_ADD),
                any(),
                any()
            )
        }
    }

    @Test
    fun `playSafetyAlert speaks via QUEUE_FLUSH`() {
        initManager()
        audioFeedbackManager.playSafetyAlert("High fatigue risk detected. Please take a break.")

        verify(exactly = 1) {
            anyConstructed<TextToSpeech>().speak(
                eq("High fatigue risk detected. Please take a break."),
                eq(TextToSpeech.QUEUE_FLUSH),
                any(),
                any()
            )
        }
    }

    @Test
    fun `playEventFeedback speaks via QUEUE_FLUSH`() {
        initManager()
        audioFeedbackManager.playEventFeedback(buildEvent(DrivingEventType.HARD_BRAKE))

        verify(exactly = 1) {
            anyConstructed<TextToSpeech>().speak(
                eq("Hard braking detected"),
                eq(TextToSpeech.QUEUE_FLUSH),
                any(),
                any()
            )
        }
    }

    @Test
    fun `playTip drops third tip when queue is full`() {
        initManager()

        // Fill the queue to MAX_TIP_QUEUE (2)
        audioFeedbackManager.playTip("First tip")
        audioFeedbackManager.playTip("Second tip")
        // This should be dropped silently
        audioFeedbackManager.playTip("Third tip — should be dropped")

        // Only 2 QUEUE_ADD speak() calls should have been made
        verify(exactly = 2) {
            anyConstructed<TextToSpeech>().speak(any<String>(), eq(TextToSpeech.QUEUE_ADD), any(), any())
        }
    }

    @Test
    fun `playTip sanitizes emoji before speaking`() {
        initManager()
        audioFeedbackManager.playTip("\uD83E\uDD16 Slow down!")

        verify(exactly = 1) {
            anyConstructed<TextToSpeech>().speak(
                eq("Slow down!"),
                eq(TextToSpeech.QUEUE_ADD),
                any(),
                any()
            )
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private fun buildEvent(type: DrivingEventType) = DrivingEvent(
        id = 1L,
        tripId = 1L,
        type = type,
        timestamp = Instant.now(),
        value = 1.0,
    )
}
