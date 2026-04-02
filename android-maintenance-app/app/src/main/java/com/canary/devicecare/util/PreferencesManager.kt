package com.canary.devicecare.util

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "devicecare_prefs")

class PreferencesManager(private val context: Context) {

    companion object {
        private const val MIRROR_PREFIX = "mirror_"
        val LAST_WATCH_ADDRESS = stringPreferencesKey("last_watch_address")
        val LAST_WATCH_NAME = stringPreferencesKey("last_watch_name")

        // Default recommended apps for notification mirroring
        val RECOMMENDED_PACKAGES = listOf(
            "com.Slack",
            "com.instagram.android",
            "com.ss.android.ugc.trill", // TikTok
            "com.android.chrome",
            "com.google.android.gm", // Gmail
            "com.whatsapp",
            "org.telegram.messenger"
        )
    }

    fun isMirrorEnabled(packageName: String): Flow<Boolean> {
        val key = booleanPreferencesKey("$MIRROR_PREFIX$packageName")
        return context.dataStore.data.map { prefs ->
            prefs[key] ?: (packageName in RECOMMENDED_PACKAGES)
        }
    }

    suspend fun setMirrorEnabled(packageName: String, enabled: Boolean) {
        val key = booleanPreferencesKey("$MIRROR_PREFIX$packageName")
        context.dataStore.edit { prefs ->
            prefs[key] = enabled
        }
    }

    fun getLastWatchAddress(): Flow<String?> {
        return context.dataStore.data.map { it[LAST_WATCH_ADDRESS] }
    }

    fun getLastWatchName(): Flow<String?> {
        return context.dataStore.data.map { it[LAST_WATCH_NAME] }
    }

    suspend fun saveWatchInfo(address: String, name: String) {
        context.dataStore.edit { prefs ->
            prefs[LAST_WATCH_ADDRESS] = address
            prefs[LAST_WATCH_NAME] = name
        }
    }

    suspend fun clearWatchInfo() {
        context.dataStore.edit { prefs ->
            prefs.remove(LAST_WATCH_ADDRESS)
            prefs.remove(LAST_WATCH_NAME)
        }
    }

    suspend fun getMirrorEnabledPackages(): Set<String> {
        var result = setOf<String>()
        context.dataStore.edit { prefs ->
            result = prefs.asMap()
                .filter { (key, value) ->
                    key.name.startsWith(MIRROR_PREFIX) && value == true
                }
                .keys
                .map { it.name.removePrefix(MIRROR_PREFIX) }
                .toSet()
        }
        return result
    }
}
