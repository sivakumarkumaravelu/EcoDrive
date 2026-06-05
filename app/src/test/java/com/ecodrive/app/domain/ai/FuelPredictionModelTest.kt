package com.ecodrive.app.domain.ai

import android.content.Context
import com.ecodrive.app.domain.model.Vehicle
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FuelPredictionModelTest {

    private val context: Context = mockk(relaxed = true)
    private lateinit var model: FuelPredictionModel

    @Before
    fun setup() {
        model = FuelPredictionModel(context)
    }

    @Test
    fun `test heuristic correction for city driving`() {
        val factor = model.predictCorrectionFactor(20.0, 0.5, 0.0, Vehicle())
        // speed < 30 -> 1.1
        assertEquals(1.1, factor, 0.01)
    }

    @Test
    fun `test heuristic correction for highway cruise`() {
        val factor = model.predictCorrectionFactor(80.0, 0.1, 0.0, Vehicle())
        // speed 70..90 and low accel -> 0.9
        assertEquals(0.9, factor, 0.01)
    }

    @Test
    fun `test heuristic correction for high load`() {
        val factor = model.predictCorrectionFactor(50.0, 2.5, 0.0, Vehicle())
        // accel > 2.0 -> 1.15
        assertEquals(1.15, factor, 0.01)
    }

    @Test
    fun `test combined penalties`() {
        val factor = model.predictCorrectionFactor(20.0, 2.5, 0.0, Vehicle())
        // speed < 30 (1.1) * accel > 2.0 (1.15) = 1.265
        assertEquals(1.265, factor, 0.001)
    }
}
