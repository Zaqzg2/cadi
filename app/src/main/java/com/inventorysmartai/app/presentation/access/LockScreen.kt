package com.inventorysmartai.app.presentation.access

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.inventorysmartai.app.data.access.AccessOutcome
import com.inventorysmartai.app.domain.access.AccessRules

/**
 * شاشة القفل: طبقة معتمة فوق التطبيق (لا بديلًا عنه) كي لا يضيع ما كان مفتوحًا من نماذج غير محفوظة، ولا تُظهر شيئًا من محتواه.
 * تمتصّ اللمس وزر الرجوع (الرجوع يُخفي التطبيق فقط)، وتُسقط التركيز عن أي حقل كان مفتوحًا تحتها فلا يُكتب فيه شيء.
 */
@Composable
fun LockScreen(viewModel: LockViewModel = hiltViewModel()) {
    val access by viewModel.access.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    var pin by remember { mutableStateOf("") }
    var recoveryOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { focusManager.clearFocus() }
    BackHandler { (context as? Activity)?.moveTaskToBack(true) }

    Surface(
        modifier = Modifier.fillMaxSize().clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = {}
        ),
        color = MaterialTheme.colorScheme.background
    ) {
        // قبل أن تُقرأ الإعدادات نعرض غطاءً فارغًا فقط (لا وميض لمحتوى التطبيق).
        if (access.loaded) {
            val selected = ui.selectedUserId ?: access.profiles.firstOrNull()?.id
            Column(
                modifier = Modifier.fillMaxSize().systemBarsPadding().imePadding().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                Text("المحلل الذكي للمخزون", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 12.dp))
                Text(
                    "اختر المستخدم وأدخل الرمز",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
                )

                Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    access.profiles.forEach { user ->
                        FilterChip(
                            selected = user.id == selected,
                            onClick = { viewModel.select(user.id); pin = "" },
                            label = { Text("${user.name} · ${user.role.titleAr}") }
                        )
                    }
                }

                OutlinedTextField(
                    value = pin,
                    onValueChange = { value -> if (value.length <= AccessRules.MAX_PIN && value.all { it in '0'..'9' }) pin = value },
                    label = { Text("الرمز") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (pin.isNotEmpty()) { viewModel.signIn(pin); pin = "" } }),
                    isError = ui.error != null,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
                ui.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
                Button(
                    onClick = { viewModel.signIn(pin); pin = "" },
                    enabled = pin.isNotEmpty() && !ui.busy,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                ) { Text(if (ui.busy) "جارٍ التحقق…" else "دخول") }
                TextButton(onClick = { recoveryOpen = true }) { Text("نسيت الرمز؟") }
            }
        }
    }

    if (recoveryOpen) {
        RecoveryDialog(
            onDismiss = { recoveryOpen = false },
            onSubmit = { code, newPin, onError ->
                viewModel.recover(code, newPin) { result ->
                    when (result) {
                        is AccessOutcome.Ok -> recoveryOpen = false
                        is AccessOutcome.Error -> onError(result.message)
                    }
                }
            }
        )
    }
}

/** إعادة ضبط رمز المدير برمز الاسترداد الذي عُرض عند تفعيل الحماية. */
@Composable
private fun RecoveryDialog(onDismiss: () -> Unit, onSubmit: (code: String, newPin: String, onError: (String) -> Unit) -> Unit) {
    var code by remember { mutableStateOf("") }
    var newPin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("استرداد الدخول") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "اكتب رمز الاسترداد الذي حفظته عند تفعيل الحماية (12 حرفًا)، ثم رمزًا جديدًا للمدير.",
                    style = MaterialTheme.typography.bodySmall
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("رمز الاسترداد") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = newPin,
                    onValueChange = { value -> if (value.length <= AccessRules.MAX_PIN && value.all { it in '0'..'9' }) newPin = value },
                    label = { Text("الرمز الجديد") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        confirmButton = {
            Button(
                onClick = { error = null; onSubmit(code, newPin) { message -> error = message } },
                enabled = code.isNotBlank() && newPin.isNotEmpty()
            ) { Text("استرداد") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } }
    )
}
