package com.ecodrive.app.util

/**
 * Application-wide constants for EcoDrive.
 * Updated for the hybrid approach (Phone Sensors + Toyota API).
 */
object Constants {

    // ── 2023 Toyota Highlander Hybrid Specs ─────────────────────
    /** Vehicle curb weight in kg */
    const val VEHICLE_MASS_KG = 2090.0

    /** Aerodynamic drag coefficient */
    const val DRAG_COEFFICIENT = 0.35

    /** Frontal area in m² */
    const val FRONTAL_AREA_M2 = 2.83

    /** Rolling resistance coefficient (all-season tires on asphalt) */
    const val ROLLING_RESISTANCE = 0.012

    /** Air density at sea level in kg/m³ */
    const val AIR_DENSITY = 1.225

    /** Gravity in m/s² */
    const val GRAVITY = 9.81

    /** Mass factor for rotating components (wheels, drivetrain) */
    const val ROTATING_MASS_FACTOR = 0.05

    /** Fuel tank capacity in liters */
    const val TANK_CAPACITY_LITERS = 65.0

    /** Combined fuel efficiency rating (L/100km) from EPA */
    const val EPA_COMBINED_LPER100KM = 6.4

    /** Engine displacement in cc */
    const val ENGINE_DISPLACEMENT_CC = 2487

    // ── Driving Thresholds ──────────────────────────────────────
    /** Hard acceleration threshold in m/s² */
    const val HARD_ACCEL_THRESHOLD = 3.0

    /** Hard braking threshold in m/s² (absolute value) */
    const val HARD_BRAKE_THRESHOLD = 3.5

    /** Sharp cornering threshold in m/s² (lateral acceleration) */
    const val SHARP_TURN_THRESHOLD = 4.0

    /** Excessive speed threshold (km/h) for fuel waste */
    const val SPEED_EXCESSIVE_KMH = 110.0

    /** Optimal speed range for fuel efficiency (km/h) */
    const val SPEED_ECO_MIN_KMH = 50.0
    const val SPEED_ECO_MAX_KMH = 90.0

    /** Idle detection: speed below this (km/h) */
    const val IDLE_SPEED_THRESHOLD_KMH = 2.0

    /** Maximum idle time before warning (seconds) */
    const val IDLE_WARNING_SECONDS = 60

    /** Speed consistency threshold — std deviation in km/h */
    const val SPEED_CONSISTENCY_GOOD = 5.0
    const val SPEED_CONSISTENCY_AVERAGE = 12.0

    // ── Eco Score Weights ───────────────────────────────────────
    const val WEIGHT_ACCELERATION = 0.20
    const val WEIGHT_BRAKING = 0.20
    const val WEIGHT_SPEED = 0.20
    const val WEIGHT_CORNERING = 0.15
    const val WEIGHT_IDLE = 0.10
    const val WEIGHT_CONSISTENCY = 0.15

    // ── Sensor Configuration ────────────────────────────────────
    /** Accelerometer / Gyroscope sampling interval in microseconds (50Hz) */
    const val SENSOR_SAMPLING_INTERVAL_US = 20_000

    /** GPS update interval in milliseconds */
    const val GPS_UPDATE_INTERVAL_MS = 1000L

    /** GPS fastest update interval in milliseconds */
    const val GPS_FASTEST_INTERVAL_MS = 500L

    /** Minimum displacement for GPS update in meters */
    const val GPS_MIN_DISPLACEMENT_M = 1.0f

    /** Low-pass filter alpha for accelerometer noise reduction */
    const val ACCEL_FILTER_ALPHA = 0.8f

    /** Minimum speed to consider vehicle moving (km/h) */
    const val MOVING_SPEED_THRESHOLD_KMH = 3.0

    /** Metrics emission interval in milliseconds */
    const val METRICS_EMIT_INTERVAL_MS = 500L

    // ── Toyota API / Smartcar ───────────────────────────────────
    /** Smartcar OAuth redirect URI */
    const val SMARTCAR_REDIRECT_URI = "ecodrive://callback"

    /** Smartcar API base URL */
    const val SMARTCAR_BASE_URL = "https://api.smartcar.com/v2.0/"

    /** Polling interval for Toyota API fuel level (milliseconds) */
    const val TOYOTA_API_POLL_INTERVAL_MS = 60_000L

    // ── Fuel Calibration ────────────────────────────────────────
    /** Minimum trip distance (km) for fuel model calibration */
    const val CALIBRATION_MIN_DISTANCE_KM = 5.0

    /** Minimum fuel level change (%) for valid calibration point */
    const val CALIBRATION_MIN_FUEL_CHANGE_PERCENT = 1.0

    /** Number of trips to average for calibration factor */
    const val CALIBRATION_WINDOW_TRIPS = 10

    /** Default calibration factor (1.0 = no adjustment) */
    const val DEFAULT_CALIBRATION_FACTOR = 1.0

    // ── Foreground Service ──────────────────────────────────────
    /** Notification channel ID for foreground service */
    const val NOTIFICATION_CHANNEL_ID = "ecodrive_data_collection"

    /** Foreground service notification ID */
    const val NOTIFICATION_ID = 1001

    // ── OBD-II Configuration ────────────────────────────────────
    /** Standard Bluetooth SPP UUID */
    const val SPP_UUID = "00001101-0000-1000-8000-00805F9B34FB"

    /** ELM327 Initialization Commands */
    val ELM_INIT_COMMANDS = listOf(
        "ATZ",   // Reset
        "ATE0",  // Echo off
        "ATL0",  // Linefeeds off
        "ATSP0", // Protocol: Automatic
    )

    /** OBD polling interval in milliseconds */
    const val OBD_POLL_INTERVAL_MS = 1000L
}
