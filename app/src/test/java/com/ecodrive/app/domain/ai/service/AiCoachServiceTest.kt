package com.ecodrive.app.domain.ai.service

import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.WeatherApiClient
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.EcoScore
import com.ecodrive.app.domain.model.WeatherContext
import io.mockk.*
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class AiCoachServiceTest {

    private val aiManager: AiManager = mockk()
    private val preferenceManager: PreferenceManager = mockk()
    private val weatherApiClient: WeatherApiClient = mockk()
    private lateinit var aiCoachService: AiCoachService

    @Before
    fun setup() {
        aiCoachService = AiCoachService(aiManager, preferenceManager, weatherApiClient)
    }

    @Test
    fun `test getRealTimeTip calls aiManager with weather context`() = runTest {
        coEvery { aiManager.generateRealTimeTip(any()) } returns "Drive smoothly"
        coEvery { weatherApiClient.getWeatherContext(any(), any(), any()) } returns WeatherContext(
            tempC = 20.0,
            conditionCode = 800,
            conditionLabel = "Clear",
            windSpeedKmh = 10.0,
        )
        
        val metrics = DrivingMetrics(speedKmh = 50.0, latitude = 0.0, longitude = 0.0)
        val score = EcoScore(overall = 80)
        
        val tip = aiCoachService.getRealTimeTip(metrics, score)
        
        assertEquals("Drive smoothly", tip)
        coVerify { aiManager.generateRealTimeTip(any()) }
        coVerify { weatherApiClient.getWeatherContext(any(), any(), any()) }
    }
}
