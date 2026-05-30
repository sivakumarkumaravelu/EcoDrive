# EcoDrive

EcoDrive is an Android application designed to help drivers improve their fuel efficiency by providing real-time eco-driving coaching. Using a hybrid data architecture, it combines **local phone sensors** (accelerometer, gyroscope, GPS) with the **Toyota Connected Services API** to deliver self-calibrating fuel estimation with persistent model accuracy.

This app features a pre-loaded physics model (Vehicle Specific Power) specifically tuned for the **2023 Toyota Highlander Hybrid**, with automatic calibration that improves over time and persists across app restarts.

## Features

- **Real-time Driving Metrics:** Uses your phone's accelerometer and GPS to monitor harsh braking, rapid acceleration, and sharp cornering with optimized sensor fusion.
- **Physics-Based Fuel Estimation:** Calculates real-time fuel efficiency (L/h) without requiring OBD-II hardware, using the Vehicle Specific Power (VSP) model tuned for the Highlander Hybrid.
- **Auto-Calibration with Persistence:** Integrates with the Toyota API to compare actual fuel consumption with the VSP model. Calibration factor is automatically saved and restored across app restarts.
- **Sensor Fusion:** Intelligently buffers and aligns GPS and IMU (accelerometer/gyroscope) data for accurate driving behavior detection.
- **Eco Score Calculator:** Generates a comprehensive driving score (0-100) evaluating speed consistency, idling, braking, and acceleration patterns.
- **Actionable Analytics:** Beautiful visual dashboards summarizing trips and driving patterns with persistent trip history.

## Tech Stack

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose & Material 3
- **Dependency Injection:** Hilt / Dagger
- **Local Storage:** Room Database & DataStore Preferences
- **Asynchronous Processing:** Kotlin Coroutines & Flows
- **Testing:** JUnit 4

## Prerequisites

To build and run this project locally, you will need:

1. **Android Studio** (Jellyfish or later recommended).
2. **Java Development Kit (JDK) 17**.
3. **Android SDK 34** (configured in your Android Studio SDK Manager).
4. An **Android Emulator** running API 26+ or a **Physical Android Device** with USB debugging enabled.

## Installation & Setup

1. **Open the Project:**
   - Launch Android Studio.
   - Select **Open** and choose the `EcoDrive` folder located at `/Users/sivakumar/Projects/EcoDrive`.

2. **Sync Project with Gradle:**
   - Upon opening, Android Studio will prompt you to sync the project.
   - If it doesn't happen automatically, click the **"Sync Project with Gradle Files"** button (the elephant icon) in the top toolbar.
   - *Note: The project uses Gradle plugins which will be downloaded automatically during sync.*

## Running the App

### Using Android Studio (Recommended)
1. In the Android Studio toolbar, select your connected device or emulator from the device dropdown menu.
2. Ensure the run configuration is set to `app`.
3. Click the **Run** button (green play icon) or press `Control + R` (Mac).

### Using the Command Line
If you prefer running via terminal, first generate the Gradle wrapper from Android Studio's terminal:
```bash
gradle wrapper
```
Once the `gradlew` script is available, you can build and install the debug APK:
```bash
./gradlew installDebug
```

## Running Tests

Comprehensive unit tests validate the core algorithms and recent bug fixes (184 tests total):

### Test Suites
- **OBD Communication** (76 tests): Validates OBD command parsing, error responses, and Bluetooth connection recovery
- **Sensor Fusion** (40 tests): Tests GPS/IMU alignment and timing synchronization
- **Fuel Estimation** (60 tests): Validates VSP model, calibration persistence, and fuel calculations
- **Driving Pattern Analysis** (8 tests): Tests event detection for hard braking, acceleration, and cornering

**To run tests via Android Studio:**
1. Navigate to the `app/src/test/java/com/ecodrive/app/` folder in the Project Explorer.
2. Right-click the folder and select **Run 'Tests in app'**.

**To run tests via Command Line:**
```bash
# Run all tests
./gradlew test

# Run specific test suite
./gradlew test --tests "ObdCommandTest"
./gradlew test --tests "SensorDataManagerTest"
./gradlew test --tests "AnalyzersExtendedTest"

# Run with coverage report
./gradlew testDebugUnitTestCoverage
```

**Test Coverage:** 65% (184 tests) — target is 80%+

## Architecture & Implementation

### Core Components

**Data Layer:**
- **SensorDataManager:** Orchestrates GPS/IMU fusion with intelligent buffering for temporal alignment
- **FuelEstimationEngine:** Vehicle Specific Power (VSP) model with self-calibration and persistence
- **DrivingPatternAnalyzer:** Real-time detection of harsh driving events
- **BluetoothConnectionManager:** Manages OBD-II adapter connection with automatic error recovery

**Database:**
- Room database with tables for trips, driving events, sensor data, fuel calibration history, and vehicle profiles
- Calibration factor persists across app restarts via FuelCalibrationDao

**Testing:**
- 184 comprehensive unit tests covering OBD communication, sensor fusion, and fuel estimation
- Tests validate error recovery, timing alignment, and persistence mechanisms

### Key Improvements (Recent Fixes)

1. **Sensor Fusion Timing:** IMU readings are buffered and aligned to GPS timestamps for accurate data fusion
2. **Calibration Persistence:** Fuel estimation calibration factor is saved to database and restored on app restart
3. **OBD Error Recovery:** Automatic retry logic with exponential backoff for robust Bluetooth communication
4. **Response Validation:** OBD response format is validated to prevent crashes on negative responses

## Documentation

For a detailed breakdown of the application architecture, the physics model, and core components:
- [EcoDrive Code Analysis](docs/ecodrive_code_analysis.md) — Deep technical analysis
- [CLAUDE.md](CLAUDE.md) — Developer guide and architecture overview
- [FIXES_APPLIED.md](FIXES_APPLIED.md) — Recent bug fixes and improvements
