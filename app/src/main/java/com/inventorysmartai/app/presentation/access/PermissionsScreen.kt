package com.inventorysmartai.app.presentation.access

import android.content.Intent
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.data.access.AccessOutcome
import com.inventorysmartai.app.domain.access.AccessRules
import com.inventorysmartai.app.domain.access.AppRole
import com.inventorysmartai.app.domain.access.UserProfile

private sealed interface PermissionDialog {
    data object Enable : PermissionDialog
    data object AddUser : PermissionDialog
    data class EditUser(val user: UserProfile) : PermissionDialog
    data class DeleteUser(val user: UserProfile) : PermissionDialog
    data object Disable : PermissionDialog
    data object NewRecovery : PermissionDialog
}

/**
 * المستخدمون والأدوار وقفل التطبيق. حماية على مستوى الشاشات داخل التطبيق على هذا الجهاز: تفصل ما يراه ويفعله كل موظف،
 * ولا تمنع من يملك الجهاز ويستطيع مسح بيانات التطبيق أو الوصول إلى ملفاته. المساعد الذكي لا يتبع الأدوار داخل أدواته؛ يُقيَّد دخوله فقط.
 */
@Composable
fun PermissionsScreen(navController: NavController, viewModel: PermissionsViewModel = hiltViewModel()) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    var dialog by remember { mutableStateOf<PermissionDialog?>(null) }
    var recoveryCode by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { AppTopBar(title = "الصلاحيات والمستخدمون", onBack = { navController.popBackStack() }) }) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("قفل التطبيق وتحديد الصلاحيات", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Switch(
                            checked = access.enabled,
                            onCheckedChange = { on -> dialog = if (on) PermissionDialog.Enable else PermissionDialog.Disable }
                        )
                    }
                    Text(
                        "عند التفعيل يطلب التطبيق رمزًا عند فتحه (وبعد دقيقة من تركه)، ويرى كل مستخدم الشاشات المسموحة لدوره فقط.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (access.enabled) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("المستخدمون", style = MaterialTheme.typography.titleSmall)
                        access.profiles.forEach { user ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(user.name + if (user.id == access.currentUserId) " (أنت)" else "", style = MaterialTheme.typography.bodyLarge)
                                    Text(user.role.titleAr, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                IconButton(onClick = { dialog = PermissionDialog.EditUser(user) }) { Icon(Icons.Filled.Edit, contentDescription = "تعديل") }
                                IconButton(onClick = { dialog = PermissionDialog.DeleteUser(user) }) { Icon(Icons.Filled.Delete, contentDescription = "حذف") }
                            }
                        }
                        OutlinedButton(onClick = { dialog = PermissionDialog.AddUser }, modifier = Modifier.fillMaxWidth()) { Text("إضافة مستخدم") }
                    }
                }

                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("ماذا يفعل كل دور", style = MaterialTheme.typography.titleSmall)
                        AppRole.entries.forEach { role ->
                            Text(role.titleAr, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                role.permissions.joinToString(" • ") { it.titleAr },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedButton(onClick = viewModel::lock, modifier = Modifier.fillMaxWidth()) { Text("قفل التطبيق الآن") }
                OutlinedButton(onClick = { dialog = PermissionDialog.NewRecovery }, modifier = Modifier.fillMaxWidth()) { Text("إنشاء رمز استرداد جديد") }
            }

            Text(
                "تنبيه: هذه حماية على مستوى التطبيق على هذا الجهاز؛ لا تمنع من يملك الجهاز ويستطيع مسح بيانات التطبيق. " +
                    "اعمل نسخة احتياطية قبل التفعيل واحتفظ برمز الاسترداد.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    when (val current = dialog) {
        null -> Unit
        PermissionDialog.Enable -> EnableDialog(
            onDismiss = { dialog = null },
            onSubmit = { name, pin, onError ->
                viewModel.enable(name, pin) { result ->
                    when (result) {
                        is AccessOutcome.Ok -> { dialog = null; recoveryCode = result.value }
                        is AccessOutcome.Error -> onError(result.message)
                    }
                }
            }
        )
        PermissionDialog.AddUser -> UserDialog(
            title = "مستخدم جديد",
            initial = null,
            onDismiss = { dialog = null },
            onSubmit = { name, role, pin, onError ->
                viewModel.addUser(name, role, pin) { result -> handle(result, onClose = { dialog = null }, onError = onError) }
            }
        )
        is PermissionDialog.EditUser -> UserDialog(
            title = "تعديل المستخدم",
            initial = current.user,
            onDismiss = { dialog = null },
            onSubmit = { name, role, pin, onError ->
                viewModel.updateUser(current.user.id, name, role, pin.ifEmpty { null }) { result ->
                    handle(result, onClose = { dialog = null }, onError = onError)
                }
            }
        )
        is PermissionDialog.DeleteUser -> DeleteUserDialog(
            name = current.user.name,
            onDismiss = { dialog = null },
            onSubmit = { onError ->
                viewModel.deleteUser(current.user.id) { result -> handle(result, onClose = { dialog = null }, onError = onError) }
            }
        )
        PermissionDialog.Disable -> PinConfirmDialog(
            title = "إيقاف القفل والصلاحيات",
            message = "ستُحذف حسابات المستخدمين ويصبح التطبيق مفتوحًا للجميع. أدخل رمز المدير للتأكيد.",
            onDismiss = { dialog = null },
            onSubmit = { pin, onError ->
                viewModel.disable(pin) { result -> handle(result, onClose = { dialog = null }, onError = onError) }
            }
        )
        PermissionDialog.NewRecovery -> PinConfirmDialog(
            title = "رمز استرداد جديد",
            message = "سيبطل الرمز القديم. أدخل رمز المدير للتأكيد.",
            onDismiss = { dialog = null },
            onSubmit = { pin, onError ->
                viewModel.newRecoveryCode(pin) { result ->
                    when (result) {
                        is AccessOutcome.Ok -> { dialog = null; recoveryCode = result.value }
                        is AccessOutcome.Error -> onError(result.message)
                    }
                }
            }
        )
    }

    recoveryCode?.let { code -> RecoveryCodeDialog(code = code, onDone = { recoveryCode = null }) }
}

private fun handle(result: AccessOutcome<Unit>, onClose: () -> Unit, onError: (String) -> Unit) {
    when (result) {
        is AccessOutcome.Ok -> onClose()
        is AccessOutcome.Error -> onError(result.message)
    }
}

@Composable
private fun PinField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.length <= AccessRules.MAX_PIN && v.all { it in '0'..'9' }) onChange(v) },
        label = { Text(label) },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        modifier = modifier.fillMaxWidth()
    )
}

@Composable
private fun EnableDialog(onDismiss: () -> Unit, onSubmit: (name: String, pin: String, onError: (String) -> Unit) -> Unit) {
    var name by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تفعيل القفل") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("ستكون أنت المدير. اختر اسمك ورمزًا من ${AccessRules.MIN_PIN} إلى ${AccessRules.MAX_PIN} أرقام.", style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("اسم المدير") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                PinField(pin, { pin = it }, "الرمز")
                PinField(confirm, { confirm = it }, "تأكيد الرمز")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    error = null
                    if (pin != confirm) error = "الرمزان غير متطابقين" else onSubmit(name, pin) { message -> error = message }
                },
                enabled = name.isNotBlank() && pin.isNotEmpty()
            ) { Text("تفعيل") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun UserDialog(
    title: String,
    initial: UserProfile?,
    onDismiss: () -> Unit,
    onSubmit: (name: String, role: AppRole, pin: String, onError: (String) -> Unit) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var role by remember { mutableStateOf(initial?.role ?: AppRole.SALES) }
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("الاسم") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AppRole.entries.forEach { option ->
                        FilterChip(selected = role == option, onClick = { role = option }, label = { Text(option.titleAr) })
                    }
                }
                PinField(pin, { pin = it }, if (initial == null) "الرمز" else "رمز جديد (اتركه فارغًا للإبقاء على الحالي)")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { error = null; onSubmit(name, role, pin) { message -> error = message } },
                enabled = name.isNotBlank() && (initial != null || pin.isNotEmpty())
            ) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** الرفض (آخر مدير، أو المستخدم الذي دخلت به) يظهر داخل الحوار بدل أن يُغلق الحوار بلا تفسير. */
@Composable
private fun DeleteUserDialog(name: String, onDismiss: () -> Unit, onSubmit: (onError: (String) -> Unit) -> Unit) {
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("حذف المستخدم؟") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("سيُحذف «$name» ولن يستطيع الدخول بعد الآن.")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = { error = null; onSubmit { message -> error = message } }) { Text("حذف") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

@Composable
private fun PinConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onSubmit: (pin: String, onError: (String) -> Unit) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(message, style = MaterialTheme.typography.bodySmall)
                PinField(pin, { pin = it }, "رمز المدير")
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = { Button(onClick = { error = null; onSubmit(pin) { m -> error = m } }, enabled = pin.isNotEmpty()) { Text("تأكيد") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}

/** يُعرض مرة واحدة: بلا هذا الرمز لا يمكن استرداد الدخول إن نُسي رمز المدير. */
@Composable
private fun RecoveryCodeDialog(code: String, onDone: () -> Unit) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text("رمز الاسترداد — احفظه الآن") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "إن نسيت رمز المدير فهذا الرمز وحده يعيد لك الدخول (وإلا لا مخرج إلا مسح بيانات التطبيق كلها). لن يُعرض مرة أخرى.",
                    style = MaterialTheme.typography.bodySmall
                )
                Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth()) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(16.dp)) {
                        Text(code, style = MaterialTheme.typography.headlineSmall, fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
                    }
                }
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, code)
                    }
                    context.startActivity(Intent.createChooser(send, "حفظ رمز الاسترداد"))
                }) { Text("مشاركة / حفظ في ملاحظاتي") }
            }
        },
        confirmButton = { Button(onClick = onDone) { Text("حفظته") } }
    )
}
