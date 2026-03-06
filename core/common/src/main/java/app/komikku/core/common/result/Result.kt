package app.komikku.core.common.result

sealed class Result<out T> {
    data class Success<T>(val data: T) : Result<T>()
    data class Error(val exception: Throwable) : Result<Nothing>()
    data object Loading : Result<Nothing>()
}

val Result<*>.isSuccess: Boolean get() = this is Result.Success
val Result<*>.isError: Boolean get() = this is Result.Error
val Result<*>.isLoading: Boolean get() = this is Result.Loading

fun <T> Result<T>.getOrNull(): T? = if (this is Result.Success) data else null

fun <T, R> Result<T>.map(transform: (T) -> R): Result<R> = when (this) {
    is Result.Success -> Result.Success(transform(data))
    is Result.Error -> this
    is Result.Loading -> this
}
