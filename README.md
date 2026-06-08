# EcoDrive 🚗🔋

**EcoDrive** is a cutting-edge Android application designed to maximize fuel efficiency and promote sustainable driving habits. By fusing real-time smartphone sensor data with high-fidelity vehicle telemetry (via OBD-II and Smartcar API), EcoDrive provides a "telematics-grade" coaching experience for every driver.

---

## 🌟 Key Features

### 📡 Hybrid Data Fusion
- **Smartphone Sensor Suite:** High-frequency IMU (accelerometer/gyroscope) and GPS processing for precise motion analysis.
- **OBD-II Direct Link:** Support for ELM327 Bluetooth adapters to read real-time engine RPM, Load, MAF, and Speed directly from the vehicle's ECU.
- **Smartcar Cloud API:** Multi-brand vehicle integration to track odometer, fuel level, and tire pressure across dozens of makes (Tesla, Ford, BMW, Toyota, etc.).
- **Environmental Context:** Real-time weather integration to adjust efficiency targets based on wind speed, temperature, and precipitation.

### 🧠 Intelligent Analysis & AI Coaching
- **Physics-Based Fuel Model (VSP):** A universal Vehicle Specific Power engine that estimates fuel/energy consumption for any vehicle type (ICE, Hybrid, EV) even without hardware OBD-II.
- **Eco Score (0-100):** Comprehensive driving evaluation based on acceleration smoothness, braking intensity, cornering stability, and idling time.
- **AI-Powered Insights:** Generates personalized coaching tips and detects vehicle anomalies (e.g., alignment issues, excessive vibration) using advanced pattern recognition.
- **Auto-Calibration:** A self-learning loop that uses Smartcar ground-truth data to continuously refine the physics model's accuracy.

### 🗺️ Navigation & Experience
- **Eco-Routing:** Compare routes based on estimated fuel consumption and CO2 emissions, taking road grade and traffic into account.
- **Interactive Trip History:** Visualize trips with color-coded efficiency paths and event markers on Google Maps or OpenStreetMap.
- **Real-time Dashboard:** Live gauges and audio feedback alerts for inefficient driving events, keeping your eyes on the road.
- **Gamification:** Earn badges (e.g., "Smooth Operator", "Highway Hero") and complete AI-generated driving challenges.

---

## 🛠️ Tech Stack

- **UI:** Jetpack Compose, Material 3, Google Maps Compose.
- **Architecture:** Clean Architecture with MVVM + MVI pattern.
- **Concurrency:** Kotlin Coroutines & Flows for reactive, high-frequency data streams.
- **Dependency Injection:** Hilt / Dagger.
- **Persistence:** Room (SQLite) for trip telemetry & DataStore for preferences.
- **Networking:** Ktor / Retrofit for Smartcar and Weather APIs.
- **Bluetooth:** Low-latency SPP for ELM327 communication.

---

## 🚀 Getting Started

### Prerequisites
- Android Studio (Jellyfish or later)
- Android SDK 34+
- A physical Android device (Sensors and Bluetooth require hardware)
- *(Optional)* ELM327 Bluetooth OBD-II adapter

### Installation
1. **Clone the repository:**
   ```bash
   git clone https://github.com/your-repo/ecodrive.git
   ```
2. **Open in Android Studio** and sync Gradle.
3. **Configure API Keys:** Add your Smartcar and Google Maps API keys to `local.defaults.properties`.
4. **Build & Run:** Deploy to your physical device.

---

## 🧪 Testing

EcoDrive is built with testability in mind, featuring 180+ unit and integration tests.

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run specific domain tests
./gradlew testDebugUnitTest --tests "com.ecodrive.app.domain.analyzer.*"
```

---

## 🏗️ Project Structure

- `com.ecodrive.app.domain.analyzer`: Core physics models, Eco Score logic, and route optimization.
- `com.ecodrive.app.domain.ai`: AI-driven coaching, anomaly detection, and gamification.
- `com.ecodrive.app.data.sensor`: High-frequency processing for GPS and IMU sensors.
- `com.ecodrive.app.data.obd`: Bluetooth protocol and ELM327 command implementations.
- `com.ecodrive.app.data.remote`: Clients for Smartcar, Weather, and Directions APIs.
- `com.ecodrive.app.service`: Foreground services for reliable background recording.
- `com.ecodrive.app.ui`: Modern, responsive Jetpack Compose screens.

---

## 📄 Technical Insights

For a deep dive into the engineering behind EcoDrive:
- [**Technical Architecture & Metric Derivation**](docs/ecodrive_code_analysis.md) - Learn how we calculate fuel and scores.
- [**Developer Guidelines**](CLAUDE.md) - Build instructions and coding standards.

---

## 📄 License
This project is licensed under the MIT License - see the LICENSE file for details.
