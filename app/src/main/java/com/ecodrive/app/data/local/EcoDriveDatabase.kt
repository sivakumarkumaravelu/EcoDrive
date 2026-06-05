package com.ecodrive.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.ecodrive.app.data.local.dao.*
import com.ecodrive.app.data.local.entity.*

/**
 * Room database for EcoDrive.
 * Stores trips, driving events, data points, vehicle profiles,
 * fuel calibration history, AI insights, and gamification data.
 */
@Database(
    entities = [
        TripEntity::class,
        DrivingEventEntity::class,
        DataPointEntity::class,
        VehicleEntity::class,
        FuelCalibrationEntity::class,
        AiInsightEntity::class,
        ChallengeEntity::class,
        BadgeEntity::class,
        AnomalyEntity::class,
    ],
    version = 6,
    exportSchema = false,
)
abstract class EcoDriveDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao
    abstract fun drivingEventDao(): DrivingEventDao
    abstract fun dataPointDao(): DataPointDao
    abstract fun vehicleDao(): VehicleDao
    abstract fun fuelCalibrationDao(): FuelCalibrationDao
    abstract fun aiInsightDao(): AiInsightDao
    abstract fun challengeDao(): ChallengeDao
    abstract fun anomalyDao(): AnomalyDao
}
