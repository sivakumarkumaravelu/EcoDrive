package com.ecodrive.app.data.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.util.Log
import com.ecodrive.app.util.Constants
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Manages the Bluetooth connection to an ELM327 OBD-II adapter.
 * Handles socket creation, initialization, command sending, and response reading.
 * Includes error recovery with exponential backoff retry logic.
 */
class ObdConnection {

    companion object {
        private const val TAG = "ObdConnection"
        private const val READ_TIMEOUT_MS = 2000L
        private const val BUFFER_SIZE = 1024
        private const val MAX_RETRIES = 3
        private const val INITIAL_BACKOFF_MS = 500L
    }

    private var socket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var outputStream: OutputStream? = null
    private var consecutiveFailures = 0

    val isConnected: Boolean
        get() = socket?.isConnected == true

    /**
     * Connect to the given Bluetooth device (ELM327 adapter).
     * Establishes the RFCOMM socket and initializes the ELM327.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(device: BluetoothDevice): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            disconnect() // Clean up any existing connection
            consecutiveFailures = 0

            val uuid = UUID.fromString(Constants.SPP_UUID)
            socket = device.createRfcommSocketToServiceRecord(uuid)
            socket?.connect()

            inputStream = socket?.inputStream
            outputStream = socket?.outputStream

            // Initialize ELM327 adapter
            initializeElm327()

            Log.i(TAG, "Connected to ELM327 at ${device.address}")
            Result.success(Unit)
        } catch (e: IOException) {
            Log.e(TAG, "Connection failed: ${e.message}")
            disconnect()
            Result.failure(e)
        }
    }

    /**
     * Send ELM327 initialization commands to configure the adapter.
     */
    private suspend fun initializeElm327() {
        for (command in Constants.ELM_INIT_COMMANDS) {
            sendRawCommand(command)
            delay(100) // Small delay between init commands
        }
        Log.d(TAG, "ELM327 initialized successfully")
    }

    /**
     * Send an OBD command with automatic retry on failure.
     * Implements exponential backoff: 500ms, 1s, 2s
     */
    suspend fun sendCommand(command: ObdCommand): Result<Double> = 
        sendCommandWithRetry(command, retryCount = 0)

    /**
     * Internal method with retry logic and exponential backoff.
     */
    private suspend fun sendCommandWithRetry(
        command: ObdCommand,
        retryCount: Int = 0
    ): Result<Double> = withContext(Dispatchers.IO) {
        try {
            val rawResponse = sendRawCommand(command.buildCommand())
            
            if (rawResponse.contains("NODATA") || rawResponse.contains("ERROR")) {
                val failure = ObdException("No data for PID: ${command.pid}")
                handleCommandFailure(command, failure, retryCount)
            } else {
                // Success - reset failure count
                consecutiveFailures = 0
                try {
                    val value = command.parseResponse(rawResponse)
                    Result.success(value)
                } catch (e: ObdException) {
                    // Response format invalid - retry once more
                    Log.w(TAG, "Invalid response format for ${command.pid}: ${e.message}")
                    handleCommandFailure(command, e, retryCount)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Command ${command.pid} failed: ${e.message}")
            handleCommandFailure(command, e, retryCount)
        }
    }

    /**
     * Handle command failure with exponential backoff retry.
     */
    private suspend fun handleCommandFailure(
        command: ObdCommand,
        exception: Exception,
        retryCount: Int
    ): Result<Double> {
        consecutiveFailures++
        
        // Check if we've exceeded max retries or consecutive failures
        if (retryCount >= MAX_RETRIES || consecutiveFailures >= 5) {
            Log.e(TAG, "Command ${command.pid} failed after $retryCount retries")
            consecutiveFailures = 0
            return Result.failure(exception)
        }

        // Exponential backoff: 500ms, 1s, 2s
        val backoffMs = INITIAL_BACKOFF_MS * (1L shl retryCount)
        Log.w(TAG, "Retrying command ${command.pid} after ${backoffMs}ms (attempt ${retryCount + 1}/$MAX_RETRIES)")
        
        delay(backoffMs)
        
        // Check connection health before retry
        if (!isConnected) {
            Log.e(TAG, "Connection lost - cannot retry command")
            return Result.failure(IOException("Connection lost"))
        }

        return sendCommandWithRetry(command, retryCount + 1)
    }

    /**
     * Send a raw command string and read the response.
     */
    private suspend fun sendRawCommand(command: String): String = withContext(Dispatchers.IO) {
        val os = outputStream ?: throw IOException("Not connected")
        val ins = inputStream ?: throw IOException("Not connected")

        try {
            // Send command with carriage return
            os.write("$command\r".toByteArray())
            os.flush()

            // Read response until '>' prompt
            readResponse(ins)
        } catch (e: IOException) {
            Log.e(TAG, "I/O error while sending command: ${e.message}")
            disconnect()
            throw e
        }
    }

    /**
     * Read from the input stream until we get the '>' prompt
     * which indicates the ELM327 is ready for the next command.
     */
    private fun readResponse(inputStream: InputStream): String {
        val buffer = ByteArray(BUFFER_SIZE)
        val response = StringBuilder()
        val startTime = System.currentTimeMillis()

        while (System.currentTimeMillis() - startTime < READ_TIMEOUT_MS) {
            if (inputStream.available() > 0) {
                try {
                    val bytesRead = inputStream.read(buffer)
                    if (bytesRead < 0) {
                        // Stream closed
                        throw IOException("Input stream closed")
                    }
                    val chunk = String(buffer, 0, bytesRead)
                    response.append(chunk)

                    if (chunk.contains(">")) {
                        break
                    }
                } catch (e: IOException) {
                    throw IOException("Error reading response: ${e.message}", e)
                }
            } else {
                Thread.sleep(10)
            }
        }

        return response.toString().trim()
    }

    /**
     * Disconnect and clean up resources.
     */
    fun disconnect() {
        try {
            inputStream?.close()
            outputStream?.close()
            socket?.close()
        } catch (e: IOException) {
            Log.w(TAG, "Error during disconnect: ${e.message}")
        } finally {
            inputStream = null
            outputStream = null
            socket = null
            consecutiveFailures = 0
        }
    }
}

/**
 * Custom exception for OBD-II communication errors.
 */
class ObdException(message: String) : Exception(message)
