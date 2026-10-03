@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.inventorysmartai.app.presentation.datacenter

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.inventorysmartai.app.presentation.common.startBarcodeScan
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.inventorysmartai.app.core.designsystem.component.StatusPill
import com.inventorysmartai.app.domain.model.ImportSourceType
import com.inventorysmartai.app.navigation.Destination

private data class ImportTile(val type: ImportSourceType, val label: String, val icon: ImageVector)

/** Tiles that open the file/photo import flow. CAMERA lands on the same setup screen, which has a
 *  "take a photo" button. BARCODE opens the scanner; MANUAL opens the manual product form. */
private val IMPORT_FLOW_TILE_TYPES = setOf(
    ImportSourceType.EXCEL,
    ImportSourceType.CSV,
    ImportSourceType.PDF,
    ImportSourceType.IMAGE,
    ImportSourceType.CAMERA
)

private val importTiles = listOf(
    ImportTile(ImportSourceType.EXCEL, "Excel", Icons.Filled.TableChart),
    ImportTile(ImportSourceType.CSV, "CSV", Icons.Filled.Description),
    ImportTile(ImportSourceType.PDF, "PDF", Icons.Filled.PictureAsPdf),
    ImportTile(ImportSourceType.IMAGE, "صورة", Icons.Filled.Image),
    ImportTile(ImportSourceType.CAMERA, "الكاميرا", Icons.Filled.CameraAlt),
    ImportTile(ImportSourceType.BARCODE, "الباركود", Icons.Filled.QrCodeScanner),
    ImportTile(ImportSourceType.MANUAL, "إدخال يدوي", Icons.Filled.Edit)
)

private data class MasterDataTile(val label: String, val route: String)

private val masterDataTiles = listOf(
    MasterDataTile("الأصناف", Destination.Inventory.route),
    MasterDataTile("الفروع", Destination.SettingsCatalog.createRoute("branch")),
    MasterDataTile("التصنيفات", Destination.SettingsCatalog.createRoute("category")),
    MasterDataTile("الوحدات", Destination.SettingsCatalog.createRoute("unit")),
    MasterDataTile("العملاء", Destination.PartyList.createRoute("customer")),
    MasterDataTile("الموردون", Destination.PartyList.createRoute("supplier"))
)

private data class OperationTile(val label: String, val filter: String)

private val operationTiles = listOf(
    OperationTile("سجل الاستيراد", "all"),
    OperationTile("قيد المراجعة", "review"),
    OperationTile("الأخطاء", "errors"),
    OperationTile("المقبولة", "completed")
)

private val externalServices = listOf("Google Drive", "Google Sheets", "Google Docs")

@Composable
fun DataCenterScreen(navController: NavController, viewModel: DataCenterViewModel = hiltViewModel()) {
    val snackbarMessage by viewModel.snackbarMessage.collectAsStateWithLifecycle()
    val scanResult by viewModel.scanResult.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(scanResult) {
        scanResult?.let { result ->
            viewModel.onScanResultHandled()
            if (result.productId != null) {
                navController.navigate(Destination.ProductDetail.createRoute(result.productId))
            } else {
                // Unknown barcode: offer to register it as a new product, barcode pre-filled.
                navController.navigate(Destination.ManualEntry.createRoute(result.barcode))
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.onSnackbarShown()
        }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("مركز البيانات") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Text("استيراد البيانات", style = MaterialTheme.typography.titleMedium) }
            items(importTiles.chunked(3)) { rowTiles ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowTiles.forEach { tile ->
                        Card(
                            modifier = Modifier.weight(1f),
                            onClick = {
                                when {
                                    tile.type in IMPORT_FLOW_TILE_TYPES -> navController.navigate(Destination.ImportSetup.route)
                                    tile.type == ImportSourceType.BARCODE -> startBarcodeScan(
                                        context,
                                        onResult = viewModel::onBarcodeScanned,
                                        onError = viewModel::onScanFailed
                                    )
                                    tile.type == ImportSourceType.MANUAL -> navController.navigate(Destination.ManualEntry.createRoute())
                                }
                            }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(tile.icon, contentDescription = null)
                                Text(tile.label, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    // keep the last row's tiles the same width as the others
                    repeat(3 - rowTiles.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }

            item { Text("البيانات الأساسية", style = MaterialTheme.typography.titleMedium) }
            items(masterDataTiles.chunked(3)) { rowTiles ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowTiles.forEach { tile ->
                        Card(modifier = Modifier.weight(1f), onClick = { navController.navigate(tile.route) }) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp, horizontal = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(tile.label, style = MaterialTheme.typography.labelLarge)
                            }
                        }
                    }
                    repeat(3 - rowTiles.size) { androidx.compose.foundation.layout.Spacer(Modifier.weight(1f)) }
                }
            }

            item { Text("العمليات", style = MaterialTheme.typography.titleMedium) }
            items(operationTiles) { tile ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { navController.navigate(Destination.ImportHistory.createRoute(tile.filter)) }
                ) {
                    Row(modifier = Modifier.fillMaxWidth().padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(tile.label, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }

            item { Text("خدمات خارجية", style = MaterialTheme.typography.titleMedium) }
            items(externalServices) { service ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { viewModel.onExternalServiceTapped(service) }
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(service, style = MaterialTheme.typography.bodyLarge)
                        StatusPill(text = "قريباً", color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}
