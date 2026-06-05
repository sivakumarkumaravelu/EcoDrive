package com.ecodrive.app.domain.ai.config

import android.util.Log

object AiUtils {
    /**
     * Extracts a JSON block from a Gemini response string.
     * Handles markdown code blocks (```json ... ```).
     */
    fun extractJson(text: String): String? {
        val jsonStart = text.indexOf("{")
        val jsonEnd = text.lastIndexOf("}")
        
        if (jsonStart == -1 || jsonEnd == -1 || jsonEnd < jsonStart) return null
        
        return text.substring(jsonStart, jsonEnd + 1)
    }
}
