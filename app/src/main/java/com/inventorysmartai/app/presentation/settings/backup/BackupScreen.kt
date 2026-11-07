@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.settings.backup

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.common.Formatters
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.data.backup.AppRestarter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * النسخ الاحتياطي والاستعادة (ومعهما نقل البيانات إلى جهاز آخر). كل بيانات التطبيق في قاعدة واحدة على هذا الهاتف،
 * فهذه الشاشة هي الحماية الوحيدة من ضياعها عند فقدان الهاتف أو مسح بيانات التطبيق.
 */
@Composable
fun BackupScreen(navController: NavController, viewModel: BackupViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    val createLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) viewModel.saveBackupTo(uri)
    }
    val openLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.onRestoreFilePicked(uri)
    }
    val folderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) viewModel.onAutoFolderPicked(uri)
    }

    LaunchedEffect(state.message) { state.message?.let { snackbar.showSnackbar(it); viewModel.onMessageShown() } }
    LaunchedEffect(state.shareUri) {
        val uri = state.shareUri ?: return@LaunchedEffect
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "application/zip"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "مشاركة النسخة الاحتياطية"))
        viewModel.onShareHandled()
    }

    Scaffold(
        topBar = { AppTopBar(title = "النسخ الاحتياطي والمزامنة", onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            if (state.busy) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

            SectionCard("حالة النسخ") {
                val last = state.lastBackupAt
                if (last == null) {
                    Text(
                        "لم تُنشأ أي نسخة بعد. بياناتك الآن على هذا الهاتف فقط، وتضيع إن فُقد أو مُسحت بيانات التطبيق.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("آخر نسخة: ${dateTime(last)}", style = MaterialTheme.typography.bodyMedium)
                }
            }

            SectionCard("نسخة احتياطية الآن") {
                Text(
                    "تُحفظ كل بياناتك (الأصناف والأرصدة والفواتير والجرد والأهداف...) في ملف واحد يمكنك حفظه في Google Drive أو إرساله لنفسك.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { createLauncher.launch("inventory-backup-${fileStamp()}.zip") },
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("حفظ في ملف") }
                    OutlinedButton(
                        onClick = viewModel::shareBackup,
                        enabled = !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("مشاركة") }
                }
            }

            SectionCard("استعادة نسخة") {
                Text(
                    "تستبدل الاستعادة كل بيانات التطبيق الحالية بما في النسخة. يُحفظ قبلها نسخة أمان من بياناتك الحالية تجدها أدناه.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(
                    onClick = { openLauncher.launch(arrayOf("application/zip", "application/octet-stream", "*/*")) },
                    enabled = !state.busy,
                    modifier = Modifier.fillMaxWidth()
                ) { Text("اختيار ملف نسخة للاستعادة") }

                if (state.safetyBackups.isNotEmpty()) {
                    Text("نسخ الأمان السابقة", style = MaterialTheme.typography.titleSmall)
                    state.safetyBackups.forEach { backup ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dateTime(backup.createdAt), style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    Formatters.formatFileSize(backup.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            TextButton(onClick = { viewModel.onSafetyBackupChosen(backup.fileName) }, enabled = !state.busy) {
                                Text("استعادة")
                            }
                        }
                    }
                }
            }

            SectionCard("نسخ تلقائي يومي") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("حفظ نسخة كل يوم في مجلد", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Switch(
                        checked = state.autoEnabled,
                        onCheckedChange = { checked ->
                            // التفعيل بلا مجلد معناه أن نطلب المجلد أولًا (الاختيار نفسه يفعّل النسخ).
                            if (checked && state.autoFolderUri == null) folderLauncher.launch(null) else viewModel.onAutoToggle(checked)
                        }
                    )
                }
                val folder = state.autoFolderUri
                Text(
                    if (folder == null) "لم يُختر مجلد بعد." else "المجلد: ${Uri.parse(folder).lastPathSegment ?: folder}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    OutlinedButton(onClick = { folderLauncher.launch(null) }, modifier = Modifier.weight(1f)) {
                        Text(if (folder == null) "اختيار مجلد" else "تغيير المجلد")
                    }
                    OutlinedButton(
                        onClick = viewModel::backupNowToFolder,
                        enabled = folder != null && !state.busy,
                        modifier = Modifier.weight(1f)
                    ) { Text("نسخ الآن") }
                }
                Text(
                    "يُحتفظ بآخر 7 نسخ فقط. اختر مجلدًا يتزامن مع Drive أو أي خدمة سحابية إن أردت نسخة خارج الهاتف.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            SectionCard("نقل البيانات إلى جهاز آخر") {
                Text(
                    "١) على هذا الجهاز: اضغط «مشاركة» وأرسل الملف إلى نفسك.\n" +
                        "٢) على الجهاز الآخر: ثبّت التطبيق (نفس الإصدار)، وافتح هذه الشاشة، واختر «اختيار ملف نسخة للاستعادة».\n" +
                        "الاستعادة تستبدل بيانات الجهاز الآخر بالكامل. المزامنة اللحظية بين جهازين غير مدعومة؛ هذا النقل يدوي.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    state.preview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissPreview,
            title = { Text("استعادة هذه النسخة؟") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("تاريخ النسخة: ${dateTime(preview.manifest.createdAt)}")
                    Text("الأصناف: ${preview.manifest.productCount} — فواتير البيع: ${preview.manifest.invoiceCount}")
                    Text("إصدار التطبيق وقت النسخ: ${preview.manifest.appVersion.ifBlank { "—" }}")
                    Text(
                        "ستُستبدل كل بياناتك الحالية. تُحفظ أولًا نسخة أمان منها.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmRestore,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("استعادة") }
            },
            dismissButton = { TextButton(onClick = viewModel::dismissPreview) { Text("إلغاء") } }
        )
    }

    if (state.restartRequired) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("جاهز لإكمال الاستعادة") },
            text = { Text("جُهِّزت الاستعادة وحُفظت نسخة أمان من بياناتك السابقة. يُعاد تشغيل التطبيق الآن لتحميل النسخة.") },
            confirmButton = { Button(onClick = { AppRestarter.restart(context) }) { Text("إعادة التشغيل الآن") } }
        )
    }
}

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            content()
        }
    }
}

private fun dateTime(epochMillis: Long): String = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(Date(epochMillis))

private fun fileStamp(): String = SimpleDateFormat("yyyy-MM-dd-HHmm", Locale.US).format(Date())
