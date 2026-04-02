package com.canary.devicecare.util

import java.util.Locale

object FormatUtils {

    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
            .coerceIn(0, units.size - 1)
        return String.format(
            Locale("pt", "BR"),
            "%.1f %s",
            bytes / Math.pow(1024.0, digitGroups.toDouble()),
            units[digitGroups]
        )
    }

    fun formatPercent(value: Float): String {
        return String.format(Locale("pt", "BR"), "%.1f%%", value * 100)
    }
}
