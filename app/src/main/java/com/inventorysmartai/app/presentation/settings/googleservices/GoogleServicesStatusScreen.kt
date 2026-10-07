package com.inventorysmartai.app.presentation.settings.googleservices

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.core.designsystem.theme.LocalInventoryStatusColors
import com.inventorysmartai.app.domain.repository.GoogleServiceStatus

private data class ServiceRow(val label: String, val value: (GoogleServiceStatus) -> String)

private val SERVICE_ROWS = listOf(
    ServiceRow("الذكاء الاصطناعي (الخادم)") { it.ai },
    ServiceRow("Google Drive") { it.drive },
    ServiceRow("Google Sheets") { it.sheets },
    ServiceRow("Google Docs") { it.docs },
    ServiceRow("Gmail") { it.gmail },
    ServiceRow("Google Calendar") { it.calendar }
)

@Composable
fun GoogleServicesStatusScreen(navController: NavController, viewModel: GoogleServicesStatusViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsState()
    val googleAvailable = state.status?.googleConfigured != false
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeError()
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "خدمات Google", onBack = { navController.popBackStack() }) },
        snackbarHost = { SnackbarHost(snackbarHostState) { Snackbar(it) } }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        if (state.linked) "الحساب متصل" else "لم يتم ربط حساب Google بعد",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        "يمنح الربط صلاحيات محدودة فقط: حفظ الملفات التي ينشئها التطبيق في Drive/Sheets/Docs، إرسال البريد نيابةً عنك، وإضافة مواعيد إلى التقويم — دون قراءة بريدك أو ملفاتك الأخرى.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                    )
                    if (state.isConnecting) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                            Text("جارٍ الربط...")
                        }
                    } else if (state.linked) {
                        OutlinedButton(onClick = viewModel::disconnect, modifier = Modifier.fillMaxWidth()) {
                            Text("إلغاء ربط الحساب")
                        }
                    } else {
                        Button(
                            onClick = { viewModel.connect(context as ComponentActivity) },
                            enabled = googleAvailable,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("ربط حساب Google")
                        }
                        if (!googleAvailable) {
                            Text(
                                "خدمات Google غير مفعّلة على الخادم (لم تُضبط بيانات OAuth هناك). الذكاء الاصطناعي لا يتأثر.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("الذكاء الاصطناعي", style = MaterialTheme.typography.titleMedium)
                    val providers = state.status?.aiProviders.orEmpty()
                    Text(
                        if (providers.isEmpty()) "لا يوجد مزوّد جاهز حاليًا على الخادم."
                        else "المزوّدون الجاهزون على الخادم: ${providers.joinToString("، ")}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline,
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                    OutlinedButton(
                        onClick = { viewModel.refresh(verifyAi = true) },
                        enabled = !state.isLoading,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("اختبار الاتصال بالذكاء الاصطناعي")
                    }
                }
            }

            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 12.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                if (state.isLoading) {
                    Row(modifier = Modifier.padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    LazyColumn {
                        items(SERVICE_ROWS) { row ->
                            val statusText = state.status?.let(row.value) ?: "غير متصل"
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(row.label, style = MaterialTheme.typography.bodyLarge)
                                StatusPill(text = statusText, color = colorFor(statusText))
                            }
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun colorFor(statusText: String) = when (statusText) {
    "متصل" -> LocalInventoryStatusColors.current.available
    "خطأ" -> LocalInventoryStatusColors.current.expired
    "يتطلب صلاحية" -> LocalInventoryStatusColors.current.low
    else -> LocalInventoryStatusColors.current.zero
}
