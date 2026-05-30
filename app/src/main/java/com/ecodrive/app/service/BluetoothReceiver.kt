package com.ecodrive.app.service

import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ecodrive.app.data.local.PreferenceManager
import com.ecodrive.app.domain.recorder.TripRecorder
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
open class BluetoothReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "BluetoothReceiver"
    }

    @Inject
    lateinit var tripRecorder: TripRecorder

    @Inject
    lateinit var preferenceManager: PreferenceManager

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val device: BluetoothDevice? = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
        }
        
        Log.i(TAG, "Bluetooth action: $action")

        CoroutineScope(Dispatchers.IO).launch {
            val autoRecordEnabled = preferenceManager.autoRecordEnabled.first()
            if (!autoRecordEnabled) return@launch

            val targetAddress = preferenceManager.carBluetoothAddress.first()

            when (action) {
                BluetoothDevice.ACTION_ACL_CONNECTED -> {
                    if (targetAddress == null || device?.address == targetAddress) {
                        Log.i(TAG, "Car Bluetooth connected, starting recording")
                        tripRecorder.startRecording()
                    }
                }
                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {
                    if (targetAddress == null || device?.address == targetAddress) {
                        Log.i(TAG, "Car Bluetooth disconnected, stopping recording")
                        tripRecorder.stopRecording()
                    }
                }
            }
        }
    }
}
