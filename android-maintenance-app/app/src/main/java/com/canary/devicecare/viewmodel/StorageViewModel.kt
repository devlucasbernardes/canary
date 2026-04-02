package com.canary.devicecare.viewmodel

import android.app.Application
import android.app.usage.StorageStatsManager
import android.content.Context
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.canary.devicecare.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class StorageViewModel(application: Application) : AndroidViewModel(application) {

    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    init {
        analyzeStorage()
    }

    fun analyzeStorage() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoading.value = true

            try {
                val context = getApplication<Application>()
                val stat = StatFs(Environment.getDataDirectory().path)
                val totalBytes = stat.totalBytes
                val freeBytes = stat.availableBytes
                val usedBytes = totalBytes - freeBytes

                // Try to get detailed breakdown using StorageStatsManager
                var appBytes = 0L
                var cacheBytes = 0L

                try {
                    val storageStatsManager = context.getSystemService(Context.STORAGE_STATS_SERVICE)
                            as StorageStatsManager
                    val storageManager = context.getSystemService(Context.STORAGE_SERVICE)
                            as StorageManager
                    val uuid = storageManager.getUuidForPath(context.filesDir)

                    val pm = context.packageManager
                    val apps = pm.getInstalledApplications(0)

                    for (appInfo in apps) {
                        try {
                            val stats = storageStatsManager.queryStatsForUid(uuid, appInfo.uid)
                            appBytes += stats.appBytes + stats.dataBytes
                            cacheBytes += stats.cacheBytes
                        } catch (e: Exception) {
                            // Skip apps we can't query
                        }
                    }
                } catch (e: Exception) {
                    // Fallback to estimates if we don't have PACKAGE_USAGE_STATS permission
                    appBytes = (usedBytes * 0.32).toLong()
                    cacheBytes = (usedBytes * 0.08).toLong()
                }

                // Calculate media by scanning media directories
                val mediaBytes = calculateMediaSize()

                // System = used - apps - cache - media
                val systemBytes = (usedBytes - appBytes - cacheBytes - mediaBytes).coerceAtLeast(0)

                _storageInfo.value = StorageInfo(
                    totalBytes = totalBytes,
                    usedBytes = usedBytes,
                    freeBytes = freeBytes,
                    appBytes = appBytes,
                    mediaBytes = mediaBytes,
                    cacheBytes = cacheBytes,
                    systemBytes = systemBytes
                )
            } catch (e: Exception) {
                // Fallback with basic info
                val stat = StatFs(Environment.getDataDirectory().path)
                val total = stat.totalBytes
                val free = stat.availableBytes
                _storageInfo.value = StorageInfo(
                    totalBytes = total,
                    usedBytes = total - free,
                    freeBytes = free
                )
            }

            _isLoading.value = false
        }
    }

    private fun calculateMediaSize(): Long {
        var totalMedia = 0L
        val mediaDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        )

        for (dir in mediaDirs) {
            if (dir.exists()) {
                totalMedia += calculateDirSize(dir)
            }
        }
        return totalMedia
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
}
