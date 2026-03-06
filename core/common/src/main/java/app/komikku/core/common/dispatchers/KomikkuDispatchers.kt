package app.komikku.core.common.dispatchers

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val komikkuDispatcher: KomikkuDispatcher)

enum class KomikkuDispatcher {
    Default,
    IO,
    Main,
}
