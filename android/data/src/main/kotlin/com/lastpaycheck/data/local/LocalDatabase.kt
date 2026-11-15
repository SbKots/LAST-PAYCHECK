package com.lastpaycheck.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [CatalogRow::class, SnapshotRow::class, TransactionRow::class, ReceiptRow::class],
    version = 2
)
internal abstract class LocalDatabase : RoomDatabase() {
    abstract fun catalog(): CatalogDao
    abstract fun sessions(): SessionDao

    companion object {
        val migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE catalog_manifest ADD COLUMN refreshedAtMillis INTEGER")
            }
        }

        fun open(context: Context, name: String) =
            Room.databaseBuilder(context, LocalDatabase::class.java, name)
                .addMigrations(migration1To2)
                .build()
    }
}
