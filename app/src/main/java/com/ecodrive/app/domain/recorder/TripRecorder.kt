package com.ecodrive.app.domain.recorder

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.util.Log
import com.ecodrive.app.domain.model.DrivingMetrics
import com.ecodrive.app.domain.model.EcoScore
import com.ecodrive.app.service.SensorForegroundService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRecorder @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    companion object {
        private const val TAG = "TripRecorder"
    }

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _currentMetrics = MutableStateFlow(DrivingMetrics())
    val currentMetrics: StateFlow<DrivingMetrics> = _currentMetrics.asStateFlow()

    private val _currentEcoScore = MutableStateFlow(EcoScore(overall = 0))
    val currentEcoScore: StateFlow<EcoScore> = _currentEcoScore.asStateFlow()

    private var boundService: SensorForegroundService? = null
    private var isBound = false
    private var observationJob: Job? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as SensorForegroundService.LocalBinder
            boundService = binder.getService()
            isBound = true
            observeService()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            boundService = null
            isBound = false
            observationJob?.cancel()
        }
    }

    init {
        // Attempt to bind initially if service is already running
        bindToService()
    }

    private fun bindToService() {
        if (isBound) return
        try {
            val intent = Intent(context, SensorForegroundService::class.java)
            context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bind to service: ${e.message}")
        }
    }

    fun unbindFromService() {
        if (isBound) {
            try {
                context.unbindService(serviceConnection)
                isBound = false
                boundService = null
                observationJob?.cancel()
            } catch (e: Exception) {
                Log.e(TAG, "Failed to unbind service: ${e.message}")
            }
        }
    }

    private fun observeService() {
        observationJob?.cancel()
        observationJob = scope.launch {
            boundService?.let { service ->
                launch {
                    service.isRecording.collect { _isRecording.value = it }
                }
                launch {
                    service.currentMetrics.collect { _currentMetrics.value = it }
                }
                launch {
                    service.currentEcoScore.collect { _currentEcoScore.value = it }
                }
            }
        }
    }

    fun startRecording() {
        Log.i(TAG, "Requesting start recording via intent")
        val intent = Intent(context, SensorForegroundService::class.java).apply {
            action = SensorForegroundService.ACTION_START_RECORDING
        }
        context.startForegroundService(intent)
        bindToService()
    }

    fun stopRecording() {
        Log.i(TAG, "Requesting stop recording via intent")
        val intent = Intent(context, SensorForegroundService::class.java).apply {
            action = SensorForegroundService.ACTION_STOP_RECORDING
        }
        context.startForegroundService(intent)
    }
}
