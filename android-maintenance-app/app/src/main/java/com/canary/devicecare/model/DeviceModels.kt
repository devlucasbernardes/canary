package com.canary.devicecare.model

import android.graphics.drawable.Drawable

data class RamInfo(
    val totalMb: Long = 0,
    val usedMb: Long = 0,
    val freeMb: Long = 0
) {
    val usagePercent: Float
        get() = if (totalMb > 0) usedMb.toFloat() / totalMb else 0f
}

data class RunningAppInfo(
    val packageName: String,
    val appName: String,
    val memoryUsageMb: Long,
    val icon: Drawable? = null
)

data class CacheInfo(
    val packageName: String,
    val appName: String,
    val cacheSizeBytes: Long,
    val icon: Drawable? = null
)

data class StorageInfo(
    val totalBytes: Long = 0,
    val usedBytes: Long = 0,
    val freeBytes: Long = 0,
    val appBytes: Long = 0,
    val mediaBytes: Long = 0,
    val cacheBytes: Long = 0,
    val systemBytes: Long = 0
)

data class BatteryInfo(
    val level: Int = 0,
    val isCharging: Boolean = false,
    val temperature: Float = 0f,
    val health: String = "Desconhecido"
)

data class CpuInfo(
    val usagePercent: Float = 0f,
    val coreCount: Int = Runtime.getRuntime().availableProcessors()
)

data class NotificationAppEntry(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isMirrorEnabled: Boolean = false
)

data class SmartWatchState(
    val isConnected: Boolean = false,
    val deviceName: String? = null,
    val deviceAddress: String? = null,
    val batteryLevel: Int? = null,
    val isScanning: Boolean = false
)

data class MirroredNotification(
    val id: Int,
    val packageName: String,
    val appName: String,
    val title: String,
    val text: String,
    val timestamp: Long
)
