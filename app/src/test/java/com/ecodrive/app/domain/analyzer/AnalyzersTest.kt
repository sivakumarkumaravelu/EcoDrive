package com.ecodrive.app.domain.analyzer

import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.local.dao.FuelCalibrationDao
import com.ecodrive.app.domain.ai.service.AiManager
import com.ecodrive.app.domain.ai.analyzer.FuelPredictionModel
import com.ecodrive.app.domain.model.*
import com.ecodrive.app.util.Constants
import io.mockk.every
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
    private val aiManager: AiManager = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val mlModel: FuelPredictionModel = mockk(relaxed = true)
    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
    
    private val iceVehicle = Vehicle(
        name = "ICE Car",
        massKg = 1500.0,
        dragCoefficient = 0.3,
        frontalAreaM2 = 2.2,
        rollingResistance = 0.012,
        engineDisplacementCc = 2000,
        fuelType = FuelType.GASOLINE,
        vehicleType = VehicleType.ICE
    )

    private val hybridVehicle = Vehicle(
        name = "Hybrid SUV",
        massKg = 2100.0,
        dragCoefficient = 0.35,
        frontalAreaM2 = 2.8,
        rollingResistance = 0.012,
        engineDisplacementCc = 2500,
        fuelType = FuelType.GASOLINE,
        vehicleType = VehicleType.HYBRID
    )

    private val electricVehicle = Vehicle(
        name = "EV",
        massKg = 2000.0,
        dragCoefficient = 0.24,
        frontalAreaM2 = 2.4,
        rollingResistance = 0.01,
        fuelType = FuelType.ELECTRICITY,
        vehicleType = VehicleType.ELECTRIC
    )

    @Before
    fun setup() {
        drivingPatternAnalyzer = DrivingPatternAnalyzer()
        
        // Mock ML model to return 1.0 by default
        every { mlModel.predictCorrectionFactor(any(), any(), any(), any()) } returns 1.0
        
        fuelEstimationEngine = FuelEstimationEngine(fuelCalibrationDao, aiManager, preferenceManager, mlModel, applicationScope)
        ecoScoreCalculator = EcoScoreCalculator()
    }

    @Test
    fun `test hard braking detection`() {
        val metrics = DrivingMetrics(
            timestamp = Instant.now(),
            speedKmh = 50.0,
            longitudinalAccelMps2 = -(Constants.HARD_BRAKE_THRESHOLD + 0.5),
            isMoving = true
        )
        val events = drivingPatternAnalyzer.analyze(metrics, tripId = 1L)
        assertTrue(events.any { it.type == DrivingEventType.HARD_BRAKE })
    }

    @Test
    fun `test fuel estimation at idle depends on displacement`() {
        val fuelRateICE = fuelEstimationEngine.estimateFuelRateLPerH(0.0, 0.0, 0.0, iceVehicle)
        val fuelRateHybrid = fuelEstimationEngine.estimateFuelRateLPerH(0.0, 0.0, 0.0, hybridVehicle)
        
        // ICE 2.0L: (2000/1000) * 0.4 = 0.8 L/h
        assertEquals(0.8, fuelRateICE, 0.01)
        // Hybrid 2.5L: (2500/1000) * 0.4 = 1.0 L/h
        assertEquals(1.0, fuelRateHybrid, 0.01)
    }

    @Test
    fun `test fuel estimation higher for heavier vehicle at same speed`() {
        val speedMps = 25.0 // ~90 km/h
        val accel = 0.0
        val grade = 0.0
        
        val fuelRateICE = fuelEstimationEngine.estimateFuelRateLPerH(speedMps, accel, grade, iceVehicle)
        val fuelRateHybrid = fuelEstimationEngine.estimateFuelRateLPerH(speedMps, accel, grade, hybridVehicle)
        
        // Hybrid is heavier (2100kg vs 1500kg) and has more drag, should consume more fuel
        assertTrue("Hybrid SUV should consume more than ICE sedan at highway speeds", fuelRateHybrid > fuelRateICE)
    }

    @Test
    fun `test hybrid efficiency bonus at low speeds`() {
        val speedMps = 5.55 // 20 km/h
        val accel = 0.5
        val grade = 0.0
        
        // Create an ICE version of the SUV for direct comparison
        val suvicVehicle = hybridVehicle.copy(vehicleType = VehicleType.ICE)
        
        val fuelRateICE = fuelEstimationEngine.estimateFuelRateLPerH(speedMps, accel, grade, suvicVehicle)
        val fuelRateHybrid = fuelEstimationEngine.estimateFuelRateLPerH(speedMps, accel, grade, hybridVehicle)
        
        // Hybrid efficiency factor at < 40km/h is better (higher eta)
        // ICE eta is penalized at low speeds
        assertTrue("Hybrid should be significantly more efficient at 20 km/h", fuelRateHybrid < fuelRateICE)
    }

    @Test
    fun `test electric vehicle uses electricity fuel type density`() {
        val speedMps = 20.0
        val accel = 1.0
        val grade = 0.0
        
        val energyRateEV = fuelEstimationEngine.estimateFuelRateLPerH(speedMps, accel, grade, electricVehicle)
        
        // EV eta is 0.85, Fuel density 3.6 MJ/L (1 kWh)
        // Power (VSP) calculation:
        // vsp = 20 * (1 * 1.05 + 9.81 * 0 + 9.81 * 0.01 * 1) + (0.5 * 1.225 * 0.24 * 2.4 * 20^3) / 2000
        // vsp = 20 * (1.05 + 0.0981) + (0.147 * 8000) / 2000
        // vsp = 20 * 1.1481 + 1176 / 2000 = 22.962 + 0.588 = 23.55 W/kg
        // Total power = 23.55 * 2000 = 47100 Watts
        // Fuel rate (L/h or kWh/h) = (47100 * 3600) / (0.85 * 3.6 * 1,000,000)
        // Fuel rate = 169,560,000 / 3,060,000 = 55.41 L/h (kWh/h)
        
        assertTrue("Energy rate should be positive", energyRateEV > 0)
        assertTrue("Energy rate should be around 55 kWh/h for high power load", energyRateEV > 50.0)
    }

    @Test
    fun `test road grade impact on fuel rate`() {
        val speedMps = 15.0
        val accel = 0.0
        
        val fuelFlat = fuelEstimationEngine.estimateFuelRateLPerH(speedMps, accel, 0.0, iceVehicle)
        val fuelUphill = fuelEstimationEngine.estimateFuelRateLPerH(speedMps, accel, 5.0, iceVehicle)
        val fuelDownhill = fuelEstimationEngine.estimateFuelRateLPerH(speedMps, accel, -5.0, iceVehicle)
        
        assertTrue("Uphill should use more fuel", fuelUphill > fuelFlat)
        assertTrue("Downhill should use less fuel", fuelDownhill < fuelFlat)
    }

    @Test
    fun `test fuel calibration factor updates correctly`() {
        val point1 = FuelCalibrationPoint(tripId = 1L, estimatedFuelLiters = 10.0, actualFuelLiters = 12.0, distanceKm = 50.0)
        val point2 = FuelCalibrationPoint(tripId = 2L, estimatedFuelLiters = 10.0, actualFuelLiters = 11.0, distanceKm = 60.0)
        val point3 = FuelCalibrationPoint(tripId = 3L, estimatedFuelLiters = 10.0, actualFuelLiters = 10.0, distanceKm = 55.0)

        fuelEstimationEngine.addCalibrationPoint(point1)
        fuelEstimationEngine.addCalibrationPoint(point2)
        fuelEstimationEngine.addCalibrationPoint(point3)
        
        val newCalibrationFactor = fuelEstimationEngine.getCalibrationFactor()
        assertEquals(1.1, newCalibrationFactor, 0.01)
    }

    @Test
    fun `test eco score perfect driving`() {
        val ecoScore = ecoScoreCalculator.calculate(
            hardBrakeCount = 0,
            hardAccelCount = 0,
            sharpTurnCount = 0,
            averageSpeedKmh = 70.0,
            idleTimePercent = 2.0,
            speedStdDeviation = 2.0,
            tripDurationMinutes = 30.0
        )
        assertEquals(100, ecoScore.overall)
    }
}
