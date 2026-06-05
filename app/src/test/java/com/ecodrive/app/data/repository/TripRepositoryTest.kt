package com.ecodrive.app.data.repository

import com.ecodrive.app.data.local.dao.*
import com.ecodrive.app.data.local.entity.*
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.domain.model.Vehicle
import com.ecodrive.app.TestUtils
import io.mockk.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TripRepositoryTest {

    private lateinit var tripDao: TripDao
    private lateinit var drivingEventDao: DrivingEventDao
    private lateinit var dataPointDao: DataPointDao
    private lateinit var smartcarApiClient: SmartcarApiClient
    private lateinit var fuelEngine: FuelEstimationEngine
    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var tripRepository: TripRepository

    @Before
    fun setup() {
        TestUtils.mockLog()
        tripDao = mockk(relaxed = true)
        drivingEventDao = mockk(relaxed = true)
        dataPointDao = mockk(relaxed = true)
        smartcarApiClient = mockk(relaxed = true)
        fuelEngine = mockk(relaxed = true)
        vehicleRepository = mockk(relaxed = true)

        val adaptiveScoreWeights: com.ecodrive.app.domain.ai.engine.AdaptiveScoreWeights = mockk(relaxed = true)
        val adaptiveThresholdEngine: com.ecodrive.app.domain.ai.engine.AdaptiveThresholdEngine = mockk(relaxed = true)
        val anomalyDao: AnomalyDao = mockk(relaxed = true)

        tripRepository = TripRepository(
            tripDao,
            drivingEventDao,
            dataPointDao,
            smartcarApiClient,
            fuelEngine,
            vehicleRepository,
            adaptiveScoreWeights,
            adaptiveThresholdEngine,
            anomalyDao
        )
    }

    @Test
    fun `test startTrip inserts new trip with fuel level`() = runTest {
        coEvery { smartcarApiClient.fetchFuelLevel() } returns 85.5
        coEvery { vehicleRepository.getDefaultVehicle() } returns Vehicle(id = 1, name = "My Car")
        coEvery { tripDao.insertTrip(any()) } returns 123L

        val tripId = tripRepository.startTrip(vehicleId = 1L)

        assertEquals(123L, tripId)
        val tripSlot = slot<TripEntity>()
        coVerify { tripDao.insertTrip(capture(tripSlot)) }
        assertEquals(85.5, tripSlot.captured.startFuelPercent!!, 0.01)
        assertTrue(tripSlot.captured.isActive)
    }

    @Test
    fun `test endTrip updates trip and calculates calibration`() = runTest {
        val existingTrip = TripEntity(
            id = 1L,
            vehicleId = 1L,
            startTimeEpochMs = System.currentTimeMillis() - 3600_000,
            startFuelPercent = 90.0,
            isActive = true
        )
        val vehicle = Vehicle(id = 1, tankCapacityLiters = 50.0)

        coEvery { tripDao.getTripById(1L) } returns existingTrip
        coEvery { vehicleRepository.getVehicleById(1L) } returns vehicle
        coEvery { smartcarApiClient.fetchFuelLevel() } returns 80.0
        every { fuelEngine.getCalibrationFactor() } returns 1.05

        tripRepository.endTrip(
            tripId = 1L,
            distanceKm = 100.0,
            durationSeconds = 3600,
            averageSpeedKmh = 100.0,
            maxSpeedKmh = 120.0,
            fuelConsumedEstimate = 4.5,
            ecoScore = 85,
            hardBrakeCount = 0,
            hardAccelCount = 0,
            sharpTurnCount = 0,
            idleTimeSeconds = 60
        )

        val updatedTripSlot = slot<TripEntity>()
        coVerify { tripDao.updateTrip(capture(updatedTripSlot)) }
        
        val updated = updatedTripSlot.captured
        assertFalse(updated.isActive)
        assertEquals(80.0, updated.endFuelPercent!!, 0.01)
        assertEquals(85, updated.ecoScore)
        assertEquals(1.05, updated.calibrationFactor, 0.01)

        // Verify calibration point added
        // Fuel delta = 90 - 80 = 10% of 50L = 5.0L actual
        coVerify { fuelEngine.addCalibrationPoint(any()) }
    }

    @Test
    fun `test endTrip handles null fuel levels gracefully`() = runTest {
        val existingTrip = TripEntity(id = 1L, vehicleId = 1L, isActive = true)
        coEvery { tripDao.getTripById(1L) } returns existingTrip
        coEvery { smartcarApiClient.fetchFuelLevel() } returns null

        tripRepository.endTrip(1L, 10.0, 600, 60.0, 80.0, 0.8, 90, 0, 0, 0, 0)

        coVerify { tripDao.updateTrip(any()) }
        coVerify(exactly = 0) { fuelEngine.addCalibrationPoint(any()) }
    }
}
