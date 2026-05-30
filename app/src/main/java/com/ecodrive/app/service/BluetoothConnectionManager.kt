package com.ecodrive.app.service

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import com.ecodrive.app.data.obd.*
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.FuelType
import com.ecodrive.app.domain.analyzer.FuelEfficiencyCalculator
import com.ecodrive.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages the Bluetooth connection to the ELM327 OBD-II adapter.
 * Provides a real-time stream of driving metrics via StateFlow.
 */
@Singleton
class BluetoothConnectionManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bluetoothAdapter: BluetoothAdapter?,
    private val obdConnection: ObdConnection,
    private val fuelCalculator: FuelEfficiencyCalculator,
) {
    companion object {
        private const val TAG = "BtConnectionManager"
    }

    // ── Connection State ────────────────────────────────────────

    enum class ConnectionState {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        ERROR,
    }

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _currentMetrics = MutableStateFlow(DrivingMetrics())
    val currentMetrics: StateFlow<DrivingMetrics> = _currentMetrics.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private var pollingJob: Job? = null
    private var fuelType: FuelType = FuelType.GASOLINE

    // ── OBD Commands to poll ────────────────────────────────────

    private val pollCommands = listOf(
        SpeedCommand(),
        RpmCommand(),
        ThrottleCommand(),
        MafCommand(),
        EngineLoadCommand(),
        CoolantTempCommand(),
    )

    // Commands polled less frequently (every 10th cycle)
    private val slowPollCommands = listOf(
        FuelTankLevelCommand(),
        AmbientTempCommand(),
        BatteryVoltageCommand(),
    )

    // ── Public API ──────────────────────────────────────────────

    /**
     * Get list of paired Bluetooth devices (ELM327 adapters).
     */
    @SuppressLint("MissingPermission")
    fun getPairedDevices(): List<BluetoothDevice> {
        return bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
    }

    /**
     * Connect to the specified Bluetooth device.
     */
    suspend fun connect(device: BluetoothDevice) {
        _connectionState.value = ConnectionState.CONNECTING
        _errorMessage.value = null

        val result = obdConnection.connect(device)
        if (result.isSuccess) {
            _connectionState.value = ConnectionState.CONNECTED
            startPolling()
        } else {
            _connectionState.value = ConnectionState.ERROR
            _errorMessage.value = result.exceptionOrNull()?.message ?: "Connection failed"
        }
    }

    /**
     * Disconnect from the current device.
     */
    fun disconnect() {
        stopPolling()
        obdConnection.disconnect()
        _connectionState.value = ConnectionState.DISCONNECTED
        _currentMetrics.value = DrivingMetrics()
    }

    /**
     * Set the fuel type for consumption calculations.
     */
    fun setFuelType(type: FuelType) {
        fuelType = type
    }

    // ── Polling Loop ────────────────────────────────────────────

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            var cycleCount = 0
            var previousSpeed = 0.0
            var previousTimestamp = Instant.now()

            while (isActive && obdConnection.isConnected) {
                try {
                    var speed = 0.0
                    var rpm = 0
                    var throttle = 0.0
                    var maf = 0.0
                    var engineLoad = 0.0
                    var coolantTemp = 0
                    var fuelTankLevel = 0.0
                    var ambientTemp = 0
                    var batteryVoltage = 0.0

                    // Poll fast-refresh commands every cycle
                    for (cmd in pollCommands) {
                        val result = obdConnection.sendCommand(cmd)
                        if (result.isSuccess) {
                            when (cmd) {
                                is SpeedCommand -> speed = result.getOrDefault(0.0)
                                is RpmCommand -> rpm = result.getOrDefault(0.0).toInt()
                                is ThrottleCommand -> throttle = result.getOrDefault(0.0)
                                is MafCommand -> maf = result.getOrDefault(0.0)
                                is EngineLoadCommand -> engineLoad = result.getOrDefault(0.0)
                                is CoolantTempCommand -> coolantTemp = result.getOrDefault(0.0).toInt()
                            }
                        }
                    }

                    // Poll slow-refresh commands every 10th cycle
                    if (cycleCount % 10 == 0) {
                        for (cmd in slowPollCommands) {
                            val result = obdConnection.sendCommand(cmd)
                            if (result.isSuccess) {
                                when (cmd) {
                                    is FuelTankLevelCommand -> fuelTankLevel = result.getOrDefault(0.0)
                                    is AmbientTempCommand -> ambientTemp = result.getOrDefault(0.0).toInt()
                                    is BatteryVoltageCommand -> batteryVoltage = result.getOrDefault(0.0)
                                }
                            }
                        }
                    }

                    // Calculate derived metrics
                    val now = Instant.now()
                    val timeDelta = (now.toEpochMilli() - previousTimestamp.toEpochMilli()) / 1000.0
                    val acceleration = if (timeDelta > 0) {
                        ((speed - previousSpeed) / 3.6) / timeDelta
                    } else 0.0

                    val fuelRateLPerH = fuelCalculator.calculateFuelRateLPerH(maf, fuelType)
                    val fuelConsumption = fuelCalculator.calculateConsumptionLPer100Km(fuelRateLPerH, speed)

                    // Update metrics state
                    _currentMetrics.value = DrivingMetrics(
                        timestamp = now,
                        speedKmh = speed,
                        rpm = rpm,
                        throttlePercent = throttle,
                        engineLoadPercent = engineLoad,
                        mafGramsPerSec = maf,
                        fuelRateLPerH = fuelRateLPerH,
                        fuelConsumptionLPer100Km = fuelConsumption,
                        coolantTempC = coolantTemp,
                        ambientTempC = ambientTemp,
                        fuelTankPercent = fuelTankLevel,
                        batteryVoltage = batteryVoltage,
                        accelerationMPerS2 = acceleration,
                    )

                    previousSpeed = speed
                    previousTimestamp = now
                    cycleCount++

                    delay(Constants.OBD_POLL_INTERVAL_MS)
                } catch (e: Exception) {
                    _connectionState.value = ConnectionState.ERROR
                    _errorMessage.value = "Connection lost: ${e.message}"
                    break
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
