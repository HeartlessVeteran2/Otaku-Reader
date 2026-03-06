package app.komikku.feature.reader.navigation

import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import app.komikku.core.navigation.KomikkuDestinations
import app.komikku.feature.reader.ReaderScreen

fun NavGraphBuilder.readerScreen(
    onBackClick: () -> Unit,
) {
    composable<KomikkuDestinations.ReaderRoute> {
        ReaderScreen(onBackClick = onBackClick)
    }
}
