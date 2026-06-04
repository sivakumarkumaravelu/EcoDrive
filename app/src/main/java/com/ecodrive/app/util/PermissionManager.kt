package com.ecodrive.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Centralized runtime permission management for EcoDrive.
 *
 * Required permissions:
 *   - ACCESS_FINE_LOCATION (GPS speed + position)
 *   - POST_NOTIFICATIONS (foreground service notification, Android 13+)
 *
 * Optional permissions:
 *   - BLUETOOTH_CONNECT, BLUETOOTH_SCAN (OBD-II pro feature)
 */
@Singleton
class PermissionManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    internal var sdkIntProvider: () -> Int = { Build.VERSION.SDK_INT }
    /**
     * Core permissions required for basic sensor data collection.
     */
    fun getRequiredPermissions(): List<String> {
        val permissions = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

        // Android 10+ requires ACTIVITY_RECOGNITION
        if (sdkIntProvider() >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACTIVITY_RECOGNITION)
        }

        // Android 13+ requires POST_NOTIFICATIONS for foreground service
        if (sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        return permissions
    }

    /**
     * Additional permissions required for auto-recording in the background.
     */
    fun getAutoRecordPermissions(): List<String> {
        val permissions = mutableListOf<String>()
        if (sdkIntProvider() >= Build.VERSION_CODES.Q) {
            permissions.add(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
        return permissions
    }

    /**
     * Optional permissions for OBD-II pro feature.
     */
    fun getBluetoothPermissions(): List<String> {
        return if (sdkIntProvider() >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.BLUETOOTH_SCAN,
            )
        } else {
            listOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
            )
        }
    }

    /**
     * Check if all required permissions are granted.
     */
    fun hasRequiredPermissions(): Boolean {
        return getRequiredPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Check if location permission is granted.
     */
    fun hasLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Check if notification permission is granted (always true pre-Android 13).
     */
    fun hasNotificationPermission(): Boolean {
        return if (sdkIntProvider() >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if background location permission is granted (required for auto-record).
     */
    fun hasBackgroundLocationPermission(): Boolean {
        return if (sdkIntProvider() >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if activity recognition permission is granted.
     */
    fun hasActivityRecognitionPermission(): Boolean {
        return if (sdkIntProvider() >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Check if Bluetooth permissions are granted (for optional OBD).
     */
    fun hasBluetoothPermissions(): Boolean {
        return getBluetoothPermissions().all { permission ->
            ContextCompat.checkSelfPermission(context, permission) ==
                    PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * Get the list of permissions that still need to be requested.
     */
    fun getMissingPermissions(): List<String> {
        return getRequiredPermissions().filter { permission ->
            ContextCompat.checkSelfPermission(context, permission) !=
                    PackageManager.PERMISSION_GRANTED
        }
    }
}
