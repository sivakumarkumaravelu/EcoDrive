package com.ecodrive.app.domain.service

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
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
import kotlinx.coroutines.launch

/**
 * Manages audio feedback for driving events, including both
 * Text-to-Speech coaching and chime-style safety alerts.
 *
 * Design choices:
 * - Safety alerts use QUEUE_FLUSH so they cut through immediately.
 * - Coaching tips use QUEUE_ADD with a max pending cap of 2 so tips
 *   never pile up and overlap mid-utterance.
 * - AudioAttributes are set to USAGE_ASSISTANCE_NAVIGATION_GUIDANCE so
 *   the OS properly ducks music and routes audio through car speakers.
 * - Best available on-device voice is selected on init (Google high-quality
 *   voices preferred over manufacturer defaults).
 * - All text is run through sanitizeForTts() which strips emoji/symbols
 *   and expands common abbreviations to pronounceable words.
 */
@Singleton
class AudioFeedbackManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: com.ecodrive.app.data.local.PreferenceManager,
    @com.ecodrive.app.di.ApplicationScope private val scope: kotlinx.coroutines.CoroutineScope
) : TextToSpeech.OnInitListener {

    companion object {
        private const val TAG = "AudioFeedbackManager"

        /** How many coaching tips may be queued at once before being dropped. */
        private const val MAX_TIP_QUEUE = 2

        /**
         * Voice name substrings ranked by preference.
         * Google ships several high-quality voices; the "x-iom" / "x-sfg" / "x-iob"
         * variants are markedly better than the fallback default.
         */
        private val PREFERRED_VOICE_PATTERNS = listOf(
            "x-iom-network",  // Google enhanced male (best, requires network)
            "x-sfg-network",  // Google enhanced female (best, requires network)
            "x-iob-network",  // Google enhanced alt (requires network)
            "x-iom-local",    // Google high-quality male (fully offline)
            "x-sfg-local",    // Google high-quality female (fully offline)
            "x-iob-local",    // Google high-quality alt (fully offline)
        )

        // Note: AudioAttributes is NOT pre-built here because static companion object
        // initializers run at class-load time, causing NoClassDefFoundError in JVM unit tests
        // where the Android framework is unavailable. It is built lazily inside onInit() instead.

        // Unit and abbreviation expansions — order matters (longer patterns first).
        private val UNIT_EXPANSIONS = listOf(
            Regex("km/h", RegexOption.IGNORE_CASE) to "kilometers per hour",
            Regex("mph", RegexOption.IGNORE_CASE) to "miles per hour",
            Regex("m/s²") to "meters per second squared",
            Regex("m/s2") to "meters per second squared",
            Regex("m/s", RegexOption.IGNORE_CASE) to "meters per second",
            Regex("L/100km", RegexOption.IGNORE_CASE) to "liters per 100 kilometers",
            Regex("kWh", RegexOption.IGNORE_CASE) to "kilowatt hours",
            Regex("\\bkm\\b", RegexOption.IGNORE_CASE) to "kilometers",
            Regex("\\bmi\\b", RegexOption.IGNORE_CASE) to "miles",
            Regex("\\bL\\b") to "liters",
            Regex("\\bmpg\\b", RegexOption.IGNORE_CASE) to "miles per gallon",
            // Expand common acronyms
            Regex("\\bECO\\b") to "Eco",
            Regex("\\bAI\\b") to "A I",
        )

        // Strip everything that is not a printable ASCII / Latin-Extended character,
        // plus a small set of safe punctuation for natural phrasing.
        private val UNSAFE_CHAR_REGEX = Regex("[^\\u0020-\\u007E\\u00C0-\\u024F]")
        // Collapse runs of whitespace left after stripping.
        private val MULTI_SPACE_REGEX = Regex("\\s{2,}")
    }

    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    /** Tracks how many tip utterances are currently pending. */
    private var pendingTipCount = 0

    private val _isAudioEnabled = MutableStateFlow(true)
    val isAudioEnabled: StateFlow<Boolean> = _isAudioEnabled.asStateFlow()

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) {
            Log.w(TAG, "TTS init failed with status $status")
            return
        }

        val result = tts?.setLanguage(Locale.US)
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            Log.w(TAG, "TTS language not supported: $result")
            return
        }

        // Tune voice quality: slightly slower and natural pitch for clarity while driving.
        tts?.setSpeechRate(0.92f)
        tts?.setPitch(1.0f)

        // Route audio through navigation channel (car speakers, music ducking).
        // Built here (not in companion object) so the class can be loaded in JVM unit tests.
        try {
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build()
            tts?.setAudioAttributes(audioAttrs)
        } catch (e: Exception) {
            Log.w(TAG, "Could not configure audio attributes: ${e.message}")
        }

        // Wait until TTS is ready to apply voice preference
        isTtsReady = true
        
        scope.launch {
            preferenceManager.coachVoice.collect { voiceType ->
                applyVoicePreference(voiceType)
            }
        }
        Log.i(TAG, "TTS ready.")
    }

    fun setAudioEnabled(enabled: Boolean) {
        _isAudioEnabled.update { enabled }
    }

    /**
     * Plays an immediate safety/event alert. Uses QUEUE_FLUSH to cut through
     * any in-progress speech — appropriate for hard braking, sharp turns, etc.
     */
    fun playEventFeedback(event: DrivingEvent) {
        if (!_isAudioEnabled.value || !isTtsReady) return

        val message = when (event.type) {
            DrivingEventType.HARD_BRAKE -> "Hard braking detected"
            DrivingEventType.HARD_ACCELERATION -> "Hard acceleration detected"
            DrivingEventType.SHARP_TURN -> "Sharp cornering detected"
            DrivingEventType.EXCESSIVE_SPEED -> "Speed warning"
            DrivingEventType.EXCESSIVE_IDLE -> "Consider turning off the engine"
            DrivingEventType.ECO_DRIVING -> "Excellent driving, keep it up"
            else -> null
        } ?: return

        // Safety events always interrupt — flush the queue.
        pendingTipCount = 0
        tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, "EcoEvent_${event.id}")
    }

    /**
     * Plays an urgent safety alert (fatigue, anomaly) with QUEUE_FLUSH priority.
     * Call this instead of [playTip] when the message is safety-critical.
     */
    fun playSafetyAlert(message: String) {
        if (!_isAudioEnabled.value || !isTtsReady) return
        val sanitized = sanitizeForTts(message)
        if (sanitized.isBlank()) return

        pendingTipCount = 0
        tts?.speak(sanitized, TextToSpeech.QUEUE_FLUSH, null, "EcoAlert_${System.currentTimeMillis()}")
    }

    /**
     * Plays a coaching tip using QUEUE_ADD. Tips never interrupt each other, but
     * at most [MAX_TIP_QUEUE] tips will be queued — extras are silently dropped to
     * prevent a backlog of stale advice building up during a busy driving event burst.
     */
    fun playTip(tip: String) {
        if (!_isAudioEnabled.value || !isTtsReady) return
        if (pendingTipCount >= MAX_TIP_QUEUE) {
            Log.d(TAG, "Tip queue full — dropping: ${tip.take(40)}")
            return
        }

        val sanitized = sanitizeForTts(tip)
        if (sanitized.isBlank()) return

        pendingTipCount++
        tts?.speak(sanitized, TextToSpeech.QUEUE_ADD, null, "EcoTip_${System.currentTimeMillis()}")

        // Decrement counter when this utterance finishes.
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("EcoTip") == true) {
                    pendingTipCount = (pendingTipCount - 1).coerceAtLeast(0)
                }
            }
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                if (utteranceId?.startsWith("EcoTip") == true) {
                    pendingTipCount = (pendingTipCount - 1).coerceAtLeast(0)
                }
            }
        })
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
        pendingTipCount = 0
    }

    // ── Private helpers ─────────────────────────────────────────────────────

    /**
     * Sanitizes text for clean TTS pronunciation:
     * 1. Strips all emoji, symbols, and non-Latin unicode (prevents noise artifacts).
     * 2. Expands common units/abbreviations to full spoken words.
     * 3. Normalises whitespace.
     */
    internal fun sanitizeForTts(text: String): String {
        var result = text

        // Step 1: Strip unsafe characters (emoji, symbols, non-Latin script).
        result = result.replace(UNSAFE_CHAR_REGEX, " ")

        // Step 2: Expand units and abbreviations before further cleanup.
        for ((pattern, replacement) in UNIT_EXPANSIONS) {
            result = result.replace(pattern, replacement)
        }

        // Step 3: Strip any residual non-printable / non-speech punctuation.
        // Use empty string (not space) because markdown markers like ** and * always wrap
        // words — replacing with space would produce "Tip :" instead of "Tip:".
        result = result.replace(Regex("[*_~`#@^|\\\\<>{}\\[\\]+=]"), "")

        // Step 4: Collapse multiple spaces introduced by the above steps.
        result = result.replace(MULTI_SPACE_REGEX, " ").trim()

        return result
    }

    /**
     * Applies the selected voice preference (JARVIS, FRIDAY, or DEFAULT).
     */
    fun applyVoicePreference(voiceType: String) {
        if (!isTtsReady) return
        val voices: Set<Voice>? = tts?.voices
        if (voices.isNullOrEmpty()) return

        val englishVoices = voices.filter { voice ->
            voice.locale?.language == Locale.ENGLISH.language && !voice.isNetworkConnectionRequired.let {
                false
            }
        }

        var selectedVoice: Voice? = null
        when (voiceType) {
            "JARVIS" -> {
                // Try to find a UK Male voice
                selectedVoice = englishVoices.firstOrNull { 
                    it.locale?.country == "GB" && (it.name.contains("-x-rjs", ignoreCase = true) || it.name.contains("male", ignoreCase = true)) 
                } ?: englishVoices.firstOrNull { it.locale?.country == "GB" }
            }
            "FRIDAY" -> {
                // Try to find a UK Female voice
                selectedVoice = englishVoices.firstOrNull { 
                    it.locale?.country == "GB" && (it.name.contains("-x-gba", ignoreCase = true) || it.name.contains("-x-gbc", ignoreCase = true) || it.name.contains("female", ignoreCase = true)) 
                } ?: englishVoices.firstOrNull { it.locale?.country == "GB" }
            }
        }

        // Fallback to DEFAULT logic if no specific voice or 'DEFAULT' selected
        if (selectedVoice == null) {
            for (pattern in PREFERRED_VOICE_PATTERNS) {
                selectedVoice = englishVoices.firstOrNull { it.name.contains(pattern, ignoreCase = true) }
                if (selectedVoice != null) break
            }
            if (selectedVoice == null) {
                selectedVoice = englishVoices
                    .filter { Voice.QUALITY_VERY_HIGH == it.quality || Voice.QUALITY_HIGH == it.quality }
                    .minByOrNull { it.latency }
            }
        }

        if (selectedVoice != null) {
            tts?.voice = selectedVoice
            Log.i(TAG, "Selected voice: ${selectedVoice.name} for preference: $voiceType")
        }
    }
}
