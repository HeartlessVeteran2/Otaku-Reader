package app.otakureader.core.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/**
 * To use [okhttp3.brotli.BrotliInterceptor] as a network interceptor,
 * add [IgnoreGzipInterceptor] right before it.
 *
 * This disables OkHttp's transparent gzip support so that both gzip and Brotli
 * are explicitly handled by [okhttp3.brotli.BrotliInterceptor].
 */
class IgnoreGzipInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (request.header("Accept-Encoding") == "gzip") {
            request = request.newBuilder().removeHeader("Accept-Encoding").build()
        }
        return chain.proceed(request)
    }
}
