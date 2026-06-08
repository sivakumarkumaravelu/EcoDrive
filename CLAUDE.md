# EcoDrive Developer Guidelines

This document provides essential information for developers working on the EcoDrive Android project.

## 🏗️ Architecture & Patterns
- **Pattern:** MVVM (Model-View-ViewModel) with MVI (Model-View-Intent) for UI state management.
- **Clean Architecture:** Strict separation between `data`, `domain`, and `ui` layers.
- **Dependency Injection:** Hilt/Dagger is used for all component lifecycles.
- **Reactive Streams:** Kotlin Flows are preferred over LiveData for high-frequency sensor data.

## 🛠️ Build Commands
```bash
# Build & Install
./gradlew installDebug           # Install to connected device
./gradlew assembleRelease        # Generate release APK

# Code Quality
./gradlew lint                   # Run Android Lint
./gradlew ktlintCheck            # Check Kotlin code style
```

## 🧪 Testing Standards
EcoDrive maintains high test coverage (~180+ tests).
- **Unit Tests:** Located in `src/test/java/`. Use JUnit 4 and MockK.
- **Naming Convention:** `[Function]_[Scenario]_[ExpectedResult]` (e.g., `calculateScore_hardBrake_returnsLowerScore`).
- **Commands:**
  ```bash
  ./gradlew testDebugUnitTest             # Run all unit tests
  ./gradlew testDebugUnitTest --tests "*Analyzer*" # Run specific tests
  ```

## 🖋️ Code Style & Conventions
- **Language:** Kotlin 1.9+
- **Concurrency:** Always use `viewModelScope` in ViewModels and `Dispatchers.IO` for DB/Network operations.
- **UI:** 100% Jetpack Compose. Use `com.ecodrive.app.ui.theme` for all styling.
- **Logging:** Use `android.util.Log` with a class-level `TAG` constant. Avoid `println()`.

## 📦 Key Packages & Responsibilities
| Package | Responsibility |
| :--- | :--- |
| `domain.analyzer` | The "Brain" - VSP math, score algorithms, and route optimization. |
| `domain.ai` | Intelligence - Anomaly detection, coaching, and gamification. |
| `data.sensor` | Hardware - Low-level GPS and IMU (Accelerometer) processing. |
| `data.obd` | Vehicle - Bluetooth protocol and ELM327/OBD-II communication. |
| `data.remote` | Cloud - Smartcar, Weather, and Directions API clients. |
| `ui.screens` | Interface - Compose screens organized by feature area. |

## 🚀 Deployment & Releases
- Versioning follows SemVer (e.g., 1.3.0).
- Check `util/Constants.kt` for debug flags and API endpoints before release.
- Ensure `proguard-rules.pro` is updated if adding new R8-sensitive libraries (e.g., JSON parsers).
