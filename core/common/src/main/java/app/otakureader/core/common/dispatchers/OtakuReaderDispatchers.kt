package app.otakureader.core.common.dispatchers

import javax.inject.Qualifier

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Dispatcher(val otakuReaderDispatcher: OtakuReaderDispatcher)

enum class OtakuReaderDispatcher {
    Default,
    IO,
    Main,
}
