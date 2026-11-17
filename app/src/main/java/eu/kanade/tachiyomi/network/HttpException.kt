package eu.kanade.tachiyomi.network

/** A response OkHttp doesn't consider successful. */
class HttpException(val code: Int) : IllegalStateException("HTTP error $code")
