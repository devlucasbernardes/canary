package com.canary.devicecare.viewmodel

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.IBinder
import android.provider.Settings
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.canary.devicecare.model.MirroredNotification
import com.canary.devicecare.model.NotificationAppEntry
import com.canary.devicecare.model.SmartWatchState
import com.canary.devicecare.service.BleSmartWatchService
import com.canary.devicecare.service.DeviceCareNotificationListener
import com.canary.devicecare.util.PreferencesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class NotificationViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    private val _appList = MutableStateFlow<List<NotificationAppEntry>>(emptyList())
    val appList: StateFlow<List<NotificationAppEntry>> = _appList.asStateFlow()

    private val _watchState = MutableStateFlow(SmartWatchState())
    val watchState: StateFlow<SmartWatchState> = _watchState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<BluetoothDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<BluetoothDevice>> = _discoveredDevices.asStateFlow()

    private val _isNotificationAccessEnabled = MutableStateFlow(false)
    val isNotificationAccessEnabled: StateFlow<Boolean> = _isNotificationAccessEnabled.asStateFlow()

    val recentNotifications: StateFlow<List<MirroredNotification>> =
        DeviceCareNotificationListener.recentNotifications
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var bleService: BleSmartWatchService? = null
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BleSmartWatchService.LocalBinder
            bleService = binder?.getService()
            isServiceBound = true

            // Observe BLE service state
            bleService?.let { svc ->
                viewModelScope.launch {
                    svc.watchState.collect { state ->
                        _watchState.value = state

                        // Save watch info when connected
                        if (state.isConnected && state.deviceAddress != null && state.deviceName != null) {
                            preferencesManager.saveWatchInfo(state.deviceAddress, state.deviceName)
                        }
                    }
                }
                viewModelScope.launch {
                    svc.discoveredDevices.collect { devices ->
                        _discoveredDevices.value = devices
                    }
                }
            }

            // Try to reconnect to last known device
            viewModelScope.launch {
                val lastAddress = preferencesManager.getLastWatchAddress().first()
                if (lastAddress != null && !(_watchState.value.isConnected)) {
                    bleService?.connectToAddress(lastAddress)
                }
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isServiceBound = false
        }
    }

    init {
        bindBleService()
        checkNotificationAccess()
        loadAppList()
    }

    private fun bindBleService() {
        val context = getApplication<Application>()
        val intent = Intent(context, BleSmartWatchService::class.java)
        context.startForegroundService(intent)
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    fun checkNotificationAccess() {
        val context = getApplication<Application>()
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        ) ?: ""
        _isNotificationAccessEnabled.value =
            enabledListeners.contains(context.packageName)
    }

    fun loadAppList() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val pm = context.packageManager

            // Get all installed apps that can send notifications
            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)

            val entries = installedApps.mapNotNull { appInfo ->
                val packageName = appInfo.packageName

                // Skip system core packages
                if (packageName == "android" ||
                    packageName == "com.android.systemui" ||
                    packageName == context.packageName
                ) return@mapNotNull null

                // Check if app has a launcher intent (user-visible app)
                val launchIntent = pm.getLaunchIntentForPackage(packageName)
                if (launchIntent == null) return@mapNotNull null

                try {
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = try { pm.getApplicationIcon(appInfo) } catch (e: Exception) { null }
                    val mirrorEnabled = preferencesManager.isMirrorEnabled(packageName).first()

                    NotificationAppEntry(
                        packageName = packageName,
                        appName = appName,
                        icon = icon,
                        isMirrorEnabled = mirrorEnabled
                    )
                } catch (e: Exception) {
                    null
                }
            }.sortedWith(
                // Recommended apps first, then alphabetical
                compareByDescending<NotificationAppEntry> {
                    it.packageName in PreferencesManager.RECOMMENDED_PACKAGES
                }.thenBy { it.appName }
            )

            _appList.value = entries
        }
    }

    fun toggleMirror(packageName: String, enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setMirrorEnabled(packageName, enabled)

            // Update the list
            _appList.value = _appList.value.map {
                if (it.packageName == packageName) it.copy(isMirrorEnabled = enabled)
                else it
            }
        }
    }

    fun startScan() {
        bleService?.startScan()
    }

    fun stopScan() {
        bleService?.stopScan()
    }

    fun connectToDevice(device: BluetoothDevice) {
        bleService?.connectToDevice(device)
    }

    fun disconnectWatch() {
        viewModelScope.launch {
            preferencesManager.clearWatchInfo()
        }
        bleService?.disconnectWatch()
    }

    override fun onCleared() {
        super.onCleared()
        if (isServiceBound) {
            try {
                getApplication<Application>().unbindService(serviceConnection)
            } catch (e: Exception) {
                // Already unbound
            }
        }
    }
}
