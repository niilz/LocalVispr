package dev.localvispr.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

// ==============================================================================
// Audio Route Monitor
// ==============================================================================
//
// Monitors connected audio devices and prioritizes external recording headsets:
// Bluetooth (SCO / BLE), USB, and wired headsets. Automatically sets communication
// device routing when an external headset is connected, and clears it when disconnected.

class AudioRouteMonitor(context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var activeDevice: AudioDeviceInfo? = null
    private var running = false

    private val deviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
                updateRoute()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
                updateRoute()
            }
        }

    @Synchronized
    fun start() {
        if (running) return
        val am = audioManager ?: return
        running = true
        am.registerAudioDeviceCallback(deviceCallback, null)
        updateRoute()
    }

    @Synchronized
    fun stop() {
        if (!running) return
        val am = audioManager ?: return
        am.unregisterAudioDeviceCallback(deviceCallback)
        clearRoute()
        running = false
    }

    @Synchronized
    private fun updateRoute() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }

        val availableDevices = am.availableCommunicationDevices
        val preferred = availableDevices.firstOrNull { isPreferredHeadset(it) }

        if (preferred != null) {
            if (activeDevice?.id != preferred.id) {
                val success = am.setCommunicationDevice(preferred)
                if (success) {
                    activeDevice = preferred
                }
            }
        } else {
            clearRoute()
        }
    }

    @Synchronized
    private fun clearRoute() {
        val am = audioManager ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (activeDevice != null) {
                am.clearCommunicationDevice()
                activeDevice = null
            }
        }
    }

    private fun isPreferredHeadset(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> true
            AudioDeviceInfo.TYPE_BLE_HEADSET -> true
            AudioDeviceInfo.TYPE_WIRED_HEADSET -> true
            AudioDeviceInfo.TYPE_USB_HEADSET -> true
            AudioDeviceInfo.TYPE_USB_DEVICE -> true
            else -> false
        }
    }
}
