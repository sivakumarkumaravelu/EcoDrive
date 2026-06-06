package com.ecodrive.app.data.sensor

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import com.ecodrive.app.util.Constants
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

/**
 * Raw accelerometer + gyroscope + barometer data from the phone's IMU.
 * All values are in the vehicle's reference frame after rotation correction.
 */
data class ImuReading(
    val timestampNs: Long,
    /** Forward/backward acceleration in m/s² (positive = forward) */
    val longitudinalAccel: Double,
    /** Left/right acceleration in m/s² (positive = right) */
    val lateralAccel: Double,
    /** Up/down acceleration in m/s² (positive = up, gravity-compensated) */
    val verticalAccel: Double,
    /** Rotation rate around the vertical axis in rad/s (yaw) */
    val yawRate: Double,
    /** Atmospheric pressure in hPa (for road grade estimation) */
    val pressureHpa: Double?,
)

/**
 * Manages the phone's inertial measurement unit (IMU) sensors:
 * accelerometer, gyroscope, and barometer.
 *
 * Applies a low-pass filter to remove noise and uses the rotation vector
 * sensor to transform accelerometer readings from the phone's arbitrary
 * orientation into the vehicle's reference frame.
 *
 * This allows the app to work regardless of how the phone is mounted
 * or placed in the car.
 */
@Singleton
class PhoneSensorManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "PhoneSensorManager"
    }

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager

    val hasAccelerometer: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null

    val hasGyroscope: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE) != null

    val hasBarometer: Boolean
        get() = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE) != null

    /**
     * Emits a continuous stream of [ImuReading] values at ~50Hz.
     *
     * Uses the rotation vector sensor to automatically compensate for
     * the phone's orientation, so the accelerometer values are always
     * in the vehicle's frame of reference:
     *   - X = lateral (cornering)
     *   - Y = longitudinal (braking / acceleration)
     *   - Z = vertical (bumps)
     */
    fun imuFlow(): Flow<ImuReading> = callbackFlow {
        val rotationMatrix = FloatArray(9)
        val orientedAccel = FloatArray(3)

        // Low-pass filtered values
        var filteredLong = 0.0
        var filteredLat = 0.0
        var filteredVert = 0.0
        var latestYawRate = 0.0
        var latestPressure: Double? = null
        var lastRotationMatrix: FloatArray? = null

        val alpha = Constants.ACCEL_FILTER_ALPHA

        // ── Rotation Vector Listener (for orientation correction) ────
        val rotationListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
                lastRotationMatrix = rotationMatrix.copyOf()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // ── Accelerometer Listener ──────────────────────────────────
        val accelListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val rm = lastRotationMatrix
                if (rm != null) {
                    // Transform to world coordinates then extract vehicle axes
                    // After rotation: [0]=East, [1]=North, [2]=Up (world frame)
                    orientedAccel[0] = rm[0] * event.values[0] + rm[1] * event.values[1] + rm[2] * event.values[2]
                    orientedAccel[1] = rm[3] * event.values[0] + rm[4] * event.values[1] + rm[5] * event.values[2]
                    orientedAccel[2] = rm[6] * event.values[0] + rm[7] * event.values[1] + rm[8] * event.values[2]

                    // Remove gravity from vertical axis
                    val vertRaw = orientedAccel[2] - SensorManager.GRAVITY_EARTH

                    // In a vehicle, "forward" roughly maps to North (orientedAccel[1])
                    // and "lateral" to East (orientedAccel[0]).
                    // The actual forward direction is refined using GPS bearing later.
                    val longRaw = orientedAccel[1].toDouble()
                    val latRaw = orientedAccel[0].toDouble()

                    // Apply low-pass filter
                    filteredLong = alpha * filteredLong + (1 - alpha) * longRaw
                    filteredLat = alpha * filteredLat + (1 - alpha) * latRaw
                    filteredVert = alpha * filteredVert + (1 - alpha) * vertRaw

                    val reading = ImuReading(
                        timestampNs = event.timestamp,
                        longitudinalAccel = filteredLong,
                        lateralAccel = filteredLat,
                        verticalAccel = filteredVert,
                        yawRate = latestYawRate,
                        pressureHpa = latestPressure,
                    )
                    trySend(reading)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // ── Gyroscope Listener ──────────────────────────────────────
        val gyroListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                // Z-axis rotation rate = yaw (turning)
                latestYawRate = event.values[2].toDouble()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // ── Barometer Listener ──────────────────────────────────────
        val pressureListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                latestPressure = event.values[0].toDouble()
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        // Register all sensors
        val interval = Constants.SENSOR_SAMPLING_INTERVAL_US

        sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)?.let {
            sensorManager.registerListener(rotationListener, it, interval)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager.registerListener(accelListener, it, interval)
        } ?: run {
            Log.e(TAG, "Accelerometer not available")
            close(IllegalStateException("Accelerometer required"))
            return@callbackFlow
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let {
            sensorManager.registerListener(gyroListener, it, interval)
        }
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)?.let {
            sensorManager.registerListener(pressureListener, it, SensorManager.SENSOR_DELAY_NORMAL)
        }

        Log.i(TAG, "IMU sensors registered (accel=✓, gyro=${hasGyroscope}, baro=${hasBarometer})")

        awaitClose {
            sensorManager.unregisterListener(rotationListener)
            sensorManager.unregisterListener(accelListener)
            sensorManager.unregisterListener(gyroListener)
            sensorManager.unregisterListener(pressureListener)
            Log.i(TAG, "IMU sensors unregistered")
        }
    }
}
