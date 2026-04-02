package com.canary.devicecare.viewmodel

import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.canary.devicecare.model.CacheInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

class TempCleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val _cacheList = MutableStateFlow<List<CacheInfo>>(emptyList())
    val cacheList: StateFlow<List<CacheInfo>> = _cacheList.asStateFlow()

    private val _totalCacheSize = MutableStateFlow(0L)
    val totalCacheSize: StateFlow<Long> = _totalCacheSize.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning: StateFlow<Boolean> = _isCleaning.asStateFlow()

    private val _cleanedBytes = MutableStateFlow(0L)
    val cleanedBytes: StateFlow<Long> = _cleanedBytes.asStateFlow()

    init {
        scanCaches()
    }

    fun scanCaches() {
        viewModelScope.launch(Dispatchers.IO) {
            _isScanning.value = true
            _cleanedBytes.value = 0

            val context = getApplication<Application>()
            val pm = context.packageManager

            val installedApps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            val cacheInfoList = mutableListOf<CacheInfo>()

            for (appInfo in installedApps) {
                // Skip system apps without updates
                if (appInfo.flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                    appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                ) continue

                try {
                    val cacheSize = getAppCacheSize(context, appInfo.packageName)
                    if (cacheSize > 0) {
                        val appName = pm.getApplicationLabel(appInfo).toString()
                        val icon = try {
                            pm.getApplicationIcon(appInfo)
                        } catch (e: Exception) {
                            null
                        }

                        cacheInfoList.add(
                            CacheInfo(
                                packageName = appInfo.packageName,
                                appName = appName,
                                cacheSizeBytes = cacheSize,
                                icon = icon
                            )
                        )
                    }
                } catch (e: Exception) {
                    // Skip apps we can't query
                }
            }

            val sorted = cacheInfoList.sortedByDescending { it.cacheSizeBytes }
            _cacheList.value = sorted
            _totalCacheSize.value = sorted.sumOf { it.cacheSizeBytes }
            _isScanning.value = false
        }
    }

    private fun getAppCacheSize(context: Context, packageName: String): Long {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE) as StorageStatsManager
                val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                val uuid = storageManager.getUuidForPath(context.filesDir)
                val uid = context.packageManager.getApplicationInfo(packageName, 0).uid
                val stats = storageStatsManager.queryStatsForUid(uuid, uid)
                stats.cacheBytes
            } else {
                // Fallback: check the app's cache directory if accessible
                val cacheDir = File(context.cacheDir.parent?.replace(context.packageName, packageName) ?: return 0)
                if (cacheDir.exists()) calculateDirSize(cacheDir) else 0
            }
        } catch (e: Exception) {
            // StorageStatsManager requires PACKAGE_USAGE_STATS permission
            // Fallback: estimate based on common cache locations
            estimateCacheSize(context, packageName)
        }
    }

    private fun estimateCacheSize(context: Context, packageName: String): Long {
        var total = 0L
        try {
            val dataDir = "/data/data/$packageName/cache"
            val file = File(dataDir)
            if (file.exists() && file.canRead()) {
                total += calculateDirSize(file)
            }

            // Also check external cache
            val externalCacheDir = File(
                context.externalCacheDir?.parent?.replace(context.packageName, packageName) ?: return total
            )
            if (externalCacheDir.exists() && externalCacheDir.canRead()) {
                total += calculateDirSize(externalCacheDir)
            }
        } catch (e: Exception) {
            // Can't access, return 0
        }
        return total
    }

    private fun calculateDirSize(dir: File): Long {
        var size = 0L
        try {
            val files = dir.listFiles() ?: return 0
            for (file in files) {
                size += if (file.isDirectory) calculateDirSize(file) else file.length()
            }
        } catch (e: Exception) {
            // Permission denied
        }
        return size
    }

    fun clearAllCaches() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCleaning.value = true
            val beforeTotal = _totalCacheSize.value

            val context = getApplication<Application>()

            // Clear our own app's cache
            clearDirContents(context.cacheDir)
            context.externalCacheDir?.let { clearDirContents(it) }

            // For other apps, we need to use the system's trim cache mechanism
            // On non-rooted devices, we can only clear our own cache directly
            // But we can request the system to free up space
            try {
                val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val uuid = sm.getUuidForPath(context.filesDir)
                    // Request the system to allocate bytes (which triggers cache cleanup)
                    try {
                        sm.allocateBytes(uuid, 1024 * 1024 * 512) // Request 512MB
                    } catch (e: Exception) {
                        // Expected to fail, but triggers cache cleanup
                    }
                }
            } catch (e: Exception) {
                // Ignore
            }

            delay(1000)
            scanCaches()

            val afterTotal = _totalCacheSize.value
            _cleanedBytes.value = (beforeTotal - afterTotal).coerceAtLeast(0)
            _isCleaning.value = false
        }
    }

    fun clearAppCache(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _isCleaning.value = true

            val context = getApplication<Application>()

            // If it's our own package, clear directly
            if (packageName == context.packageName) {
                clearDirContents(context.cacheDir)
                context.externalCacheDir?.let { clearDirContents(it) }
            } else {
                // For other apps on non-rooted devices, we trigger the system's cache trimming
                // and redirect user to app settings for individual cache clear
                try {
                    val sm = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val uuid = sm.getUuidForPath(context.filesDir)
                        try {
                            sm.allocateBytes(uuid, 1024 * 1024 * 256)
                        } catch (e: Exception) {
                            // Expected
                        }
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            delay(500)
            scanCaches()
            _isCleaning.value = false
        }
    }

    private fun clearDirContents(dir: File) {
        try {
            val files = dir.listFiles() ?: return
            for (file in files) {
                if (file.isDirectory) {
                    clearDirContents(file)
                    file.delete()
                } else {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            // Permission denied
        }
    }
}
