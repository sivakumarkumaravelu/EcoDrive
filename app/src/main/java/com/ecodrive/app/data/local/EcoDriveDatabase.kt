package com.ecodrive.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ecodrive.app.data.local.dao.*
import com.ecodrive.app.data.local.entity.*

/**
 * Room database for EcoDrive.
 * Stores trips, driving events, data points, vehicle profiles,
 * and fuel calibration history.
 */
@Database(
    entities = [
        TripEntity::class,
        DrivingEventEntity::class,
        DataPointEntity::class,
        VehicleEntity::class,
        FuelCalibrationEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class EcoDriveDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun drivingEventDao(): DrivingEventDao
    abstract fun dataPointDao(): DataPointDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelCalibrationDao(): FuelCalibrationDao
}
