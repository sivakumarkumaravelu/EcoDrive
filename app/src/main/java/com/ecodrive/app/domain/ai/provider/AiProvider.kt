package com.ecodrive.app.domain.ai.provider

import com.ecodrive.app.domain.ai.config.AiConfig

/**
 * Generic interface for AI content generation providers.
 */
interface AiProvider {
    /**
     * Display name of the provider.
     */
    val name: String

    /**
     * Generates a short real-time tip.
     */
    suspend fun generateRealTimeTip(prompt: String): String?

    /**
     * Generates a detailed trip insight.
     */
    suspend fun generateTripInsight(prompt: String): String?

    /**
     * Generates a multi-paragraph weekly report.
     */
    suspend fun generateWeeklyReport(prompt: String): String?

    /**
     * Generates an analytics summary.
     */
    suspend fun generateAnalyticsSummary(prompt: String): String?

    /**
     * Generates an eco-score prediction or any short predictive content.
     * Defaults to [generateRealTimeTip] for providers that don't implement it.
     */
    suspend fun generatePrediction(prompt: String): String? = generateRealTimeTip(prompt)

    /**
     * Generates a conversational response with multi-turn history context.
     * [messages] is a list of (text, isUser) pairs representing the conversation.
     * Providers that support native multi-turn (e.g. Gemini) should override this.
     * Default: concatenates history into the prompt for single-turn providers.
     */
    suspend fun generateConversationalResponse(
        messages: List<Pair<String, Boolean>>,
        systemPrompt: String,
    ): String? {
        // Default: build a contextual single-turn prompt from history
        val historyContext = messages.dropLast(1).joinToString("\n") { (text, isUser) ->
            if (isUser) "User: $text" else "Coach: $text"
        }
        val lastMessage = messages.lastOrNull()?.first ?: return null
        val fullPrompt = buildString {
            append(systemPrompt)
            if (historyContext.isNotBlank()) {
                append("\n\nConversation History:\n")
                append(historyContext)
            }
            append("\n\nUser: $lastMessage")
            append("\n\nCoach:")
        }
        return generateTripInsight(fullPrompt)
    }
}
