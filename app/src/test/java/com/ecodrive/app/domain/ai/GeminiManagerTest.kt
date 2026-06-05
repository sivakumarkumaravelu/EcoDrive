package com.ecodrive.app.domain.ai

import com.ecodrive.app.TestUtils
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class GeminiManagerTest {

    private lateinit var geminiManager: GeminiManager

    @Before
    fun setup() {
        TestUtils.mockLog()
        geminiManager = GeminiManager()
    }

    @Test
    fun `test isCoachingRateLimited returns false initially`() {
        assertFalse(geminiManager.isCoachingRateLimited())
    }

    @Test
    fun `test generateRealTimeTip returns null if apiKey is blank`() = runTest {
        val result = geminiManager.generateRealTimeTip("", "test prompt")
        assertNull(result)
    }

    @Test
    fun `test invalidateCache clears cached models`() = runTest {
        // This is mostly verifying it doesn't crash as we can't easily see private modelCache
        geminiManager.invalidateCache()
    }
}
