package com.inventorysmartai.app.presentation.settings.aiproviders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.data.ai.provider.ProviderConfig

@Composable
fun AiProvidersScreen(navController: NavController, viewModel: AiProvidersViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(topBar = { AppTopBar(title = "إعدادات الذكاء الاصطناعي", onBack = { navController.popBackStack() }) }) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("مزوّدو الذكاء الاصطناعي المجانيون", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "أدخل مفتاح API الخاص بك لأي مزوّد. يُحفظ المفتاح مشفّرًا على هذا الجهاز فقط ولا يخرج منه إلا إلى المزوّد نفسه. " +
                                "عند فشل أحد المزوّدين (حدّ الاستخدام أو انقطاع) ينتقل التطبيق تلقائيًا إلى التالي حسب الترتيب أدناه.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            "تنبيه خصوصية: صور الفواتير ونصوصها تُرسل إلى المزوّد المختار. بعض الطبقات المجانية قد تسجّل الطلبات أو تستخدمها " +
                                "لتحسين النماذج، فلا ترسل بيانات عملاء حساسة عبر مزوّد لم تراجع شروطه.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                        HorizontalDivider()
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                                Text("قراءة المستندات بـ Mistral OCR أولًا", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "الأدق للعربية والجداول؛ يتطلب مفتاح Mistral.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.outline
                                )
                            }
                            Switch(checked = state.snapshot.preferMistralOcr, onCheckedChange = viewModel::setPreferMistralOcr)
                        }
                    }
                }
            }

            itemsIndexed(state.snapshot.providers, key = { _, p -> p.id.name }) { index, config ->
                ProviderCard(
                    config = config,
                    isFirst = index == 0,
                    isLast = index == state.snapshot.providers.lastIndex,
                    testing = config.id in state.testing,
                    message = state.messages[config.id],
                    onEnabled = { viewModel.setEnabled(config.id, it) },
                    onMove = { viewModel.move(config.id, it) },
                    onSaveKey = { viewModel.saveKey(config.id, it) },
                    onClearKey = { viewModel.clearKey(config.id) },
                    onSaveModels = { text, vision -> viewModel.saveModels(config.id, text, vision) },
                    onTest = { viewModel.test(config.id) }
                )
            }
        }
    }
}

@Composable
private fun ProviderCard(
    config: ProviderConfig,
    isFirst: Boolean,
    isLast: Boolean,
    testing: Boolean,
    message: String?,
    onEnabled: (Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onSaveKey: (String) -> Unit,
    onClearKey: () -> Unit,
    onSaveModels: (String, String) -> Unit,
    onTest: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    var keyInput by remember(config.id) { mutableStateOf("") }
    var textModel by remember(config.id, config.textModel) { mutableStateOf(config.textModel) }
    var visionModel by remember(config.id, config.visionModel) { mutableStateOf(config.visionModel) }
    val ltr = LocalTextStyle.current.copy(textDirection = TextDirection.Ltr)

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(config.id.labelAr, style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (config.hasKey) "المفتاح: محفوظ" else "المفتاح: غير مُدخل",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (config.hasKey) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                    )
                }
                IconButton(onClick = { onMove(-1) }, enabled = !isFirst) { Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "رفع الأولوية") }
                IconButton(onClick = { onMove(1) }, enabled = !isLast) { Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "خفض الأولوية") }
                Switch(checked = config.enabled, onCheckedChange = onEnabled)
            }
            Text(config.id.noteAr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)

            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text(if (config.hasKey) "استبدال المفتاح" else "مفتاح API") },
                singleLine = true,
                textStyle = ltr,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onSaveKey(keyInput); keyInput = "" }, enabled = keyInput.isNotBlank()) { Text("حفظ المفتاح") }
                if (config.hasKey) OutlinedButton(onClick = onClearKey) { Text("حذف") }
                TextButton(onClick = { uriHandler.openUri(config.id.keyUrl) }) { Text("الحصول على مفتاح") }
            }

            HorizontalDivider()
            OutlinedTextField(
                value = textModel,
                onValueChange = { textModel = it },
                label = { Text("نموذج النص والمساعد") },
                singleLine = true,
                textStyle = ltr,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = visionModel,
                onValueChange = { visionModel = it },
                label = { Text("نموذج قراءة الصور") },
                singleLine = true,
                textStyle = ltr,
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { onSaveModels(textModel, visionModel) }) { Text("حفظ النماذج") }
                TextButton(onClick = {
                    textModel = config.id.defaultTextModel
                    visionModel = config.id.defaultVisionModel
                    onSaveModels("", "")
                }) { Text("الافتراضي") }
            }
            Text(
                "أسماء النماذج تتغير كثيرًا لدى المزوّدين؛ إن رُفض الطلب بسبب اسم نموذج، حدّثه هنا من موقع المزوّد دون انتظار تحديث للتطبيق.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = onTest, enabled = !testing && config.hasKey) { Text("اختبار الاتصال") }
                if (testing) CircularProgressIndicator(modifier = Modifier.padding(2.dp))
            }
            message?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
