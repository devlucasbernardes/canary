package com.canary.devicecare.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.canary.devicecare.model.RamInfo
import com.canary.devicecare.model.RunningAppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class RamCleanerViewModel(application: Application) : AndroidViewModel(application) {

    private val _ramInfo = MutableStateFlow(RamInfo())
    val ramInfo: StateFlow<RamInfo> = _ramInfo.asStateFlow()

    private val _runningApps = MutableStateFlow<List<RunningAppInfo>>(emptyList())
    val runningApps: StateFlow<List<RunningAppInfo>> = _runningApps.asStateFlow()

    private val _isCleaning = MutableStateFlow(false)
    val isCleaning: StateFlow<Boolean> = _isCleaning.asStateFlow()

    private val _cleanedMb = MutableStateFlow(0L)
    val cleanedMb: StateFlow<Long> = _cleanedMb.asStateFlow()

    init {
        startMonitoring()
        loadRunningApps()
    }

    private fun startMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshRamInfo()
                delay(2000)
            }
        }
    }

    private fun refreshRamInfo() {
        val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memInfo = ActivityManager.MemoryInfo()
        am.getMemoryInfo(memInfo)

        val totalMb = memInfo.totalMem / (1024 * 1024)
        val freeMb = memInfo.availMem / (1024 * 1024)
        val usedMb = totalMb - freeMb

        _ramInfo.value = RamInfo(totalMb = totalMb, usedMb = usedMb, freeMb = freeMb)
    }

    fun loadRunningApps() {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val pm = context.packageManager

            @Suppress("DEPRECATION")
            val processes = am.getRunningAppProcesses() ?: emptyList()

            val apps = processes.mapNotNull { process ->
                val pkgName = process.pkgList?.firstOrNull() ?: return@mapNotNull null

                // Skip system processes
                if (pkgName == "android" || pkgName == "com.android.systemui") return@mapNotNull null

                try {
                    val appInfo = pm.getApplicationInfo(pkgName, 0)
                    val appName = pm.getApplicationLabel(appInfo).toString()
                    val icon = pm.getApplicationIcon(appInfo)

                    // Estimate memory usage from process importance
                    val memoryMb = when {
                        process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND -> 150L
                        process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE -> 100L
                        process.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_SERVICE -> 60L
                        else -> 30L
                    }

                    // Try to get actual memory info via Debug.MemoryInfo
                    val actualMemory = try {
                        val pids = intArrayOf(process.pid)
                        val memInfos = am.getProcessMemoryInfo(pids)
                        if (memInfos.isNotEmpty()) {
                            memInfos[0].totalPss.toLong() / 1024 // Convert KB to MB
                        } else memoryMb
                    } catch (e: Exception) {
                        memoryMb
                    }

                    RunningAppInfo(
                        packageName = pkgName,
                        appName = appName,
                        memoryUsageMb = if (actualMemory > 0) actualMemory else memoryMb,
                        icon = icon
                    )
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
            }.sortedByDescending { it.memoryUsageMb }

            _runningApps.value = apps
        }
    }

    fun cleanRam() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCleaning.value = true
            val beforeFree = _ramInfo.value.freeMb

            val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            @Suppress("DEPRECATION")
            val processes = am.getRunningAppProcesses() ?: emptyList()

            for (process in processes) {
                if (process.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    process.pkgList?.forEach { pkg ->
                        try {
                            am.killBackgroundProcesses(pkg)
                        } catch (e: Exception) {
                            // Ignore
                        }
                    }
                }
            }

            delay(1000)
            refreshRamInfo()
            loadRunningApps()

            val afterFree = _ramInfo.value.freeMb
            _cleanedMb.value = (afterFree - beforeFree).coerceAtLeast(0)
            _isCleaning.value = false
        }
    }

    fun killApp(packageName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            try {
                am.killBackgroundProcesses(packageName)
            } catch (e: Exception) {
                // Ignore
            }
            delay(300)
            loadRunningApps()
            refreshRamInfo()
        }
    }
}
