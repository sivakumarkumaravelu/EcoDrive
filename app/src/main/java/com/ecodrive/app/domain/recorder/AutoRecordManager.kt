package com.ecodrive.app.domain.recorder

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.service.ActivityRecognitionReceiver
import com.ecodrive.app.util.PermissionManager
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityRecognitionClient
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AutoRecordManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferenceManager: PreferenceManager,
    private val permissionManager: PermissionManager,
    private val defaultDispatcher: CoroutineDispatcher = Dispatchers.Default,
) {
    companion object {
        private const val TAG = "AutoRecordManager"
        private const val DETECTION_INTERVAL_MS = 30_000L // 30 seconds
    }

    private val client: ActivityRecognitionClient = ActivityRecognition.getClient(context)
    private val scope = CoroutineScope(defaultDispatcher + SupervisorJob())

    private val pendingIntent: PendingIntent by lazy {
        val intent = Intent(context, ActivityRecognitionReceiver::class.java)
        PendingIntent.getBroadcast(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    init {
        observeAutoRecordPreference()
    }

    private fun observeAutoRecordPreference() {
        scope.launch {
            preferenceManager.autoRecordEnabled.collectLatest { enabled ->
                if (enabled) {
                    startActivityRecognition()
                } else {
                    stopActivityRecognition()
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startActivityRecognition() {
        if (!permissionManager.hasActivityRecognitionPermission()) {
            Log.w(TAG, "Cannot start activity recognition: permission missing")
            return
        }

        client.requestActivityUpdates(DETECTION_INTERVAL_MS, pendingIntent)
            .addOnSuccessListener {
                Log.i(TAG, "Activity recognition started")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to start activity recognition", e)
            }
    }

    private fun stopActivityRecognition() {
        client.removeActivityUpdates(pendingIntent)
            .addOnSuccessListener {
                Log.i(TAG, "Activity recognition stopped")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to stop activity recognition", e)
            }
    }
}
