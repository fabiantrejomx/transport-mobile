package com.bng.drivo.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {

    val isDarkTheme: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[DARK_THEME_KEY] ?: false
    }

    val isLocationTrackingEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[LOCATION_TRACKING_KEY] ?: true
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[DARK_THEME_KEY] = enabled }
    }

    suspend fun setLocationTrackingEnabled(enabled: Boolean) {
        context.dataStore.edit { it[LOCATION_TRACKING_KEY] = enabled }
    }

    private companion object {
        val DARK_THEME_KEY = booleanPreferencesKey("dark_theme")
        val LOCATION_TRACKING_KEY = booleanPreferencesKey("location_tracking_enabled")
    }
}
