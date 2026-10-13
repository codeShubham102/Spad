package com.example.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "spad_user_preferences")

data class SpadUserPreferences(
    val themeMode: String = "dark",
    val activeProfileId: String = "builtin_xbox",
    val lastTvIp: String = "",
    val lastTvName: String = "",
    val tvToken: String = "",
    val hapticEnabled: Boolean = true,
    val hapticStrength: Float = 0.8f,
    val experimentalGamingHub: Boolean = false,
    val pcReceiverIp: String = "192.168.1.100",
    val pcReceiverPort: Int = 5150,
    val batterySaverMode: Boolean = true
)

class UserPreferencesRepository(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        val LAST_TV_IP = stringPreferencesKey("last_tv_ip")
        val LAST_TV_NAME = stringPreferencesKey("last_tv_name")
        val TV_TOKEN = stringPreferencesKey("tv_token")
        val HAPTIC_ENABLED = booleanPreferencesKey("haptic_enabled")
        val HAPTIC_STRENGTH = floatPreferencesKey("haptic_strength")
        val EXPERIMENTAL_GAMING_HUB = booleanPreferencesKey("experimental_gaming_hub")
        val PC_RECEIVER_IP = stringPreferencesKey("pc_receiver_ip")
        val PC_RECEIVER_PORT = intPreferencesKey("pc_receiver_port")
        val BATTERY_SAVER_MODE = booleanPreferencesKey("battery_saver_mode")
    }

    val preferencesFlow: Flow<SpadUserPreferences> = context.dataStore.data.map { prefs ->
        SpadUserPreferences(
            themeMode = prefs[Keys.THEME_MODE] ?: "dark",
            activeProfileId = prefs[Keys.ACTIVE_PROFILE_ID] ?: "builtin_xbox",
            lastTvIp = prefs[Keys.LAST_TV_IP] ?: "",
            lastTvName = prefs[Keys.LAST_TV_NAME] ?: "",
            tvToken = prefs[Keys.TV_TOKEN] ?: "",
            hapticEnabled = prefs[Keys.HAPTIC_ENABLED] ?: true,
            hapticStrength = prefs[Keys.HAPTIC_STRENGTH] ?: 0.8f,
            experimentalGamingHub = prefs[Keys.EXPERIMENTAL_GAMING_HUB] ?: false,
            pcReceiverIp = prefs[Keys.PC_RECEIVER_IP] ?: "192.168.1.100",
            pcReceiverPort = prefs[Keys.PC_RECEIVER_PORT] ?: 5150,
            batterySaverMode = prefs[Keys.BATTERY_SAVER_MODE] ?: true
        )
    }

    suspend fun setThemeMode(mode: String) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    suspend fun setActiveProfileId(id: String) {
        context.dataStore.edit { it[Keys.ACTIVE_PROFILE_ID] = id }
    }

    suspend fun saveTvConnection(ip: String, name: String, token: String) {
        context.dataStore.edit {
            it[Keys.LAST_TV_IP] = ip
            it[Keys.LAST_TV_NAME] = name
            it[Keys.TV_TOKEN] = token
        }
    }

    suspend fun setHapticEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.HAPTIC_ENABLED] = enabled }
    }

    suspend fun setHapticStrength(strength: Float) {
        context.dataStore.edit { it[Keys.HAPTIC_STRENGTH] = strength }
    }

    suspend fun setExperimentalGamingHub(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EXPERIMENTAL_GAMING_HUB] = enabled }
    }

    suspend fun setPcReceiver(ip: String, port: Int) {
        context.dataStore.edit {
            it[Keys.PC_RECEIVER_IP] = ip
            it[Keys.PC_RECEIVER_PORT] = port
        }
    }

    suspend fun setBatterySaverMode(enabled: Boolean) {
        context.dataStore.edit { it[Keys.BATTERY_SAVER_MODE] = enabled }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit {
            it[Keys.THEME_MODE] = "dark"
            it[Keys.ACTIVE_PROFILE_ID] = "builtin_xbox"
            it[Keys.HAPTIC_ENABLED] = true
            it[Keys.HAPTIC_STRENGTH] = 0.8f
            it[Keys.EXPERIMENTAL_GAMING_HUB] = false
            it[Keys.PC_RECEIVER_IP] = "192.168.1.100"
            it[Keys.PC_RECEIVER_PORT] = 5150
            it[Keys.BATTERY_SAVER_MODE] = true
        }
    }
}
