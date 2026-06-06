package com.ecodrive.app.domain.ai.provider

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class AiProviderTest {

    private class TestProvider : AiProvider {
        override val name: String = "TestProvider"

        override suspend fun generateRealTimeTip(prompt: String, model: String?): String? {
            return "Tip: $prompt"
        }

        override suspend fun generateTripInsight(prompt: String, model: String?): String? {
            return "Insight: $prompt"
        }

        override suspend fun generateWeeklyReport(prompt: String, model: String?): String? {
            return "Report: $prompt"
        }

        override suspend fun generateAnalyticsSummary(prompt: String, model: String?): String? {
            return "Summary: $prompt"
        }
    }

    private val provider = TestProvider()

    @Test
    fun `test generatePrediction defaults to generateRealTimeTip`() = runTest {
        val result = provider.generatePrediction("data")
        assertEquals("Tip: data", result)
    }

    @Test
    fun `test generateConversationalResponse build correct prompt`() = runTest {
        val messages = listOf(
            "Hello" to true,
            "Hi there!" to false,
            "How am I driving?" to true
        )
        val systemPrompt = "You are a coach."
        
        val result = provider.generateConversationalResponse(messages, systemPrompt)
        
        val expectedPrompt = """
            You are a coach.

            Conversation History:
            User: Hello
            Coach: Hi there!

            User: How am I driving?

            Coach:
        """.trimIndent()
        
        assertEquals("Insight: $expectedPrompt", result)
    }
}
