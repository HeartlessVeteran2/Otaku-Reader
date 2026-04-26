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
import java.io.File
import java.net.URI
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
    var urlError by remember { mutableStateOf<String?>(null) }
    var fileError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.extensions_install_title)) },
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
                text = stringResource(R.string.extensions_install_from_url_section),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = urlText,
                onValueChange = {
                    urlText = it
                    urlError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.extensions_install_url_label)) },
                placeholder = { Text(stringResource(R.string.extensions_install_url_placeholder)) },
                singleLine = true,
                isError = urlError != null,
                supportingText = urlError?.let { { Text(it) } }
            )

            Button(
                onClick = {
                    val trimmed = urlText.trim()
                    val validationError = validateApkUrl(trimmed)
                    if (validationError != null) {
                        urlError = validationError
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        val result = viewModel.installFromUrl(trimmed)
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
                    Icon(Icons.Default.Download, contentDescription = "Download")
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.extensions_install_from_url))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // File Installation Section
            Text(
                text = stringResource(R.string.extensions_install_from_file_section),
                style = MaterialTheme.typography.titleMedium
            )

            OutlinedTextField(
                value = filePath,
                onValueChange = {
                    filePath = it
                    fileError = null
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.extensions_install_file_label)) },
                placeholder = { Text(stringResource(R.string.extensions_install_file_placeholder)) },
                singleLine = true,
                isError = fileError != null,
                supportingText = fileError?.let { { Text(it) } }
            )

            Button(
                onClick = {
                    val trimmed = filePath.trim()
                    val validationError = validateApkFile(trimmed)
                    if (validationError != null) {
                        fileError = validationError
                        return@Button
                    }
                    scope.launch {
                        isLoading = true
                        val result = viewModel.installFromFile(trimmed)
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
                    Icon(Icons.Default.FileOpen, contentDescription = "Open file")
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text(stringResource(R.string.extensions_install_from_file))
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

private fun validateApkUrl(url: String): String? {
    if (url.isBlank()) return "URL cannot be empty"
    if (!url.startsWith("https://")) return "URL must use HTTPS"
    if (!url.endsWith(".apk")) return "URL must point to an .apk file"
    return runCatching { URI(url).toURL() }.fold(
        onSuccess = { null },
        onFailure = { "Invalid URL format" }
    )
}

private fun validateApkFile(path: String): String? {
    if (path.isBlank()) return "File path cannot be empty"
    val file = File(path)
    if (!file.exists()) return "File does not exist"
    if (!file.isFile) return "Path is not a file"
    if (file.extension.lowercase() != "apk") return "File must be an .apk"
    return null
}

/**
 * Sample extension URLs for testing.
 *
 * **SECURITY WARNING (C-4):** Hardcoded APK URLs pointing to a third-party GitHub
 * repository have been removed. If that repository were ever compromised, users
 * would silently download malicious APKs.
 *
 * Extension sources should be fetched from a secure, signed, and updatable remote
 * repository configuration (e.g. a JSON index served over HTTPS from a domain you
 * control, with a pinned certificate or signed payload). Do NOT re-add hardcoded
 * APK URLs here.
 *
 * For development and testing, supply URLs via the UI input field at runtime.
 */
@Deprecated(
    message = "Hardcoded APK URLs are a security risk (audit finding C-4). " +
        "Fetch extension sources from a signed remote configuration instead.",
    level = DeprecationLevel.ERROR
)
object SampleExtensionUrls
