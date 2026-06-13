package com.ecodrive.app.data.remote

import com.ecodrive.app.util.Constants
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class SmartcarApiClientTest {

    private lateinit var smartcarApiClient: SmartcarApiClient

    @Before
    fun setup() {
        smartcarApiClient = SmartcarApiClient()
    }

    @Test
    fun `test getAuthUrl includes make when provided`() {
        // Given
        val clientId = "test-client-id"
        val make = "ford"

        // When
        val url = smartcarApiClient.getAuthUrl(clientId, make)

        // Then
        assertTrue(url.contains("make=FORD"))
        // Smartcar Connect requires the raw ID without the client_ prefix
        assertTrue(url.contains("client_id=test-client-id"))
    }

    @Test
    fun `test getAuthUrl omits make when null`() {
        // Given
        val clientId = "test-client-id"

        // When
        val url = smartcarApiClient.getAuthUrl(clientId, null)

        // Then
        assertTrue(!url.contains("make="))
        // Smartcar Connect requires the raw ID without the client_ prefix
        assertTrue(url.contains("client_id=test-client-id"))
    }

    @Test
    fun `test getAuthUrl uses existing client_ prefix`() {
        // Given
        val clientId = "client_test-client-id"

        // When
        val url = smartcarApiClient.getAuthUrl(clientId, null)

        // Then
        // Must strip the client_ prefix
        assertTrue(url.contains("client_id=test-client-id"))
        assertTrue(!url.contains("client_id=client_test-client-id"))
    }

    @Test
    fun `test getAuthUrl includes required scopes`() {
        // Given
        val clientId = "test-client-id"

        // When
        val url = smartcarApiClient.getAuthUrl(clientId)

        // Then
        assertTrue(url.contains("read_vehicle_info"))
        assertTrue(url.contains("read_fuel"))
        assertTrue(url.contains("read_odometer"))
        assertTrue(url.contains("read_tires"))
        assertTrue(url.contains("read_location"))
    }
}
