package com.canary.devicecare.viewmodel

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.canary.devicecare.model.BatteryInfo
import com.canary.devicecare.model.CpuInfo
import com.canary.devicecare.model.RamInfo
import com.canary.devicecare.model.StorageInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.RandomAccessFile

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val _ramInfo = MutableStateFlow(RamInfo())
    val ramInfo: StateFlow<RamInfo> = _ramInfo.asStateFlow()

    private val _batteryInfo = MutableStateFlow(BatteryInfo())
    val batteryInfo: StateFlow<BatteryInfo> = _batteryInfo.asStateFlow()

    private val _cpuInfo = MutableStateFlow(CpuInfo())
    val cpuInfo: StateFlow<CpuInfo> = _cpuInfo.asStateFlow()

    private val _storageInfo = MutableStateFlow(StorageInfo())
    val storageInfo: StateFlow<StorageInfo> = _storageInfo.asStateFlow()

    private val _isCleaningRam = MutableStateFlow(false)
    val isCleaningRam: StateFlow<Boolean> = _isCleaningRam.asStateFlow()

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshRamInfo()
                refreshBatteryInfo()
                refreshCpuInfo()
                refreshStorageInfo()
                delay(3000)
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

    private fun refreshBatteryInfo() {
        val context = getApplication<Application>()
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))

        batteryIntent?.let { intent ->
            val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val percent = if (scale > 0) (level * 100) / scale else 0

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) / 10f

            val healthInt = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1)
            val health = when (healthInt) {
                BatteryManager.BATTERY_HEALTH_GOOD -> "Boa"
                BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Superaquecimento"
                BatteryManager.BATTERY_HEALTH_DEAD -> "Morta"
                BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Sobretensão"
                BatteryManager.BATTERY_HEALTH_COLD -> "Fria"
                else -> "Desconhecida"
            }

            _batteryInfo.value = BatteryInfo(
                level = percent,
                isCharging = isCharging,
                temperature = temp,
                health = health
            )
        }
    }

    private fun refreshCpuInfo() {
        try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val firstLine = reader.readLine()
            reader.close()

            val parts = firstLine.split("\\s+".toRegex())
            if (parts.size >= 5) {
                val user = parts[1].toLongOrNull() ?: 0
                val nice = parts[2].toLongOrNull() ?: 0
                val system = parts[3].toLongOrNull() ?: 0
                val idle = parts[4].toLongOrNull() ?: 0
                val iowait = parts.getOrNull(5)?.toLongOrNull() ?: 0
                val irq = parts.getOrNull(6)?.toLongOrNull() ?: 0
                val softirq = parts.getOrNull(7)?.toLongOrNull() ?: 0

                val total = user + nice + system + idle + iowait + irq + softirq
                val busy = total - idle - iowait

                val usage = if (total > 0) (busy.toFloat() / total) * 100f else 0f

                _cpuInfo.value = CpuInfo(
                    usagePercent = usage.coerceIn(0f, 100f),
                    coreCount = Runtime.getRuntime().availableProcessors()
                )
            }
        } catch (e: Exception) {
            // On newer Android versions, /proc/stat may not be readable
            _cpuInfo.value = CpuInfo(usagePercent = 0f)
        }
    }

    private fun refreshStorageInfo() {
        try {
            val stat = StatFs(Environment.getDataDirectory().path)
            val totalBytes = stat.totalBytes
            val freeBytes = stat.availableBytes
            val usedBytes = totalBytes - freeBytes

            // Estimate breakdown (actual values would require StorageStatsManager with permissions)
            val cacheBytes = (usedBytes * 0.08).toLong()
            val systemBytes = (usedBytes * 0.25).toLong()
            val mediaBytes = (usedBytes * 0.35).toLong()
            val appBytes = usedBytes - cacheBytes - systemBytes - mediaBytes

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
            // Ignore
        }
    }

    fun cleanRam() {
        viewModelScope.launch(Dispatchers.IO) {
            _isCleaningRam.value = true
            val am = getApplication<Application>().getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager

            @Suppress("DEPRECATION")
            val runningApps = am.getRunningAppProcesses() ?: emptyList()

            for (process in runningApps) {
                if (process.importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                    try {
                        am.killBackgroundProcesses(process.pkgList?.firstOrNull() ?: continue)
                    } catch (e: Exception) {
                        // Some processes can't be killed
                    }
                }
            }

            delay(500)
            refreshRamInfo()
            _isCleaningRam.value = false
        }
    }
}
