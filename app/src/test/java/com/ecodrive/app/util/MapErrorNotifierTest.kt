package com.ecodrive.app.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MapErrorNotifierTest {

    @Test
    fun `test triggerFallback emits event`() = runTest(UnconfinedTestDispatcher()) {
        var eventReceived = false
        val job = launch {
            MapErrorNotifier.fallbackEvent.first()
            eventReceived = true
        }

        MapErrorNotifier.triggerFallback()
        job.join()
        
        assertTrue(eventReceived)
    }
}
