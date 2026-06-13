package com.ecodrive.app.domain.analyzer

import com.ecodrive.app.domain.model.FuelType
import org.junit.Assert.assertEquals
import org.junit.Test

class FuelEfficiencyCalculatorTest {

    private val calculator = FuelEfficiencyCalculator()

    @Test
    fun `test calculateFuelRateLPerH returns zero for non-positive maf`() {
        assertEquals(0.0, calculator.calculateFuelRateLPerH(0.0, FuelType.GASOLINE), 0.0)
        assertEquals(0.0, calculator.calculateFuelRateLPerH(-1.5, FuelType.GASOLINE), 0.0)
    }

    @Test
    fun `test calculateFuelRateLPerH returns zero for electricity`() {
        assertEquals(0.0, calculator.calculateFuelRateLPerH(10.0, FuelType.ELECTRICITY), 0.0)
    }

    @Test
    fun `test calculateFuelRateLPerH gasoline calculation`() {
        // MAF = 5.0 g/s. gasoline stoichiometry = 14.7, density = 0.745
        // Expected = (5.0 * 3600) / (14.7 * 0.745 * 1000)
        // = 18000 / 10951.5 = 1.6436 L/h
        val expected = (5.0 * 3600.0) / (14.7 * 0.745 * 1000.0)
        assertEquals(expected, calculator.calculateFuelRateLPerH(5.0, FuelType.GASOLINE), 0.001)
    }

    @Test
    fun `test calculateFuelRateLPerH diesel calculation`() {
        // MAF = 5.0 g/s. diesel stoichiometry = 14.5, density = 0.832
        // Expected = (5.0 * 3600) / (14.5 * 0.832 * 1000)
        // = 18000 / 12064 = 1.492 L/h
        val expected = (5.0 * 3600.0) / (14.5 * 0.832 * 1000.0)
        assertEquals(expected, calculator.calculateFuelRateLPerH(5.0, FuelType.DIESEL), 0.001)
    }

    @Test
    fun `test calculateFuelRateLPerH ethanol calculation`() {
        // MAF = 5.0 g/s. ethanol stoichiometry = 9.0, density = 0.789
        // Expected = (5.0 * 3600) / (9.0 * 0.789 * 1000)
        // = 18000 / 7101 = 2.5348 L/h
        val expected = (5.0 * 3600.0) / (9.0 * 0.789 * 1000.0)
        assertEquals(expected, calculator.calculateFuelRateLPerH(5.0, FuelType.ETHANOL), 0.001)
    }

    @Test
    fun `test calculateFuelRateLPerH lpg calculation`() {
        // MAF = 5.0 g/s. lpg stoichiometry = 15.5, density = 0.510
        // Expected = (5.0 * 3600) / (15.5 * 0.510 * 1000)
        // = 18000 / 7905 = 2.277 L/h
        val expected = (5.0 * 3600.0) / (15.5 * 0.510 * 1000.0)
        assertEquals(expected, calculator.calculateFuelRateLPerH(5.0, FuelType.LPG), 0.001)
    }

    @Test
    fun `test calculateConsumptionLPer100Km returns cap for low speed`() {
        assertEquals(99.9, calculator.calculateConsumptionLPer100Km(5.0, 5.0), 0.0)
        assertEquals(99.9, calculator.calculateConsumptionLPer100Km(5.0, 2.0), 0.0)
        assertEquals(99.9, calculator.calculateConsumptionLPer100Km(5.0, 0.0), 0.0)
    }

    @Test
    fun `test calculateConsumptionLPer100Km standard calculation`() {
        // Fuel rate = 8.0 L/h, speed = 80.0 km/h
        // Expected = (8.0 / 80.0) * 100 = 10.0 L/100km
        assertEquals(10.0, calculator.calculateConsumptionLPer100Km(8.0, 80.0), 0.001)
    }
}
