package app.komikku.core.navigation

sealed interface Destination {
    val route: String
}

data object LibraryDestination : Destination { override val route: String = "library" }
data object ReaderDestination : Destination { override val route: String = "reader" }
data object BrowseDestination : Destination { override val route: String = "browse" }
data object UpdatesDestination : Destination { override val route: String = "updates" }
data object HistoryDestination : Destination { override val route: String = "history" }
data object SettingsDestination : Destination { override val route: String = "settings" }
