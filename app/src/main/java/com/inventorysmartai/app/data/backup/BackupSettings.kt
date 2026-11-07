package com.inventorysmartai.app.data.backup

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** إعدادات النسخ الاحتياطي (آخر نسخة، والنسخ التلقائي ومجلده) في نفس DataStore الذي يستعمله التطبيق. */
@Singleton
class BackupSettings @Inject constructor(private val dataStore: DataStore<Preferences>) {

    private val data: Flow<Preferences> = dataStore.data.catch { emit(emptyPreferences()) }

    val lastBackupAt: Flow<Long?> = data.map { it[LAST_BACKUP_AT] }
    val autoEnabled: Flow<Boolean> = data.map { it[AUTO_ENABLED] ?: false }

    /** عنوان المجلد (شجرة SAF) الذي يُكتب فيه النسخ التلقائي، أو null إن لم يُختر. */
    val autoFolderUri: Flow<String?> = data.map { it[AUTO_FOLDER_URI] }

    suspend fun setLastBackupAt(at: Long) { dataStore.edit { it[LAST_BACKUP_AT] = at } }
    suspend fun setAutoEnabled(enabled: Boolean) { dataStore.edit { it[AUTO_ENABLED] = enabled } }
    suspend fun setAutoFolderUri(uri: String?) {
        dataStore.edit { prefs -> if (uri == null) prefs.remove(AUTO_FOLDER_URI) else prefs[AUTO_FOLDER_URI] = uri }
    }

    private companion object {
        val LAST_BACKUP_AT = longPreferencesKey("backup_last_at")
        val AUTO_ENABLED = booleanPreferencesKey("backup_auto_enabled")
        val AUTO_FOLDER_URI = stringPreferencesKey("backup_auto_folder_uri")
    }
}
