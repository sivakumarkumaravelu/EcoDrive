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
        // Duration = 60s. condition idleDuration > 60 is FALSE. 
        // Need T=61.1s for duration 60.1s. 
        val events61 = drivingPatternAnalyzer.analyze(createMetrics(speed = 0.0, isIdle = true, timestamp = Instant.ofEpochMilli(62100)), 1L)
        // Duration = 61.1s. 61.1 > 60 is TRUE. 61.1 % 30 is NOT 0.
        // The implementation uses idleDuration % 30 == 0L.
        // So we need exactly 90, 120, etc. (since it starts > 60)
        
        // At 90s idle (T=91s)
        val events91 = drivingPatternAnalyzer.analyze(createMetrics(speed = 0.0, isIdle = true, timestamp = Instant.ofEpochMilli(91000)), 1L)
        assertTrue("Expected idle event at 90s", events91.any { it.type == DrivingEventType.EXCESSIVE_IDLE })
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
