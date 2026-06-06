package com.ecodrive.app.domain.ai.config

import com.ecodrive.app.BuildConfig

/**
 * Configuration for AI providers.
 * API keys are provided via local.properties (local development) or environment variables (CI/CD).
 * These are accessed through the generated BuildConfig class.
 */
object AiConfig {
    val GEMINI_API_KEY: String = BuildConfig.GEMINI_API_KEY
    val GROQ_API_KEY: String = BuildConfig.GROQ_API_KEY
    val MISTRAL_API_KEY: String = BuildConfig.MISTRAL_API_KEY
    val OPENROUTER_API_KEY: String = BuildConfig.OPENROUTER_API_KEY
    val SAMBANOVA_API_KEY: String = BuildConfig.SAMBANOVA_API_KEY
    val DEEPSEEK_API_KEY: String = BuildConfig.DEEPSEEK_API_KEY
    val COHERE_API_KEY: String = BuildConfig.COHERE_API_KEY
    val CLOUDFLARE_API_KEY: String = BuildConfig.CLOUDFLARE_API_KEY
    val CLOUDFLARE_ACCOUNT_ID: String = BuildConfig.CLOUDFLARE_ACCOUNT_ID
    
    // Providers to show in the settings UI.
    val UI_PROVIDERS = listOf("GEMINI", "GROQ", "MISTRAL", "SAMBANOVA", "DEEPSEEK", "LOCAL")

    // Default provider used if none is specified or available
    const val DEFAULT_PROVIDER = "GEMINI"
}
