package com.canary.devicecare.service

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.canary.devicecare.model.MirroredNotification
import com.canary.devicecare.util.PreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DeviceCareNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "NotifListener"

        private val _recentNotifications = MutableStateFlow<List<MirroredNotification>>(emptyList())
        val recentNotifications: StateFlow<List<MirroredNotification>> = _recentNotifications.asStateFlow()

        private val _isListenerActive = MutableStateFlow(false)
        val isListenerActive: StateFlow<Boolean> = _isListenerActive.asStateFlow()

        private const val MAX_RECENT = 50
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var preferencesManager: PreferencesManager
    private var bleService: BleSmartWatchService? = null
    private var isBleServiceBound = false

    private val bleServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as? BleSmartWatchService.LocalBinder
            bleService = binder?.getService()
            isBleServiceBound = true
            Log.d(TAG, "BLE service connected to notification listener")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            bleService = null
            isBleServiceBound = false
        }
    }

    override fun onCreate() {
        super.onCreate()
        preferencesManager = PreferencesManager(applicationContext)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isListenerActive.value = true
        Log.i(TAG, "Notification listener connected")

        // Bind to BLE service
        val intent = Intent(this, BleSmartWatchService::class.java)
        bindService(intent, bleServiceConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isListenerActive.value = false
        Log.i(TAG, "Notification listener disconnected")

        if (isBleServiceBound) {
            unbindService(bleServiceConnection)
            isBleServiceBound = false
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val packageName = sbn.packageName ?: return

        // Skip our own notifications and system UI
        if (packageName == "com.canary.devicecare" ||
            packageName == "android" ||
            packageName == "com.android.systemui"
        ) return

        serviceScope.launch {
            try {
                // Check if mirroring is enabled for this app
                val mirrorEnabled = preferencesManager.isMirrorEnabled(packageName).first()
                if (!mirrorEnabled) return@launch

                val notification = sbn.notification ?: return@launch
                val extras = notification.extras ?: return@launch

                val title = extras.getCharSequence("android.title")?.toString() ?: ""
                val text = extras.getCharSequence("android.text")?.toString() ?: ""

                if (title.isBlank() && text.isBlank()) return@launch

                val pm = applicationContext.packageManager
                val appName = try {
                    val appInfo = pm.getApplicationInfo(packageName, 0)
                    pm.getApplicationLabel(appInfo).toString()
                } catch (e: Exception) {
                    packageName.substringAfterLast('.')
                }

                val mirroredNotification = MirroredNotification(
                    id = sbn.id,
                    packageName = packageName,
                    appName = appName,
                    title = title,
                    text = text,
                    timestamp = sbn.postTime
                )

                // Add to recent list
                val current = _recentNotifications.value.toMutableList()
                current.add(0, mirroredNotification)
                if (current.size > MAX_RECENT) {
                    _recentNotifications.value = current.take(MAX_RECENT)
                } else {
                    _recentNotifications.value = current
                }

                // Forward to smartwatch via BLE
                bleService?.let { service ->
                    if (service.isConnected()) {
                        service.sendNotification(mirroredNotification)
                        Log.d(TAG, "Forwarded notification from $appName to watch")
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "Error processing notification from $packageName", e)
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        // Optional: could notify the watch to dismiss the notification
        Log.d(TAG, "Notification removed: ${sbn.packageName}")
    }

    override fun onDestroy() {
        serviceScope.cancel()
        if (isBleServiceBound) {
            unbindService(bleServiceConnection)
        }
        super.onDestroy()
    }
}
