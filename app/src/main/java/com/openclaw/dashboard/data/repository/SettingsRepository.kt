package com.openclaw.dashboard.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "openclaw_settings")

/**
 * Theme mode options
 */
enum class ThemeMode {
    SYSTEM,  // Follow system setting
    LIGHT,   // Always light
    DARK     // Always dark
}

/**
 * Repository for storing connection settings
 */
class SettingsRepository(private val context: Context) {
    
    companion object {
        private val KEY_DASHBOARD_URL = stringPreferencesKey("dashboard_url")
        private val KEY_HF_TOKEN = stringPreferencesKey("hf_token")
        private val KEY_GATEWAY_TOKEN = stringPreferencesKey("gateway_token")
        private val KEY_GATEWAY_PASSWORD = stringPreferencesKey("gateway_password")
        private val KEY_IS_CONFIGURED = booleanPreferencesKey("is_configured")
        private val KEY_USE_DYNAMIC_COLOR = booleanPreferencesKey("use_dynamic_color")
        private val KEY_THEME_MODE = stringPreferencesKey("theme_mode")
    }
    
    /**
     * Connection settings data class
     */
    data class ConnectionSettings(
        val dashboardUrl: String = "",
        val hfToken: String = "",
        val gatewayToken: String = "",
        val gatewayPassword: String = "",
        val isConfigured: Boolean = false
    )
    
    /**
     * Get connection settings as Flow
     */
    val connectionSettings: Flow<ConnectionSettings> = context.dataStore.data.map { preferences ->
        ConnectionSettings(
            dashboardUrl = preferences[KEY_DASHBOARD_URL] ?: "",
            hfToken = preferences[KEY_HF_TOKEN] ?: "",
            gatewayToken = preferences[KEY_GATEWAY_TOKEN] ?: "",
            gatewayPassword = preferences[KEY_GATEWAY_PASSWORD] ?: "",
            isConfigured = preferences[KEY_IS_CONFIGURED] ?: false
        )
    }
    
    /**
     * Check if app is configured
     */
    val isConfigured: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_CONFIGURED] ?: false
    }
    
    /**
     * Get dynamic color preference
     */
    val useDynamicColor: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_USE_DYNAMIC_COLOR] ?: true
    }
    
    /**
     * Get theme mode preference
     */
    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { preferences ->
        val modeString = preferences[KEY_THEME_MODE] ?: ThemeMode.SYSTEM.name
        try {
            ThemeMode.valueOf(modeString)
        } catch (e: IllegalArgumentException) {
            ThemeMode.SYSTEM
        }
    }
    
    /**
     * Save connection settings
     */
    suspend fun saveConnectionSettings(
        dashboardUrl: String,
        hfToken: String,
        gatewayToken: String,
        gatewayPassword: String
    ) {
        context.dataStore.edit { preferences ->
            preferences[KEY_DASHBOARD_URL] = dashboardUrl
            preferences[KEY_HF_TOKEN] = hfToken
            preferences[KEY_GATEWAY_TOKEN] = gatewayToken
            preferences[KEY_GATEWAY_PASSWORD] = gatewayPassword
            preferences[KEY_IS_CONFIGURED] = true
        }
    }
    
    /**
     * Update dynamic color preference
     */
    suspend fun setUseDynamicColor(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_USE_DYNAMIC_COLOR] = enabled
        }
    }
    
    /**
     * Update theme mode preference
     */
    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { preferences ->
            preferences[KEY_THEME_MODE] = mode.name
        }
    }
    
    /**
     * Clear all settings
     */
    suspend fun clearSettings() {
        context.dataStore.edit { preferences ->
            preferences.clear()
        }
    }
}

