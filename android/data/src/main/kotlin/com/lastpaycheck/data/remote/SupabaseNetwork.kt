package com.lastpaycheck.data.remote

import com.lastpaycheck.data.catalog.DefaultCatalogRepository
import com.lastpaycheck.data.remote.error.RemoteCalls
import com.lastpaycheck.data.remote.interceptor.SupabaseHeaders
import com.lastpaycheck.data.remote.interceptor.TokenProvider
import com.lastpaycheck.domain.repository.CatalogManifestRepository
import java.util.concurrent.TimeUnit
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

class SupabaseNetwork(
    baseUrl: String,
    publishableKey: String?,
    tokenProvider: TokenProvider,
    hasConnection: () -> Boolean,
    debugLog: ((String) -> Unit)? = null
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val base = baseUrl.toHttpUrl()
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .callTimeout(25, TimeUnit.SECONDS)
        .followRedirects(false)
        .followSslRedirects(false)
        .retryOnConnectionFailure(false)
        .addInterceptor(SupabaseHeaders(base, publishableKey, tokenProvider))
        .apply {
            if (debugLog != null) {
                addInterceptor { chain ->
                    val request = chain.request()
                    val response = chain.proceed(request)
                    debugLog("${request.method} HTTP ${response.code}")
                    response
                }
            }
        }
        .build()
    private val retrofit = Retrofit.Builder()
        .baseUrl(base)
        .client(httpClient)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    internal val calls = RemoteCalls(json, hasConnection)
    internal val saveApi: SaveApi by lazy { retrofit.create(SaveApi::class.java) }
    val catalogRepository: CatalogManifestRepository by lazy {
        DefaultCatalogRepository(retrofit.create(CatalogApi::class.java), calls)
    }
}
