# EcoDrive

EcoDrive is an Android application designed to help drivers improve their fuel efficiency by providing real-time eco-driving coaching. It uses a hybrid data architecture that combines **local phone sensors** (accelerometer, gyroscope, GPS) with the **Toyota Connected Services API** and **OBD-II hardware** to deliver precise driving analysis and self-calibrating fuel estimation.

The app features a pre-loaded physics model (Vehicle Specific Power) specifically tuned for the **2023 Toyota Highlander Hybrid**, with automatic calibration that improves over time and persists across app restarts.

## 🚀 Key Features

- **Auto-Record Drives:** Intelligently detects when you are in a moving vehicle using **Activity Recognition** and **Bluetooth connection triggers** to start/stop recording automatically.
- **Real-time Driving Metrics:** Monitors harsh braking, rapid acceleration, and sharp cornering using phone sensors or direct OBD-II data.
- **Physics-Based Fuel Estimation:** Calculates real-time fuel efficiency (L/h) using the Vehicle Specific Power (VSP) model, even without specialized hardware.
- **Auto-Calibration:** Integrates with the Toyota API via **Smartcar** to compare actual fuel consumption with the physics model, automatically improving accuracy over time.
- **Eco Score Calculator:** Generates a comprehensive driving score (0-100) based on speed consistency, idling, braking, and acceleration patterns.
- **Trip History & Analytics:** Beautiful dashboards summarizing every drive with persistent storage in a local Room database, now featuring **interactive route maps** and **history list previews**.
- **Visual Route Mapping:** Visualizes your driving path on **Google Maps** with markers for notable driving events (hard braking, sharp turns, etc.).
- **Audio Feedback:** Provides real-time coaching tips and event alerts via voice to keep your eyes on the road.

## 🛠️ Tech Stack

- **Language:** [Kotlin](https://kotlinlang.org/)
- **UI Toolkit:** [Jetpack Compose](https://developer.android.com/jetpack/compose) & Material 3
- **Dependency Injection:** [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) / Dagger
- **Local Storage:** [Room Database](https://developer.android.com/training/data-storage/room) & [DataStore Preferences](https://developer.android.com/topic/libraries/architecture/datastore)
- **Async & Flows:** Kotlin Coroutines & Flows
- **Maps:** [Google Maps SDK for Android](https://developers.google.com/maps/documentation/android-sdk/overview) & [Maps Compose](https://github.com/googlemaps/android-maps-compose)
- **External APIs:** Google Play Services (Location, Activity Recognition), Smartcar API (Toyota Connected Services)
- **Testing:** JUnit 4 & [MockK](https://mockk.io/)

## 📋 Prerequisites

- **Android Studio** (Jellyfish or later recommended)
- **JDK 17**
- **Android SDK 34+**
- **Physical Device** (Recommended for sensor/Bluetooth features) or Emulator with API 26+

## ⚙️ Installation & Setup

1.  **Open the Project:** Launch Android Studio and open the `EcoDrive` folder.
2.  **Sync Gradle:** Allow Android Studio to sync the project files and download dependencies.
3.  **Permissions:** Upon first launch, the app will request:
    *   **Location**: For speed and distance tracking.
    *   **Activity Recognition**: For the Auto-Record feature.
    *   **Notifications**: For the background recording service.

## 🧪 Running Tests

The project includes a robust suite of **180 unit tests** covering core logic, sensor fusion, and automation.

```bash
# Run all unit tests
./gradlew testDebugUnitTest
```

### Test Coverage Highlights:
- **Trip History (Latest)**: New batch-fetching strategy for route points ensures smooth scrolling when displaying mini-map previews in the history list.
- **Automation**: `TripRecorderTest`, `AutoRecordManagerTest`, and Receiver tests ensure reliable auto-start/stop behavior.
- **OBD-II**: Validates command parsing and Bluetooth protocol handling.
- **Physics Engine**: Tests the VSP model and fuel calibration math.
- **Sensor Fusion**: Validates the alignment of GPS and IMU data.

## 🏗️ Architecture

The app follows a modern Android architecture with clean separation of concerns:

*   **`domain.recorder`**: Centralized logic for trip recording and background triggers.
*   **`sensor`**: Data fusion layer for GPS and IMU (Accelerometer/Gyroscope).
*   **`domain.analyzer`**: Physics engines for fuel estimation and eco-scoring.
*   **`data.remote`**: API clients for Toyota (Smartcar) integration.
*   **`data.local`**: Persistence layer using Room and DataStore.
*   **`service`**: Foreground services for reliable background data collection.

## 📄 Documentation

- [EcoDrive Code Analysis](docs/ecodrive_code_analysis.md) — Deep technical architecture.
- [CLAUDE.md](CLAUDE.md) — Developer guidelines.
- [Walkthrough](.artifacts/20260529-235604-ad5d849d-6299-4b25-b4e3-a43d22a6eca5/walkthrough.artifact.md) — Implementation details for recent features.
