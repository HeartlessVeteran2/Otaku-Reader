@file:Suppress("MaxLineLength")
package app.otakureader.feature.more.qr

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.otakureader.domain.model.ShareableLibrary
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.serialization.json.Json

/**
 * Screen that scans a QR code from another device and imports the library.
 *
 * @param onLibraryScanned Called after the import completes (not just when the QR is decoded).
 *   The caller should navigate away at this point; the library data is provided for
 *   informational use but the actual import was already performed by [viewModel].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanLibraryScreen(
    onNavigateBack: () -> Unit,
    onLibraryScanned: (ShareableLibrary) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ScanLibraryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    var scannedLibrary by remember { mutableStateOf<ShareableLibrary?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var isScanning by remember { mutableStateOf(false) }
    val importState by viewModel.importState.collectAsStateWithLifecycle()

    // Navigate back automatically once import finishes
    LaunchedEffect(importState) {
        if (importState is ScanLibraryViewModel.ImportState.Done) {
            scannedLibrary?.let { onLibraryScanned(it) }
        }
    }

    // Declared before cameraLauncher so the permission-granted path can launch it.
    val qrLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        isScanning = false
        if (result.contents == null) {
            error = "Scan cancelled"
            return@rememberLauncherForActivityResult
        }
        try {
            scannedLibrary = Json.decodeFromString(result.contents)
        } catch (e: Exception) {
            error = "Invalid QR code: ${e.message}"
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission just granted — actually open the scanner. This previously called an
            // empty stub (launchQrScanner), so first-time users who granted the camera
            // permission got a dead screen and the scanner never opened.
            isScanning = true
            qrLauncher.launch(qrScanOptions())
        } else {
            error = "Camera permission required to scan QR codes"
            isScanning = false
        }
    }

    LaunchedEffect(Unit) {
        when (PackageManager.PERMISSION_GRANTED) {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                isScanning = true
                qrLauncher.launch(qrScanOptions())
            }
            else -> cameraLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(app.otakureader.feature.more.R.string.more_scan_library_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(app.otakureader.feature.more.R.string.more_back)
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Capture vars into local vals before the when block so Kotlin can smart-cast.
            val localError = error
            val localLibrary = scannedLibrary
            when {
                isScanning -> {
                    CircularProgressIndicator()
                    Text(
                        stringResource(app.otakureader.feature.more.R.string.more_scan_library_opening_camera),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                localError != null -> {
                    Text(
                        text = localError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(onClick = {
                        error = null
                        cameraLauncher.launch(Manifest.permission.CAMERA)
                    }) {
                        Text(stringResource(app.otakureader.feature.more.R.string.more_scan_library_try_again))
                    }
                }
                localLibrary != null -> {
                    when (val state = importState) {
                        is ScanLibraryViewModel.ImportState.Importing -> {
                            CircularProgressIndicator()
                            Text(
                                stringResource(app.otakureader.feature.more.R.string.more_scan_library_importing),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        is ScanLibraryViewModel.ImportState.Done -> {
                            Text(
                                text = stringResource(app.otakureader.feature.more.R.string.more_scan_library_import_complete),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = stringResource(
                                    app.otakureader.feature.more.R.string.more_scan_library_import_result,
                                    state.imported,
                                    state.skipped
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        is ScanLibraryViewModel.ImportState.Error -> {
                            Text(
                                text = stringResource(app.otakureader.feature.more.R.string.more_scan_library_import_failed, state.message),
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            // importLibrary() transitions to Importing before re-running,
                            // so the UI correctly reflects the new attempt.
                            Button(onClick = { viewModel.importLibrary(localLibrary) }) {
                                Text(stringResource(app.otakureader.feature.more.R.string.more_scan_library_retry))
                            }
                        }
                        else -> {
                            Text(
                                text = stringResource(app.otakureader.feature.more.R.string.more_scan_library_found),
                                style = MaterialTheme.typography.headlineSmall
                            )
                            Text(
                                text = stringResource(
                                    app.otakureader.feature.more.R.string.more_scan_library_ready_to_import,
                                    localLibrary.manga.size
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.weight(1f))
                            Button(
                                onClick = { viewModel.importLibrary(localLibrary) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(app.otakureader.feature.more.R.string.more_scan_library_add_to_library))
                            }
                            Button(
                                onClick = {
                                    scannedLibrary = null
                                    error = null
                                    cameraLauncher.launch(Manifest.permission.CAMERA)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(app.otakureader.feature.more.R.string.more_scan_library_scan_another))
                            }
                        }
                    }
                }
                else -> {
                    Text(
                        text = stringResource(app.otakureader.feature.more.R.string.more_scan_library_prompt),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

private fun qrScanOptions(): ScanOptions = ScanOptions().apply {
    setDesiredBarcodeFormats(ScanOptions.QR_CODE)
    setPrompt("Scan a library QR code")
    setCameraId(0)
    setBeepEnabled(false)
    setBarcodeImageEnabled(false)
}
