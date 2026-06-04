package com.ecodrive.app.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PermissionManagerTest {

    private val context: Context = mockk()
    private lateinit var permissionManager: PermissionManager

    @Before
    fun setup() {
        mockkStatic(ContextCompat::class)
        permissionManager = PermissionManager(context)
    }

    @After
    fun tearDown() {
        unmockkStatic(ContextCompat::class)
    }

    private fun setSdkVersion(version: Int) {
        permissionManager.sdkIntProvider = { version }
    }

    @Test
    fun `test getRequiredPermissions for Android 13`() {
        setSdkVersion(33) // Android 13 (TIRAMISU)
        val permissions = permissionManager.getRequiredPermissions()
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACTIVITY_RECOGNITION))
        assertTrue(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun `test getRequiredPermissions for Android 9`() {
        setSdkVersion(28) // Android 9 (P)
        val permissions = permissionManager.getRequiredPermissions()
        assertTrue(permissions.contains(Manifest.permission.ACCESS_FINE_LOCATION))
        assertTrue(permissions.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertFalse(permissions.contains(Manifest.permission.ACTIVITY_RECOGNITION))
        assertFalse(permissions.contains(Manifest.permission.POST_NOTIFICATIONS))
    }

    @Test
    fun `test getAutoRecordPermissions for Android 10`() {
        setSdkVersion(29) // Android 10 (Q)
        val permissions = permissionManager.getAutoRecordPermissions()
        assertTrue(permissions.contains(Manifest.permission.ACCESS_BACKGROUND_LOCATION))
    }

    @Test
    fun `test getBluetoothPermissions for Android 12`() {
        setSdkVersion(31) // Android 12 (S)
        val permissions = permissionManager.getBluetoothPermissions()
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_CONNECT))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_SCAN))
    }

    @Test
    fun `test getBluetoothPermissions for Android 11`() {
        setSdkVersion(30) // Android 11 (R)
        val permissions = permissionManager.getBluetoothPermissions()
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH))
        assertTrue(permissions.contains(Manifest.permission.BLUETOOTH_ADMIN))
    }

    @Test
    fun `test hasRequiredPermissions returns true when all granted`() {
        setSdkVersion(33)
        every { ContextCompat.checkSelfPermission(context, any()) } returns PackageManager.PERMISSION_GRANTED
        assertTrue(permissionManager.hasRequiredPermissions())
    }

    @Test
    fun `test hasRequiredPermissions returns false when one is denied`() {
        setSdkVersion(33)
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) } returns PackageManager.PERMISSION_DENIED
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) } returns PackageManager.PERMISSION_GRANTED

        assertFalse(permissionManager.hasRequiredPermissions())
    }

    @Test
    fun `test getMissingPermissions lists only denied permissions`() {
        setSdkVersion(33)
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) } returns PackageManager.PERMISSION_DENIED
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) } returns PackageManager.PERMISSION_GRANTED
        every { ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) } returns PackageManager.PERMISSION_DENIED

        val missing = permissionManager.getMissingPermissions()
        assertEquals(2, missing.size)
        assertTrue(missing.contains(Manifest.permission.ACCESS_COARSE_LOCATION))
        assertTrue(missing.contains(Manifest.permission.POST_NOTIFICATIONS))
    }
}
