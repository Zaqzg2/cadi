package com.inventorysmartai.app.data.alerts

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import com.inventorysmartai.app.domain.alerts.StockAlertSchedule
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** تفعيل تنبيهات المخزون اليومية وساعة إرسالها. مُطفأة افتراضيًا: الإشعارات لا تُفرض على المستخدم. */
@Singleton
class StockAlertSettings @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private val data: Flow<Preferences> = dataStore.data.catch { emit(emptyPreferences()) }

    val enabled: Flow<Boolean> = data.map { it[ENABLED] ?: false }
    val hour: Flow<Int> = data.map { (it[HOUR] ?: StockAlertSchedule.DEFAULT_HOUR).coerceIn(0, 23) }

    suspend fun setEnabled(value: Boolean) { dataStore.edit { it[ENABLED] = value } }
    suspend fun setHour(value: Int) { dataStore.edit { it[HOUR] = value.coerceIn(0, 23) } }

    private companion object {
        val ENABLED = booleanPreferencesKey("stock_alerts_enabled")
        val HOUR = intPreferencesKey("stock_alerts_hour")
    }
}
