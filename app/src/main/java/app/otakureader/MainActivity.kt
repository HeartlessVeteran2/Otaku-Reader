package app.otakureader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import app.otakureader.core.navigation.OtakuReaderNavHost
import app.otakureader.core.preferences.GeneralPreferences
import app.otakureader.core.ui.theme.OtakuReaderTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var generalPreferences: GeneralPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeMode by generalPreferences.themeMode
                .collectAsStateWithLifecycle(initialValue = 0)
            val useDynamicColor by generalPreferences.useDynamicColor
                .collectAsStateWithLifecycle(initialValue = true)

            val darkTheme = when (themeMode) {
                1 -> false              // light
                2 -> true               // dark
                else -> isSystemInDarkTheme() // system default
            }

            OtakuReaderTheme(darkTheme = darkTheme, dynamicColor = useDynamicColor) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    OtakuReaderApp()
                }
            }
        }
    }
}

@Composable
fun OtakuReaderApp() {
    val navController = rememberNavController()

    Scaffold { padding ->
        OtakuReaderNavHost(
            navController = navController,
            modifier = Modifier.padding(padding)
        )
    }
}
