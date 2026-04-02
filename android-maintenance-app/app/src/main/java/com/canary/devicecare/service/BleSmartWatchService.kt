package com.canary.devicecare.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.canary.devicecare.R
import com.canary.devicecare.model.MirroredNotification
import com.canary.devicecare.model.SmartWatchState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class BleSmartWatchService : Service() {

    companion object {
        private const val TAG = "BleSmartWatch"
        private const val CHANNEL_ID = "ble_service_channel"
        private const val NOTIFICATION_ID = 1001

        // Generic GATT notification service UUID - used for sending notification data
        // In a real production app, this would match the smartwatch's actual service UUID
        val NOTIFICATION_SERVICE_UUID: UUID = UUID.fromString("00001811-0000-1000-8000-00805f9b34fb")
        val NOTIFICATION_CHAR_UUID: UUID = UUID.fromString("00002a46-0000-1000-8000-00805f9b34fb")

        // Alternative custom service for watches that support it
        val CUSTOM_SERVICE_UUID: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
        val CUSTOM_TX_CHAR_UUID: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

        private const val SCAN_TIMEOUT_MS = 15000L
    }

    private val binder = LocalBinder()

    private var bluetoothManager: BluetoothManager? = null
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bleScanner: BluetoothLeScanner? = null
    private var bluetoothGatt: BluetoothGatt? = null
    private var notificationCharacteristic: BluetoothGattCharacteristic? = null

    private val _watchState = MutableStateFlow(SmartWatchState())
    val watchState: StateFlow<SmartWatchState> = _watchState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val scanHandler = android.os.Handler(android.os.Looper.getMainLooper())

    inner class LocalBinder : Binder() {
        fun getService(): BleSmartWatchService = this@BleSmartWatchService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
        bluetoothAdapter = bluetoothManager?.adapter
        bleScanner = bluetoothAdapter?.bluetoothLeScanner
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildForegroundNotification())
        return START_STICKY
    }

    override fun onDestroy() {
        stopScan()
        disconnectWatch()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Serviço Smartwatch BLE",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Mantém a conexão com o smartwatch"
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildForegroundNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("DeviceCare")
            .setContentText("Conectado ao smartwatch")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
            .build()
    }

    private fun hasBluetoothPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
        } else {
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun startScan() {
        if (!hasBluetoothPermission()) {
            Log.w(TAG, "Missing Bluetooth permissions")
            return
        }
        if (bleScanner == null) {
            Log.w(TAG, "BLE scanner not available")
            return
        }

        _discoveredDevices.value = emptyList()
        _watchState.value = _watchState.value.copy(isScanning = true)

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()

        try {
            bleScanner?.startScan(null, settings, scanCallback)
            scanHandler.postDelayed({ stopScan() }, SCAN_TIMEOUT_MS)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception during scan", e)
            _watchState.value = _watchState.value.copy(isScanning = false)
        }
    }

    fun stopScan() {
        if (!hasBluetoothPermission()) return
        try {
            bleScanner?.stopScan(scanCallback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception stopping scan", e)
        }
        scanHandler.removeCallbacksAndMessages(null)
        _watchState.value = _watchState.value.copy(isScanning = false)
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            if (!hasBluetoothPermission()) return
            try {
                val name = device.name
                if (name != null) {
                    val currentList = _discoveredDevices.value.toMutableList()
                    if (currentList.none { it.address == device.address }) {
                        currentList.add(device)
                        _discoveredDevices.value = currentList
                    }
                }
            } catch (e: SecurityException) {
                Log.e(TAG, "Security exception reading device name", e)
            }
        }

        override fun onScanFailed(errorCode: Int) {
            Log.e(TAG, "BLE scan failed with error code: $errorCode")
            _watchState.value = _watchState.value.copy(isScanning = false)
        }
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (!hasBluetoothPermission()) return
        stopScan()

        try {
            bluetoothGatt?.close()
            bluetoothGatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception connecting", e)
        }
    }

    fun connectToAddress(address: String) {
        if (!hasBluetoothPermission()) return
        val device = bluetoothAdapter?.getRemoteDevice(address) ?: return
        connectToDevice(device)
    }

    fun disconnectWatch() {
        if (!hasBluetoothPermission()) return
        try {
            bluetoothGatt?.disconnect()
            bluetoothGatt?.close()
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception disconnecting", e)
        }
        bluetoothGatt = null
        notificationCharacteristic = null
        _watchState.value = SmartWatchState()
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (!hasBluetoothPermission()) return
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.i(TAG, "Connected to GATT server")
                    try {
                        val deviceName = gatt.device?.name ?: "Smartwatch"
                        val deviceAddress = gatt.device?.address ?: ""
                        _watchState.value = SmartWatchState(
                            isConnected = true,
                            deviceName = deviceName,
                            deviceAddress = deviceAddress
                        )
                        gatt.discoverServices()
                    } catch (e: SecurityException) {
                        Log.e(TAG, "Security exception on connect", e)
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.i(TAG, "Disconnected from GATT server")
                    _watchState.value = SmartWatchState(isConnected = false)
                    notificationCharacteristic = null
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i(TAG, "Services discovered")
                // Try to find notification characteristic
                findNotificationCharacteristic(gatt)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Notification data sent successfully")
            } else {
                Log.w(TAG, "Failed to send notification, status: $status")
            }
        }
    }

    private fun findNotificationCharacteristic(gatt: BluetoothGatt) {
        // Try the standard Alert Notification Service first
        var service: BluetoothGattService? = gatt.getService(NOTIFICATION_SERVICE_UUID)
        if (service != null) {
            notificationCharacteristic = service.getCharacteristic(NOTIFICATION_CHAR_UUID)
            if (notificationCharacteristic != null) {
                Log.i(TAG, "Found standard notification characteristic")
                return
            }
        }

        // Try custom UART service (common on many smartwatches/bands)
        service = gatt.getService(CUSTOM_SERVICE_UUID)
        if (service != null) {
            notificationCharacteristic = service.getCharacteristic(CUSTOM_TX_CHAR_UUID)
            if (notificationCharacteristic != null) {
                Log.i(TAG, "Found custom UART characteristic")
                return
            }
        }

        // Fallback: iterate all services to find a writable characteristic
        for (svc in gatt.services) {
            for (char in svc.characteristics) {
                val props = char.properties
                if (props and BluetoothGattCharacteristic.PROPERTY_WRITE != 0 ||
                    props and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0
                ) {
                    notificationCharacteristic = char
                    Log.i(TAG, "Found fallback writable characteristic: ${char.uuid}")
                    return
                }
            }
        }

        Log.w(TAG, "No suitable notification characteristic found")
    }

    /**
     * Send a notification to the connected smartwatch.
     * The payload format: [type(1)][appName(var)][0x00][title(var)][0x00][text(var)]
     */
    fun sendNotification(notification: MirroredNotification) {
        if (!hasBluetoothPermission()) return
        val gatt = bluetoothGatt ?: return
        val characteristic = notificationCharacteristic ?: return

        try {
            // Build a simple payload that most BLE notification services can understand
            val payload = buildNotificationPayload(notification)

            // Split into chunks if needed (BLE MTU is typically 20 bytes default, may be negotiated higher)
            val chunks = payload.toList().chunked(20)

            for (chunk in chunks) {
                val bytes = chunk.toByteArray()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeCharacteristic(
                        characteristic,
                        bytes,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    )
                } else {
                    @Suppress("DEPRECATION")
                    characteristic.value = bytes
                    @Suppress("DEPRECATION")
                    characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    @Suppress("DEPRECATION")
                    gatt.writeCharacteristic(characteristic)
                }
            }

            Log.d(TAG, "Sent notification: ${notification.appName} - ${notification.title}")
        } catch (e: SecurityException) {
            Log.e(TAG, "Security exception sending notification", e)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending notification", e)
        }
    }

    private fun buildNotificationPayload(notification: MirroredNotification): ByteArray {
        // Simple protocol: category byte + null-separated fields
        // Category: 0x01 = social, 0x02 = email, 0x03 = news, 0x00 = other
        val category: Byte = when {
            notification.packageName.contains("slack", ignoreCase = true) -> 0x01
            notification.packageName.contains("instagram", ignoreCase = true) -> 0x01
            notification.packageName.contains("whatsapp", ignoreCase = true) -> 0x01
            notification.packageName.contains("telegram", ignoreCase = true) -> 0x01
            notification.packageName.contains("tiktok", ignoreCase = true) ||
            notification.packageName.contains("trill", ignoreCase = true) -> 0x01
            notification.packageName.contains("gmail", ignoreCase = true) ||
            notification.packageName.contains("mail", ignoreCase = true) -> 0x02
            notification.packageName.contains("chrome", ignoreCase = true) -> 0x03
            else -> 0x00
        }

        val appNameBytes = notification.appName.toByteArray(Charsets.UTF_8)
        val titleBytes = notification.title.take(64).toByteArray(Charsets.UTF_8)
        val textBytes = notification.text.take(128).toByteArray(Charsets.UTF_8)
        val separator = byteArrayOf(0x00)

        return byteArrayOf(category) + appNameBytes + separator + titleBytes + separator + textBytes
    }

    fun isConnected(): Boolean = _watchState.value.isConnected
}
