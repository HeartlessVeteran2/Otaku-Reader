package app.otakureader

import androidx.lifecycle.ViewModel
import app.otakureader.core.webview.WebViewChallengeManager
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

/**
 * Carries the singleton [WebViewChallengeManager] into the navigation host.
 *
 * The nav host is a composable, so it cannot take a constructor injection, and the challenge
 * collector has to live there — it is the only place that can navigate. A one-field ViewModel is
 * the smallest way to reach the singleton from composition without threading it through every
 * caller of `OtakuReaderNavHost`.
 *
 * The manager itself is `@Singleton`; this holds a reference and owns nothing, so the ViewModel
 * being recreated does not disturb an in-flight challenge.
 */
@HiltViewModel
class ChallengeHostViewModel @Inject constructor(
    val manager: WebViewChallengeManager,
) : ViewModel()
