package com.lastpaycheck.domain.catalog

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class GetCatalogVersionTest {
    @Test
    fun returnsCurrentVersionFromRepository() = runTest {
        var version = "demo-1"
        val repository = object : CatalogRepository {
            override suspend fun getVersion(): String = version
        }
        val getCatalogVersion = GetCatalogVersion(repository)

        assertEquals("demo-1", getCatalogVersion())
        version = "demo-2"
        assertEquals("demo-2", getCatalogVersion())
    }

    @Test
    fun propagatesRepositoryFailure() = runTest {
        val failure = IllegalStateException("Catalog is unavailable")
        val repository = object : CatalogRepository {
            override suspend fun getVersion(): String = throw failure
        }

        val result = runCatching { GetCatalogVersion(repository)() }

        assertSame(failure, result.exceptionOrNull())
    }
}
