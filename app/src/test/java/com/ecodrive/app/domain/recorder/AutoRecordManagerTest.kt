package com.ecodrive.app.domain.recorder

import android.app.PendingIntent
import android.content.Context
import com.ecodrive.app.TestUtils
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.util.PermissionManager
import com.google.android.gms.location.ActivityRecognitionClient
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AutoRecordManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val permissionManager: PermissionManager = mockk(relaxed = true)
    private val activityRecognitionClient: ActivityRecognitionClient = mockk(relaxed = true)

    private lateinit var autoRecordManager: AutoRecordManager
    private val testDispatcher = UnconfinedTestDispatcher()
    private val autoRecordEnabledFlow = MutableStateFlow(false)

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()
        
        every { preferenceManager.autoRecordEnabled } returns autoRecordEnabledFlow
        
        // Mock static ActivityRecognition.getClient
        mockkStatic("com.google.android.gms.location.ActivityRecognition")
        every { com.google.android.gms.location.ActivityRecognition.getClient(any<Context>()) } returns activityRecognitionClient

        // Mock PendingIntent.getBroadcast
        mockkStatic(PendingIntent::class)
        every { PendingIntent.getBroadcast(any(), any(), any(), any()) } returns mockk()

        autoRecordManager = AutoRecordManager(
            context,
            preferenceManager,
            permissionManager,
            testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkStatic("com.google.android.gms.location.ActivityRecognition")
        unmockkStatic(PendingIntent::class)
    }

    @Test
    fun `test activity recognition starts when enabled and permission granted`() = runTest(testDispatcher) {
        // Given
        every { permissionManager.hasActivityRecognitionPermission() } returns true
        
        // When
        autoRecordEnabledFlow.value = true
        
        // Then
        verify { activityRecognitionClient.requestActivityUpdates(any(), any()) }
    }

    @Test
    fun `test activity recognition does not start when enabled but permission missing`() = runTest(testDispatcher) {
        // Given
        every { permissionManager.hasActivityRecognitionPermission() } returns false
        
        // When
        autoRecordEnabledFlow.value = true
        
        // Then
        verify(exactly = 0) { activityRecognitionClient.requestActivityUpdates(any(), any()) }
    }

    @Test
    fun `test activity recognition stops when disabled`() = runTest(testDispatcher) {
        // Given - start it first
        every { permissionManager.hasActivityRecognitionPermission() } returns true
        autoRecordEnabledFlow.value = true
        
        // When
        autoRecordEnabledFlow.value = false
        
        // Then
        verify { activityRecognitionClient.removeActivityUpdates(any()) }
    }
}
