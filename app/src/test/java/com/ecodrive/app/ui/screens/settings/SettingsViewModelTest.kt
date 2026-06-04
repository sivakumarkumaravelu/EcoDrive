package com.ecodrive.app.ui.screens.settings

import com.ecodrive.app.TestUtils
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.data.remote.SmartcarVehicleData
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppTheme
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
class SettingsViewModelTest {

    private val smartcarApiClient: SmartcarApiClient = mockk(relaxed = true)
    private val fuelEngine: FuelEstimationEngine = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val permissionManager: PermissionManager = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val apiStateFlow = MutableStateFlow(SmartcarApiClient.ApiState.NOT_CONFIGURED)
    private val vehicleDataFlow = MutableStateFlow(SmartcarVehicleData())
    private val autoRecordFlow = MutableStateFlow(false)
    private val useMetricFlow = MutableStateFlow(true)
    private val appThemeFlow = MutableStateFlow(AppTheme.DARK)
    private val colorPaletteFlow = MutableStateFlow(AppColorPalette.ECO_GREEN)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()

        every { smartcarApiClient.state } returns apiStateFlow
        every { smartcarApiClient.vehicleData } returns vehicleDataFlow
        every { preferenceManager.autoRecordEnabled } returns autoRecordFlow
        every { preferenceManager.useMetricUnits } returns useMetricFlow
        every { preferenceManager.appTheme } returns appThemeFlow
        every { preferenceManager.colorPalette } returns colorPaletteFlow
        every { fuelEngine.getCalibrationFactor() } returns 1.05
        every { permissionManager.hasBluetoothPermissions() } returns false
        every { permissionManager.hasBackgroundLocationPermission() } returns false

        // Suppress MainActivity.authCodeFlow by mocking it
        mockkObject(com.ecodrive.app.ui.MainActivity.Companion)
        every { com.ecodrive.app.ui.MainActivity.authCodeFlow } returns MutableStateFlow(null)

        viewModel = SettingsViewModel(smartcarApiClient, fuelEngine, preferenceManager, permissionManager)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkObject(com.ecodrive.app.ui.MainActivity.Companion)
    }

    @Test
    fun `test initial state has correct defaults`() = runTest {
        advanceUntilIdle()
        val state = viewModel.state.value
        assertEquals(SmartcarApiClient.ApiState.NOT_CONFIGURED, state.smartcarApiState)
        assertEquals("", state.smartcarClientId)
        assertEquals("", state.smartcarClientSecret)
        assertTrue(state.useMetric)
        assertEquals(AppTheme.DARK, state.appTheme)
        assertEquals(AppColorPalette.ECO_GREEN, state.appPalette)
    }

    @Test
    fun `test calibrationFactor reflected from fuelEngine`() = runTest {
        apiStateFlow.value = SmartcarApiClient.ApiState.CONNECTED
        advanceUntilIdle()
        assertEquals(1.05, viewModel.state.value.calibrationFactor, 0.001)
    }

    @Test
    fun `test updateClientId updates state`() = runTest {
        viewModel.updateClientId("abc-123")
        assertEquals("abc-123", viewModel.state.value.smartcarClientId)
    }

    @Test
    fun `test updateClientSecret updates state`() = runTest {
        viewModel.updateClientSecret("secret-xyz")
        assertEquals("secret-xyz", viewModel.state.value.smartcarClientSecret)
    }

    @Test
    fun `test getAuthUrl returns null when clientId blank`() {
        val url = viewModel.getAuthUrl()
        assertNull(url)
    }

    @Test
    fun `test getAuthUrl delegates to smartcarApiClient when clientId set`() {
        every { smartcarApiClient.getAuthUrl(any()) } returns "https://auth.example.com"
        viewModel.updateClientId("my-client")
        val url = viewModel.getAuthUrl()
        assertEquals("https://auth.example.com", url)
    }

    @Test
    fun `test disconnectSmartcar delegates to client`() {
        viewModel.disconnectSmartcar()
        verify { smartcarApiClient.disconnect() }
    }

    @Test
    fun `test toggleUnits calls preferenceManager`() = runTest {
        viewModel.toggleUnits()
        advanceUntilIdle()
        coVerify { preferenceManager.setUseMetricUnits(false) } // true -> false
    }

    @Test
    fun `test toggleAutoRecord calls preferenceManager`() = runTest {
        viewModel.toggleAutoRecord()
        advanceUntilIdle()
        coVerify { preferenceManager.setAutoRecordEnabled(true) } // false -> true
    }

    @Test
    fun `test toggleObd flips isObdEnabled`() {
        assertFalse(viewModel.state.value.isObdEnabled)
        viewModel.toggleObd()
        assertTrue(viewModel.state.value.isObdEnabled)
        viewModel.toggleObd()
        assertFalse(viewModel.state.value.isObdEnabled)
    }

    @Test
    fun `test setAppTheme calls preferenceManager`() = runTest {
        viewModel.setAppTheme(AppTheme.LIGHT)
        advanceUntilIdle()
        coVerify { preferenceManager.setAppTheme(AppTheme.LIGHT) }
    }

    @Test
    fun `test setColorPalette calls preferenceManager`() = runTest {
        viewModel.setColorPalette(AppColorPalette.MIDNIGHT_BLUE)
        advanceUntilIdle()
        coVerify { preferenceManager.setColorPalette(AppColorPalette.MIDNIGHT_BLUE) }
    }

    @Test
    fun `test vehicleData updates fuelTankPercent and odometer`() = runTest {
        vehicleDataFlow.value = SmartcarVehicleData(fuelPercent = 75.0, odometerKm = 12345.0)
        advanceUntilIdle()
        assertEquals(75.0, viewModel.state.value.fuelTankPercent)
        assertEquals(12345.0, viewModel.state.value.odometerKm)
    }
}
