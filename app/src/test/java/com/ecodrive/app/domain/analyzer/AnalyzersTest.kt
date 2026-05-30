package com.ecodrive.app.domain.analyzer

import com.ecodrive.app.data.local.dao.FuelCalibrationDao
import com.ecodrive.app.domain.model.DrivingEventType
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.FuelCalibrationPoint
import com.ecodrive.app.util.Constants
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Instant

class AnalyzersTest {

    private lateinit var drivingPatternAnalyzer: DrivingPatternAnalyzer
    private lateinit var fuelEstimationEngine: FuelEstimationEngine
    private lateinit var ecoScoreCalculator: EcoScoreCalculator
    private val fuelCalibrationDao: FuelCalibrationDao = mockk(relaxed = true)

    @Before
    fun setup() {
        drivingPatternAnalyzer = DrivingPatternAnalyzer()
        fuelEstimationEngine = FuelEstimationEngine(fuelCalibrationDao)
        ecoScoreCalculator = EcoScoreCalculator()
    }

    @Test
    fun `test hard braking detection`() {
        // Given
        val metrics = DrivingMetrics(
            timestamp = Instant.now(),
            speedKmh = 50.0,
            longitudinalAccelMps2 = -(Constants.HARD_BRAKE_THRESHOLD + 0.5), // Exceeds threshold
            isMoving = true
        )

        // When
        val events = drivingPatternAnalyzer.analyze(metrics, tripId = 1L)

        // Then
        assertTrue(events.any { it.type == DrivingEventType.HARD_BRAKE })
    }

    @Test
    fun `test hard acceleration detection`() {
        // Given
        val metrics = DrivingMetrics(
            timestamp = Instant.now(),
            speedKmh = 50.0,
            longitudinalAccelMps2 = Constants.HARD_ACCEL_THRESHOLD + 0.5, // Exceeds threshold
            isMoving = true
        )

        // When
        val events = drivingPatternAnalyzer.analyze(metrics, tripId = 1L)

        // Then
        assertTrue(events.any { it.type == DrivingEventType.HARD_ACCELERATION })
    }

    @Test
    fun `test sharp turn detection`() {
        // Given
        val metrics = DrivingMetrics(
            timestamp = Instant.now(),
            speedKmh = 50.0,
            lateralAccelMps2 = Constants.SHARP_TURN_THRESHOLD + 0.5, // Exceeds threshold
            isMoving = true
        )

        // When
        val events = drivingPatternAnalyzer.analyze(metrics, tripId = 1L)

        // Then
        assertTrue(events.any { it.type == DrivingEventType.SHARP_TURN })
    }

    @Test
    fun `test excessive speed detection`() {
        // Given
        val metrics = DrivingMetrics(
            timestamp = Instant.now(),
            speedKmh = Constants.SPEED_EXCESSIVE_KMH + 10.0, // Exceeds threshold
            isMoving = true
        )

        // When
        val events = drivingPatternAnalyzer.analyze(metrics, tripId = 1L)

        // Then
        assertTrue(events.any { it.type == DrivingEventType.EXCESSIVE_SPEED })
    }

    @Test
    fun `test fuel estimation at idle`() {
        // Given
        val speedMps = 0.0
        val accelerationMps2 = 0.0
        val roadGradePercent = 0.0

        // When
        val fuelRate = fuelEstimationEngine.estimateFuelRateLPerH(speedMps, accelerationMps2, roadGradePercent)

        // Then
        // Idle should be approx 0.5 L/H per the model logic
        assertEquals(0.5, fuelRate, 0.01)
    }

    @Test
    fun `test fuel calibration factor updates correctly`() {
        // Given
        val point1 = FuelCalibrationPoint(tripId = 1L, estimatedFuelLiters = 10.0, actualFuelLiters = 12.0, distanceKm = 50.0)
        val point2 = FuelCalibrationPoint(tripId = 2L, estimatedFuelLiters = 10.0, actualFuelLiters = 11.0, distanceKm = 60.0)
        val point3 = FuelCalibrationPoint(tripId = 3L, estimatedFuelLiters = 10.0, actualFuelLiters = 10.0, distanceKm = 55.0)

        // When
        fuelEstimationEngine.addCalibrationPoint(point1)
        fuelEstimationEngine.addCalibrationPoint(point2)
        fuelEstimationEngine.addCalibrationPoint(point3)
        
        // ratios: 1.2, 1.1, 1.0. Average: 1.1
        val newCalibrationFactor = fuelEstimationEngine.getCalibrationFactor()

        // Then
        assertEquals(1.1, newCalibrationFactor, 0.01)
    }

    @Test
    fun `test eco score perfect driving`() {
        // When
        val ecoScore = ecoScoreCalculator.calculate(
            hardBrakeCount = 0,
            hardAccelCount = 0,
            sharpTurnCount = 0,
            averageSpeedKmh = 70.0, // Eco range (50-90)
            idleTimePercent = 2.0, // Good
            speedStdDeviation = 2.0, // Good consistency
            tripDurationMinutes = 30.0
        )

        // Then
        assertEquals(100, ecoScore.overall)
        assertEquals(100, ecoScore.accelerationScore)
        assertEquals(100, ecoScore.brakingScore)
        assertEquals(100, ecoScore.speedScore)
        assertEquals(100, ecoScore.corneringScore)
        assertEquals(100, ecoScore.idleScore)
        assertEquals(100, ecoScore.consistencyScore)
    }

    @Test
    fun `test eco score poor driving`() {
        // When
        val ecoScore = ecoScoreCalculator.calculate(
            hardBrakeCount = 20,
            hardAccelCount = 20,
            sharpTurnCount = 10,
            averageSpeedKmh = 120.0, // Excessive
            idleTimePercent = 40.0, // Excessive
            speedStdDeviation = 30.0, // Poor consistency
            tripDurationMinutes = 10.0
        )

        // Then
        // Score should be heavily penalized across all metrics
        assertTrue(ecoScore.overall < 50)
        assertTrue(ecoScore.accelerationScore < 50)
        assertTrue(ecoScore.brakingScore < 50)
        assertTrue(ecoScore.speedScore < 50)
        assertTrue(ecoScore.corneringScore < 50)
        assertTrue(ecoScore.idleScore < 50)
        assertTrue(ecoScore.consistencyScore < 50)
    }
}
