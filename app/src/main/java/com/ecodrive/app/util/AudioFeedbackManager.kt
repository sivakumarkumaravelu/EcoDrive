package com.ecodrive.app.util

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.speech.tts.TextToSpeech
import com.ecodrive.app.domain.model.DrivingEvent
import com.ecodrive.app.domain.model.DrivingEventType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages audio feedback for driving events, including both
 * Text-to-Speech coaching and subtle chime alerts.
 */
@Singleton
class AudioFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context,
) : TextToSpeech.OnInitListener {

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private val _isAudioEnabled = MutableStateFlow(true)
    val isAudioEnabled: StateFlow<Boolean> = _isAudioEnabled.asStateFlow()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts?.setLanguage(Locale.US)
            if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                isTtsReady = true
            }
        }
    }

    fun setAudioEnabled(enabled: Boolean) {
        _isAudioEnabled.update { enabled }
    }

    fun playEventFeedback(event: DrivingEvent) {
        if (!_isAudioEnabled.value || !isTtsReady) return

        val message = when (event.type) {
            DrivingEventType.HARD_BRAKE -> "Hard braking"
            DrivingEventType.HARD_ACCELERATION -> "Hard acceleration"
            DrivingEventType.SHARP_TURN -> "Sharp cornering"
            DrivingEventType.EXCESSIVE_SPEED -> "Speed warning"
            DrivingEventType.EXCESSIVE_IDLE -> "Consider turning off the engine"
            DrivingEventType.ECO_DRIVING -> "Excellent driving"
            else -> null
        }

        message?.let {
            tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "EcoEvent_${event.id}")
        }
    }

    fun playTip(tip: String) {
        if (!_isAudioEnabled.value || !isTtsReady) return
        tts?.speak(tip, TextToSpeech.QUEUE_FLUSH, null, "EcoTip")
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}
