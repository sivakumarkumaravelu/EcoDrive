package com.ecodrive.app.domain.ai.config

/**
 * Configuration for AI providers.
 * API keys should be provided via build-time secrets or environment variables.
 * Placeholders are used here for demonstration.
 */
object AiConfig {
    const val GEMINI_API_KEY = "YOUR_GEMINI_API_KEY_HERE"
    const val GROQ_API_KEY = "YOUR_GROQ_API_KEY_HERE"
    const val MISTRAL_API_KEY = "YOUR_MISTRAL_API_KEY_HERE"
    const val OPENROUTER_API_KEY = "YOUR_OPENROUTER_API_KEY_HERE"
    const val SAMBANOVA_API_KEY = "YOUR_SAMBANOVA_API_KEY_HERE"
    const val DEEPSEEK_API_KEY = "YOUR_DEEPSEEK_API_KEY_HERE"
    const val COHERE_API_KEY = "YOUR_COHERE_API_KEY_HERE"
    const val CLOUDFLARE_API_KEY = "YOUR_CLOUDFLARE_API_TOKEN_HERE"
    const val CLOUDFLARE_ACCOUNT_ID = "YOUR_CLOUDFLARE_ACCOUNT_ID_HERE"
    
    // Select exactly 3 to show in UI. 
    val UI_PROVIDERS = listOf("GEMINI", "GROQ", "LOCAL")

    // Default provider used if none is specified or available
    const val DEFAULT_PROVIDER = "GEMINI"
}
