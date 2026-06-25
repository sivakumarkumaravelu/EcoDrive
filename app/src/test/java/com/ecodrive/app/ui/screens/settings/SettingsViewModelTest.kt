package com.ecodrive.app.ui.screens.settings

import com.ecodrive.app.TestUtils
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.data.remote.SmartcarVehicleData
import com.ecodrive.app.data.repository.VehicleRepository
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.domain.model.AppColorPalette
import com.ecodrive.app.domain.model.AppTheme
import com.ecodrive.app.domain.model.Vehicle
import com.ecodrive.app.util.PermissionManager
import com.ecodrive.app.domain.ai.service.AiManager
import com.ecodrive.app.domain.ai.provider.AiProvider
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
    private val aiManager: AiManager = mockk(relaxed = true)
    private val permissionManager: PermissionManager = mockk(relaxed = true)
    private val vehicleRepository: VehicleRepository = mockk(relaxed = true)
    private val mockProvider: AiProvider = mockk(relaxed = true)

    private val testDispatcher = StandardTestDispatcher()

    private val apiStateFlow = MutableStateFlow(SmartcarApiClient.ApiState.NOT_CONFIGURED)
    private val vehicleDataFlow = MutableStateFlow(SmartcarVehicleData())
    private val autoRecordFlow = MutableStateFlow(false)
    private val useMetricFlow = MutableStateFlow(true)
    private val appThemeFlow = MutableStateFlow(AppTheme.DARK)
    private val colorPaletteFlow = MutableStateFlow(AppColorPalette.ECO_GREEN)

    private val useGoogleMapsFlow = MutableStateFlow(false)
    private val smartcarApplicationIdFlow = MutableStateFlow("")
    private val smartcarClientIdFlow = MutableStateFlow("")
    private val smartcarClientSecretFlow = MutableStateFlow("")
    private val smartcarRefreshTokenFlow = MutableStateFlow("")
    private val smartcarUserIdFlow = MutableStateFlow("")
    private val mapStyleFlow = MutableStateFlow(com.ecodrive.app.util.MapStyle.DEFAULT)
    private val liveCoachingFlow = MutableStateFlow(true)
    private val coachVoiceFlow = MutableStateFlow("DEFAULT")
    private val appFontScaleFlow = MutableStateFlow(com.ecodrive.app.domain.model.AppFontScale.MEDIUM)

    private lateinit var viewModel: SettingsViewModel

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()

        every { smartcarApiClient.state } returns apiStateFlow
        every { smartcarApiClient.vehicleData } returns vehicleDataFlow
        coEvery { smartcarApiClient.authenticateWithCode(any(), any(), any(), any()) } returns Result.success(Unit)
        every { preferenceManager.autoRecordEnabled } returns autoRecordFlow
        every { preferenceManager.useMetricUnits } returns useMetricFlow
        every { preferenceManager.appTheme } returns appThemeFlow
        every { preferenceManager.colorPalette } returns colorPaletteFlow
        coEvery { preferenceManager.setSmartcarApplicationId(any()) } just Runs
        every { preferenceManager.useGoogleMaps } returns useGoogleMapsFlow
        every { preferenceManager.smartcarApplicationId } returns smartcarApplicationIdFlow
        every { preferenceManager.smartcarClientId } returns smartcarClientIdFlow
        every { preferenceManager.smartcarClientSecret } returns smartcarClientSecretFlow
        every { preferenceManager.smartcarRefreshToken } returns smartcarRefreshTokenFlow
        every { preferenceManager.smartcarUserId } returns smartcarUserIdFlow
        every { preferenceManager.mapStyle } returns mapStyleFlow
        every { preferenceManager.liveCoachingEnabled } returns liveCoachingFlow
        every { preferenceManager.coachVoice } returns coachVoiceFlow
        every { preferenceManager.appFontScale } returns appFontScaleFlow


        coEvery { preferenceManager.setSmartcarApplicationId(any()) } just Runs
        coEvery { preferenceManager.setSmartcarClientId(any()) } just Runs
        coEvery { preferenceManager.setSmartcarClientSecret(any()) } just Runs
        coEvery { preferenceManager.setSmartcarRefreshToken(any()) } just Runs
        coEvery { preferenceManager.setSmartcarUserId(any()) } just Runs
        coEvery { preferenceManager.setMapStyle(any()) } just Runs
        coEvery { preferenceManager.setAppFontScale(any()) } just Runs
        coEvery { preferenceManager.setLiveCoachingEnabled(any()) } just Runs
        coEvery { preferenceManager.setCoachVoice(any()) } just Runs
        
        every { aiManager.getProviderByName(any()) } returns mockProvider
        every { aiManager.getAllProviders() } returns listOf(mockProvider)
        every { mockProvider.name } returns "GEMINI"
        every { mockProvider.defaultModel } returns "gemini-2.0-flash"
        coEvery { mockProvider.getAvailableModels() } returns listOf("gemini-2.0-flash")

        every { fuelEngine.getCalibrationFactor() } returns 1.05
        every { permissionManager.hasBluetoothPermissions() } returns false
        every { permissionManager.hasBackgroundLocationPermission() } returns false
        coEvery { vehicleRepository.getDefaultVehicle() } returns Vehicle()

        // Suppress MainActivity.authCodeFlow by mocking it
        mockkObject(com.ecodrive.app.ui.MainActivity.Companion)
        every { com.ecodrive.app.ui.MainActivity.authCodeFlow } returns MutableStateFlow<Pair<String, String?>?>(null)

        viewModel = SettingsViewModel(
            smartcarApiClient,
            vehicleRepository,
            fuelEngine,
            preferenceManager,
            aiManager,
            permissionManager
        )
        testDispatcher.scheduler.advanceUntilIdle()
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
        assertFalse(state.useGoogleMaps)
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
    fun `test getAuthUrl delegates to smartcarApiClient when clientId set`() = runTest {
        advanceUntilIdle()
        every { smartcarApiClient.getAuthUrl("my-app") } returns "https://auth.example.com"
        viewModel.updateApplicationId("my-app")
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
    fun `test toggleUseGoogleMaps calls preferenceManager`() = runTest {
        viewModel.toggleUseGoogleMaps()
        advanceUntilIdle()
        coVerify { preferenceManager.setUseGoogleMaps(true) } // false -> true
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



    @Test
    fun `test credentials loaded on init`() = runTest {
        smartcarClientIdFlow.value = "client_saved"
        smartcarClientSecretFlow.value = "secret_saved"

        // Recreate viewModel to test init block load
        viewModel = SettingsViewModel(
            smartcarApiClient,
            vehicleRepository,
            fuelEngine,
            preferenceManager,
            aiManager,
            permissionManager
        )
        advanceUntilIdle()

        assertEquals("client_saved", viewModel.state.value.smartcarClientId)
        assertEquals("secret_saved", viewModel.state.value.smartcarClientSecret)
    }

    @Test
    fun `test getAuthUrl trims credentials and persists them`() = runTest {
        advanceUntilIdle()
        viewModel.updateApplicationId("  app_123  ")
        viewModel.updateClientId("  client_123   ")
        viewModel.updateClientSecret("   secret_456  ")

        every { smartcarApiClient.getAuthUrl("app_123") } returns "https://auth.example.com?client_id=app_123"

        val url = viewModel.getAuthUrl()

        assertEquals("https://auth.example.com?client_id=app_123", url)

        advanceUntilIdle()

        coVerify { preferenceManager.setSmartcarApplicationId("app_123") }
        coVerify { preferenceManager.setSmartcarClientId("client_123") }
        coVerify { preferenceManager.setSmartcarClientSecret("secret_456") }
    }

    @Test
    fun `test setMapStyle calls preferenceManager`() = runTest {
        viewModel.setMapStyle(com.ecodrive.app.util.MapStyle.TERRAIN)
        advanceUntilIdle()
        coVerify { preferenceManager.setMapStyle(com.ecodrive.app.util.MapStyle.TERRAIN) }
    }

    @Test
    fun `test handleAuthCallback trims credentials before exchange`() = runTest {
        viewModel.updateClientId("  client_123   ")
        viewModel.updateClientSecret("   secret_456  ")

        viewModel.handleAuthCallback("auth_code_xyz", "user_abc")

        advanceUntilIdle()

        coVerify { smartcarApiClient.authenticateWithCode("auth_code_xyz", "user_abc", "client_123", "secret_456") }
    }

    @Test
    fun `test disconnect clears credentials and VM state`() = runTest {
        viewModel.updateClientId("client_123")
        viewModel.updateClientSecret("secret_456")

        viewModel.disconnectSmartcar()

        advanceUntilIdle()

        verify { smartcarApiClient.disconnect() }
        coVerify(exactly = 0) { preferenceManager.setSmartcarClientId(any()) }
        coVerify(exactly = 0) { preferenceManager.setSmartcarClientSecret(any()) }
        coVerify { preferenceManager.setSmartcarRefreshToken("") }
        coVerify { preferenceManager.setSmartcarUserId("") }
        assertEquals("client_123", viewModel.state.value.smartcarClientId)
        assertEquals("secret_456", viewModel.state.value.smartcarClientSecret)
    }

    @Test
    fun `test deep link authCodeFlow triggers handleAuthCallback`() = runTest {
        val authCodeFlow = MutableStateFlow<Pair<String, String?>?>(null)
        every { com.ecodrive.app.ui.MainActivity.authCodeFlow } returns authCodeFlow

        // Recreate viewModel to observe our custom flow
        viewModel = SettingsViewModel(
            smartcarApiClient,
            vehicleRepository,
            fuelEngine,
            preferenceManager,
            aiManager,
            permissionManager
        )
        advanceUntilIdle()
        viewModel.updateClientId("client_123")
        viewModel.updateClientSecret("secret_456")

        authCodeFlow.value = Pair("deep_link_code_abc", "user_xyz")
        advanceUntilIdle()

        coVerify { smartcarApiClient.authenticateWithCode("deep_link_code_abc", "user_xyz", "client_123", "secret_456") }
        assertNull(authCodeFlow.value) // Reset after consumption
    }

    @Test
    fun `test setAppFontScale calls preferenceManager`() = runTest {
        viewModel.setAppFontScale(com.ecodrive.app.domain.model.AppFontScale.LARGE)
        advanceUntilIdle()
        coVerify { preferenceManager.setAppFontScale(com.ecodrive.app.domain.model.AppFontScale.LARGE) }
    }

    @Test
    fun `test toggleLiveCoaching calls preferenceManager`() = runTest {
        viewModel.toggleLiveCoaching()
        advanceUntilIdle()
        coVerify { preferenceManager.setLiveCoachingEnabled(false) } // true -> false
    }

    @Test
    fun `test setCoachVoice calls preferenceManager`() = runTest {
        viewModel.setCoachVoice("SARAH")
        advanceUntilIdle()
        coVerify { preferenceManager.setCoachVoice("SARAH") }
    }

    @Test
    fun `test AUTH_FAILED api state triggers disconnectSmartcar`() = runTest {
        viewModel.updateClientId("client_123")
        viewModel.updateClientSecret("secret_456")

        apiStateFlow.value = SmartcarApiClient.ApiState.AUTH_FAILED
        advanceUntilIdle()

        verify { smartcarApiClient.disconnect() }
        coVerify(exactly = 0) { preferenceManager.setSmartcarClientId(any()) }
        coVerify(exactly = 0) { preferenceManager.setSmartcarClientSecret(any()) }
        coVerify { preferenceManager.setSmartcarRefreshToken("") }
        coVerify { preferenceManager.setSmartcarUserId("") }
        assertEquals("client_123", viewModel.state.value.smartcarClientId)
        assertEquals("secret_456", viewModel.state.value.smartcarClientSecret)
    }
}
