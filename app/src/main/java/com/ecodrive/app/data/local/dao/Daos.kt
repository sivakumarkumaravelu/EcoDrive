package com.ecodrive.app.data.local.dao

import androidx.room.*
import com.ecodrive.app.data.local.entity.*
import kotlinx.coroutines.flow.Flow

/**
 * Partial entity for fetching only route points.
 */
data class TripRoutePoint(
    val tripId: Long,
    val latitude: Double,
    val longitude: Double
)

/**
 * Data Access Object for Trip operations.
 */
@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: TripEntity): Long

    @Update
    suspend fun updateTrip(trip: TripEntity)

    @Delete
    suspend fun deleteTrip(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :tripId")
    suspend fun getTripById(tripId: Long): TripEntity?

    @Query("SELECT * FROM trips WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTrip(): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startTimeEpochMs DESC")
    fun getAllTrips(): Flow<List<TripEntity>>

    @Query("SELECT * FROM trips ORDER BY startTimeEpochMs DESC LIMIT :limit")
    fun getRecentTrips(limit: Int): Flow<List<TripEntity>>

    @Query("SELECT AVG(ecoScore) FROM trips WHERE startTimeEpochMs >= :sinceMs AND isActive = 0")
    suspend fun getAverageEcoScore(sinceMs: Long): Double?

    @Query("SELECT SUM(fuelConsumedLiters) FROM trips WHERE startTimeEpochMs >= :sinceMs AND isActive = 0")
    suspend fun getTotalFuelConsumed(sinceMs: Long): Double?

    @Query("SELECT SUM(distanceKm) FROM trips WHERE startTimeEpochMs >= :sinceMs AND isActive = 0")
    suspend fun getTotalDistance(sinceMs: Long): Double?

    @Query("SELECT COUNT(*) FROM trips WHERE startTimeEpochMs >= :sinceMs AND isActive = 0")
    suspend fun getTripCount(sinceMs: Long): Int
}

/**
 * Data Access Object for Driving Events.
 */
@Dao
interface DrivingEventDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvent(event: DrivingEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEvents(events: List<DrivingEventEntity>)

    @Query("SELECT * FROM driving_events WHERE tripId = :tripId ORDER BY timestampEpochMs ASC")
    fun getEventsForTrip(tripId: Long): Flow<List<DrivingEventEntity>>

    @Query("SELECT COUNT(*) FROM driving_events WHERE tripId = :tripId AND type = :type")
    suspend fun getEventCount(tripId: Long, type: String): Int

    @Query("DELETE FROM driving_events WHERE tripId = :tripId")
    suspend fun deleteEventsForTrip(tripId: Long)
}

/**
 * Data Access Object for raw sensor Data Points.
 */
@Dao
interface DataPointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataPoint(dataPoint: DataPointEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDataPoints(dataPoints: List<DataPointEntity>)

    @Query("SELECT * FROM data_points WHERE tripId = :tripId ORDER BY timestampEpochMs ASC")
    fun getDataPointsForTrip(tripId: Long): Flow<List<DataPointEntity>>

    @Query("SELECT * FROM data_points WHERE tripId = :tripId ORDER BY timestampEpochMs DESC LIMIT 1")
    suspend fun getLatestDataPoint(tripId: Long): DataPointEntity?

    @Query("SELECT tripId, latitude, longitude FROM data_points WHERE tripId IN (:tripIds) AND latitude != 0.0 AND longitude != 0.0 ORDER BY timestampEpochMs ASC")
    fun getRoutePointsForTrips(tripIds: List<Long>): Flow<List<TripRoutePoint>>

    @Query("SELECT COUNT(*) FROM data_points WHERE tripId = :tripId")
    suspend fun getDataPointCount(tripId: Long): Int

    @Query("DELETE FROM data_points WHERE tripId = :tripId")
    suspend fun deleteDataPointsForTrip(tripId: Long)

    @Query("SELECT AVG(speedKmh) FROM data_points WHERE tripId = :tripId")
    suspend fun getAverageSpeed(tripId: Long): Double?

    @Query("SELECT MAX(speedKmh) FROM data_points WHERE tripId = :tripId")
    suspend fun getMaxSpeed(tripId: Long): Double?
}

/**
 * Data Access Object for Vehicle profiles.
 */
@Dao
interface VehicleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVehicle(vehicle: VehicleEntity): Long

    @Update
    suspend fun updateVehicle(vehicle: VehicleEntity)

    @Query("SELECT * FROM vehicles WHERE id = :vehicleId")
    suspend fun getVehicleById(vehicleId: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles ORDER BY id ASC LIMIT 1")
    suspend fun getDefaultVehicle(): VehicleEntity?

    @Query("SELECT * FROM vehicles")
    fun getAllVehicles(): Flow<List<VehicleEntity>>
}

/**
 * Data Access Object for Fuel Calibration history.
 */
@Dao
interface FuelCalibrationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(calibration: FuelCalibrationEntity)

    @Query("SELECT * FROM fuel_calibration ORDER BY timestampEpochMs DESC LIMIT :limit")
    suspend fun getRecent(limit: Int): List<FuelCalibrationEntity>

    @Query("SELECT AVG(correctionRatio) FROM fuel_calibration ORDER BY timestampEpochMs DESC LIMIT :limit")
    suspend fun getAverageCorrectionRatio(limit: Int): Double?
}
