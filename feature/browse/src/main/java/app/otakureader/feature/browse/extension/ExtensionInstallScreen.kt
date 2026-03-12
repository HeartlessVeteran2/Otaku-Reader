package app.otakureader.feature.browse.extension

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import app.otakureader.domain.repository.SourceRepository
import app.otakureader.feature.browse.R
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for ExtensionInstallScreen.
 */
@HiltViewModel
class ExtensionInstallViewModel @Inject constructor(
    private val sourceRepository: SourceRepository
) : ViewModel() {

    suspend fun installFromUrl(url: String): Result<Unit> =
        sourceRepository.loadExtensionFromUrl(url)

    suspend fun installFromFile(path: String): Result<Unit> =
        sourceRepository.loadExtension(path)
}

/**
 * Screen for installing Tachiyomi extensions from URL or file path.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtensionInstallScreen(
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExtensionInstallViewModel = hiltViewModel()
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var urlText by remember { mutableStateOf("") }
    var filePath by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var installResult by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Install Extension") },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.extensions_install_back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // URL Installation Section
            Text(
                text = "Install from URL",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = urlText,
                onValueChange = { urlText = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Extension URL") },
                placeholder = { Text("https://example.com/extension.apk") },
                singleLine = true
            )

            Button(
                onClick = {
                    if (urlText.isNotBlank()) {
                        scope.launch {
                            isLoading = true
                            val result = viewModel.installFromUrl(urlText)
                            isLoading = false
                            result.onSuccess {
                                installResult = "Extension installed successfully!"
                                snackbarHostState.showSnackbar("Extension installed successfully!")
                                urlText = ""
                            }.onFailure { error ->
                                installResult = "Error: ${error.message}"
                                snackbarHostState.showSnackbar("Error: ${error.message}")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && urlText.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.Download, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Download & Install")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File Installation Section
            Text(
                text = "Install from File",
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = filePath,
                onValueChange = { filePath = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("APK File Path") },
                placeholder = { Text("/storage/emulated/0/Download/extension.apk") },
                singleLine = true
            )

            Button(
                onClick = {
                    if (filePath.isNotBlank()) {
                        scope.launch {
                            isLoading = true
                            val result = viewModel.installFromFile(filePath)
                            isLoading = false
                            result.onSuccess {
                                installResult = "Extension installed successfully!"
                                snackbarHostState.showSnackbar("Extension installed successfully!")
                                filePath = ""
                            }.onFailure { error ->
                                installResult = "Error: ${error.message}"
                                snackbarHostState.showSnackbar("Error: ${error.message}")
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading && filePath.isNotBlank()
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.height(24.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(Icons.Default.FileOpen, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Install from File")
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Result Display
            installResult?.let { result ->
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (result.startsWith("Error")) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.primary
                    }
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Instructions
            Text(
                text = "Instructions",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "1. To install a Tachiyomi extension, you need the APK file.\n\n" +
                        "2. You can download extensions from:\n" +
                        "   • Suwayomi repository\n" +
                        "   • Tachiyomi extensions archive\n\n" +
                        "3. Enter the URL or local file path above.\n\n" +
                        "4. The extension will be loaded and its sources will appear in Browse.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Sample extension URLs for testing.
 */
object SampleExtensionUrls {
    // These are example URLs - replace with actual extension URLs
    const val MANGADEX = "https://github.com/tachiyomiorg/tachiyomi-extensions/raw/apk/repo/eu.kanade.tachiyomi.extension.all.mangadex.apk"
    const val MANGAPLUS = "https://github.com/tachiyomiorg/tachiyomi-extensions/raw/apk/repo/eu.kanade.tachiyomi.extension.all.mangaplus.apk"
    const val COMICK = "https://github.com/tachiyomiorg/tachiyomi-extensions/raw/apk/repo/eu.kanade.tachiyomi.extension.all.comick.apk"
}
