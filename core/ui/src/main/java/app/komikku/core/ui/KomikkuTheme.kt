package app.komikku.core.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

@Composable
fun KomikkuTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(content = content)
    }
}
