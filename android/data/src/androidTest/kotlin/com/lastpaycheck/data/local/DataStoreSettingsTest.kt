package com.lastpaycheck.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.test.core.app.ApplicationProvider
import com.lastpaycheck.domain.model.ControlMode
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.GameSettings
import com.lastpaycheck.domain.model.ThemePreference
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DataStoreSettingsTest {
    @Test
    fun defaultSettingsAndUpdatedThemeSurviveReopening() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val file = File(context.cacheDir, "${UUID.randomUUID()}.preferences_pb")
        val defaults = GameSettings(ControlMode.TOUCH, 0.0, true, true)
        val updated = GameSettings(ControlMode.TILT, -0.3, false, false, ThemePreference.DARK)
        try {
            withStore(file) {
                assertEquals(DomainResult.Success(defaults), it.observe().first())
                assertEquals(DomainResult.Success(updated), it.update(updated))
            }
            withStore(file) {
                assertEquals(DomainResult.Success(updated), it.observe().first())
            }
        } finally {
            file.delete()
        }
    }

    @Test
    fun ioFailuresAreReportedInsteadOfReplacingPersistedPreferencesWithDefaults() = runBlocking {
        val store = object : DataStore<Preferences> {
            override val data = flow<Preferences> { throw IOException("test read failure") }

            override suspend fun updateData(
                transform: suspend (Preferences) -> Preferences
            ): Preferences = throw IOException("test write failure")
        }
        val repo = DataStoreSettingsRepository(store)
        val settings = GameSettings(ControlMode.TOUCH, 0.0, true, true)
        val failure = DomainResult.Failure(GameError.StorageFailure)
        assertEquals(failure, repo.observe().first())
        assertEquals(failure, repo.update(settings))
    }

    private suspend fun withStore(
        file: File,
        block: suspend (DataStoreSettingsRepository) -> Unit
    ) {
        val job = Job()
        val scope = CoroutineScope(Dispatchers.IO + job)
        try {
            val store = PreferenceDataStoreFactory.create(scope = scope) { file }
            block(DataStoreSettingsRepository(store))
        } finally {
            job.cancelAndJoin()
        }
    }
}
