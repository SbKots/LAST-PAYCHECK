package com.lastpaycheck.data.local

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Upsert

@Entity(tableName = "catalog_manifest")
internal data class CatalogRow(
    @PrimaryKey val slot: Int,
    val payload: String,
    val refreshedAtMillis: Long?
)

@Dao
internal interface CatalogDao {
    @Query("SELECT * FROM catalog_manifest WHERE slot = 0")
    suspend fun read(): CatalogRow?

    @Upsert
    suspend fun write(row: CatalogRow)
}
