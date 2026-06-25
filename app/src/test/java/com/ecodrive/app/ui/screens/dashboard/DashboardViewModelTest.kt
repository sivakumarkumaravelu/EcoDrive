package com.ecodrive.app.ui.screens.dashboard

import com.ecodrive.app.TestUtils
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.data.remote.SmartcarVehicleData
import com.ecodrive.app.data.repository.VehicleRepository
import com.ecodrive.app.domain.ai.service.AiCoachService
import com.ecodrive.app.domain.model.*
import com.ecodrive.app.domain.recorder.TripRecorder
import com.ecodrive.app.data.sensor.SensorDataManager
import com.ecodrive.app.util.PermissionManager
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DashboardViewModelTest {

    private val sensorDataManager: SensorDataManager = mockk(relaxed = true)
    private val smartcarApiClient: SmartcarApiClient = mockk(relaxed = true)
    private val tripRecorder: TripRecorder = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val aiCoachService: AiCoachService = mockk(relaxed = true)
    private val ecoScorePredictor: com.ecodrive.app.domain.ai.analyzer.EcoScorePredictor = mockk(relaxed = true)
    private val permissionManager: PermissionManager = mockk(relaxed = true)
    private val vehicleRepository: VehicleRepository = mockk(relaxed = true)

    private lateinit var viewModel: DashboardViewModel

    private val testDispatcher = StandardTestDispatcher()

    private val sensorStateFlow = MutableStateFlow(SensorDataManager.CollectionState.IDLE)
    private val sensorErrorFlow = MutableStateFlow<String?>(null)
    private val smartcarStateFlow = MutableStateFlow(SmartcarApiClient.ApiState.NOT_CONFIGURED)
    private val smartcarDataFlow = MutableStateFlow(SmartcarVehicleData())
    private val isRecordingFlow = MutableStateFlow(false)
    private val currentMetricsFlow = MutableStateFlow(DrivingMetrics())
    private val currentEcoScoreFlow = MutableStateFlow(EcoScore(overall = 0))
    private val latestTipFlow = MutableStateFlow<String?>(null)
    private val tripDurationSecondsFlow = MutableStateFlow(0L)
    private val tripDistanceKmFlow = MutableStateFlow(0.0)
    private val fuelConsumedEstimateFlow = MutableStateFlow(0.0)
    private val hardBrakeCountFlow = MutableStateFlow(0)
    private val hardAccelCountFlow = MutableStateFlow(0)
    private val sharpTurnCountFlow = MutableStateFlow(0)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()

        coEvery { vehicleRepository.getDefaultVehicle() } returns Vehicle()
        every { sensorDataManager.state } returns sensorStateFlow
        every { sensorDataManager.errorMessage } returns sensorErrorFlow
        every { smartcarApiClient.state } returns smartcarStateFlow
        every { smartcarApiClient.vehicleData } returns smartcarDataFlow
        every { tripRecorder.isRecording } returns isRecordingFlow
        every { tripRecorder.currentMetrics } returns currentMetricsFlow
        every { tripRecorder.currentEcoScore } returns currentEcoScoreFlow
        every { tripRecorder.latestTip } returns latestTipFlow
        every { tripRecorder.tripDurationSeconds } returns tripDurationSecondsFlow
        every { tripRecorder.tripDistanceKm } returns tripDistanceKmFlow
        every { tripRecorder.fuelConsumedEstimate } returns fuelConsumedEstimateFlow
        every { tripRecorder.hardBrakeCount } returns hardBrakeCountFlow
        every { tripRecorder.hardAccelCount } returns hardAccelCountFlow
        every { tripRecorder.sharpTurnCount } returns sharpTurnCountFlow
        every { permissionManager.hasRequiredPermissions() } returns true
        every { preferenceManager.useMetricUnits } returns flowOf(true)
        every { preferenceManager.smartcarClientId } returns flowOf("")
        every { preferenceManager.smartcarClientSecret } returns flowOf("")
        every { preferenceManager.smartcarRefreshToken } returns flowOf("")
        every { preferenceManager.smartcarUserId } returns flowOf("")

        viewModel = DashboardViewModel(
            sensorDataManager,
            smartcarApiClient,
            vehicleRepository,
            tripRecorder,
            preferenceManager,
            aiCoachService,
            ecoScorePredictor,
            permissionManager
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial state has correct defaults`() = runTest {
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(SensorDataManager.CollectionState.IDLE, state.sensorState)
        assertFalse(state.isRecording)
        assertFalse(state.needsPermissions)
    }

    @Test
    fun `test state reflects sensor collecting state`() = runTest {
        sensorStateFlow.value = SensorDataManager.CollectionState.COLLECTING
        advanceUntilIdle()
        assertEquals(SensorDataManager.CollectionState.COLLECTING, viewModel.state.value.sensorState)
    }

    @Test
    fun `test state reflects recording state`() = runTest {
        isRecordingFlow.value = true
        advanceUntilIdle()
        assertTrue(viewModel.state.value.isRecording)
    }

    @Test
    fun `test state reflects updated metrics`() = runTest {
        val metrics = DrivingMetrics(speedKmh = 80.0)
        currentMetricsFlow.value = metrics
        advanceUntilIdle()
        assertEquals(80.0, viewModel.state.value.metrics.speedKmh, 0.001)
    }

    @Test
    fun `test checkPermissions sets needsPermissions true when missing`() = runTest {
        every { permissionManager.hasRequiredPermissions() } returns false
        viewModel.checkPermissions()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.needsPermissions)
    }

    @Test
    fun `test onPermissionsGranted clears needsPermissions`() = runTest {
        every { permissionManager.hasRequiredPermissions() } returns false
        viewModel.checkPermissions()
        advanceUntilIdle()
        viewModel.onPermissionsGranted()
        assertFalse(viewModel.state.value.needsPermissions)
    }

    @Test
    fun `test startRecording calls tripRecorder when permissions granted`() = runTest {
        every { permissionManager.hasRequiredPermissions() } returns true
        viewModel.startRecording()
        advanceUntilIdle()
        verify { tripRecorder.startRecording() }
    }

    @Test
    fun `test startRecording sets needsPermissions when permissions missing`() = runTest {
        every { permissionManager.hasRequiredPermissions() } returns false
        viewModel.startRecording()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.needsPermissions)
        verify(exactly = 0) { tripRecorder.startRecording() }
    }

    @Test
    fun `test stopRecording calls tripRecorder and updates tip`() = runTest {
        viewModel.stopRecording()
        advanceUntilIdle()
        verify { tripRecorder.stopRecording() }
        assertTrue(viewModel.state.value.drivingTip.contains("Trip saved"))
    }

    @Test
    fun `test error message propagated from sensor manager`() = runTest {
        sensorErrorFlow.value = "GPS error"
        advanceUntilIdle()
        assertEquals("GPS error", viewModel.state.value.errorMessage)
    }

    @Test
    fun `test dataSource shows API label when fuelTankPercent is present`() = runTest {
        isRecordingFlow.value = true
        val metricsWithFuel = DrivingMetrics(fuelTankPercent = 75.0)
        currentMetricsFlow.value = metricsWithFuel
        advanceUntilIdle()
        assertTrue(viewModel.state.value.dataSource.contains("Vehicle API"))
    }
}
