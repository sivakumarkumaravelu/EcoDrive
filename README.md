# EcoDrive

**EcoDrive** is an advanced Android application designed to optimize fuel efficiency through real-time driving analysis, eco-coaching, and hybrid data fusion. It bridges the gap between traditional phone-based trackers and professional telematics by combining local smartphone sensors with **OBD-II hardware** and the **Smartcar API**.

---

## 🚀 Key Features

### 📡 Hybrid Data Architecture
- **Smartphone Sensors:** High-frequency IMU (accelerometer/gyroscope) and GPS data fusion for motion analysis.
- **OBD-II Integration:** Real-time engine telemetry (RPM, Speed, MAF, Fuel Rate) via ELM327 Bluetooth adapters.
- **Smartcar API:** Multi-brand vehicle cloud integration for precise fuel level verification and odometer tracking.
- **Unit Flexibility:** Support for both **Metric (km, L/100km)** and **Imperial (mi, MPG)** unit systems with a dedicated conversion engine.

### 🧠 Intelligent Analytics
- **Physics-Based Fuel Model:** A universal **Vehicle Specific Power (VSP)** engine that estimates consumption for ICE, Hybrid, and EVs even without OBD-II hardware.
- **Auto-Calibration:** Self-improving fuel estimation model that learns from actual Smartcar fuel data to reach high accuracy.
- **Eco Score (0-100):** Comprehensive driving evaluation based on speed consistency, idling, braking, and cornering intensity.

### 🚘 Automation & Experience
- **Auto-Record:** Intelligent trip detection using **Google Activity Recognition** and **Bluetooth connection triggers**.
- **Real-time Coaching:** Live dashboard with visual gauges and **Audio Feedback** alerts for inefficient driving events.
- **Trip History:** Interactive maps with event markers (hard brakes, sharp turns) and detailed performance charts.
- **Data Export:** Export detailed trip telemetry to CSV for external analysis.

---

## 🛠️ Tech Stack

- **UI:** Jetpack Compose, Material 3, Google Maps Compose.
- **Architecture:** MVVM/MVI with Clean Architecture principles.
- **DI:** Hilt / Dagger.
- **Persistence:** Room (SQL) for trip history & DataStore for preferences.
- **Concurrency:** Kotlin Coroutines & Flows for reactive data streams.
- **Networking:** Smartcar API (OAuth2 + REST).
- **Bluetooth:** Standard SPP for ELM327 communication.

---

## 📋 Prerequisites

- **Android Studio** (Jellyfish or later)
- **JDK 17** & Android SDK 34+
- **Physical Device** (Required for Sensor, Bluetooth, and GPS features)
- *(Optional)* **ELM327 OBD-II Adapter** for direct engine telemetry.

---

## ⚙️ Installation & Setup

1. **Clone & Open:** Open the project in Android Studio.
2. **Permissions:** Grant Location (Always), Activity Recognition, and Bluetooth permissions when prompted.
3. **Vehicle Config:** Add your vehicle details in the app settings to initialize the VSP physics model.
4. **Smartcar (Optional):** Link your vehicle cloud account via the Connect screen for automatic fuel calibration.

---

## 🧪 Testing & Quality

EcoDrive includes a comprehensive test suite (180+ tests) covering:
- **Domain Logic:** VSP math, Eco Score algorithms, and fuel rate calculations.
- **Data Layer:** OBD-II command parsing and Room DAO implementations.
- **Automation:** Trigger logic for `AutoRecordManager` and `BluetoothReceiver`.

```bash
# Run unit tests
./gradlew testDebugUnitTest
```

---

## 🏗️ Project Structure

- `com.ecodrive.app.domain.analyzer`: The "brain" — contains physics models and score calculators.
- `com.ecodrive.app.sensor`: Hardware abstraction for phone sensors and GPS.
- `com.ecodrive.app.data.obd`: Bluetooth protocol and ELM327 command implementations.
- `com.ecodrive.app.service`: Foreground services for reliable background recording.
- `com.ecodrive.app.ui`: Modern Compose-based screens and components.
- `com.ecodrive.app.util`: Utilities including unit conversion, audio feedback, and permission handling.

---

## 📄 Documentation

For deeper technical insights, refer to:
- [Technical Architecture](docs/ecodrive_code_analysis.md)
- [Developer Guidelines](CLAUDE.md)
