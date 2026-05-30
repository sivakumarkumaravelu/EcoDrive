# EcoDrive Deep Code Analysis

## Architecture Overview
The EcoDrive Android application is designed around a three-tier data architecture to offer eco-driving coaching. The layers involve:
1. **Local Phone Sensors:** Accelerometer, gyroscope, and GPS are used to measure real-time vehicle dynamics (`DrivingMetrics`).
2. **Toyota Connected Services API:** For retrieving fuel levels and calibration data using the `ToyotaApiClient`.
3. **Optional OBD-II:** Support for reading real-time engine data if connected (`ObdConnection`).

The application uses **Android Jetpack** libraries:
- **UI:** Jetpack Compose for declarative UI.
- **Dependency Injection:** Hilt/Dagger for modular component injection.
- **Local Storage:** Room Database (`EcoDriveDatabase`) for persisting trips, metrics, and calibration data.
- **Navigation:** Compose Navigation with strict typed arguments.

## Core Domain Logic
The most critical part of the application resides in `com.ecodrive.app.domain.analyzer.Analyzers.kt`, which contains three main components:

### 1. `DrivingPatternAnalyzer`
This component uses real-time phone sensor metrics (like longitudinal and lateral acceleration) to detect poor driving behaviors.
- **Hard Braking & Acceleration:** Driven by thresholds configured in `Constants.kt` (e.g., `-3.5 m/s²` and `3.0 m/s²`).
- **Sharp Cornering:** Evaluates lateral acceleration.
- **Excessive Idling:** Detects if the vehicle has been stationary and idling for more than 60 seconds.

### 2. `FuelEstimationEngine`
This implements a sophisticated Vehicle Specific Power (VSP) model that:
- Estimates the instantaneous fuel consumption in liters per hour (L/h) based on the vehicle mass, road grade, speed, and aerodynamic drag.
- Hardcoded for the 2023 Toyota Highlander Hybrid (mass = 2090 kg, drag = 0.35).
- Uses a **Hybrid Efficiency Map** to apply bonuses depending on the speed (e.g., mostly electric below 30km/h).
- **Self-Calibration:** Uses `FuelCalibrationPoint` data retrieved from the Toyota API to correct the engine's internal factor over time. By comparing estimated fuel consumption to the actual fuel tank level drop, the model adapts and improves its accuracy.

### 3. `EcoScoreCalculator`
Generates a holistic driving score out of 100 based on penalties from `DrivingPatternAnalyzer` events.
- Highly penalizes excessive speeding and hard acceleration/braking.
- Rewards consistency in driving speed using the standard deviation of speed history.
- The weighting mechanism focuses heavily on Speed (20%), Braking (20%), and Acceleration (20%).

## Data Collection Layer
- **Foreground Services:** Data collection runs in background/foreground services (`SensorForegroundService`, `ObdForegroundService`). This ensures Android doesn't kill the app while driving.
- **Sensor Fusion:** The `PhoneSensorManager` applies low-pass filters to accelerometer data to reduce noise from potholes or phone movements.

## Evaluation
The application utilizes cutting-edge practices by falling back on physics (VSP) and phone sensors rather than relying entirely on hardware OBD-II tools. The calibration loop via Toyota's API is a very clever implementation to solve phone-based fuel estimation errors.
