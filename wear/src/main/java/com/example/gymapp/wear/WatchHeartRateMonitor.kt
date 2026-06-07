package com.example.gymapp.wear

import android.content.Context
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DataTypeAvailability
import androidx.health.services.client.data.DeltaDataType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Streams the wearer's live heart rate straight off the watch's on-wrist sensor
 * via Health Services — accurate mid-set, unlike polling Health Connect from the
 * phone, which only sees whatever's already synced there.
 *
 * Subscribing raises the sensor's sampling rate (and battery draw), so callers
 * should only [start] while a workout is actively running and [stop] otherwise.
 */
object WatchHeartRateMonitor {

    private val _bpm = MutableStateFlow<Int?>(null)
    val bpm: StateFlow<Int?> = _bpm.asStateFlow()

    private var registered = false

    private val callback = object : MeasureCallback {
        override fun onAvailabilityChanged(dataType: DeltaDataType<*, *>, availability: Availability) {
            if (availability is DataTypeAvailability && availability != DataTypeAvailability.AVAILABLE) {
                _bpm.value = null
            }
        }

        override fun onDataReceived(data: DataPointContainer) {
            data.getData(DataType.HEART_RATE_BPM).lastOrNull()?.let { _bpm.value = it.value.toInt() }
        }
    }

    fun start(context: Context) {
        if (registered) return
        registered = true
        HealthServices.getClient(context.applicationContext).measureClient
            .registerMeasureCallback(DataType.HEART_RATE_BPM, callback)
    }

    fun stop(context: Context) {
        if (!registered) return
        registered = false
        _bpm.value = null
        HealthServices.getClient(context.applicationContext).measureClient
            .unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, callback)
    }
}
