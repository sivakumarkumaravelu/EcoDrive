# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

EcoDrive is an Android application that provides real-time eco-driving coaching using a hybrid data architecture combining **local phone sensors** (accelerometer, gyroscope, GPS) with the **Smartcar API**. The app estimates fuel efficiency for any vehicle using a physics-based Vehicle Specific Power (VSP) model.

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

## Testing

```bash
# Run all unit tests
./gradlew test

# Run tests matching pattern
./gradlew test --tests "*Analyzer*"
```

## Code Architecture

### Three-Tier Data Architecture

1. **Local Phone Sensors** (Primary)
   - Location: `sensor/` package
   - Provides: GPS, accelerometer, gyroscope data in real-time

2. **Smartcar API** (Supplementary)
   - Location: `data/remote/SmartcarApiClient`
   - Purpose: Fetch fuel levels, calibration factors across multiple brands

3. **OBD-II** (Optional)
   - Location: `data/obd/` package
   - Status: Optional pro feature

### Core Domain Logic (`domain/analyzer/Analyzers.kt`)

**DrivingPatternAnalyzer:**
- Detects poor driving behaviors (hard braking, rapid acceleration, sharp cornering)
- Thresholds configured in `util/Constants.kt`

**FuelEstimationEngine:**
- Universal physics-based VSP model
- Calculates power (Watts) based on velocity, acceleration, mass, and drag
- Converts power to fuel rate (L/h) using fuel energy density (Gasoline, Diesel, etc.)
- **Self-calibrating:** Uses Smartcar API data to adjust internal correction factor

**EcoScoreCalculator:**
- Generates 0-100 driving score based on behavior frequency

### Data Layer (`data/`)

**Room Database** (`local/EcoDriveDatabase`):
- `VehicleEntity`: Stores dynamic vehicle profiles (mass, drag, fuel type)
- `TripEntity`, `DrivingEventEntity`, `DataPointEntity`, `FuelCalibrationEntity`

## Key Files Reference

| File | Purpose |
|------|---------|
| `util/Constants.kt` | Thresholds and universal physical constants |
| `domain/analyzer/Analyzers.kt` | Core logic: DrivingPatternAnalyzer, FuelEstimationEngine |
| `data/remote/SmartcarApiClient.kt` | Multi-brand vehicle API integration |
| `data/repository/VehicleRepository.kt` | Manages active vehicle profiles |
| `sensor/SensorDataManager.kt` | GPS/IMU fusion and vehicle profile injection |

## Development Workflow

### Adding a New Vehicle Profile
1. Update `VehicleEntity` in `Entities.kt` if new physics parameters are needed.
2. Use `VehicleRepository` to save and fetch profiles.
3. The `FuelEstimationEngine` will automatically adapt to the active profile's mass, drag, and fuel type.
