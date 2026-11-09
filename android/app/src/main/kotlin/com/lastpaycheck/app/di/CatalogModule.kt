package com.lastpaycheck.app.di

import com.lastpaycheck.data.catalog.CatalogVersionDataSource
import com.lastpaycheck.data.catalog.DefaultCatalogRepository
import com.lastpaycheck.data.catalog.DemoCatalogVersionDataSource
import com.lastpaycheck.domain.catalog.CatalogRepository
import com.lastpaycheck.domain.catalog.GetCatalogVersion
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object CatalogModule {
    @Provides
    fun provideCatalogVersionDataSource(): CatalogVersionDataSource = DemoCatalogVersionDataSource

    @Provides
    fun provideCatalogRepository(dataSource: CatalogVersionDataSource): CatalogRepository =
        DefaultCatalogRepository(dataSource)

    @Provides
    fun provideGetCatalogVersion(repository: CatalogRepository): GetCatalogVersion =
        GetCatalogVersion(repository)
}
