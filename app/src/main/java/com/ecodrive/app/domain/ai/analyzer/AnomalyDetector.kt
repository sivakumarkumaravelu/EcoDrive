package com.ecodrive.app.domain.ai.analyzer

import android.util.Log
import com.ecodrive.app.domain.ai.service.AiManager
import com.ecodrive.app.domain.model.AnomalySeverity
import com.ecodrive.app.domain.model.AnomalyType
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.VehicleAnomaly
import com.ecodrive.app.util.Constants
import com.ecodrive.app.util.UnitConverter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.launch

/**
 * Detects statistical anomalies in vehicle telemetry that may indicate
 * mechanical issues. Uses a rolling baseline built from recent data points
 * and z-score deviation analysis.
 *
 * Examples of detected anomalies:
 * - Fuel consumption 30%+ above baseline at the same speed → underinflated tires or clogged filter
 * - Persistent lateral pull bias → wheel alignment or tire pressure imbalance
 * - High vertical acceleration → suspension issue or road
 */
@Singleton
class AnomalyDetector @Inject constructor(
    private val aiManager: AiManager,
    private val preferenceManager: com.ecodrive.app.data.local.PreferenceManager,
    @com.ecodrive.app.di.ApplicationScope private val scope: kotlinx.coroutines.CoroutineScope
) {
    companion object {
        private const val TAG = "AnomalyDetector"
        private const val BASELINE_WINDOW = 200  // Data points for baseline
        private const val ALERT_COOLDOWN_MS = 120_000L  // 2 minutes between same alerts
    }

    // Rolling baselines
    private val fuelRateHistory = mutableListOf<Double>()
    private val lateralAccelHistory = mutableListOf<Double>()
    private val verticalAccelHistory = mutableListOf<Double>()
    private val speedHistory = mutableListOf<Double>()

    private val lastAlertMs = mutableMapOf<AnomalyType, Long>()
    private val detectedAnomalies = mutableListOf<VehicleAnomaly>()
    private var useMetric = true

    init {
        scope.launch {
            preferenceManager.useMetricUnits.collect { metric ->
                useMetric = metric
            }
        }
    }

    /**
     * Feed a new data point and return any newly detected anomalies.
     */
    fun analyze(metrics: DrivingMetrics): List<VehicleAnomaly> {
        // Only analyze while actively moving (avoid idle noise)
        if (!metrics.isMoving || metrics.speedKmh < 20) {
            updateHistory(metrics)
            return emptyList()
        }

        val newAnomalies = mutableListOf<VehicleAnomaly>()

        if (fuelRateHistory.size >= BASELINE_WINDOW / 2) {
            // 1. High fuel consumption anomaly
            checkHighFuelConsumption(metrics)?.let { newAnomalies.add(it) }

            // 2. Lateral pull bias
            checkLateralPull(metrics)?.let { newAnomalies.add(it) }

            // 3. Harsh vibration (vertical acceleration)
            checkHarshVibration(metrics)?.let { newAnomalies.add(it) }

            // 4. Speed oscillation (cruise instability)
            checkSpeedOscillation(metrics)?.let { newAnomalies.add(it) }
        }

        updateHistory(metrics)
        detectedAnomalies.addAll(newAnomalies)
        return newAnomalies
    }

    private fun checkHighFuelConsumption(metrics: DrivingMetrics): VehicleAnomaly? {
        if (fuelRateHistory.size < 30 || metrics.fuelRateLPerH <= 0) return null
        if (!canAlert(AnomalyType.HIGH_FUEL_CONSUMPTION)) return null

        // Only evaluate fuel consumption anomalies during steady cruising (no hard accel/braking)
        if (abs(metrics.longitudinalAccelMps2) > 0.5) return null

        // Only compare at similar speeds (within ±10 km/h of current)
        val targetSpeed = metrics.speedKmh
        val similarSpeedFuel = fuelRateHistory.zip(speedHistory).filter { (_, s) ->
            abs(s - targetSpeed) < 10
        }.map { it.first }

        if (similarSpeedFuel.size < 10) return null

        val baseline = similarSpeedFuel.average()
        if (baseline <= 0) return null

        val excess = (metrics.fuelRateLPerH - baseline) / baseline
        if (excess > 0.30) {
            markAlert(AnomalyType.HIGH_FUEL_CONSUMPTION)
            val severity = if (excess > 0.50) AnomalySeverity.HIGH else AnomalySeverity.MEDIUM
            Log.d(TAG, "HIGH_FUEL_CONSUMPTION detected: ${(excess * 100).toInt()}% above baseline")
            return VehicleAnomaly(
                type = AnomalyType.HIGH_FUEL_CONSUMPTION,
                severity = severity,
                description = "Fuel use is ${(excess * 100).toInt()}% above your baseline",
                detectedAtSpeedKmh = metrics.speedKmh,
            )
        }
        return null
    }

    private fun checkLateralPull(metrics: DrivingMetrics): VehicleAnomaly? {
        if (lateralAccelHistory.size < 60) return null
        if (!canAlert(AnomalyType.LATERAL_PULL)) return null

        // Average lateral bias over recent history (should be near 0 for straight roads)
        val avgLateral = lateralAccelHistory.takeLast(60).average()
        if (abs(avgLateral) > 0.4) {
            markAlert(AnomalyType.LATERAL_PULL)
            val direction = if (avgLateral > 0) "right" else "left"
            return VehicleAnomaly(
                type = AnomalyType.LATERAL_PULL,
                severity = AnomalySeverity.MEDIUM,
                description = "Persistent pull to the $direction detected — possible tire or alignment issue",
                detectedAtSpeedKmh = metrics.speedKmh,
            )
        }
        return null
    }

    private fun checkHarshVibration(metrics: DrivingMetrics): VehicleAnomaly? {
        if (!canAlert(AnomalyType.HARSH_VIBRATION)) return null

        val baseline = stdDev(verticalAccelHistory.takeLast(100))
        if (abs(metrics.verticalAccelMps2) > baseline * 4 && abs(metrics.verticalAccelMps2) > 2.0) {
            markAlert(AnomalyType.HARSH_VIBRATION)
            return VehicleAnomaly(
                type = AnomalyType.HARSH_VIBRATION,
                severity = AnomalySeverity.LOW,
                description = "Unusual vertical impact detected — rough road or possible suspension issue",
                detectedAtSpeedKmh = metrics.speedKmh,
            )
        }
        return null
    }

    private fun checkSpeedOscillation(metrics: DrivingMetrics): VehicleAnomaly? {
        if (speedHistory.size < 60 || !canAlert(AnomalyType.SPEED_OSCILLATION)) return null

        // High frequency speed changes at highway speeds suggest engine hunt or misfire
        if (metrics.speedKmh > 80) {
            val recentSpeedStdDev = stdDev(speedHistory.takeLast(30))
            if (recentSpeedStdDev > 8.0) {
                markAlert(AnomalyType.SPEED_OSCILLATION)
                return VehicleAnomaly(
                    type = AnomalyType.SPEED_OSCILLATION,
                    severity = AnomalySeverity.MEDIUM,
                    description = "Unstable speed at highway cruise — possible engine hunt or throttle issue",
                    detectedAtSpeedKmh = metrics.speedKmh,
                )
            }
        }
        return null
    }

    /**
     * Enriches anomalies with AI diagnosis. Call after a trip ends.
     * @return the same list with [VehicleAnomaly.aiDiagnosis] populated where possible.
     */
    suspend fun enrichWithAiDiagnosis(anomalies: List<VehicleAnomaly>): List<VehicleAnomaly> {
        if (anomalies.isEmpty()) return emptyList()

        val prompt = """
            You are a Vehicle Diagnostics Expert. Based on these detected anomalies during a recent drive,
            provide a concise diagnostic note (max 2 sentences per anomaly) explaining the most likely cause.
            
            Anomalies:
            ${anomalies.joinToString("\n") { "- [${it.type}] ${it.description}" }}
            
            For each anomaly, respond in this format:
            [ANOMALY_TYPE]: Diagnosis text here.
            
            Focus on actionable, safe guidance (e.g., "Check tire pressures when cold").
        """.trimIndent()

        return try {
            val response = aiManager.generateTripInsight(prompt) ?: return anomalies
            anomalies.map { anomaly ->
                val marker = "[${anomaly.type}]:"
                val diagnosisLine = response.lines().firstOrNull { it.contains(marker) }
                val diagnosis = diagnosisLine?.substringAfter(marker)?.trim()
                if (diagnosis != null) anomaly.copy(aiDiagnosis = diagnosis) else anomaly
            }
        } catch (e: Exception) {
            Log.w(TAG, "AI diagnosis enrichment failed: ${e.message}")
            anomalies
        }
    }

    fun getDetectedAnomalies(): List<VehicleAnomaly> = detectedAnomalies.toList()

    fun reset() {
        fuelRateHistory.clear()
        lateralAccelHistory.clear()
        verticalAccelHistory.clear()
        speedHistory.clear()
        lastAlertMs.clear()
        detectedAnomalies.clear()
    }

    private fun updateHistory(metrics: DrivingMetrics) {
        // Build fuel history ONLY during steady state to get an accurate cruising baseline
        if (metrics.fuelRateLPerH > 0 && abs(metrics.longitudinalAccelMps2) < 0.5) {
            fuelRateHistory.add(metrics.fuelRateLPerH)
        }
        lateralAccelHistory.add(metrics.lateralAccelMps2)
        verticalAccelHistory.add(metrics.verticalAccelMps2)
        speedHistory.add(metrics.speedKmh)

        // Trim to window size
        if (fuelRateHistory.size > BASELINE_WINDOW) fuelRateHistory.removeAt(0)
        if (lateralAccelHistory.size > BASELINE_WINDOW) lateralAccelHistory.removeAt(0)
        if (verticalAccelHistory.size > BASELINE_WINDOW) verticalAccelHistory.removeAt(0)
        if (speedHistory.size > BASELINE_WINDOW) speedHistory.removeAt(0)
    }

    private fun canAlert(type: AnomalyType): Boolean {
        val last = lastAlertMs[type] ?: 0L
        return System.currentTimeMillis() - last > ALERT_COOLDOWN_MS
    }

    private fun markAlert(type: AnomalyType) {
        lastAlertMs[type] = System.currentTimeMillis()
    }

    private fun stdDev(data: List<Double>): Double {
        if (data.size < 2) return 0.0
        val mean = data.average()
        val variance = data.sumOf { (it - mean) * (it - mean) } / data.size
        return sqrt(variance)
    }
}
