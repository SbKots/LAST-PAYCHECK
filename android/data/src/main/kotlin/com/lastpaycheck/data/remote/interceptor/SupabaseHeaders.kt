package com.lastpaycheck.data.remote.interceptor

import com.lastpaycheck.domain.model.RemoteFailure
import java.io.IOException
import okhttp3.HttpUrl
import okhttp3.Interceptor
import okhttp3.RequestBody
import okhttp3.Response
import okio.BufferedSink
import retrofit2.Invocation

fun interface TokenProvider {
    fun accessToken(): String?
}

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
internal annotation class ProtectedRpc

internal class RequestRejected(val failure: RemoteFailure) : IOException()

internal class SupabaseHeaders(
    private val origin: HttpUrl,
    private val publishableKey: String?,
    private val tokenProvider: TokenProvider
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.scheme != origin.scheme || request.url.host != origin.host ||
            request.url.port != origin.port
        ) {
            throw RequestRejected(RemoteFailure.MISSING_CONFIGURATION)
        }

        val builder = request.newBuilder()
            .header("Accept", "application/json")
            .removeHeader("Authorization")
            .removeHeader("apikey")
        val protected = request.tag(Invocation::class.java)?.method()
            ?.isAnnotationPresent(ProtectedRpc::class.java) == true

        if (protected) {
            val token = tokenProvider.accessToken()?.takeIf { it.matches(jwtPattern) }
                ?: throw RequestRejected(RemoteFailure.AUTH_REQUIRED)
            val key = publishableKey ?: throw RequestRejected(RemoteFailure.MISSING_CONFIGURATION)
            builder.header("apikey", key)
                .header("Authorization", "Bearer $token")
                .header("Accept-Profile", "lastpay_api")
            if (request.body != null) builder.header("Content-Profile", "lastpay_api")
        }

        val body = request.body
        if (body != null && request.method != "GET" && request.method != "HEAD") {
            builder.method(request.method, NonRetryableBody(body))
        }
        return chain.proceed(builder.build())
    }

    private class NonRetryableBody(private val delegate: RequestBody) : RequestBody() {
        override fun contentType() = delegate.contentType()
        override fun contentLength() = delegate.contentLength()
        override fun isOneShot() = true
        override fun writeTo(sink: BufferedSink) = delegate.writeTo(sink)
    }
}

private val jwtPattern = Regex("[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
