package com.ecodrive.app.di

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import androidx.room.Room
import com.ecodrive.app.data.local.EcoDriveDatabase
import com.ecodrive.app.data.local.dao.*
import com.ecodrive.app.data.obd.ObdConnection
import com.ecodrive.app.data.remote.SmartcarApiClient
import com.ecodrive.app.data.repository.VehicleRepository
import com.ecodrive.app.domain.analyzer.FuelEstimationEngine
import com.ecodrive.app.sensor.LocationTracker
import com.ecodrive.app.sensor.PhoneSensorManager
import com.ecodrive.app.sensor.SensorDataManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Singleton

/**
 * Hilt module providing application-level dependencies.
 * Updated for universal approach: primary = sensors + Smartcar API,
 * optional = OBD-II adapter.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    // ── Database ────────────────────────────────────────────────

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): EcoDriveDatabase {
        return Room.databaseBuilder(
            context,
            EcoDriveDatabase::class.java,
            "ecodrive_database"
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideTripDao(db: EcoDriveDatabase): TripDao = db.tripDao()

    @Provides
    fun provideDrivingEventDao(db: EcoDriveDatabase): DrivingEventDao = db.drivingEventDao()

    @Provides
    fun provideDataPointDao(db: EcoDriveDatabase): DataPointDao = db.dataPointDao()

    @Provides
    fun provideVehicleDao(db: EcoDriveDatabase): VehicleDao = db.vehicleDao()

    @Provides
    fun provideFuelCalibrationDao(db: EcoDriveDatabase): FuelCalibrationDao = db.fuelCalibrationDao()

    // ── Phone Sensors (Primary Data Source) ─────────────────────

    @Provides
    @Singleton
    fun providePhoneSensorManager(
        @ApplicationContext context: Context,
    ): PhoneSensorManager = PhoneSensorManager(context)

    @Provides
    @Singleton
    fun provideLocationTracker(
        @ApplicationContext context: Context,
    ): LocationTracker = LocationTracker(context)

    @Provides
    @Singleton
    fun provideFuelEstimationEngine(
        fuelCalibrationDao: FuelCalibrationDao,
    ): FuelEstimationEngine = FuelEstimationEngine(fuelCalibrationDao)

    @Provides
    @Singleton
    fun provideSensorDataManager(
        locationTracker: LocationTracker,
        phoneSensorManager: PhoneSensorManager,
        fuelEngine: FuelEstimationEngine,
        vehicleRepository: VehicleRepository,
    ): SensorDataManager = SensorDataManager(locationTracker, phoneSensorManager, fuelEngine, vehicleRepository)

    // ── Smartcar API (Supplementary) ─────────────────────────────

    @Provides
    @Singleton
    fun provideSmartcarApiClient(): SmartcarApiClient = SmartcarApiClient()

    // ── OBD-II (Optional Pro Feature) ───────────────────────────

    @Provides
    @Singleton
    fun provideBluetoothManager(@ApplicationContext context: Context): BluetoothManager {
        return context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    @Provides
    @Singleton
    fun provideBluetoothAdapter(bluetoothManager: BluetoothManager): BluetoothAdapter? {
        return bluetoothManager.adapter
    }

    @Provides
    @Singleton
    fun provideObdConnection(): ObdConnection = ObdConnection()
}
