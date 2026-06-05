package com.ecodrive.app.di

import com.ecodrive.app.domain.ai.GeminiManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AiModule {

    @Provides
    @Singleton
    fun provideGeminiManager(): GeminiManager = GeminiManager()
}
