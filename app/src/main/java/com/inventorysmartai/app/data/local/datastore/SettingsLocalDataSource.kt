package com.inventorysmartai.app.data.local.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.inventorysmartai.app.core.common.Constants
import com.inventorysmartai.app.domain.model.ColorOverrides
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
        val ACCENT_HUE = floatPreferencesKey("accent_hue")
        val PURE_BLACK_DARK = booleanPreferencesKey("pure_black_dark")
        val CUSTOM_BACKGROUND = intPreferencesKey("custom_background_color")
        val CUSTOM_TEXT = intPreferencesKey("custom_text_color")
        val CUSTOM_CARD = intPreferencesKey("custom_card_color")
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

    /** درجة لون التمييز (0..360)، أو null للون الافتراضي. */
    val accentHue: Flow<Float?> = dataStore.data.map { it[Keys.ACCENT_HUE] }
    suspend fun setAccentHue(hue: Float?) {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ACCENT_HUE)
            if (hue != null) prefs[Keys.ACCENT_HUE] = hue
        }
    }

    val pureBlackDark: Flow<Boolean> = dataStore.data.map { it[Keys.PURE_BLACK_DARK] ?: false }
    suspend fun setPureBlackDark(value: Boolean) {
        dataStore.edit { it[Keys.PURE_BLACK_DARK] = value }
    }

    /** ألوان الخلفية والخط والبطاقات التي خصصها المستخدم (null لما لم يغيّره). */
    val colorOverrides: Flow<ColorOverrides> = dataStore.data.map {
        ColorOverrides(background = it[Keys.CUSTOM_BACKGROUND], text = it[Keys.CUSTOM_TEXT], card = it[Keys.CUSTOM_CARD])
    }

    suspend fun setBackgroundColor(argb: Int?) = setOptionalInt(Keys.CUSTOM_BACKGROUND, argb)
    suspend fun setTextColor(argb: Int?) = setOptionalInt(Keys.CUSTOM_TEXT, argb)
    suspend fun setCardColor(argb: Int?) = setOptionalInt(Keys.CUSTOM_CARD, argb)

    suspend fun resetColorOverrides() {
        dataStore.edit {
            it.remove(Keys.CUSTOM_BACKGROUND)
            it.remove(Keys.CUSTOM_TEXT)
            it.remove(Keys.CUSTOM_CARD)
        }
    }

    private suspend fun setOptionalInt(key: Preferences.Key<Int>, value: Int?) {
        dataStore.edit { prefs ->
            prefs.remove(key)
            if (value != null) prefs[key] = value
        }
    }
}
