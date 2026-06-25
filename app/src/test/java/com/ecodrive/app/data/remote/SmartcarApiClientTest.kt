package com.ecodrive.app.data.remote

import com.ecodrive.app.util.Constants
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmartcarApiClientTest {

    private lateinit var smartcarApiClient: SmartcarApiClient

    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

    @Before
    fun setup() {
        smartcarApiClient = SmartcarApiClient(applicationScope)
    }

    @Test
    fun `test getAuthUrl includes make when provided`() {
        val appId = "test_app_id"
        val make = "FORD"

        val url = smartcarApiClient.getAuthUrl(appId, make)

        assertTrue("URL should contain client_id", url.contains("client_id=test_app_id"))
        assertTrue("URL should contain make=FORD", url.contains("make=FORD"))
        assertTrue("URL should contain single_select", url.contains("single_select=true"))
    }

    @Test
    fun `test getAuthUrl omits make when null`() {
        val appId = "test_app_id"

        val url = smartcarApiClient.getAuthUrl(appId, null)

        assertTrue("URL should contain client_id", url.contains("client_id=test_app_id"))
        assertTrue("URL should not contain make", !url.contains("make="))
    }

    @Test
    fun `test getAuthUrl uses existing app id`() {
        val appId = "fa9028be-a5c6-4c9b-8ca8-8289e90c701c"

        val url = smartcarApiClient.getAuthUrl(appId, null)

        assertTrue("URL should contain exact app id", url.contains("client_id=fa9028be-a5c6-4c9b-8ca8-8289e90c701c"))
        assertTrue("URL should not contain make", !url.contains("make="))
    }

    @Test
    fun `test getAuthUrl includes required scopes`() {
        val appId = "test_app_id"

        val url = smartcarApiClient.getAuthUrl(appId)

        // Then
        assertTrue(url.contains("read_vehicle_info"))
        assertTrue(url.contains("read_fuel"))
        assertTrue(url.contains("read_odometer"))
        assertTrue(url.contains("read_tires"))
        assertTrue(url.contains("read_location"))
    }
}
