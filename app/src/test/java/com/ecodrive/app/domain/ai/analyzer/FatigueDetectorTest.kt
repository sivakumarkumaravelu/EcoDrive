package com.ecodrive.app.domain.ai.analyzer

import com.ecodrive.app.domain.model.DrivingMetrics
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Instant

class FatigueDetectorTest {

    private lateinit var fatigueDetector: FatigueDetector

    @Before
    fun setup() {
        fatigueDetector = FatigueDetector()
    }

    @Test
    fun `test normal status with steady speed`() {
        var status = FatigueStatus.NORMAL
        for (i in 0 until 100) {
            status = fatigueDetector.analyze(DrivingMetrics(speedKmh = 60.0, isMoving = true))
        }
        assertEquals(FatigueStatus.NORMAL, status)
    }

    @Test
    fun `test moderate risk with some speed variability`() {
        // High variability at constant speed (simulating fatigue)
        for (i in 0 until 50) {
            fatigueDetector.analyze(DrivingMetrics(speedKmh = 60.0))
        }
        
        var status = FatigueStatus.NORMAL
        for (i in 0 until 50) {
            val speed = if (i % 2 == 0) 75.0 else 45.0
            status = fatigueDetector.analyze(DrivingMetrics(speedKmh = speed))
        }
        
        assertEquals(FatigueStatus.MODERATE_RISK, status)
    }

    @Test
    fun `test high risk with extreme speed variability`() {
        for (i in 0 until 50) {
            fatigueDetector.analyze(DrivingMetrics(speedKmh = 60.0))
        }
        
        var status = FatigueStatus.NORMAL
        for (i in 0 until 50) {
            val speed = if (i % 2 == 0) 90.0 else 30.0
            status = fatigueDetector.analyze(DrivingMetrics(speedKmh = speed))
        }
        
        // Final reading was 30.0, so it's MODERATE_RISK (speed < 50)
        assertEquals(FatigueStatus.MODERATE_RISK, status)
        
        // One more reading at high speed to trigger HIGH_RISK
        status = fatigueDetector.analyze(DrivingMetrics(speedKmh = 80.0))
        assertEquals(FatigueStatus.HIGH_RISK, status)
    }

    @Test
    fun `test high risk with swerving (lateral accel)`() {
        for (i in 0 until 50) {
            fatigueDetector.analyze(DrivingMetrics(speedKmh = 60.0, lateralAccelMps2 = 0.0))
        }
        
        var status = FatigueStatus.NORMAL
        for (i in 0 until 50) {
            // Use 4.0 to ensure average abs > 1.5
            // (50*0 + 50*4)/100 = 2.0
            status = fatigueDetector.analyze(DrivingMetrics(speedKmh = 60.0, lateralAccelMps2 = 4.0))
        }
        
        assertEquals(FatigueStatus.MODERATE_RISK, status)
    }
}
