package com.inventorysmartai.app.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inventorysmartai.app.core.common.Constants
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class SettingsLocalDataSource @Inject constructor(
    private val dataStore: DataStore<Preferences>
) {
    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val USE_ARABIC_INDIC_DIGITS = booleanPreferencesKey("use_arabic_indic_digits")
        val NEAR_EXPIRY_WINDOW_DAYS = intPreferencesKey("near_expiry_window_days")
        val DEFAULT_LOW_STOCK_THRESHOLD = doublePreferencesKey("default_low_stock_threshold")
        val DEMO_DATA_LOADED = booleanPreferencesKey("demo_data_loaded")
    }

    val themeMode: Flow<String> = dataStore.data.map { it[Keys.THEME_MODE] ?: "SYSTEM" }
    suspend fun setThemeMode(mode: String) {
        dataStore.edit { it[Keys.THEME_MODE] = mode }
    }

    val useArabicIndicDigits: Flow<Boolean> = dataStore.data.map { it[Keys.USE_ARABIC_INDIC_DIGITS] ?: false }
    suspend fun setUseArabicIndicDigits(value: Boolean) {
        dataStore.edit { it[Keys.USE_ARABIC_INDIC_DIGITS] = value }
    }

    val nearExpiryWindowDays: Flow<Int> =
        dataStore.data.map { it[Keys.NEAR_EXPIRY_WINDOW_DAYS] ?: Constants.DEFAULT_NEAR_EXPIRY_WINDOW_DAYS }
    suspend fun setNearExpiryWindowDays(days: Int) {
        dataStore.edit { it[Keys.NEAR_EXPIRY_WINDOW_DAYS] = days }
    }

    val defaultLowStockThreshold: Flow<Double> =
        dataStore.data.map { it[Keys.DEFAULT_LOW_STOCK_THRESHOLD] ?: Constants.DEFAULT_LOW_STOCK_THRESHOLD }
    suspend fun setDefaultLowStockThreshold(value: Double) {
        dataStore.edit { it[Keys.DEFAULT_LOW_STOCK_THRESHOLD] = value }
    }

    val isDemoDataLoaded: Flow<Boolean> = dataStore.data.map { it[Keys.DEMO_DATA_LOADED] ?: false }
    suspend fun setDemoDataLoaded(value: Boolean) {
        dataStore.edit { it[Keys.DEMO_DATA_LOADED] = value }
    }
}
