package com.ecodrive.app.data.sensor

import android.location.Location
import org.junit.Assert.*
import org.junit.Test
import io.mockk.*

class LocationTrackerTest {

    @Test
    fun `test Location toGpsReading maps speed correctly`() {
        val location = mockk<Location> {
            every { hasSpeed() } returns true
            every { speed } returns 10.0f  // m/s
            every { latitude } returns 37.5
            every { longitude } returns -122.0
            every { hasAltitude() } returns true
            every { altitude } returns 50.0
            every { hasBearing() } returns true
            every { bearing } returns 180.0f
            every { hasAccuracy() } returns true
            every { accuracy } returns 5.0f
            every { time } returns 1000L
        }

        val reading = location.toGpsReading()

        assertEquals(36.0, reading.speedKmh, 0.001)  // 10 m/s * 3.6 = 36 km/h
        assertEquals(37.5, reading.latitude, 0.001)
        assertEquals(-122.0, reading.longitude, 0.001)
        assertEquals(50.0, reading.altitudeM, 0.001)
        assertEquals(180.0f, reading.bearingDegrees, 0.001f)
        assertEquals(5.0f, reading.accuracyM, 0.001f)
        assertEquals(1000L, reading.timestampMs)
        assertTrue(reading.hasSpeed)
        assertTrue(reading.hasBearing)
    }

    @Test
    fun `test Location toGpsReading sets zero speed when hasSpeed false`() {
        val location = mockk<Location> {
            every { hasSpeed() } returns false
            every { speed } returns 15.0f
            every { latitude } returns 0.0
            every { longitude } returns 0.0
            every { hasAltitude() } returns false
            every { altitude } returns 0.0
            every { hasBearing() } returns false
            every { bearing } returns 0.0f
            every { hasAccuracy() } returns false
            every { accuracy } returns 0.0f
            every { time } returns 0L
        }

        val reading = location.toGpsReading()

        assertEquals(0.0, reading.speedKmh, 0.001)
        assertFalse(reading.hasSpeed)
    }

    @Test
    fun `test Location toGpsReading sets zero altitude when hasAltitude false`() {
        val location = mockk<Location> {
            every { hasSpeed() } returns false
            every { speed } returns 0.0f
            every { latitude } returns 0.0
            every { longitude } returns 0.0
            every { hasAltitude() } returns false
            every { altitude } returns 999.0
            every { hasBearing() } returns false
            every { bearing } returns 0.0f
            every { hasAccuracy() } returns false
            every { accuracy } returns 0.0f
            every { time } returns 0L
        }

        val reading = location.toGpsReading()

        assertEquals(0.0, reading.altitudeM, 0.001)
    }

    @Test
    fun `test Location toGpsReading sets MAX_VALUE accuracy when hasAccuracy false`() {
        val location = mockk<Location> {
            every { hasSpeed() } returns false
            every { speed } returns 0.0f
            every { latitude } returns 0.0
            every { longitude } returns 0.0
            every { hasAltitude() } returns false
            every { altitude } returns 0.0
            every { hasBearing() } returns false
            every { bearing } returns 0.0f
            every { hasAccuracy() } returns false
            every { accuracy } returns 0.0f
            every { time } returns 0L
        }

        val reading = location.toGpsReading()

        assertEquals(Float.MAX_VALUE, reading.accuracyM, 0.001f)
    }
}
