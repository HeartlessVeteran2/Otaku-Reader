package app.komikku.feature.updates

data class UpdatesState(
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed class UpdatesEvent {
    data object OnRefresh : UpdatesEvent()
}

sealed class UpdatesEffect
