# AI Integration Plan for EcoDrive

After a thorough analysis of the entire EcoDrive repository, I've identified **7 high-impact areas** where AI can be integrated or enhanced. They are ranked by **business value × implementation feasibility** — the best features first.

---

## Current AI Footprint

EcoDrive already has a **minimal Gemini integration** in [TripDetailViewModel.kt](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/tripdetail/TripDetailViewModel.kt#L87-L135):
- Single-shot post-trip insight using `gemini-1.5-flash`
- Falls back to the rule-based [LocalEcoCoach](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/analyzer/LocalEcoCoach.kt) when no API key is configured
- Gemini API key stored in [PreferenceManager](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/data/local/PreferenceManager.kt)
- `generativeai:0.9.0` dependency already in [build.gradle.kts](file:///Users/sivakumar/Projects/EcoDrive/app/build.gradle.kts#L113)

Everything else — coaching, scoring, fuel estimation, pattern detection, route planning — is **rule-based or physics-only**. This creates massive opportunity.

---

## Feature Rankings (Best → Good)

### 🥇 Feature 1: AI-Powered Real-Time Driving Coach

**Impact: ⭐⭐⭐⭐⭐ | Effort: Medium**

> [!IMPORTANT]
> This is the highest-value feature. The current coaching system is a static `when` block with ~8 hardcoded tips. An AI coach can deliver truly personalized, context-aware guidance in real time.

**What exists today:**
- [DashboardViewModel.generateDrivingTip()](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/dashboard/DashboardViewModel.kt#L171-L196) — 8 static `when` branches
- [AudioFeedbackManager.playTip()](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/util/AudioFeedbackManager.kt#L68-L71) — already supports TTS for any string
- [CoachViewModel](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/coach/CoachViewModel.kt#L84-L96) — 6 hardcoded tips based on event counts

**What AI enables:**
- **Contextual coaching** that considers road type, time of day, weather, trip history patterns, and current driving behavior simultaneously
- **Adaptive thresholds** — learns that the driver does well on highways but struggles in city traffic
- **Conversational summaries** at end of trip instead of a single canned sentence
- **Progressive difficulty** — starts with basic tips, evolves to advanced techniques as the driver improves

**Proposed changes:**

#### [NEW] `domain/ai/AiCoachService.kt`
A Gemini-backed coaching service that:
- Maintains a **sliding context window** of the last 5 minutes of driving metrics
- Generates coaching tips via Gemini at strategic moments (not every second — triggered by events or periodic 2-min intervals)
- Uses **structured output** (JSON mode) so tips can be parsed into type + message + priority
- Falls back to `LocalEcoCoach` when offline or API key not set

#### [MODIFY] [DashboardViewModel.kt](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/dashboard/DashboardViewModel.kt)
- Replace `generateDrivingTip()` with calls to `AiCoachService`
- Add debouncing logic so AI tips don't overwhelm the driver

#### [MODIFY] [CoachViewModel.kt](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/coach/CoachViewModel.kt)
- Replace hardcoded tips with AI-generated weekly coaching reports
- Add a "Ask the Coach" freeform input where users can ask driving questions

---

### 🥈 Feature 2: AI Trip Summary & Natural Language Insights

**Impact: ⭐⭐⭐⭐⭐ | Effort: Low**

> [!TIP]
> This builds on the existing Gemini integration in TripDetailViewModel but makes it dramatically richer.

**What exists today:**
- [TripDetailViewModel.generateAiInsight()](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/tripdetail/TripDetailViewModel.kt#L87-L135) — sends a basic prompt with trip summary stats
- Prompt doesn't include telemetry data points, route info, or historical context

**What AI enables:**
- **Rich narrative summaries**: "Your morning commute on Highway 101 was smooth until the downtown section where you hit 4 hard brakes near Market St — likely due to traffic lights. Next time, try leaving 5 minutes earlier to avoid the 8:15 signal cycle."
- **Comparative insights**: "This trip scored 12 points higher than your average Tuesday commute. The key difference was 40% fewer hard acceleration events."
- **Pattern recognition**: "You've hard-braked at the same intersection on 3 of your last 5 trips. Consider taking Oak Avenue instead."

**Proposed changes:**

#### [MODIFY] [TripDetailViewModel.kt](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/tripdetail/TripDetailViewModel.kt#L87-L135)
- Enrich the prompt with: speed/accel chart data, GPS route waypoints, event locations, historical trip averages
- Use Gemini `gemini-2.0-flash` (upgrade from 1.5-flash) for better structured reasoning
- Add **multi-section output**: Summary, Key Moments, Improvement Plan
- Cache AI responses in Room to avoid re-generation on revisits

#### [NEW] `data/local/entity/AiInsightEntity.kt` + `data/local/dao/AiInsightDao.kt`
- Persist AI-generated insights per trip for offline access

#### [MODIFY] [TripDetailScreen.kt](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/tripdetail/TripDetailScreen.kt)
- Redesign the AI insight section with expandable cards: Summary, Hotspots, Recommendation

---

### 🥉 Feature 3: Predictive Fuel Consumption with ML

**Impact: ⭐⭐⭐⭐ | Effort: High**

> [!IMPORTANT]
> The current VSP physics model in [FuelEstimationEngine](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/analyzer/Analyzers.kt#L93-L221) is solid but uses a single correction factor. A personalized ML model can dramatically improve accuracy.

**What exists today:**
- Physics-based VSP model with hardcoded efficiency factors per vehicle type (0.25 ICE, 0.35 Hybrid, 0.85 EV)
- Simple linear calibration via `calibrationFactor` (single scalar)
- [FuelCalibrationPoint](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/model/Models.kt#L242-L251) stores actual vs estimated fuel

**What AI enables:**
- **On-device TensorFlow Lite model** trained on the user's own driving data (speed profiles, acceleration patterns, grade, fuel consumption)
- **Non-linear correction** — different correction factors for city vs highway, cold engine vs warm, uphill vs flat
- **Predictive fuel estimate** before a trip starts based on planned route + time of day + historical patterns

**Proposed changes:**

#### [NEW] `domain/ai/FuelPredictionModel.kt`
- TFLite model wrapper for on-device inference
- Trains incrementally on accumulated `DataPointEntity` data
- Inputs: speed, acceleration, grade, time-of-day, trip-type (city/highway)
- Output: corrected fuel rate multiplier

#### [MODIFY] [FuelEstimationEngine](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/analyzer/Analyzers.kt#L124-L178)
- Add ML correction as an optional layer on top of the physics model
- Fall back to physics-only when insufficient training data (<20 trips)

#### [MODIFY] [RouteOptimizer](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/analyzer/RouteOptimizer.kt)
- Use the ML model for per-segment fuel predictions instead of assuming constant speed

#### [NEW] `build.gradle.kts` — add `org.tensorflow:tensorflow-lite:2.16.1`

---

### Feature 4: AI-Powered Driving Pattern Anomaly Detection

**Impact: ⭐⭐⭐⭐ | Effort: Medium**

**What exists today:**
- [DrivingPatternAnalyzer](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/analyzer/Analyzers.kt#L22-L85) uses **fixed thresholds** from [Constants.kt](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/util/Constants.kt#L20-L27):
  - Hard brake: -3.5 m/s²
  - Hard accel: +3.0 m/s²
  - Sharp turn: 4.0 m/s² lateral
- These thresholds are universal — same for all drivers, vehicles, and road conditions

**What AI enables:**
- **Personalized thresholds** that adapt to each driver's baseline behavior
- **Anomaly detection**: "This braking event was in your 95th percentile — unusual for you"
- **Fatigue/distraction detection** via patterns: increased speed variability, delayed braking, unusual swerving patterns
- **Road segment learning**: knows that a specific intersection typically causes hard brakes → stops flagging it as "bad driving"

**Proposed changes:**

#### [NEW] `domain/ai/AdaptiveThresholdEngine.kt`
- Uses Gemini to periodically analyze accumulated driving data and suggest personalized thresholds
- Alternatively, uses simple statistical modeling (z-scores) for on-device computation
- Persists per-driver threshold profiles in DataStore

#### [MODIFY] [DrivingPatternAnalyzer](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/analyzer/Analyzers.kt#L29-L69)
- Replace `Constants.HARD_BRAKE_THRESHOLD` etc. with dynamic thresholds from `AdaptiveThresholdEngine`
- Add severity levels (mild / moderate / severe) instead of binary detection

#### [NEW] `domain/ai/FatigueDetector.kt`
- Monitors speed variability, reaction time proxies, and lateral movement patterns
- Alerts via `AudioFeedbackManager` when fatigue indicators are detected

---

### Feature 5: AI Eco-Route Recommendation

**Impact: ⭐⭐⭐⭐ | Effort: Medium**

**What exists today:**
- [RouteOptimizer](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/analyzer/RouteOptimizer.kt) calculates fuel metrics for Google Maps routes
- [RoutePlannerViewModel](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/routeplanner/RoutePlannerViewModel.kt#L80-L81) uses a **dummy destination** (placeholder) — `val dummyDest = LatLng(origin.latitude + 0.1, ...)`
- No consideration of traffic, time-of-day, or driver-specific history

**What AI enables:**
- **Gemini-powered route analysis** that explains *why* one route is more eco-friendly in natural language
- **Historical learning**: "Based on your last 10 trips to work, Route A via Highway 280 saves you an average of 0.3L compared to Route B via El Camino"
- **Time-of-day optimization**: "Take the surface streets before 7am; switch to the highway after traffic clears at 9:30am"
- **Proper geocoding** — replace the dummy destination with Gemini-assisted place resolution

**Proposed changes:**

#### [MODIFY] [RoutePlannerViewModel](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/routeplanner/RoutePlannerViewModel.kt)
- Replace dummy destination with Google Places Autocomplete or Gemini text-to-coordinates
- Add Gemini-generated natural language comparison of routes
- Integrate historical trip data for the same destination

#### [NEW] `domain/ai/RouteInsightGenerator.kt`
- Takes route alternatives + eco metrics → generates a concise "Pick Route A because..." summary
- Considers driver's historical patterns on similar routes

---

### Feature 6: AI-Enhanced Analytics & Trend Forecasting

**Impact: ⭐⭐⭐ | Effort: Medium**

**What exists today:**
- [AnalyticsViewModel](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/analytics/AnalyticsViewModel.kt) computes basic stats: averages, totals, weekly aggregations
- Static fuel saved estimate vs EPA baseline (hardcoded `6.4 L/100km`)
- No trend interpretation or forecasting

**What AI enables:**
- **Natural language analytics summary**: "Your driving efficiency improved 8% this month. At this rate, you'll save $420 in fuel by year-end."
- **Goal setting & tracking**: AI sets achievable eco-score targets based on driver's trajectory
- **Trend forecasting**: "If you maintain current habits, you'll reach Eco Champion status in 3 weeks"
- **Peer comparison** (anonymized): "You're driving more efficiently than 72% of sedan drivers in your area"
- **Carbon footprint reports** with AI-generated environmental impact narratives

**Proposed changes:**

#### [NEW] `domain/ai/AnalyticsInsightGenerator.kt`
- Takes aggregated trip data → generates Gemini-powered weekly/monthly reports
- Outputs structured data: narrative summary, key metrics, goals, forecast

#### [MODIFY] [AnalyticsViewModel](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/analytics/AnalyticsViewModel.kt)
- Add AI-generated summary section to `AnalyticsState`
- Replace hardcoded EPA baseline with vehicle-specific intelligent baseline

#### [MODIFY] [AnalyticsScreen.kt](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/analytics/AnalyticsScreen.kt)
- Add "AI Insights" card at top with weekly narrative
- Add goal tracker UI component

---

### Feature 7: Smart Eco Score with AI Weighting

**Impact: ⭐⭐⭐ | Effort: Low**

**What exists today:**
- [EcoScoreCalculator](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/analyzer/Analyzers.kt#L226-L311) uses **fixed weights** from [Constants.kt](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/util/Constants.kt#L47-L52):
  - Acceleration: 0.20, Braking: 0.20, Speed: 0.20, Cornering: 0.15, Idle: 0.10, Consistency: 0.15
- Fixed scoring buckets (e.g., `brakeFreq < 0.5 → 100`, `< 1.0 → 85`, etc.)

**What AI enables:**
- **Personalized weight adjustment** based on what matters most for the specific vehicle and driving context
- **Vehicle-type-aware scoring**: EVs should weight regenerative braking differently; hybrids should reward low-speed efficiency
- **Road-context scoring**: Highway trips should weight consistency higher; city trips should weight idle time and braking higher
- **AI score explanation**: "Your score dropped because of 3 hard brakes in a 2-minute span near Exit 12"

**Proposed changes:**

#### [NEW] `domain/ai/AdaptiveScoreWeights.kt`
- Periodically uses Gemini to analyze trip patterns and suggest optimal weight distributions
- Considers vehicle type, typical driving conditions, and improvement potential

#### [MODIFY] [EcoScoreCalculator](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/domain/analyzer/Analyzers.kt#L226-L311)
- Accept dynamic weights from `AdaptiveScoreWeights` instead of hardcoded constants
- Add `scoreExplanation: String` to `EcoScore` model — AI-generated

---

## Cross-Cutting: AI Infrastructure

These shared components support all features above:

#### [NEW] `domain/ai/GeminiManager.kt`
Central singleton for Gemini API management:
- Model initialization and caching (avoid creating `GenerativeModel` on every call as done currently in [TripDetailViewModel L117-L119](file:///Users/sivakumar/Projects/EcoDrive/app/src/main/java/com/ecodrive/app/ui/screens/tripdetail/TripDetailViewModel.kt#L117-L119))
- Rate limiting and request batching
- Graceful fallback chain: Gemini → Local AI → Rule-based
- Token budget management

#### [NEW] `di/AiModule.kt`
Hilt module providing AI dependencies:
- `GeminiManager`, `AiCoachService`, all AI feature singletons

#### [MODIFY] [build.gradle.kts](file:///Users/sivakumar/Projects/EcoDrive/app/build.gradle.kts#L113)
- Upgrade `generativeai` to latest version
- Add TFLite dependency (for Feature 3)
- Add `kotlinx-serialization-json` for structured AI output parsing

---

## Open Questions

> [!IMPORTANT]
> **API Key Strategy**: Should the Gemini API key remain user-provided (current approach), or should you ship a backend proxy so users don't need their own key? A proxy would improve UX but adds server infrastructure.

> [!IMPORTANT]
> **On-Device vs Cloud Trade-offs**: Features 1 and 4 require low-latency decisions during driving. Should we use Gemini Nano (on-device, limited capability) for real-time coaching, with full Gemini for post-trip analysis? This affects supported device range (Gemini Nano requires Pixel 8+ or Samsung S24+).

> [!WARNING]
> **Data Privacy**: Sending GPS coordinates and driving telemetry to Gemini raises privacy concerns. Should we anonymize/strip location data from prompts? Should there be an opt-in toggle per feature?

> [!IMPORTANT]
> **Feature 3 (TFLite ML)**: This is the highest-effort feature. Would you prefer to start with a simpler approach (e.g., Gemini-generated correction factors from historical data) before investing in on-device ML training?

---

## Recommended Implementation Order

| Phase | Features | Timeline Estimate |
|-------|----------|-------------------|
| **Phase 1** | Feature 2 (Trip Summary) + AI Infrastructure (`GeminiManager`) | 1-2 weeks |
| **Phase 2** | Feature 1 (Real-Time Coach) + Feature 7 (Smart Scoring) | 2-3 weeks |
| **Phase 3** | Feature 4 (Anomaly Detection) + Feature 5 (Eco-Routes) | 2-3 weeks |
| **Phase 4** | Feature 6 (Analytics AI) + Feature 3 (ML Fuel Prediction) | 3-4 weeks |

Phase 1 starts with the lowest-risk, highest-visibility improvements since the Gemini plumbing already exists.

---

## Verification Plan

### Automated Tests
- Unit tests for all new AI service classes with mocked Gemini responses
- Integration tests verifying fallback chains (Gemini → Local → Rule-based)
- Regression tests for existing `EcoScoreCalculator`, `FuelEstimationEngine`, `DrivingPatternAnalyzer`
```bash
./gradlew testDebugUnitTest
```

### Manual Verification
- Test with Gemini API key set and unset (verify graceful fallback)
- Test with airplane mode (verify offline behavior)
- Drive test with real device to validate coaching timing and relevance
- Compare fuel estimates: physics-only vs ML-enhanced (Feature 3)
