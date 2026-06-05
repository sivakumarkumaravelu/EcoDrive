package com.ecodrive.app.domain.ai

import android.content.Context
import android.util.Log
import com.ecodrive.app.domain.model.Vehicle
import dagger.hilt.android.qualifiers.ApplicationContext
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * On-device ML model for predicting fuel consumption correction factors.
 * It combines physics-based VSP with a trained model that learns
 * specific vehicle idiosyncrasies.
 */
@Singleton
class FuelPredictionModel @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private var interpreter: Interpreter? = null
    private var isModelLoaded = false

    init {
        loadModel()
    }

    private fun loadModel() {
        // In a real app, this would load a .tflite file from assets or internal storage
        // For this demo, we'll simulate the ML layer until a model is trained/shipped.
        try {
            val modelFile = File(context.filesDir, "fuel_predictor.tflite")
            if (modelFile.exists()) {
                interpreter = Interpreter(modelFile)
                isModelLoaded = true
                Log.i("FuelPredictionModel", "TFLite model loaded successfully")
            }
        } catch (e: Exception) {
            Log.e("FuelPredictionModel", "Failed to load TFLite model: ${e.message}")
        }
    }

    /**
     * Predicts a correction factor for the current driving state.
     * Inputs: speed, acceleration, road grade, vehicle mass, etc.
     */
    fun predictCorrectionFactor(
        speedKmh: Double,
        accelMps2: Double,
        gradePercent: Double,
        vehicle: Vehicle
    ): Double {
        if (!isModelLoaded) {
            // Fallback to a simple non-linear heuristic (mocking the ML behavior)
            return calculateHeuristicCorrection(speedKmh, accelMps2, gradePercent)
        }

        return try {
            val input = ByteBuffer.allocateDirect(4 * 4).order(ByteOrder.nativeOrder())
            input.putFloat(speedKmh.toFloat())
            input.putFloat(accelMps2.toFloat())
            input.putFloat(gradePercent.toFloat())
            input.putFloat(vehicle.massKg.toFloat())

            val output = ByteBuffer.allocateDirect(4).order(ByteOrder.nativeOrder())
            interpreter?.run(input, output)
            
            output.rewind()
            output.float.toDouble().coerceIn(0.5, 2.0)
        } catch (e: Exception) {
            1.0
        }
    }

    private fun calculateHeuristicCorrection(
        speedKmh: Double,
        accelMps2: Double,
        gradePercent: Double
    ): Double {
        // Simulate ML-like non-linear adjustments
        var factor = 1.0
        
        // Cold engine / city penalty
        if (speedKmh < 30) factor *= 1.1 
        
        // High load penalty (aggressive accel or steep hills)
        if (accelMps2 > 2.0 || gradePercent > 5.0) factor *= 1.15
        
        // Efficiency sweet spot (highway cruise)
        if (speedKmh in 70.0..90.0 && abs(accelMps2) < 0.2) factor *= 0.9
        
        return factor
    }

    private fun abs(v: Double) = if (v < 0) -v else v
}
