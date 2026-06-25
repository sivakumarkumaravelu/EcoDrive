package com.ecodrive.app.data.sensor

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.util.Log
import com.ecodrive.app.util.Constants
import com.google.android.gms.location.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * GPS location data relevant to driving analysis.
 */
data class GpsReading(
    val timestampMs: Long,
    /** Speed in km/h (from GPS Doppler, very accurate) */
    val speedKmh: Double,
    val latitude: Double,
    val longitude: Double,
    /** Altitude above sea level in meters */
    val altitudeM: Double,
    /** Direction of travel in degrees (0 = North, 90 = East) */
    val bearingDegrees: Float,
    /** Estimated accuracy of the fix in meters */
    val accuracyM: Float,
    /** Whether speed data is from the GPS hardware */
    val hasSpeed: Boolean,
    /** Whether bearing data is from the GPS hardware */
    val hasBearing: Boolean,
)

/**
 * Manages GPS location updates using the Fused Location Provider
 * from Google Play Services for maximum accuracy and battery efficiency.
 *
 * GPS Doppler-derived speed is typically accurate to ±0.1 m/s (±0.36 km/h)
 * and is the primary source for vehicle speed in EcoDrive.
 */
@Singleton
class LocationTracker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "LocationTracker"
    }

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    /**
     * Emits GPS readings at ~1Hz (configurable via Constants).
     * Uses high-accuracy priority for best speed readings.
     *
     * Requires ACCESS_FINE_LOCATION permission granted before calling.
     */
    @SuppressLint("MissingPermission")
    fun locationFlow(): Flow<GpsReading> = callbackFlow {
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            Constants.GPS_UPDATE_INTERVAL_MS,
        )
            .setMinUpdateIntervalMillis(Constants.GPS_FASTEST_INTERVAL_MS)
            .setMinUpdateDistanceMeters(Constants.GPS_MIN_DISPLACEMENT_M)
            .setWaitForAccurateLocation(false)
            .build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(result: LocationResult) {
                result.lastLocation?.let { location ->
                    val reading = location.toGpsReading()
                    trySend(reading)
                }
            }
        }

        // D08: Use a dedicated background HandlerThread for GPS callbacks so the
        // main thread is never blocked by 1Hz location processing.
        val gpsThread = HandlerThread("ecodrive-gps-worker").also { it.start() }
        val gpsHandler = Handler(gpsThread.looper)

        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            callback,
            gpsThread.looper,
        ).addOnFailureListener { e ->
            close(e)
        }

        Log.i(TAG, "GPS location updates started on background thread (interval=${Constants.GPS_UPDATE_INTERVAL_MS}ms)")

        awaitClose {
            fusedLocationClient.removeLocationUpdates(callback)
            gpsThread.quitSafely()
            Log.i(TAG, "GPS location updates stopped")
        }
    }

    /**
     * Get the last known location (if available) without starting updates.
     */
    @SuppressLint("MissingPermission")
    suspend fun getLastLocation(): Location? {
        return try {
            fusedLocationClient.lastLocation.await()
        } catch (e: Exception) {
            Log.w(TAG, "Could not get last location: ${e.message}")
            null
        }
    }
}

/**
 * Convert Android Location to our GpsReading model.
 */
fun Location.toGpsReading(): GpsReading {
    return GpsReading(
        timestampMs = time,
        speedKmh = if (hasSpeed()) speed * 3.6 else 0.0,  // m/s → km/h
        latitude = latitude,
        longitude = longitude,
        altitudeM = if (hasAltitude()) altitude else 0.0,
        bearingDegrees = if (hasBearing()) bearing else 0f,
        accuracyM = if (hasAccuracy()) accuracy else Float.MAX_VALUE,
        hasSpeed = hasSpeed(),
        hasBearing = hasBearing(),
    )
}
