package com.ecodrive.app.domain.analyzer

import com.ecodrive.app.domain.model.*
import com.ecodrive.app.util.Constants
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

/**
 * Extended analyzer tests for edge cases and boundary conditions.
 */
class AnalyzersExtendedTest {

    private lateinit var drivingPatternAnalyzer: DrivingPatternAnalyzer

    @Before
    fun setup() {
        drivingPatternAnalyzer = DrivingPatternAnalyzer()
    }

    // ── Boundary Condition Tests ────────────────────────────────

    @Test
    fun `test hard brake exactly at threshold`() {
        val metrics = createMetrics(longAccel = -Constants.HARD_BRAKE_THRESHOLD)
        val events = drivingPatternAnalyzer.analyze(metrics, 1L)
        // Threshold is usually exclusive in implementation: metrics.longitudinalAccelMps2 < -Constants.HARD_BRAKE_THRESHOLD
        assertTrue(events.isEmpty())
    }

    @Test
    fun `test hard brake just over threshold`() {
        val metrics = createMetrics(longAccel = -Constants.HARD_BRAKE_THRESHOLD - 0.1)
        val events = drivingPatternAnalyzer.analyze(metrics, 1L)
        assertTrue(events.any { it.type == DrivingEventType.HARD_BRAKE })
    }

    @Test
    fun `test hard accel exactly at threshold`() {
        val metrics = createMetrics(longAccel = Constants.HARD_ACCEL_THRESHOLD)
        val events = drivingPatternAnalyzer.analyze(metrics, 1L)
        assertFalse(events.any { it.type == DrivingEventType.HARD_ACCELERATION })
    }

    @Test
    fun `test hard accel just over threshold`() {
        val metrics = createMetrics(longAccel = Constants.HARD_ACCEL_THRESHOLD + 0.1)
        val events = drivingPatternAnalyzer.analyze(metrics, 1L)
        assertTrue(events.any { it.type == DrivingEventType.HARD_ACCELERATION })
    }

    @Test
    fun `test sharp turn exactly at threshold`() {
        val metrics = createMetrics(latAccel = Constants.SHARP_TURN_THRESHOLD)
        val events = drivingPatternAnalyzer.analyze(metrics, 1L)
        assertFalse(events.any { it.type == DrivingEventType.SHARP_TURN })
    }

    @Test
    fun `test sharp turn just over threshold`() {
        val metrics = createMetrics(latAccel = Constants.SHARP_TURN_THRESHOLD + 0.1)
        val events = drivingPatternAnalyzer.analyze(metrics, 1L)
        assertTrue(events.any { it.type == DrivingEventType.SHARP_TURN })
    }

    @Test
    fun `test excessive speed exactly at threshold`() {
        val metrics = createMetrics(speed = Constants.SPEED_EXCESSIVE_KMH)
        val events = drivingPatternAnalyzer.analyze(metrics, 1L)
        assertFalse(events.any { it.type == DrivingEventType.EXCESSIVE_SPEED })
    }

    @Test
    fun `test excessive speed just over threshold`() {
        val metrics = createMetrics(speed = Constants.SPEED_EXCESSIVE_KMH + 1.0)
        val events = drivingPatternAnalyzer.analyze(metrics, 1L)
        assertTrue(events.any { it.type == DrivingEventType.EXCESSIVE_SPEED })
    }

    // ── Idle Detection Tests ────────────────────────────────────

    @Test
    fun `test excessive idle warning at interval`() {
        // First idle event at T=1s
        drivingPatternAnalyzer.analyze(createMetrics(speed = 0.0, isIdle = true, timestamp = Instant.ofEpochMilli(1000)), 1L)
        
        // After 60s (Constants.IDLE_WARNING_SECONDS = 60)
        // Duration = 61.1s. idleDuration will be 61L (due to integer division 61100/1000). 61 > 60 is TRUE.
        // It should trigger the first warning.
        val events61 = drivingPatternAnalyzer.analyze(createMetrics(speed = 0.0, isIdle = true, timestamp = Instant.ofEpochMilli(62100)), 1L)
        assertTrue("Expected initial idle warning > 60s", events61.any { it.type == DrivingEventType.EXCESSIVE_IDLE })
        
        // Duration = 91.1s (90.1s since start). idleDuration will be 91L. 91 - 60 = 31 >= 30.
        // It should trigger the second warning.
        val events91 = drivingPatternAnalyzer.analyze(createMetrics(speed = 0.0, isIdle = true, timestamp = Instant.ofEpochMilli(92100)), 1L)
        assertTrue("Expected idle event at ~90s", events91.any { it.type == DrivingEventType.EXCESSIVE_IDLE })
    }

    @Test
    fun `test multiple simultaneous thresholds`() {
        val metrics = createMetrics(
            speed = Constants.SPEED_EXCESSIVE_KMH + 1.0,
            longAccel = -Constants.HARD_BRAKE_THRESHOLD - 0.1,
            latAccel = Constants.SHARP_TURN_THRESHOLD + 0.1
        )
        val events = drivingPatternAnalyzer.analyze(metrics, 1L)
        assertTrue("Expected EXCESSIVE_SPEED", events.any { it.type == DrivingEventType.EXCESSIVE_SPEED })
        assertTrue("Expected HARD_BRAKE", events.any { it.type == DrivingEventType.HARD_BRAKE })
        assertTrue("Expected SHARP_TURN", events.any { it.type == DrivingEventType.SHARP_TURN })
    }

    @Test
    fun `test idle warning resets when speed increases`() {
        drivingPatternAnalyzer.analyze(createMetrics(speed = 0.0, isIdle = true, timestamp = Instant.ofEpochMilli(1000)), 1L)
        
        val events61 = drivingPatternAnalyzer.analyze(createMetrics(speed = 0.0, isIdle = true, timestamp = Instant.ofEpochMilli(62100)), 1L)
        assertTrue(events61.any { it.type == DrivingEventType.EXCESSIVE_IDLE })

        // Speed increases
        drivingPatternAnalyzer.analyze(createMetrics(speed = 10.0, isIdle = false, timestamp = Instant.ofEpochMilli(63100)), 1L)

        // Idle again for 10s (should not trigger)
        val eventsNew = drivingPatternAnalyzer.analyze(createMetrics(speed = 0.0, isIdle = true, timestamp = Instant.ofEpochMilli(73100)), 1L)
        assertTrue(eventsNew.isEmpty())
    }

    private fun createMetrics(
        speed: Double = 0.0,
        longAccel: Double = 0.0,
        latAccel: Double = 0.0,
        isIdle: Boolean = false,
        timestamp: Instant = Instant.now()
    ) = DrivingMetrics(
        timestamp = timestamp,
        speedKmh = speed,
        longitudinalAccelMps2 = longAccel,
        lateralAccelMps2 = latAccel,
        isMoving = speed > 3.0,
        isIdle = isIdle
    )
}
