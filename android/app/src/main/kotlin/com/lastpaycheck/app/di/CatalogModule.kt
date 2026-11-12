package com.lastpaycheck.app.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.lastpaycheck.app.BuildConfig
import com.lastpaycheck.data.remote.SupabaseNetwork
import com.lastpaycheck.data.remote.interceptor.TokenProvider
import com.lastpaycheck.domain.catalog.GetCatalogVersion
import com.lastpaycheck.domain.repository.CatalogManifestRepository
import com.lastpaycheck.domain.usecase.GetCatalogManifest
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    fun provideTokenProvider(): TokenProvider = TokenProvider { null }

    @Provides
    @Singleton
    fun provideSupabaseNetwork(
        @ApplicationContext context: Context,
        tokenProvider: TokenProvider
    ): SupabaseNetwork {
        val connectivity = context.getSystemService(ConnectivityManager::class.java)
        return SupabaseNetwork(
            baseUrl = "https://mmuytwyhdwttzxuredfm.supabase.co/",
            publishableKey = BuildConfig.SUPABASE_PUBLISHABLE_KEY.ifEmpty { null },
            tokenProvider = tokenProvider,
            hasConnection = {
                connectivity?.getNetworkCapabilities(connectivity.activeNetwork)
                    ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            },
            debugLog = if (BuildConfig.DEBUG) {
                (
                    { message ->
                        Log.d(
                            "SupabaseNetwork",
                            message
                        )
                    }
                    )
            } else {
                null
            }
        )
    }

    @Provides
    fun provideCatalogRepository(network: SupabaseNetwork): CatalogManifestRepository =
        network.catalogRepository

    @Provides
    fun provideGetCatalogVersion(repository: CatalogManifestRepository): GetCatalogVersion =
        GetCatalogVersion(repository)

    @Provides
    fun provideGetCatalogManifest(repository: CatalogManifestRepository): GetCatalogManifest =
        GetCatalogManifest(repository)
}
