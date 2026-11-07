package com.inventorysmartai.app.presentation.settings.backup

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.inventorysmartai.app.data.backup.BackupManager
import com.inventorysmartai.app.data.backup.BackupManifest
import com.inventorysmartai.app.data.backup.BackupScheduler
import com.inventorysmartai.app.data.backup.BackupSettings
import com.inventorysmartai.app.data.backup.InspectResult
import com.inventorysmartai.app.data.backup.SafetyBackup
import com.inventorysmartai.app.data.backup.StageResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** نسخة اختارها المستخدم وتنتظر تأكيده قبل الاستعادة: من ملف ([sourceUri]) أو من نسخة أمان محفوظة ([safetyName]). */
data class RestorePreview(val manifest: BackupManifest, val sourceUri: String? = null, val safetyName: String? = null)

data class BackupUiState(
    val busy: Boolean = false,
    val lastBackupAt: Long? = null,
    val autoEnabled: Boolean = false,
    val autoFolderUri: String? = null,
    val safetyBackups: List<SafetyBackup> = emptyList(),
    val message: String? = null,
    val preview: RestorePreview? = null,
    /** عنوان ملف النسخة الجاهز للمشاركة؛ تستهلكه الشاشة مرة واحدة ثم تستدعي [BackupViewModel.onShareHandled]. */
    val shareUri: Uri? = null,
    val restartRequired: Boolean = false
)

@HiltViewModel
class BackupViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val manager: BackupManager,
    private val settings: BackupSettings
) : ViewModel() {

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            combine(settings.lastBackupAt, settings.autoEnabled, settings.autoFolderUri) { last, enabled, folder ->
                Triple(last, enabled, folder)
            }.collect { (last, enabled, folder) ->
                _state.update { it.copy(lastBackupAt = last, autoEnabled = enabled, autoFolderUri = folder) }
            }
        }
        refreshSafetyBackups()
    }

    fun onMessageShown() { _state.update { it.copy(message = null) } }
    fun onShareHandled() { _state.update { it.copy(shareUri = null) } }
    fun dismissPreview() { _state.update { it.copy(preview = null) } }

    // ------------------------------------------------------------------ backup

    fun saveBackupTo(uri: Uri) = launchBusy("تم حفظ النسخة الاحتياطية") {
        val stream = context.contentResolver.openOutputStream(uri) ?: error("تعذّر فتح الملف للكتابة")
        stream.use { manager.writeBackup(it) }
        settings.setLastBackupAt(System.currentTimeMillis())
    }

    fun shareBackup() = launchBusy(null) {
        val file = manager.createShareFile()
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        _state.update { it.copy(shareUri = uri) }
    }

    fun backupNowToFolder() = launchBusy("تم حفظ نسخة في المجلد المختار") {
        val folder = _state.value.autoFolderUri ?: error("اختر مجلدًا أولًا")
        manager.writeBackupToFolder(Uri.parse(folder))
    }

    // ------------------------------------------------------------------ restore

    fun onRestoreFilePicked(uri: Uri) = launchBusy(null) {
        val stream = context.contentResolver.openInputStream(uri) ?: error("تعذّر فتح الملف")
        when (val result = stream.use { manager.inspect(it) }) {
            is InspectResult.Valid -> _state.update { it.copy(preview = RestorePreview(result.manifest, sourceUri = uri.toString())) }
            is InspectResult.Rejected -> _state.update { it.copy(message = result.reason) }
        }
    }

    fun onSafetyBackupChosen(fileName: String) = launchBusy(null) {
        val stream = manager.openSafetyBackup(fileName) ?: error("لم تعد نسخة الأمان هذه موجودة")
        when (val result = stream.use { manager.inspect(it) }) {
            is InspectResult.Valid -> _state.update { it.copy(preview = RestorePreview(result.manifest, safetyName = fileName)) }
            is InspectResult.Rejected -> _state.update { it.copy(message = result.reason) }
        }
    }

    /** بعد تأكيد المستخدم: تجهيز الاستعادة (بعد نسخة أمان من الحالي). الاستبدال الفعلي يحدث عند إعادة التشغيل. */
    fun confirmRestore() {
        val preview = _state.value.preview ?: return
        _state.update { it.copy(preview = null) }
        launchBusy(null) {
            val name = preview.safetyName
            val source = preview.sourceUri
            val stream = when {
                name != null -> manager.openSafetyBackup(name)
                source != null -> context.contentResolver.openInputStream(Uri.parse(source))
                else -> null
            } ?: error("تعذّر فتح النسخة")
            when (val result = stream.use { manager.stageRestore(it) }) {
                is StageResult.Staged -> _state.update { it.copy(restartRequired = true) }
                is StageResult.Rejected -> _state.update { it.copy(message = result.reason) }
            }
            refreshSafetyBackups()
        }
    }

    // ------------------------------------------------------------------ automatic backup

    fun onAutoFolderPicked(uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            _state.update { it.copy(message = "تعذّر حفظ إذن الوصول إلى هذا المجلد. اختر مجلدًا آخر.") }
            return
        }
        viewModelScope.launch {
            settings.setAutoFolderUri(uri.toString())
            settings.setAutoEnabled(true)
            BackupScheduler.apply(context, enabled = true)
            _state.update { it.copy(message = "تم تفعيل النسخ التلقائي اليومي") }
        }
    }

    fun onAutoToggle(enabled: Boolean) {
        viewModelScope.launch {
            settings.setAutoEnabled(enabled)
            BackupScheduler.apply(context, enabled)
        }
    }

    // ------------------------------------------------------------------ internals

    private fun refreshSafetyBackups() {
        viewModelScope.launch {
            val list = manager.listSafetyBackups()
            _state.update { it.copy(safetyBackups = list) }
        }
    }

    private fun launchBusy(successMessage: String?, block: suspend () -> Unit) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true, message = null) }
            try {
                block()
                _state.update { it.copy(busy = false, message = successMessage ?: it.message) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(busy = false, message = e.message ?: "فشلت العملية") }
            }
        }
    }
}
