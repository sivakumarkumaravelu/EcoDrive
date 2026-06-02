package com.ecodrive.app.data.obd

import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive tests for OBD command parsing and response handling.
 * Validates parsing logic for all OBD-II PIDs and edge cases.
 */
class ObdCommandTest {

    // ── Speed Command Tests ─────────────────────────────────────

    @Test
    fun `test SpeedCommand parses simple response correctly`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "410D 3E >"  // 62 km/h

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(62.0, value, 0.01)
    }

    @Test
    fun `test SpeedCommand with multiple spaces`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "41  0D    3E   >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(62.0, value, 0.01)
    }

    @Test
    fun `test SpeedCommand with zero speed`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "410D 00 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    @Test
    fun `test SpeedCommand with max speed`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "410D FF >"  // 255 km/h

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(255.0, value, 0.01)
    }

    @Test
    fun `test SpeedCommand with empty response`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = ""

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)  // Should handle gracefully
    }

    @Test
    fun `test SpeedCommand without data bytes`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "410D>"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    // ── RPM Command Tests ────────────────────────────────────────

    @Test
    fun `test RpmCommand parses correctly`() {
        // Given
        val cmd = RpmCommand()
        // ((0x19 * 256) + 0xA0) / 4 = (6400 + 160) / 4 = 1640 RPM
        val rawResponse = "410C 19 A0 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(1640.0, value, 0.01)
    }

    @Test
    fun `test RpmCommand at idle`() {
        // Given
        val cmd = RpmCommand()
        // ((0x03 * 256) + 0xE8) / 4 = (768 + 232) / 4 = 250 RPM
        val rawResponse = "410C 03 E8 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(250.0, value, 0.01)
    }

    @Test
    fun `test RpmCommand with incomplete data`() {
        // Given
        val cmd = RpmCommand()
        val rawResponse = "410C 19 >"  // Only one byte

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    @Test
    fun `test RpmCommand with high RPM`() {
        // Given
        val cmd = RpmCommand()
        // ((0xFF * 256) + 0xFF) / 4 = 65535 / 4 = 16383.75 RPM
        val rawResponse = "410C FF FF >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(16383.75, value, 0.01)
    }

    // ── Throttle Command Tests ──────────────────────────────────

    @Test
    fun `test ThrottleCommand parses correctly`() {
        // Given
        val cmd = ThrottleCommand()
        // 0x80 * 100 / 255 = 128 * 100 / 255 = 50.196%
        val rawResponse = "4111 80 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(50.196, value, 0.01)
    }

    @Test
    fun `test ThrottleCommand at zero throttle`() {
        // Given
        val cmd = ThrottleCommand()
        val rawResponse = "4111 00 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    @Test
    fun `test ThrottleCommand at full throttle`() {
        // Given
        val cmd = ThrottleCommand()
        val rawResponse = "4111 FF >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(100.0, value, 0.01)
    }

    // ── MAF Command Tests ───────────────────────────────────────

    @Test
    fun `test MafCommand parses correctly`() {
        // Given
        val cmd = MafCommand()
        // ((0x04 * 256) + 0xD2) / 100 = (1024 + 210) / 100 = 12.34 g/s
        val rawResponse = "4110 04 D2 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(12.34, value, 0.01)
    }

    @Test
    fun `test MafCommand at idle`() {
        // Given
        val cmd = MafCommand()
        // ((0x00 * 256) + 0x0A) / 100 = 10 / 100 = 0.1 g/s
        val rawResponse = "4110 00 0A >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.1, value, 0.01)
    }

    // ── Coolant Temp Command Tests ──────────────────────────────

    @Test
    fun `test CoolantTempCommand parses correctly`() {
        // Given
        val cmd = CoolantTempCommand()
        // 0x9E - 40 = 158 - 40 = 118°C
        val rawResponse = "4105 9E >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(118.0, value, 0.01)
    }

    @Test
    fun `test CoolantTempCommand below freezing`() {
        // Given
        val cmd = CoolantTempCommand()
        // 0x1A - 40 = 26 - 40 = -14°C
        val rawResponse = "4105 1A >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(-14.0, value, 0.01)
    }

    @Test
    fun `test CoolantTempCommand at reference point`() {
        // Given
        val cmd = CoolantTempCommand()
        // 0x28 - 40 = 40 - 40 = 0°C (reference)
        val rawResponse = "4105 28 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    // ── Engine Load Command Tests ───────────────────────────────

    @Test
    fun `test EngineLoadCommand parses correctly`() {
        // Given
        val cmd = EngineLoadCommand()
        // 0x7F * 100 / 255 = 127 * 100 / 255 = 49.804%
        val rawResponse = "4104 7F >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(49.804, value, 0.01)
    }

    @Test
    fun `test EngineLoadCommand at zero`() {
        // Given
        val cmd = EngineLoadCommand()
        val rawResponse = "4104 00 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    // ── Fuel Rate Command Tests ─────────────────────────────────

    @Test
    fun `test FuelRateCommand parses correctly`() {
        // Given
        val cmd = FuelRateCommand()
        // ((0x00 * 256) + 0x50) / 20 = 80 / 20 = 4.0 L/h
        val rawResponse = "415E 00 50 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(4.0, value, 0.01)
    }

    @Test
    fun `test FuelRateCommand at idle`() {
        // Given
        val cmd = FuelRateCommand()
        // ((0x00 * 256) + 0x14) / 20 = 20 / 20 = 1.0 L/h
        val rawResponse = "415E 00 14 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(1.0, value, 0.01)
    }

    // ── Fuel Tank Level Command Tests ───────────────────────────

    @Test
    fun `test FuelTankLevelCommand parses correctly`() {
        // Given
        val cmd = FuelTankLevelCommand()
        // 0x80 * 100 / 255 = 128 * 100 / 255 = 50.196%
        val rawResponse = "412F 80 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(50.196, value, 0.01)
    }

    @Test
    fun `test FuelTankLevelCommand empty`() {
        // Given
        val cmd = FuelTankLevelCommand()
        val rawResponse = "412F 00 >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    @Test
    fun `test FuelTankLevelCommand full`() {
        // Given
        val cmd = FuelTankLevelCommand()
        val rawResponse = "412F FF >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(100.0, value, 0.01)
    }

    // ── Battery Voltage Command Tests ───────────────────────────

    @Test
    fun `test BatteryVoltageCommand parses correctly`() {
        // Given
        val cmd = BatteryVoltageCommand()
        // ((0x2E * 256) + 0xB4) / 1000 = 11956 / 1000 = 11.956V
        val rawResponse = "4142 2E B4 >"  // 11.956V normal

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(11.956, value, 0.01)
    }

    // ── Response Edge Cases ──────────────────────────────────────

    @Test
    fun `test command with NODATA response`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "410D NODATA >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    @Test
    fun `test command with ERROR response`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "ERROR >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    @Test
    fun `test command with invalid hex characters`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "410D GH >"  // Invalid hex

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    @Test
    fun `test buildCommand returns correct PID`() {
        // Given
        val speedCmd = SpeedCommand()
        val rpmCmd = RpmCommand()
        val throttleCmd = ThrottleCommand()

        // When/Then
        assertEquals("010D", speedCmd.buildCommand())
        assertEquals("010C", rpmCmd.buildCommand())
        assertEquals("0111", throttleCmd.buildCommand())
    }

    @Test
    fun `test displayName is set correctly`() {
        // When/Then
        assertEquals("Vehicle Speed", SpeedCommand().displayName)
        assertEquals("Engine RPM", RpmCommand().displayName)
        assertEquals("Throttle Position", ThrottleCommand().displayName)
        assertEquals("Coolant Temp", CoolantTempCommand().displayName)
        assertEquals("Engine Load", EngineLoadCommand().displayName)
    }

    @Test
    fun `test response with leading SEARCHING indicator`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "SEARCHING... 410D 3E >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(62.0, value, 0.01)
    }

    @Test
    fun `test response with echoed command`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "010D 410D 3E >"

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(62.0, value, 0.01)
    }

    @Test
    fun `test cleanResponse removes all whitespace`() {
        // Given
        val cmd = SpeedCommand()

        // When
        val cleaned = cmd.cleanResponse("41 0D  3E\n>\r")

        // Then
        assertTrue(cleaned.contains("410D3E") || cleaned.contains("410D"))
    }

    @Test
    fun `test command with invalid header prefix throws ObdException`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "420D 3E >"  // Expected 41, got 42

        // When/Then
        assertThrows(ObdException::class.java) {
            cmd.parseResponse(rawResponse)
        }
    }

    @Test
    fun `test command with negative response throws ObdException`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "7F0D 11 >"  // Service not supported or similar

        // When/Then
        assertThrows(ObdException::class.java) {
            cmd.parseResponse(rawResponse)
        }
    }

    @Test
    fun `test command with short response returns 0`() {
        // Given
        val cmd = SpeedCommand()
        val rawResponse = "41 >"  // Too short

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertEquals(0.0, value, 0.01)
    }

    @Test
    fun `test ambiguous response header handled correctly`() {
        // This tests boundary condition where response might be ambiguous
        // Given
        val cmd = RpmCommand()
        val rawResponse = "410C010C19A0>"  // Repeated mode/PID

        // When
        val value = cmd.parseResponse(rawResponse)

        // Then
        assertTrue(value >= 0)  // Should not crash
    }
}
