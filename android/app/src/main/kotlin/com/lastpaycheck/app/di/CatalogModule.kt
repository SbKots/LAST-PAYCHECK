package com.lastpaycheck.app.di

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.lastpaycheck.app.BuildConfig
import com.lastpaycheck.data.auth.SupabaseAccountRepository
import com.lastpaycheck.data.local.LocalPersistence
import com.lastpaycheck.data.remote.SupabaseNetwork
import com.lastpaycheck.data.remote.interceptor.TokenProvider
import com.lastpaycheck.domain.catalog.GetCatalogVersion
import com.lastpaycheck.domain.repository.AccountRepository
import com.lastpaycheck.domain.repository.CatalogManifestRepository
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.repository.SettingsRepository
import com.lastpaycheck.domain.usecase.GetAccountUseCase
import com.lastpaycheck.domain.usecase.GetCatalogManifest
import com.lastpaycheck.domain.usecase.ObserveSettingsUseCase
import com.lastpaycheck.domain.usecase.SignOutUseCase
import com.lastpaycheck.domain.usecase.SubmitAuthFormUseCase
import com.lastpaycheck.domain.usecase.UpdateSettingsUseCase
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
    @Singleton
    fun account(@ApplicationContext context: Context): AccountRepository =
        SupabaseAccountRepository(
            context,
            "https://mmuytwyhdwttzxuredfm.supabase.co/",
            BuildConfig.SUPABASE_PUBLISHABLE_KEY
        )

    @Provides
    fun getAccount(repository: AccountRepository) = GetAccountUseCase(repository)

    @Provides
    fun submitAuth(repository: AccountRepository) = SubmitAuthFormUseCase(repository)

    @Provides
    fun signOut(repository: AccountRepository) = SignOutUseCase(repository)

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
    @Singleton
    fun provideLocalPersistence(@ApplicationContext context: Context): LocalPersistence =
        LocalPersistence(context)

    @Provides
    @Singleton
    fun provideCatalogRepository(
        network: SupabaseNetwork,
        local: LocalPersistence
    ): CatalogManifestRepository = local.catalogRepository(network)

    @Provides
    fun provideSessions(local: LocalPersistence): GameSessionRepository = local.sessions

    @Provides
    fun provideSettings(local: LocalPersistence): SettingsRepository = local.settings

    @Provides
    fun provideObserveSettings(settings: SettingsRepository) = ObserveSettingsUseCase(settings)

    @Provides
    fun provideUpdateSettings(settings: SettingsRepository) = UpdateSettingsUseCase(settings)

    @Provides
    fun provideGetCatalogVersion(repository: CatalogManifestRepository): GetCatalogVersion =
        GetCatalogVersion(repository)

    @Provides
    fun provideGetCatalogManifest(repository: CatalogManifestRepository): GetCatalogManifest =
        GetCatalogManifest(repository)
}
