package com.ecodrive.app.domain.ai.analyzer

import com.ecodrive.app.domain.ai.service.AiManager

import com.ecodrive.app.domain.model.DrivingMetrics
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.abs

/**
 * Service that detects potential driver fatigue or distraction
 * based on driving patterns (speed variability, lateral movement).
 */
@Singleton
class FatigueDetector @Inject constructor() {

    private val speedHistory = mutableListOf<Double>()
    private val lateralAccelHistory = mutableListOf<Double>()
    private val maxHistorySize = 300 // 5 minutes at 1Hz

    /**
     * Analyzes current metrics and returns true if fatigue indicators are high.
     */
    fun analyze(metrics: DrivingMetrics): FatigueStatus {
        speedHistory.add(metrics.speedKmh)
        lateralAccelHistory.add(metrics.lateralAccelMps2)
        
        if (speedHistory.size > maxHistorySize) {
            speedHistory.removeAt(0)
            lateralAccelHistory.removeAt(0)
        }

        if (speedHistory.size < 60) return FatigueStatus.NORMAL

        // 1. Speed Variability (High variability at constant target speed = fatigue)
        val speedStdDev = calculateStdDev(speedHistory)
        
        // 2. Lateral Movement Patterns (Swerving)
        val avgLateralAbs = lateralAccelHistory.map { abs(it) }.average()
        
        return when {
            speedStdDev > 15.0 && metrics.speedKmh > 50 -> FatigueStatus.HIGH_RISK
            avgLateralAbs > 1.5 -> FatigueStatus.MODERATE_RISK
            speedStdDev > 10.0 -> FatigueStatus.MODERATE_RISK
            else -> FatigueStatus.NORMAL
        }
    }

    private fun calculateStdDev(data: List<Double>): Double {
        val mean = data.average()
        val variance = data.map { (it - mean) * (it - mean) }.average()
        return kotlin.math.sqrt(variance)
    }

    fun reset() {
        speedHistory.clear()
        lateralAccelHistory.clear()
    }
}

enum class FatigueStatus {
    NORMAL,
    MODERATE_RISK,
    HIGH_RISK
}
