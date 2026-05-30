package com.ecodrive.app.data.obd

/**
 * Represents an OBD-II command to send to the ELM327 adapter.
 * Each command encapsulates its PID, display name, and response parsing logic.
 */
abstract class ObdCommand(
    val pid: String,
    val displayName: String,
) {
    /** Build the raw command string to send over Bluetooth */
    open fun buildCommand(): String = pid

    /** Parse the hex response bytes into a meaningful value */
    abstract fun parseResponse(rawResponse: String): Double

    /**
     * Clean the raw response string by removing whitespace,
     * prompt characters, and the echoed command.
     */
    internal fun cleanResponse(raw: String): String {
        return raw
            .replace("\\s".toRegex(), "")
            .replace(">", "")
            .replace("SEARCHING...", "")
            .replace("NODATA", "")
            .let { cleaned ->
                // Remove echoed command if present
                val pidNoSpaces = pid.replace(" ", "")
                if (cleaned.startsWith(pidNoSpaces)) {
                    cleaned.removePrefix(pidNoSpaces)
                } else {
                    cleaned
                }
            }
    }

    /**
     * Extract data bytes from response after the mode+PID header.
     * For Mode 01 responses, the header is "41XX" where XX is the PID.
     *
     * @throws ObdException if response format is invalid (e.g., negative response)
     */
    protected fun extractDataBytes(raw: String): List<Int> {
        val cleaned = cleanResponse(raw)
        
        // ── Response Format Validation ─────────────────────────
        // Valid: "41XX" (positive response)
        // Invalid: "7FXX" (negative response), "61XX" (different ECU)
        if (cleaned.length < 4) {
            return emptyList()  // Insufficient data
        }
        
        val pidByte = pid.takeLast(2)
        val headerPrefix = "41$pidByte"
        val headerIndex = cleaned.indexOf(headerPrefix, ignoreCase = true)
        
        if (headerIndex == -1) {
            // Check for negative response (7FXX)
            if (cleaned.startsWith("7F", ignoreCase = true)) {
                throw ObdException("Negative response (unsupported PID): $cleaned")
            }
            // Check for other invalid response formats
            if (cleaned.matches(Regex("[0-9A-Fa-f]{2}[0-9A-Fa-f]{2}.*"))) {
                // Has response header but not "41XX"
                val responseMode = cleaned.substring(0, 2)
                throw ObdException("Invalid response format: expected 41, got $responseMode")
            }
            return emptyList()
        }

        val dataHex = cleaned.substring(headerIndex + headerPrefix.length)
        return dataHex.chunked(2).mapNotNull { hex ->
            try {
                hex.toInt(16)
            } catch (e: NumberFormatException) {
                null
            }
        }
    }
}

// ── Concrete OBD Commands ───────────────────────────────────────

/** Vehicle Speed (PID 010D) → km/h */
class SpeedCommand : ObdCommand("010D", "Vehicle Speed") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.isEmpty()) return 0.0
        return bytes[0].toDouble() // A (single byte, 0-255 km/h)
    }
}

/** Engine RPM (PID 010C) → RPM */
class RpmCommand : ObdCommand("010C", "Engine RPM") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.size < 2) return 0.0
        return ((bytes[0] * 256) + bytes[1]) / 4.0 // ((A*256)+B)/4
    }
}

/** Throttle Position (PID 0111) → % */
class ThrottleCommand : ObdCommand("0111", "Throttle Position") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.isEmpty()) return 0.0
        return bytes[0] * 100.0 / 255.0 // A*100/255
    }
}

/** MAF Air Flow Rate (PID 0110) → grams/sec */
class MafCommand : ObdCommand("0110", "MAF Air Flow Rate") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.size < 2) return 0.0
        return ((bytes[0] * 256) + bytes[1]) / 100.0 // ((A*256)+B)/100
    }
}

/** Engine Coolant Temperature (PID 0105) → °C */
class CoolantTempCommand : ObdCommand("0105", "Coolant Temp") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.isEmpty()) return 0.0
        return bytes[0] - 40.0 // A-40
    }
}

/** Calculated Engine Load (PID 0104) → % */
class EngineLoadCommand : ObdCommand("0104", "Engine Load") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.isEmpty()) return 0.0
        return bytes[0] * 100.0 / 255.0 // A*100/255
    }
}

/** Engine Fuel Rate (PID 015E) → L/h (not supported on all vehicles) */
class FuelRateCommand : ObdCommand("015E", "Fuel Rate") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.size < 2) return 0.0
        return ((bytes[0] * 256) + bytes[1]) / 20.0 // ((A*256)+B)/20
    }
}

/** Fuel Tank Level Input (PID 012F) → % */
class FuelTankLevelCommand : ObdCommand("012F", "Fuel Tank Level") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.isEmpty()) return 0.0
        return bytes[0] * 100.0 / 255.0 // A*100/255
    }
}

/** Ambient Air Temperature (PID 0146) → °C */
class AmbientTempCommand : ObdCommand("0146", "Ambient Temp") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.isEmpty()) return 0.0
        return bytes[0] - 40.0 // A-40
    }
}

/** Control Module Voltage (PID 0142) → V */
class BatteryVoltageCommand : ObdCommand("0142", "Battery Voltage") {
    override fun parseResponse(rawResponse: String): Double {
        val bytes = extractDataBytes(rawResponse)
        if (bytes.size < 2) return 0.0
        return ((bytes[0] * 256) + bytes[1]) / 1000.0 // ((A*256)+B)/1000
    }
}
