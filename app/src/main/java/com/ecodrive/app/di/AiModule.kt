package com.ecodrive.app.di

import com.ecodrive.app.domain.ai.provider.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AiModule {

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindGeminiProvider(provider: GeminiProvider): AiProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindGroqProvider(provider: GroqProvider): AiProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindMistralProvider(provider: MistralProvider): AiProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindOpenRouterProvider(provider: OpenRouterProvider): AiProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindSambaNovaProvider(provider: SambaNovaProvider): AiProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindDeepSeekProvider(provider: DeepSeekProvider): AiProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindCohereProvider(provider: CohereProvider): AiProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindCloudflareProvider(provider: CloudflareProvider): AiProvider

    @Binds
    @IntoSet
    @Singleton
    abstract fun bindLocalProvider(provider: LocalProvider): AiProvider
}
