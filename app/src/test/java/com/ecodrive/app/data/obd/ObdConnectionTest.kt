package com.ecodrive.app.data.obd

import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import com.ecodrive.app.TestUtils
import com.ecodrive.app.util.Constants
import io.mockk.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class ObdConnectionTest {

    private lateinit var obdConnection: ObdConnection
    private lateinit var mockDevice: BluetoothDevice
    private lateinit var mockSocket: BluetoothSocket

    @Before
    fun setup() {
        TestUtils.mockLog()
        obdConnection = ObdConnection()
        mockDevice = mockk(relaxed = true)
        mockSocket = mockk(relaxed = true)

        every { mockDevice.createRfcommSocketToServiceRecord(any()) } returns mockSocket
    }

    @Test
    fun `test initial connection state is disconnected`() {
        assertFalse(obdConnection.isConnected)
    }

    @Test
    fun `test connect establishes socket and initializes ELM`() = runTest {
        val mockIn = mockk<java.io.InputStream>(relaxed = true)
        val mockOut = mockk<java.io.OutputStream>(relaxed = true)
        
        every { mockSocket.inputStream } returns mockIn
        every { mockSocket.outputStream } returns mockOut
        every { mockSocket.isConnected } returns true
        
        // Return prompt for each init command
        every { mockIn.read(any()) } answers {
            val b = it.invocation.args[0] as ByteArray
            ">".toByteArray().copyInto(b)
            1
        }
        every { mockIn.available() } returns 1

        val result = obdConnection.connect(mockDevice)

        assertTrue(result.isSuccess)
        assertTrue(obdConnection.isConnected)
    }

    @Test
    fun `test sendCommand successful parsing`() = runTest {
        val mockIn = mockk<java.io.InputStream>(relaxed = true)
        val mockOut = mockk<java.io.OutputStream>(relaxed = true)
        
        every { mockSocket.inputStream } returns mockIn
        every { mockSocket.outputStream } returns mockOut
        every { mockSocket.isConnected } returns true
        
        // Connect first (init)
        every { mockIn.read(any()) } answers {
            val b = it.invocation.args[0] as ByteArray
            ">".toByteArray().copyInto(b)
            1
        }
        every { mockIn.available() } returns 1
        obdConnection.connect(mockDevice)
        
        // Now mock command response
        every { mockIn.read(any()) } answers {
            val b = it.invocation.args[0] as ByteArray
            "410D 3E >".toByteArray().copyInto(b)
            9
        }
        
        val result = obdConnection.sendCommand(SpeedCommand())
        
        assertTrue(result.isSuccess)
        assertEquals(62.0, result.getOrNull()!!, 0.01)
    }

    @Test
    fun `test sendCommand with NODATA returns failure`() = runTest {
        val mockIn = mockk<java.io.InputStream>(relaxed = true)
        val mockOut = mockk<java.io.OutputStream>(relaxed = true)
        
        every { mockSocket.inputStream } returns mockIn
        every { mockSocket.outputStream } returns mockOut
        every { mockSocket.isConnected } returns true
        
        // Connect first
        every { mockIn.read(any()) } answers {
            val b = it.invocation.args[0] as ByteArray
            ">".toByteArray().copyInto(b)
            1
        }
        every { mockIn.available() } returns 1
        obdConnection.connect(mockDevice)
        
        // Now mock NODATA response
        every { mockIn.read(any()) } answers {
            val b = it.invocation.args[0] as ByteArray
            "NODATA >".toByteArray().copyInto(b)
            8
        }
        
        val result = obdConnection.sendCommand(SpeedCommand())
        
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ObdException)
    }

    @Test
    fun `test retry logic on IOException`() = runTest {
        val mockIn = mockk<java.io.InputStream>(relaxed = true)
        val mockOut = mockk<java.io.OutputStream>(relaxed = true)
        
        every { mockSocket.inputStream } returns mockIn
        every { mockSocket.outputStream } returns mockOut
        every { mockSocket.isConnected } returns true
        
        // Connect first
        every { mockIn.read(any()) } answers {
            val b = it.invocation.args[0] as ByteArray
            ">".toByteArray().copyInto(b)
            1
        }
        every { mockIn.available() } returns 1
        obdConnection.connect(mockDevice)
        
        // Throw IOException on next read
        every { mockIn.read(any()) } throws IOException("Read error")
        
        val result = obdConnection.sendCommand(SpeedCommand())
        assertTrue(result.isFailure)
    }

    @Test
    fun `test disconnect closes socket and streams`() = runTest {
        val mockIn = mockk<java.io.InputStream>(relaxed = true)
        val mockOut = mockk<java.io.OutputStream>(relaxed = true)
        
        every { mockSocket.inputStream } returns mockIn
        every { mockSocket.outputStream } returns mockOut
        every { mockSocket.isConnected } returns true
        
        // Connect first
        every { mockIn.read(any()) } answers {
            val b = it.invocation.args[0] as ByteArray
            ">".toByteArray().copyInto(b)
            1
        }
        every { mockIn.available() } returns 1
        obdConnection.connect(mockDevice)

        obdConnection.disconnect()
        
        verify { mockIn.close() }
        verify { mockOut.close() }
        verify { mockSocket.close() }
        assertFalse(obdConnection.isConnected)
    }
}
