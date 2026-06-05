package com.ecodrive.app.util

/**
 * Utility for converting between metric and imperial units.
 * Supports Distance, Speed, Fuel Efficiency, and Fuel Volume.
 */
object UnitConverter {
    // ── Distance ────────────────────────────────────────────────
    fun kmToMiles(km: Double): Double = km * 0.621371
    fun milesToKm(miles: Double): Double = miles / 0.621371

    // ── Speed ───────────────────────────────────────────────────
    fun kmhToMph(kmh: Double): Double = kmh * 0.621371
    fun mphToKmh(mph: Double): Double = mph / 0.621371

    // ── Fuel Efficiency ─────────────────────────────────────────
    /**
     * L/100km to MPG (US): MPG = 235.215 / (L/100km)
     */
    fun l100kmToMpg(l100km: Double): Double {
        if (l100km <= 0) return 0.0
        return 235.215 / l100km
    }
    
    fun mpgToL100km(mpg: Double): Double {
        if (mpg <= 0) return 0.0
        return 235.215 / mpg
    }

    // ── Fuel Volume ─────────────────────────────────────────────
    fun litersToGallons(liters: Double): Double = liters * 0.264172
    fun gallonsToLiters(gallons: Double): Double = gallons / 0.264172

    // ── Formatting Helpers ──────────────────────────────────────
    
    fun formatDistance(km: Double, useMetric: Boolean): String {
        return if (useMetric) {
            "%.1f km".format(km)
        } else {
            "%.1f mi".format(kmToMiles(km))
        }
    }

    fun formatSpeed(kmh: Double, useMetric: Boolean): String {
        return if (useMetric) {
            "%.0f km/h".format(kmh)
        } else {
            "%.0f mph".format(kmhToMph(kmh))
        }
    }

    fun formatFuelEfficiency(l100km: Double, useMetric: Boolean): String {
        if (l100km <= 0) return "—"
        return if (useMetric) {
            "%.1f L/100km".format(l100km)
        } else {
            "%.1f mpg".format(l100kmToMpg(l100km))
        }
    }
    
    fun formatFuelVolume(liters: Double, useMetric: Boolean): String {
        return if (useMetric) {
            "%.2f L".format(liters)
        } else {
            "%.2f gal".format(litersToGallons(liters))
        }
    }
}
