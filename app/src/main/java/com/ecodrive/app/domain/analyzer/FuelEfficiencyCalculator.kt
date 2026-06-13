package com.ecodrive.app.domain.analyzer

import com.ecodrive.app.domain.model.FuelType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Calculates fuel efficiency metrics from OBD-II data (MAF).
 */
@Singleton
class FuelEfficiencyCalculator @Inject constructor() {

    /**
     * Calculate instantaneous fuel rate in Liters per Hour using MAF.
     * Formula: (MAF * 3600) / (Stoichiometric_Ratio * fuel_density * 1000)
     */
    fun calculateFuelRateLPerH(mafGramsPerSec: Double, fuelType: FuelType): Double {
        if (mafGramsPerSec <= 0 || fuelType == FuelType.ELECTRICITY) return 0.0
        
        // Stoichiometric ratio (parts air to 1 part fuel by mass)
        val airFuelRatio = when (fuelType) {
            FuelType.GASOLINE -> 14.7
            FuelType.DIESEL -> 14.5
            FuelType.ETHANOL -> 9.0
            FuelType.LPG -> 15.5
            FuelType.ELECTRICITY -> 1.0 // Not applicable
        }
        
        // Typical density in kg/L
        val fuelDensity = when (fuelType) {
            FuelType.GASOLINE -> 0.745
            FuelType.DIESEL -> 0.832
            FuelType.ETHANOL -> 0.789
            FuelType.LPG -> 0.510
            FuelType.ELECTRICITY -> 1.0 // Not applicable
        }
        
        // (grams_air/sec * 3600 sec/hr) / (grams_air/grams_fuel * grams_fuel/liter)
        return (mafGramsPerSec * 3600.0) / (airFuelRatio * fuelDensity * 1000.0)
    }

    /**
     * Calculate fuel consumption in L/100km.
     */
    fun calculateConsumptionLPer100Km(fuelRateLPerH: Double, speedKmh: Double): Double {
        if (speedKmh <= 5.0) return 99.9 // Avoid division by zero, max out consumption at idle
        return (fuelRateLPerH / speedKmh) * 100.0
    }
}
