package app.otakureader.feature.about.developer

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.otakureader.feature.about.R

/**
 * Passphrase prompt for the hidden developer screen.
 *
 * The field is masked for the ordinary reason — someone may be looking over your shoulder — not
 * because the value is a real credential. `DeveloperUnlock` is explicit that the gate is
 * obscurity; masking here just keeps it obscure in the moment it is typed.
 */
@Composable
fun DeveloperPromptDialog(
    state: AboutDeveloperState,
    onSubmit: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (!state.isPromptVisible) return

    var input by remember { mutableStateOf("") }
    val isUnavailable = state.error == AboutDeveloperError.NotConfigured

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.about_developer_prompt_title)) },
        text = {
            Column {
                if (!isUnavailable) {
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        label = { Text(stringResource(R.string.about_developer_prompt_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        // Masking alone only hides the glyphs. Without the password keyboard type
                        // the IME still treats this as ordinary prose: it offers suggestions and
                        // can learn the passphrase into the user's personal dictionary, where it
                        // then surfaces in unrelated text fields.
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        isError = state.error == AboutDeveloperError.WrongPassphrase,
                    )
                }
                val message = when (state.error) {
                    AboutDeveloperError.WrongPassphrase -> stringResource(R.string.about_developer_wrong)
                    AboutDeveloperError.NotConfigured -> stringResource(R.string.about_developer_unavailable)
                    null -> null
                }
                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            // Omitted entirely when unconfigured: a disabled Unlock button would invite repeated
            // tapping at something that cannot succeed in this build.
            if (!isUnavailable) {
                TextButton(
                    onClick = { onSubmit(input) },
                    enabled = input.isNotEmpty(),
                ) {
                    Text(stringResource(R.string.about_developer_prompt_unlock))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.about_developer_prompt_cancel))
            }
        },
    )
}
