package com.ecodrive.app.domain.ai.service

import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.WeatherApiClient
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.EcoScore
import com.ecodrive.app.domain.model.WeatherContext
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class AiPromptUnitTest {

    private val aiManager = mockk<AiManager>(relaxed = true)
    private val preferenceManager = mockk<PreferenceManager>()
    private val weatherApiClient = mockk<WeatherApiClient>()

    @Test
    fun `test real-time tip prompt uses mph when metric is disabled`() = runBlocking {
        // Given
        coEvery { preferenceManager.useMetricUnits } returns flowOf(false)
        coEvery { weatherApiClient.getWeatherContext(any(), any()) } returns WeatherContext()
        
        val service = AiCoachService(aiManager, preferenceManager, weatherApiClient)
        val metrics = DrivingMetrics(
            timestamp = Instant.now(),
            speedKmh = 100.0, // Should become ~62.1 mph
            latitude = 0.0,
            longitude = 0.0
        )
        val ecoScore = EcoScore(overall = 85)

        var capturedPrompt = ""
        coEvery { aiManager.generateRealTimeTip(any()) } answers {
            capturedPrompt = firstArg()
            "Mock Tip"
        }

        // When
        service.getRealTimeTip(metrics, ecoScore)

        // Then
        assertTrue("Prompt should mention mph", capturedPrompt.contains("mph"))
        assertTrue("Prompt should mention 62.1", capturedPrompt.contains("62.1"))
    }
}
