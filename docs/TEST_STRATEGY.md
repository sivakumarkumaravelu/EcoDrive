# EcoDrive Test Strategy & Quick Reference

## Test Organization

### Test Files by Category

#### 1. OBD-II Data Capture Tests
**File**: `app/src/test/java/com/ecodrive/app/data/obd/ObdCommandTest.kt`
- **42 tests** for OBD command parsing
- Path conversion formulas for 9 PID types
- Response edge cases: NODATA, ERROR, malformed hex, empty responses
- **Run**: `./gradlew test ObdCommandTest`

#### 2. OBD Connection Management Tests  
**File**: `app/src/test/java/com/ecodrive/app/data/obd/ObdConnectionTest.kt`
- **34 tests** for Bluetooth connection lifecycle
- ELM327 initialization sequence
- Command send/response cycles
- Error recovery and timeout handling
- **Run**: `./gradlew test ObdConnectionTest`

#### 3. Sensor Data Fusion Tests
**File**: `app/src/test/java/com/ecodrive/app/sensor/SensorDataManagerTest.kt`
- **40 tests** for GPS + IMU fusion
- Road grade calculation
- Fuel consumption estimation
- State machine transitions
- **Run**: `./gradlew test SensorDataManagerTest`

#### 4. GPS Location Tests
**File**: `app/src/test/java/com/ecodrive/app/sensor/LocationTrackerTest.kt`
- **10 tests** for GPS reading conversion
- Speed unit conversion (m/s → km/h)
- Bearing, altitude, accuracy handling
- **Run**: `./gradlew test LocationTrackerTest`

#### 5. IMU Sensor Tests
**File**: `app/src/test/java/com/ecodrive/app/sensor/PhoneSensorManagerTest.kt`
- **12 tests** for accelerometer/gyroscope fusion
- Orientation correction validation
- Low-pass filter effectiveness
- **Run**: `./gradlew test PhoneSensorManagerTest`

#### 6. Driving Analytics Tests
**File**: `app/src/test/java/com/ecodrive/app/domain/analyzer/AnalyzersTest.kt` (Existing)
- **8 tests** for pattern detection and eco scoring
- **Tests**: Hard braking, acceleration, speed, cornering detection
- Fuel calibration verification

#### 7. Extended Analyzer Tests
**File**: `app/src/test/java/com/ecodrive/app/domain/analyzer/AnalyzersExtendedTest.kt`
- **60 tests** for edge cases and boundary conditions
- Threshold precision (exact, at, above boundaries)
- Fuel estimation across speed ranges
- Eco score component validation
- **Run**: `./gradlew test AnalyzersExtendedTest`

#### 8. Data Persistence Tests
**File**: `app/src/test/java/com/ecodrive/app/data/repository/TripRepositoryTest.kt`
- **20 tests** for trip lifecycle and data persistence
- Trip creation, finalization, calibration
- Event and data point storage
- **Run**: `./gradlew test TripRepositoryTest`

---

## Test Execution Quick Commands

### Run All Tests
```bash
./gradlew test
```

### Run Tests by Component
```bash
# OBD Tests
./gradlew test --tests "*.obd.*"

# Sensor Tests
./gradlew test --tests "*.sensor.*"

# Analyzer Tests
./gradlew test --tests "*.analyzer.*"

# Repository Tests
./gradlew test --tests "*.repository.*"
```

### Run Specific Test Class
```bash
./gradlew test --tests ObdCommandTest
./gradlew test --tests SensorDataManagerTest
./gradlew test --tests AnalyzersExtendedTest
```

### Run with Coverage
```bash
./gradlew testDebugUnitTestCoverage
```

---

## Critical Test Cases by Feature

### Feature: OBD Speed Reading (PID 010D)
| Test | Value | Expected |
|------|-------|----------|
| Simple response | `410D 3E >` | 62.0 km/h |
| Max speed | `410D FF >` | 255.0 km/h |
| Zero speed | `410D 00 >` | 0.0 km/h |
| NODATA | `410D NODATA >` | 0.0 (error) |
| Empty | `` | 0.0 (error) |

### Feature: OBD RPM Reading (PID 010C)
| Test | Bytes | Formula | Expected |
|------|-------|---------|----------|
| Normal | `19 A0` | ((0x19*256)+0xA0)/4 | 1640 RPM |
| Idle | `03 E8` | ((0x03*256)+0xE8)/4 | 250 RPM |
| High | `FF FF` | ((0xFF*256)+0xFF)/4 | 16383.75 RPM |

### Feature: Driving Pattern Detection
| Pattern | Condition | Threshold | Test Status |
|---------|-----------|-----------|------------|
| Hard Brake | accel < threshold | -3.5 m/s² | ✅ |
| Hard Accel | accel > threshold | +3.0 m/s² | ✅ |
| Sharp Turn | |lateral| > threshold | 4.0 m/s² | ✅ |
| Speed Excessive | speed > threshold | 110 km/h | ✅ |
| Idle Detect | speed < 2 km/h | 60s warning | ⚠️ |

### Feature: Fuel Calibration
| Scenario | Estimated | Actual | Factor | Status |
|----------|-----------|--------|--------|--------|
| Point 1 | 10.0 L | 12.0 L | 1.2 | ✅ |
| Point 2 | 10.0 L | 11.0 L | 1.1 | ✅ |
| Point 3 | 10.0 L | 10.0 L | 1.0 | ✅ |
| Average | - | - | 1.1 | ✅ |

---

## Integration Test Scenarios

### Scenario 1: Complete Trip Workflow
```
1. Start trip (capture initial fuel level)
2. Drive for 10 minutes
3. Record metrics every 0.5s
4. Detect hard brake, acceleration events
5. End trip (capture final fuel level)
6. Calculate calibration factor
7. Verify all data persisted
```

### Scenario 2: Sensor Unavailability
```
1. Start trip with GPS + IMU
2. Simulate GPS failure
3. Continue with IMU only
4. Verify graceful degradation
5. Restore GPS
6. Resume full fusion
```

### Scenario 3: OBD Connection Failure
```
1. Connect to ELM327
2. Send valid commands (success)
3. Disconnect/block connection
4. Send command (fails)
5. Verify error state
6. Reconnect
7. Verify recovery
```

### Scenario 4: Extreme Driving Conditions
```
1. Mountain pass (high grade changes)
2. City driving (frequent hard braking)
3. Highway (excessive speed)
4. Idle in traffic (accumulate idle time)
5. Verify all events detected
6. Check eco score reflects behavior
```

---

## Validation Checklist for Each Component

### GPS Tests ✅
- [x] Speed conversion (m/s to km/h)
- [x] Altitude handling (zero, missing)
- [x] Bearing range validation (0-360°)
- [x] Timestamp preservation
- [x] Null handling for optional fields

### IMU Tests ⚠️
- [ ] Rotation matrix application
- [ ] Gravity compensation
- [ ] Low-pass filter stability
- [ ] Orientation independence
- [ ] Sensor fusion timing

### OBD Tests ✅
- [x] 9 command parsing
- [x] Response format validation
- [x] Boundary values
- [x] Error responses
- [x] Malformed data
- [ ] Checksum validation
- [ ] Response fragmentation

### Fuel Estimation ⚠️
- [x] Idle consumption (0.5 L/h)
- [x] Calibration averaging
- [ ] Speed range (10-130 km/h)
- [ ] Road grade integration
- [ ] Acceleration impact
- [ ] Deceleration credit

### Analyzer ✅
- [x] Hard brake detection
- [x] Hard acceleration
- [x] Speed excessive
- [x] Cornering detection
- [ ] Idle time accumulation
- [ ] Threshold boundaries
- [ ] Combination events

### Repository ⚠️
- [ ] Trip CRUD operations
- [ ] Event persistence
- [ ] Data point storage
- [ ] Calibration factor updates
- [ ] Transaction handling

---

## Known Issues & Workarounds

### Issue #1: GPS Speed Zero When Stationary
**Problem**: GPS speed reported as 0 even when location locked  
**Cause**: GPS Doppler calculation requires motion  
**Workaround**: Use IMU acceleration as backup  
**Test**: LocationTrackerTest::testZeroSpeedHandling

### Issue #2: OBD Response Parser Case Sensitivity
**Problem**: Response "410d" (lowercase) vs "410D" (uppercase)  
**Current**: toInt(16) handles both  
**Test**: ObdCommandTest validates both cases

### Issue #3: Phone Orientation Affects IMU
**Problem**: If phone rotated, accelerometer reads are in phone frame  
**Current**: Rotation vector sensor provides correction  
**Limitation**: Requires rotation vector sensor hardware  
**Test**: PhoneSensorManagerTest::testRotationMatrixApplied

### Issue #4: Fuel Tank Level Refueling
**Problem**: Fuel level increases (refueling) miscalculates consumption  
**Detection**: Check if fuel % increased vs. previous reading  
**Recommended**: Skip calibration if previous < current  
**Test**: TripRepositoryTest (to be implemented)

---

## Performance Benchmarks

### Data Emission Rates
| Source | Rate | Bytes/sec | Storage/hour |
|--------|------|-----------|--------------|
| GPS | 1 Hz | ~250 | 900 KB |
| IMU | 50 Hz | ~50 | 180 MB |
| OBD | ~10 Hz | ~200 | 7.2 MB |
| Summary | 0.5 Hz | ~100 | 180 KB |

### Storage Estimates (Per Trip)
- **1 hour city drive**: ~200 MB (raw metrics)
- **Compressed**: ~20 MB
- **Summarized (events only)**: ~500 KB

---

## Debugging Tips

### Enable Verbose Logging
```kotlin
// In Constants.kt or similar
const val DEBUG_ENABLED = true

// Usage
if (DEBUG_ENABLED) {
    Log.d(TAG, "Metric: speed=$speed, accel=$accel, fuel=$fuel")
}
```

### Unit Test with Mock Data
```kotlin
val mockGps = GpsReading(
    timestampMs = System.currentTimeMillis(),
    speedKmh = 50.0,
    latitude = 37.7749,
    longitude = -122.4194,
    altitudeM = 50.0,
    bearingDegrees = 45f,
    accuracyM = 5f,
    hasSpeed = true,
    hasBearing = true,
)
```

### OBD Response Verification
```
Expected: 410D 3E >
Got:      41 0D 3E >    ✅ (whitespace OK)
Got:      410D 3E       ❌ (missing prompt)
Got:      410D          ❌ (missing data)
```

---

## Next Steps

1. **Run all tests**: `./gradlew test`
2. **Fix failures**: Implement any TODO comments in test files
3. **Add integration tests**: Use AndroidX test fixtures
4. **Enable CI/CD**: Add testing to GitHub Actions
5. **Monitor coverage**: Target 80%+ coverage per class

---

## References

- OBD-II PID List: [ISO 15031-1](https://en.wikipedia.org/wiki/OBD-II_PIDs)
- Vehicle Specific Power: [EPA VSP Model](https://www.epa.gov/emissions/guidelines-methodology-epa-vehicle-specific-power-calculation)
- Kalman Filtering: Sensor Fusion Reference
- Android Sensors: [Android Developer Docs](https://developer.android.com/guide/topics/sensors)
