package com.lastpaycheck.data.local

import android.content.Context
import androidx.datastore.preferences.preferencesDataStore
import com.lastpaycheck.data.remote.SupabaseNetwork
import com.lastpaycheck.domain.repository.CatalogManifestRepository
import com.lastpaycheck.domain.repository.GameSessionRepository
import com.lastpaycheck.domain.repository.SettingsRepository

private val Context.settingsStore by preferencesDataStore(name = "last_paycheck_settings")

class LocalPersistence(context: Context) {
    private val appContext = context.applicationContext
    private val database by lazy { LocalDatabase.open(appContext, "last_paycheck.db") }

    val sessions: GameSessionRepository by lazy { RoomGameSessionRepository(database) }
    val settings: SettingsRepository by lazy {
        DataStoreSettingsRepository(
            appContext.settingsStore
        )
    }

    fun catalogRepository(network: SupabaseNetwork): CatalogManifestRepository =
        network.catalogRepository(RoomCatalogCache(database.catalog()))
}
