# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EcoDrive is an Android application that provides real-time eco-driving coaching using a hybrid data architecture combining **local phone sensors** (accelerometer, gyroscope, GPS) with the **Toyota Connected Services API**. The app estimates fuel efficiency without OBD-II hardware using a Vehicle Specific Power (VSP) physics model tuned for the 2023 Toyota Highlander Hybrid.

**Tech Stack:**
- **Language:** Kotlin
- **UI:** Jetpack Compose & Material 3
- **DI:** Hilt/Dagger
- **Local Storage:** Room Database & DataStore
- **Async:** Kotlin Coroutines & Flows
- **Testing:** JUnit 4

## Build & Compilation

```bash
# Build debug APK
./gradlew clean build

# Build and install debug APK to device/emulator
./gradlew installDebug

# Build release APK (minified)
./gradlew assembleRelease
```

**Build Configuration:**
- Min SDK: 26, Target SDK: 34, Compile SDK: 34
- Java/Kotlin: JDK 17, Kotlin 1.9.22
- Gradle: 8.2.2
- Compose compiler: 1.5.8

## Testing

```bash
# Run all unit tests
./gradlew test

# Run tests with coverage report
./gradlew testDebugUnitTestCoverage

# Run specific test class
./gradlew test --tests "ObdCommandTest"

# Run tests matching pattern
./gradlew test --tests "*Analyzer*"

# Run single test method
./gradlew test --tests "ObdCommandTest::testSpeedCommandParsesSimpleResponse"
```

**Test Organization:**
- Unit tests: `app/src/test/java/com/ecodrive/app/`
- Test categories: `data/obd/`, `sensor/`, `domain/analyzer/`
- Current coverage: 184 tests (28% baseline → 65% after recent additions)

## Code Architecture

### Three-Tier Data Architecture

1. **Local Phone Sensors** (Primary)
   - Location: `sensor/` package
   - Components: `PhoneSensorManager`, `LocationTracker`, `SensorDataManager`
   - Provides: GPS, accelerometer, gyroscope data in real-time

2. **Toyota Connected Services API** (Supplementary)
   - Location: `data/remote/ToyotaApiClient`
   - Purpose: Fetch fuel levels, calibration factors for self-tuning

3. **OBD-II** (Optional)
   - Location: `data/obd/` package
   - Status: Optional pro feature, not required for core functionality
   - Components: `ObdConnection`, `BluetoothConnectionManager`

### Core Domain Logic (`domain/analyzer/Analyzers.kt`)

This file contains the three critical business components:

**DrivingPatternAnalyzer:**
- Detects poor driving behaviors (hard braking, rapid acceleration, sharp cornering)
- Thresholds configured in `util/Constants.kt` (e.g., `-3.5 m/s²` hard brake, `3.0 m/s²` acceleration)
- Flags excessive idling (>60 seconds stationary)

**FuelEstimationEngine:**
- Implements VSP (Vehicle Specific Power) physics model
- Vehicle constants hardcoded: 2023 Highlander Hybrid (mass=2090kg, drag=0.35)
- Outputs: L/h (liters per hour) consumption estimate
- **Self-calibrating:** Uses Toyota API data to adjust internal correction factor over time
- Applies hybrid efficiency bonus (mostly electric <30km/h)

**EcoScoreCalculator:**
- Generates 0-100 driving score
- Weights: Speed consistency (20%), Braking (20%), Acceleration (20%), other factors
- Heavily penalizes speeding and harsh events

### Service Layer

**Foreground Services** (ensures background execution while driving):
- `SensorForegroundService`: Manages sensor data collection lifecycle
- `ObdForegroundService`: Manages OBD polling (if connected)

**Bluetooth Management:**
- `BluetoothConnectionManager`: Handles OBD adapter connection/reconnection
- Implements error recovery and polling retry logic

### Data Layer (`data/`)

**Room Database** (`local/EcoDriveDatabase`):
- DAOs: `TripDao`, `DrivingEventDao`, `DataPointDao`, `VehicleDao`, `FuelCalibrationDao`
- Stores: trips, driving events (harsh braking/acceleration), sensor data points, fuel calibration

**OBD Command Format** (`data/obd/ObdCommand`):
- Parses OBD-II ISO-TP protocol responses
- Handles positive (61XX) and negative (7FXX) responses
- Critical: Must validate response format to prevent crashes

### UI Layer (`ui/`)

- **Compose-based** screens in `screens/`
- **Components:** Charts (Vico), dashboards, real-time metric displays
- **Navigation:** Type-safe navigation with sealed routes

## Critical Implementation Notes

### Sensor Fusion Timing
The `SensorDataManager` must synchronize GPS and IMU data collection rates. GPS updates ~1/sec, IMU can be 50+ Hz—data alignment is critical for accurate VSP calculations.

### Calibration Persistence
The `FuelEstimationEngine`'s correction factor must persist across app restarts. This is stored via `FuelCalibrationDao`. Loss of calibration resets model accuracy.

### OBD Response Validation
OBD responses include error codes in 7FXX format. The `ObdCommand.extractDataBytes()` method must validate before parsing to avoid crashes on malformed responses.

### Hybrid Efficiency Logic
The VSP model applies efficiency bonuses for hybrid operation. Thresholds and bonus values are critical—see `FuelEstimationEngine.calculateInstantaneousFuelConsumption()` for exact application.

## Development Workflow

### Adding a New Feature

1. Define domain logic in `domain/analyzer/` if it's a calculation/algorithm
2. Add data persistence in `data/local/` if it requires storage
3. Expose via service layer (`service/` or `sensor/` packages)
4. Build UI in Compose screens (`ui/screens/`)
5. Wire dependencies in `di/AppModule.kt` (Hilt providers)
6. Add unit tests alongside feature code

### Modifying Sensor Collection

- Changes to `PhoneSensorManager` or `LocationTracker` affect all downstream consumers
- Test with `SensorDataManagerTest` to verify fusion timing
- Ensure foreground service lifecycle in `SensorForegroundService` remains robust

### Updating Physics Model

- Vehicle constants: Edit `FuelEstimationEngine` (mass, drag, efficiency curve)
- Recalibrate if vehicle spec changes
- Add regression tests to `AnalyzersExtendedTest` for edge cases

## Key Files Reference

| File | Purpose |
|------|---------|
| `util/Constants.kt` | Thresholds: braking (-3.5 m/s²), acceleration (3.0 m/s²), idling (60s) |
| `domain/analyzer/Analyzers.kt` | Core logic: DrivingPatternAnalyzer, FuelEstimationEngine, EcoScoreCalculator |
| `sensor/SensorDataManager.kt` | GPS/IMU fusion orchestration |
| `sensor/PhoneSensorManager.kt` | Phone accelerometer/gyroscope + low-pass filtering |
| `data/obd/ObdCommand.kt` | OBD protocol parsing (ISO-TP format) |
| `service/BluetoothConnectionManager.kt` | Bluetooth lifecycle + error recovery |
| `data/local/EcoDriveDatabase.kt` | Room database schema + DAOs |
| `di/AppModule.kt` | Dependency injection providers |

## Documentation

- **Deep Analysis:** `docs/ecodrive_code_analysis.md` — Architecture & physics model details
- **Test Strategy:** `docs/TEST_STRATEGY.md` — Test organization & execution guide
- **Executive Summary:** `docs/EXECUTIVE_SUMMARY.md` — High-level findings & roadmap
- **Analysis Report:** `docs/ANALYSIS_REPORT.md` — Comprehensive feature validation

## Known Critical Issues (Phase 1 Priority)

1. **OBD Response Format Validation:** `ObdCommand.extractDataBytes()` doesn't validate 7FXX error responses
2. **OBD Error Recovery:** `BluetoothConnectionManager.startPolling()` lacks retry logic on failure
3. **Sensor Fusion Timing:** GPS/IMU data misalignment in `SensorDataManager`
4. **Calibration Persistence:** Fuel calibration factor lost on app restart

See `QUICK_REFERENCE.md` for full roadmap and test coverage targets.

## Gradle Dependency Management

Dependencies are defined in `app/build.gradle.kts`. Key constraint: KSP (Kotlin Symbol Processing) version must match Kotlin version (1.9.22). Room, Hilt, and Compose versions are locked to tested combinations—changing one often requires updating others.

## ProGuard / Minification

Release builds enable minification and resource shrinking. Ensure custom model classes remain unobfuscated if dynamically instantiated. ProGuard rules: `app/proguard-rules.pro`.
