package com.lastpaycheck.data.local

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.lastpaycheck.data.auth.ProtectedAuthStore
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ProtectedAuthStoreTest {
    @Test
    fun sessionIsEncryptedExcludedFromBackupAndCanBeReopenedAndCleared() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = ProtectedAuthStore(context)
        try {
            store.write("test-access-and-refresh")
            val bytes = File(context.noBackupFilesDir, "account-session").readBytes()
            assertFalse(bytes.decodeToString().contains("test-access-and-refresh"))
            assertEquals("test-access-and-refresh", ProtectedAuthStore(context).read())
            store.clear()
            assertNull(store.read())
        } finally {
            store.clear()
        }
    }
}
