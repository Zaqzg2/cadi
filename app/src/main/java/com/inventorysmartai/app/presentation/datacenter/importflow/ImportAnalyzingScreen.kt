package com.inventorysmartai.app.presentation.datacenter.importflow

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.AppTopBar
import com.inventorysmartai.app.navigation.Destination

/** Spec section 24 step 4 ("تحليل الملف") plus 23's "show progress, rows processed, current
 *  step". Auto-advances to the column-mapping screen the moment analysis finishes (headers
 *  become available); shows the error inline with a retry path back to setup if it fails. */
@Composable
fun ImportAnalyzingScreen(navController: NavController, viewModel: ImportFlowViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.isBusy, state.headers) {
        if (!state.isBusy && state.headers.isNotEmpty() && state.analysisError == null) {
            navController.navigate(Destination.ImportColumnMapping.route) {
                popUpTo(Destination.ImportAnalyzing.route) { inclusive = true }
            }
        }
    }

    Scaffold(
        topBar = { AppTopBar(title = "تحليل الملف") }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (state.analysisError != null) {
                Text("تعذّر تحليل الملف", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(4.dp))
                Text(
                    state.analysisError.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.padding(8.dp))
                Button(onClick = { navController.popBackStack(Destination.ImportSetup.route, inclusive = false) }) {
                    Text("العودة والمحاولة مرة أخرى")
                }
            } else {
                if (state.analyzedTotal > 0) {
                    LinearProgressIndicator(
                        progress = { state.analyzedProcessed.toFloat() / state.analyzedTotal.toFloat() },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text(
                        "${state.analyzedProcessed} / ${state.analyzedTotal} صف",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                } else {
                    CircularProgressIndicator()
                }
                Text(
                    state.stageLabel.ifBlank { "جاري التحليل..." },
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(top = 16.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
