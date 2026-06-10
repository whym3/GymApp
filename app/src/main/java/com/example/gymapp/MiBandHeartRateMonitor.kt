package com.example.gymapp

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Streams live heart rate from any BLE heart-rate broadcaster — e.g. a Mi Band
 * 10 with "Broadcast Heart Rate" turned on. In that mode the band behaves like a
 * chest strap: it advertises the standard Bluetooth Heart Rate Service
 * (`0x180D`) and pushes a measurement notification (`0x2A37`) roughly once a
 * second. No Xiaomi account or proprietary protocol is involved.
 *
 * Flow: scan for the first device advertising the HR service → connect GATT →
 * discover services → enable notifications on the measurement characteristic →
 * decode BPM per the BLE spec. Exposes the current reading via [heartRate] and a
 * coarse [status] for any connection UI.
 *
 * Requires the runtime permissions BLUETOOTH_SCAN and BLUETOOTH_CONNECT
 * (API 31+); calls are no-ops with [Status.NO_PERMISSION] if they're missing.
 */
class MiBandHeartRateMonitor(private val context: Context) {

    enum class Status { IDLE, SCANNING, CONNECTING, CONNECTED, NO_PERMISSION, UNAVAILABLE }

    private val _status = MutableStateFlow(Status.IDLE)
    val status: StateFlow<Status> = _status.asStateFlow()

    private val _heartRate = MutableStateFlow<Int?>(null)
    val heartRate: StateFlow<Int?> = _heartRate.asStateFlow()

    private var gatt: BluetoothGatt? = null
    private var scanning = false
    private val samples = mutableListOf<Int>()

    private val adapter: BluetoothAdapter?
        get() = (context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    private fun hasPermissions(): Boolean =
        listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            .all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }

    /** Begin scanning for, and connecting to, a broadcasting HR device. Idempotent. */
    @SuppressLint("MissingPermission")
    fun start() {
        if (scanning || gatt != null) return
        if (!hasPermissions()) { _status.value = Status.NO_PERMISSION; return }
        val scanner = adapter?.takeIf { it.isEnabled }?.bluetoothLeScanner
        if (scanner == null) { _status.value = Status.UNAVAILABLE; return }

        samples.clear()
        _heartRate.value = null

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(HR_SERVICE)).build()
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        scanning = true
        _status.value = Status.SCANNING
        runCatching { scanner.startScan(listOf(filter), settings, scanCallback) }
            .onFailure { Log.e(TAG, "startScan failed", it); scanning = false; _status.value = Status.UNAVAILABLE }
    }

    /** Disconnect and release the GATT/scan resources. Idempotent. */
    @SuppressLint("MissingPermission")
    fun stop() {
        stopScan()
        gatt?.let { g ->
            runCatching { g.disconnect() }
            runCatching { g.close() }
        }
        gatt = null
        _status.value = Status.IDLE
    }

    /** Average BPM observed since the last [start], or null if nothing was read. */
    fun sessionAverage(): Int? = if (samples.isEmpty()) null else samples.average().toInt()

    @SuppressLint("MissingPermission")
    private fun stopScan() {
        if (!scanning) return
        scanning = false
        runCatching { adapter?.bluetoothLeScanner?.stopScan(scanCallback) }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            stopScan()
            connect(device)
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "scan failed: $errorCode")
            scanning = false
            _status.value = Status.UNAVAILABLE
        }
    }

    @SuppressLint("MissingPermission")
    private fun connect(device: BluetoothDevice) {
        if (!hasPermissions()) { _status.value = Status.NO_PERMISSION; return }
        _status.value = Status.CONNECTING
        gatt = runCatching {
            device.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        }.getOrNull()
        if (gatt == null) _status.value = Status.UNAVAILABLE
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> runCatching { g.discoverServices() }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    _heartRate.value = null
                    // If the workout is still going we'd like to reconnect; the
                    // simplest robust behaviour is to fall back to scanning again.
                    runCatching { g.close() }
                    if (gatt === g) {
                        gatt = null
                        _status.value = Status.IDLE
                    }
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val ch = g.getService(HR_SERVICE)?.getCharacteristic(HR_MEASUREMENT)
            if (ch == null) { _status.value = Status.UNAVAILABLE; return }
            g.setCharacteristicNotification(ch, true)
            val cccd = ch.getDescriptor(CCCD) ?: return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
            _status.value = Status.CONNECTED
        }

        // API 33+ delivers the value directly.
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            ch: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            parseMeasurement(value)
        }

        // Pre-33 path — value lives on the characteristic.
        @Deprecated("Used on API < 33")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(g: BluetoothGatt, ch: BluetoothGattCharacteristic) {
            ch.value?.let { parseMeasurement(it) }
        }
    }

    /**
     * Decode a Heart Rate Measurement packet. Bit 0 of the flags byte selects the
     * value format: 0 → uint8 at offset 1, 1 → uint16 (little-endian) at offset 1.
     */
    private fun parseMeasurement(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val flags = bytes[0].toInt()
        val bpm = if (flags and 0x01 == 0) {
            if (bytes.size < 2) return
            bytes[1].toInt() and 0xFF
        } else {
            if (bytes.size < 3) return
            (bytes[1].toInt() and 0xFF) or ((bytes[2].toInt() and 0xFF) shl 8)
        }
        if (bpm in 1..250) {
            _heartRate.value = bpm
            samples.add(bpm)
        }
    }

    companion object {
        private const val TAG = "MiBandHr"
        private val HR_SERVICE: UUID = UUID.fromString("0000180d-0000-1000-8000-00805f9b34fb")
        private val HR_MEASUREMENT: UUID = UUID.fromString("00002a37-0000-1000-8000-00805f9b34fb")
        private val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
