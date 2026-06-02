package com.ecodrive.app.sensor

import com.ecodrive.app.data.repository.VehicleRepository
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.domain.model.Vehicle
import com.ecodrive.app.util.Constants
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class SensorDataManagerTest {

    private lateinit var locationTracker: LocationTracker
    private lateinit var phoneSensorManager: PhoneSensorManager
    private lateinit var fuelEngine: FuelEstimationEngine
    private lateinit var vehicleRepository: VehicleRepository
    private lateinit var sensorDataManager: SensorDataManager

    @Before
    fun setup() {
        com.ecodrive.app.TestUtils.mockLog()
        locationTracker = mockk()
        phoneSensorManager = mockk()
        fuelEngine = mockk()
        vehicleRepository = mockk()

        coEvery { vehicleRepository.getDefaultVehicle() } returns Vehicle(name = "Test Car")
        every { phoneSensorManager.hasAccelerometer } returns true
        
        sensorDataManager = SensorDataManager(
            locationTracker,
            phoneSensorManager,
            fuelEngine,
            vehicleRepository,
            UnconfinedTestDispatcher()
        )
    }

    @Test
    fun `test initial state is IDLE`() {
        assertEquals(SensorDataManager.CollectionState.IDLE, sensorDataManager.state.value)
    }

    @Test
    fun `test startCollection updates state to COLLECTING`() = runTest {
        val gpsFlow = MutableSharedFlow<GpsReading>()
        val imuFlow = MutableSharedFlow<ImuReading>()

        every { locationTracker.locationFlow() } returns gpsFlow
        every { phoneSensorManager.imuFlow() } returns imuFlow
        every { fuelEngine.estimateFuelRateLPerH(any(), any(), any(), any()) } returns 1.0

        sensorDataManager.startCollection()
        
        // Wait for state to update
        sensorDataManager.state.first { it == SensorDataManager.CollectionState.COLLECTING }

        assertEquals(SensorDataManager.CollectionState.COLLECTING, sensorDataManager.state.value)
        sensorDataManager.stopCollection()
    }

    @Test
    fun `test metrics update when GPS emits`() = runTest {
        val gpsFlow = MutableSharedFlow<GpsReading>()
        val imuFlow = MutableSharedFlow<ImuReading>()

        every { locationTracker.locationFlow() } returns gpsFlow
        every { phoneSensorManager.imuFlow() } returns imuFlow
        every { fuelEngine.estimateFuelRateLPerH(any(), any(), any(), any()) } returns 5.5

        sensorDataManager.startCollection()
        sensorDataManager.state.first { it == SensorDataManager.CollectionState.COLLECTING }

        val gpsReading = GpsReading(
            timestampMs = System.currentTimeMillis(),
            speedKmh = 60.0,
            latitude = 10.0,
            longitude = 20.0,
            altitudeM = 100.0,
            bearingDegrees = 0f,
            accuracyM = 5f,
            hasSpeed = true,
            hasBearing = true
        )

        gpsFlow.emit(gpsReading)
        
        // Wait for metrics to update
        sensorDataManager.metrics.first { it.speedKmh == 60.0 }

        val metrics = sensorDataManager.metrics.value
        assertEquals(60.0, metrics.speedKmh, 0.01)
        assertEquals(5.5, metrics.fuelRateLPerH, 0.01)
        assertTrue(metrics.isMoving)

        sensorDataManager.stopCollection()
    }

    @Test
    fun `test road grade calculation after 20m distance`() = runTest {
        val gpsFlow = MutableSharedFlow<GpsReading>()
        val imuFlow = MutableSharedFlow<ImuReading>()

        every { locationTracker.locationFlow() } returns gpsFlow
        every { phoneSensorManager.imuFlow() } returns imuFlow
        
        val capturedGrade = java.util.concurrent.atomic.AtomicReference<Double>(0.0)
        every { fuelEngine.estimateFuelRateLPerH(any(), any(), any(), any()) } answers {
            val grade = it.invocation.args[2] as Double
            capturedGrade.set(grade)
            1.0
        }

        sensorDataManager.startCollection()
        sensorDataManager.state.first { it == SensorDataManager.CollectionState.COLLECTING }

        // 1. Initial fix
        gpsFlow.emit(createGpsReading(speedKmh = 72.0, altitude = 100.0)) // 20 m/s
        
        // 2. Second fix after 1 second (20 meters traveled)
        // elevation gain = 2m over 20m = 10% grade
        gpsFlow.emit(createGpsReading(speedKmh = 72.0, altitude = 102.0))
        
        // Wait for grade to be calculated and reflected in metrics
        sensorDataManager.metrics.first { it.roadGradePercent != 0.0 }

        assertEquals(10.0, capturedGrade.get(), 0.1)

        sensorDataManager.stopCollection()
    }

    @Test
    fun `test isIdle flag based on speed and accel`() = runTest {
        val gpsFlow = MutableSharedFlow<GpsReading>()
        val imuFlow = MutableSharedFlow<ImuReading>()

        every { locationTracker.locationFlow() } returns gpsFlow
        every { phoneSensorManager.imuFlow() } returns imuFlow
        every { fuelEngine.estimateFuelRateLPerH(any(), any(), any(), any()) } returns 0.5

        sensorDataManager.startCollection()
        sensorDataManager.state.first { it == SensorDataManager.CollectionState.COLLECTING }

        // 1. Not moving, low accel -> IDLE
        imuFlow.emit(createImuReading(longAccel = 0.1))
        gpsFlow.emit(createGpsReading(speedKmh = 0.0))
        sensorDataManager.metrics.first { it.isIdle }
        assertTrue(sensorDataManager.metrics.value.isIdle)

        // 2. Not moving, but high accel -> NOT IDLE (maybe starting)
        imuFlow.emit(createImuReading(longAccel = 1.5))
        gpsFlow.emit(createGpsReading(speedKmh = 0.0))
        sensorDataManager.metrics.first { !it.isIdle }
        assertFalse(sensorDataManager.metrics.value.isIdle)

        // 3. Moving -> NOT IDLE
        gpsFlow.emit(createGpsReading(speedKmh = 10.0))
        sensorDataManager.metrics.first { it.speedKmh == 10.0 }
        assertFalse(sensorDataManager.metrics.value.isIdle)

        sensorDataManager.stopCollection()
    }

    private fun createGpsReading(speedKmh: Double, altitude: Double = 100.0) = GpsReading(
        timestampMs = System.currentTimeMillis(),
        speedKmh = speedKmh,
        latitude = 0.0,
        longitude = 0.0,
        altitudeM = altitude,
        bearingDegrees = 0f,
        accuracyM = 1f,
        hasSpeed = true,
        hasBearing = true
    )

    private fun createImuReading(longAccel: Double) = ImuReading(
        timestampNs = System.nanoTime(),
        longitudinalAccel = longAccel,
        lateralAccel = 0.0,
        verticalAccel = 0.0,
        yawRate = 0.0,
        pressureHpa = null
    )
}
