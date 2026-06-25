package com.ecodrive.app.domain.analyzer

import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.data.local.dao.FuelCalibrationDao
import android.location.Location
import com.ecodrive.app.domain.ai.service.AiManager
import com.ecodrive.app.domain.ai.analyzer.FuelPredictionModel
import com.ecodrive.app.domain.model.*
import com.google.android.gms.maps.model.LatLng
import io.mockk.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RouteOptimizerTest {

    private lateinit var fuelEstimationEngine: FuelEstimationEngine
    private lateinit var routeOptimizer: RouteOptimizer
    private val fuelCalibrationDao: FuelCalibrationDao = mockk(relaxed = true)
    private val aiManager: AiManager = mockk(relaxed = true)
    private val preferenceManager: PreferenceManager = mockk(relaxed = true)
    private val mlModel: FuelPredictionModel = mockk(relaxed = true)
    private val applicationScope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)

    private val testVehicle = Vehicle(
        name = "Test Car",
        massKg = 1500.0,
        dragCoefficient = 0.3,
        frontalAreaM2 = 2.2,
        rollingResistance = 0.012,
        engineDisplacementCc = 2000,
        fuelType = FuelType.GASOLINE,
        vehicleType = VehicleType.ICE
    )

    @Before
    fun setup() {
        mockkStatic(Location::class)
        val resultsSlot = slot<FloatArray>()
        every { 
            Location.distanceBetween(any(), any(), any(), any(), capture(resultsSlot)) 
        } answers {
            resultsSlot.captured[0] = 500f // Return 500m distance for any two points
        }

        every { mlModel.predictCorrectionFactor(any(), any(), any(), any()) } returns 1.0
        fuelEstimationEngine = FuelEstimationEngine(fuelCalibrationDao, aiManager, preferenceManager, mlModel, applicationScope)
        routeOptimizer = RouteOptimizer(fuelEstimationEngine)
    }

    @Test
    fun `test calculateEcoMetrics with flat route`() {
        // 1km flat route at 60km/h (60 seconds)
        val route = MapRoute(
            polyline = "abc",
            distanceMeters = 1000,
            durationSeconds = 60,
            summary = "Flat Route",
            points = listOf(LatLng(0.0, 0.0), LatLng(0.009, 0.0)) // approx 1km
        )
        val elevations = listOf(100.0, 100.0)

        val metrics = routeOptimizer.calculateEcoMetrics(route, elevations, testVehicle)

        assertTrue(metrics.estimatedFuelLiters > 0)
        assertEquals(0.0, metrics.averageGradePercent, 0.01)
        assertEquals(1.0, metrics.distanceKm, 0.01)
        assertEquals(1, metrics.durationMinutes)
    }

    @Test
    fun `test calculateEcoMetrics with hilly route`() {
        val route = MapRoute(
            polyline = "abc",
            distanceMeters = 1000,
            durationSeconds = 60,
            summary = "Hilly Route",
            points = listOf(LatLng(0.0, 0.0), LatLng(0.009, 0.0))
        )
        
        val flatElevations = listOf(100.0, 100.0)
        val uphillElevations = listOf(100.0, 150.0) // 50m climb over 1km = 5% grade

        val flatMetrics = routeOptimizer.calculateEcoMetrics(route, flatElevations, testVehicle)
        val uphillMetrics = routeOptimizer.calculateEcoMetrics(route, uphillElevations, testVehicle)

        assertTrue("Uphill route should consume more fuel than flat route", 
            uphillMetrics.estimatedFuelLiters > flatMetrics.estimatedFuelLiters)
        assertTrue(uphillMetrics.averageGradePercent > 0)
        assertTrue(uphillMetrics.ecoScore < flatMetrics.ecoScore)
    }

    @Test
    fun `test calculateEcoMetrics with diesel vehicle impacts co2`() {
        val route = MapRoute(
            polyline = "abc",
            distanceMeters = 1000,
            durationSeconds = 60,
            summary = "Test Route",
            points = listOf(LatLng(0.0, 0.0), LatLng(0.009, 0.0))
        )
        val elevations = listOf(100.0, 100.0)

        val gasolineVehicle = testVehicle.copy(fuelType = FuelType.GASOLINE)
        val dieselVehicle = testVehicle.copy(fuelType = FuelType.DIESEL)

        val gasMetrics = routeOptimizer.calculateEcoMetrics(route, elevations, gasolineVehicle)
        val dieselMetrics = routeOptimizer.calculateEcoMetrics(route, elevations, dieselVehicle)

        // Diesel has higher energy density, so it might use fewer liters, 
        // but it has a higher CO2 factor (2.68 vs 2.31).
        // Let's just verify CO2 is calculated differently.
        assertTrue(gasMetrics.estimatedCo2Kg != dieselMetrics.estimatedCo2Kg)
    }

    @Test
    fun `test calculateEcoMetrics with empty points returns zeros`() {
        val route = MapRoute(
            polyline = "",
            distanceMeters = 0,
            durationSeconds = 0,
            summary = "",
            points = emptyList()
        )
        val metrics = routeOptimizer.calculateEcoMetrics(route, emptyList(), testVehicle)

        assertEquals(0.0, metrics.estimatedFuelLiters, 0.0)
        assertEquals(0.0, metrics.distanceKm, 0.0)
        assertEquals(0, metrics.durationMinutes)
    }
}
