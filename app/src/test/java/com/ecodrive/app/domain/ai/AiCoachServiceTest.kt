package com.ecodrive.app.domain.ai

import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.EcoScore
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AiCoachServiceTest {

    private val geminiManager: GeminiManager = mockk()
    private val preferenceManager: PreferenceManager = mockk()
    private lateinit var aiCoachService: AiCoachService

    @Before
    fun setup() {
        aiCoachService = AiCoachService(geminiManager, preferenceManager)
    }

    @Test
    fun `test getRealTimeTip calls gemini when key is present`() = runTest {
        val apiKey = "test-api-key"
        every { preferenceManager.geminiApiKey } returns flowOf(apiKey)
        coEvery { geminiManager.generateRealTimeTip(apiKey, any()) } returns "Drive smoothly"

        val metrics = DrivingMetrics(speedKmh = 50.0)
        val score = EcoScore(overall = 80)
        
        val tip = aiCoachService.getRealTimeTip(metrics, score)
        
        assertEquals("Drive smoothly", tip)
        coVerify { geminiManager.generateRealTimeTip(apiKey, any()) }
    }

    @Test
    fun `test getRealTimeTip returns null when apiKey is blank`() = runTest {
        every { preferenceManager.geminiApiKey } returns flowOf("")
        
        val metrics = DrivingMetrics(speedKmh = 50.0)
        val score = EcoScore(overall = 80)
        
        val tip = aiCoachService.getRealTimeTip(metrics, score)
        
        assertEquals(null, tip)
        coVerify(exactly = 0) { geminiManager.generateRealTimeTip(any(), any()) }
    }
}
