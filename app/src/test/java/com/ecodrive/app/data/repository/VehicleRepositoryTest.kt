package com.ecodrive.app.data.repository

import com.ecodrive.app.data.local.dao.VehicleDao
import com.ecodrive.app.data.local.entity.VehicleEntity
import com.ecodrive.app.domain.model.FuelType
import com.ecodrive.app.domain.model.Vehicle
import com.ecodrive.app.domain.model.VehicleType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

class VehicleRepositoryTest {

    private lateinit var vehicleDao: VehicleDao
    private lateinit var vehicleRepository: VehicleRepository

    @Before
    fun setup() {
        vehicleDao = mockk()
        vehicleRepository = VehicleRepository(vehicleDao)
    }

    @Test
    fun `test getDefaultVehicle returns domain model`() = runBlocking {
        // Given
        val entity = VehicleEntity(
            id = 1L,
            name = "Test Car",
            make = "Toyota",
            model = "Corolla",
            vehicleType = "ICE",
            fuelType = "GASOLINE"
        )
        coEvery { vehicleDao.getDefaultVehicle() } returns entity

        // When
        val result = vehicleRepository.getDefaultVehicle()

        // Then
        assertNotNull(result)
        assertEquals("Test Car", result?.name)
        assertEquals(VehicleType.ICE, result?.vehicleType)
        assertEquals(FuelType.GASOLINE, result?.fuelType)
    }

    @Test
    fun `test saveVehicle calls insert for new vehicle`() = runBlocking {
        // Given
        val vehicle = Vehicle(
            id = 0L,
            name = "New Car",
            vehicleType = VehicleType.HYBRID,
            fuelType = FuelType.GASOLINE
        )
        coEvery { vehicleDao.insertVehicle(any()) } returns 1L

        // When
        val resultId = vehicleRepository.saveVehicle(vehicle)

        // Then
        assertEquals(1L, resultId)
        coVerify { vehicleDao.insertVehicle(match { it.name == "New Car" && it.vehicleType == "HYBRID" }) }
    }

    @Test
    fun `test saveVehicle calls update for existing vehicle`() = runBlocking {
        // Given
        val vehicle = Vehicle(
            id = 1L,
            name = "Updated Car",
            vehicleType = VehicleType.ICE
        )
        coEvery { vehicleDao.updateVehicle(any()) } returns Unit

        // When
        val resultId = vehicleRepository.saveVehicle(vehicle)

        // Then
        assertEquals(1L, resultId)
        coVerify { vehicleDao.updateVehicle(match { it.id == 1L && it.name == "Updated Car" }) }
    }
}
