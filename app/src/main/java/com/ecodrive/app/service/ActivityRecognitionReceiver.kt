package com.ecodrive.app.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ecodrive.app.domain.recorder.TripRecorder
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.DetectedActivity
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
open class ActivityRecognitionReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ActivityRecogReceiver"
        private const val CONFIDENCE_THRESHOLD = 75
    }

    @Inject
    lateinit var tripRecorder: TripRecorder

    override fun onReceive(context: Context, intent: Intent) {
        if (!ActivityRecognitionResult.hasResult(intent)) return

        val result = ActivityRecognitionResult.extractResult(intent) ?: return
        val mostProbableActivity = result.mostProbableActivity
        
        Log.i(TAG, "Activity detected: ${mostProbableActivity.typeToString()} (${mostProbableActivity.confidence}%)")

        when (mostProbableActivity.type) {
            DetectedActivity.IN_VEHICLE -> {
                if (mostProbableActivity.confidence >= CONFIDENCE_THRESHOLD) {
                    tripRecorder.startRecording()
                }
            }
            DetectedActivity.WALKING, DetectedActivity.STILL, DetectedActivity.ON_FOOT -> {
                if (mostProbableActivity.confidence >= CONFIDENCE_THRESHOLD && tripRecorder.isRecording.value) {
                    if (mostProbableActivity.type == DetectedActivity.WALKING) {
                        tripRecorder.stopRecording()
                    }
                }
            }
        }
    }

    private fun DetectedActivity.typeToString(): String {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.RUNNING -> "RUNNING"
            else -> "INVALID"
        }
    }
}
