package eu.kanade.tachiyomi.network.interceptor

import okhttp3.Interceptor
import okhttp3.Response

/** Adds the default User-Agent to requests that don't set one. */
class UserAgentInterceptor(private val userAgent: () -> String) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.header("User-Agent").isNullOrBlank()) return chain.proceed(request)
        return chain.proceed(request.newBuilder().header("User-Agent", userAgent()).build())
    }
}
