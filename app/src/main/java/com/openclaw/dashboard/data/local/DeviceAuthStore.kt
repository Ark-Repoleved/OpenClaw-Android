package com.openclaw.dashboard.data.local

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.deviceAuthStore by preferencesDataStore(name = "openclaw_device_auth")

class DeviceAuthStore(private val context: Context) {
    
    suspend fun loadToken(deviceId: String, role: String): String? {
        val key = stringPreferencesKey(tokenKey(deviceId, role))
        return context.deviceAuthStore.data.map { it[key] }.first()?.trim()?.takeIf { it.isNotEmpty() }
    }

    suspend fun saveToken(deviceId: String, role: String, token: String) {
        val key = stringPreferencesKey(tokenKey(deviceId, role))
        context.deviceAuthStore.edit { it[key] = token.trim() }
    }

    suspend fun clearToken(deviceId: String, role: String) {
        val key = stringPreferencesKey(tokenKey(deviceId, role))
        context.deviceAuthStore.edit { it.remove(key) }
    }

    private fun tokenKey(deviceId: String, role: String): String {
        val normalizedDevice = deviceId.trim().lowercase()
        val normalizedRole = role.trim().lowercase()
        return "deviceToken_${normalizedDevice}_${normalizedRole}"
    }
}
