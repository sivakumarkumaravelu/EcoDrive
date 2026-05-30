package com.ecodrive.app

import android.app.Application
import com.ecodrive.app.domain.recorder.AutoRecordManager
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * EcoDrive Application class.
 * Annotated with @HiltAndroidApp to trigger Hilt's code generation
 * and serve as the application-level dependency container.
 */
@HiltAndroidApp
class EcoDriveApplication : Application() {
    
    @Inject
    lateinit var autoRecordManager: AutoRecordManager
    
    override fun onCreate() {
        super.onCreate()
        // AutoRecordManager is initialized here via injection
    }
}
