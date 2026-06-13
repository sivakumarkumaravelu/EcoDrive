package com.ecodrive.app.data.repository

import com.ecodrive.app.data.local.dao.VehicleDao
import com.ecodrive.app.data.local.entity.VehicleEntity
import com.ecodrive.app.domain.model.FuelType
import com.ecodrive.app.domain.model.Vehicle
import com.ecodrive.app.domain.model.VehicleType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for managing vehicle profiles.
 */
@Singleton
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao,
) {
    /**
     * Get all vehicles.
     */
    fun getAllVehicles(): Flow<List<Vehicle>> {
        return vehicleDao.getAllVehicles().map { entities ->
            entities.map { it.toDomain() }
        }.flowOn(Dispatchers.IO)
    }

    /**
     * Get the default (active) vehicle.
     */
    suspend fun getDefaultVehicle(): Vehicle? {
        return vehicleDao.getDefaultVehicle()?.toDomain()
    }

    /**
     * Get a vehicle by ID.
     */
    suspend fun getVehicleById(vehicleId: Long): Vehicle? {
        return vehicleDao.getVehicleById(vehicleId)?.toDomain()
    }

    /**
     * Add or update a vehicle.
     */
    suspend fun saveVehicle(vehicle: Vehicle): Long {
        val entity = vehicle.toEntity()
        return if (entity.id == 0L) {
            vehicleDao.insertVehicle(entity)
        } else {
            vehicleDao.updateVehicle(entity)
            entity.id
        }
    }
}

// ── Entity ↔ Domain Mappers ─────────────────────────────────────

private fun VehicleEntity.toDomain() = Vehicle(
    id = id,
    name = name,
    make = make,
    model = model,
    year = year,
    vehicleType = try { VehicleType.valueOf(vehicleType) } catch (e: Exception) { VehicleType.ICE },
    fuelType = try { FuelType.valueOf(fuelType) } catch (e: Exception) { FuelType.GASOLINE },
    massKg = massKg,
    dragCoefficient = dragCoefficient,
    frontalAreaM2 = frontalAreaM2,
    rollingResistance = rollingResistance,
    tankCapacityLiters = tankCapacityLiters,
    engineDisplacementCc = engineDisplacementCc,
    fuelCalibrationFactor = fuelCalibrationFactor,
    odometerKm = odometerKm,
    fuelLevelPercent = fuelLevelPercent,
    isDefault = isDefault,
)

private fun Vehicle.toEntity() = VehicleEntity(
    id = id,
    name = name,
    make = make,
    model = model,
    year = year,
    vehicleType = vehicleType.name,
    fuelType = fuelType.name,
    massKg = massKg,
    dragCoefficient = dragCoefficient,
    frontalAreaM2 = frontalAreaM2,
    rollingResistance = rollingResistance,
    tankCapacityLiters = tankCapacityLiters,
    engineDisplacementCc = engineDisplacementCc,
    fuelCalibrationFactor = fuelCalibrationFactor,
    odometerKm = odometerKm,
    fuelLevelPercent = fuelLevelPercent,
    isDefault = isDefault,
)
