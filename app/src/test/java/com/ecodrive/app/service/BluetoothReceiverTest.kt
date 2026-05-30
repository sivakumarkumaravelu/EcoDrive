package com.ecodrive.app.service

import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import com.ecodrive.app.TestUtils
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.domain.recorder.TripRecorder
import io.mockk.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class BluetoothReceiverTest {

    private val context: Context = mockk(relaxed = true)
    private val tripRecorder: TripRecorder = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private lateinit var receiver: TestBluetoothReceiver
    private val testDispatcher = StandardTestDispatcher()

    class TestBluetoothReceiver : BluetoothReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action ?: return
            val device: BluetoothDevice? = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
            
            CoroutineScope(Dispatchers.IO).launch {
                val autoRecordEnabled = preferenceManager.autoRecordEnabled.first()
                if (!autoRecordEnabled) return@launch

                val targetAddress = preferenceManager.carBluetoothAddress.first()

                when (action) {
                    BluetoothDevice.ACTION_ACL_CONNECTED -> {
                        if (targetAddress == null || device?.address == targetAddress) {
                            tripRecorder.startRecording()
                        }
                    }
                    BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                        if (targetAddress == null || device?.address == targetAddress) {
                            tripRecorder.stopRecording()
                        }
                    }
                }
            }
        }
    }

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        TestUtils.mockLog()
        
        receiver = TestBluetoothReceiver()
        receiver.tripRecorder = tripRecorder
        receiver.preferenceManager = preferenceManager
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test start recording on Bluetooth connected when enabled`() = runTest {
        // Given
        val intent = mockk<Intent>()
        val device = mockk<BluetoothDevice>()
        every { intent.action } returns BluetoothDevice.ACTION_ACL_CONNECTED
        
        // Mocking the behavior of the intent to return our device
        every { intent.getParcelableExtra<BluetoothDevice>(any()) } returns device
        
        every { preferenceManager.autoRecordEnabled } returns flowOf(true)
        every { preferenceManager.carBluetoothAddress } returns flowOf(null)

        mockkStatic(Dispatchers::class)
        every { Dispatchers.IO } returns testDispatcher

        // When
        receiver.onReceive(context, intent)
        advanceUntilIdle()
        
        // Then
        verify { tripRecorder.startRecording() }
        
        unmockkStatic(Dispatchers::class)
    }
}
