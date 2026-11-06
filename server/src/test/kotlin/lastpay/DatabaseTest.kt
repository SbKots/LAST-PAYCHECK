package lastpay

import lastpay.application.CatalogService
import lastpay.domain.*
import lastpay.infrastructure.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable
import java.time.*
import java.util.UUID

@EnabledIfEnvironmentVariable(named="LASTPAY_TEST_DATABASE",matches="true")
class DatabaseTest {
    private val config=Configuration.load().also {
        require(Regex("jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/lastpay_test_[a-z0-9_]+").matches(it.dbUrl))
    }
    private val db=CatalogDatabase(config)
    private val service=CatalogService(db)
    private fun draft(): String = ("junit-"+UUID.randomUUID().toString().take(16)).also { service.createDraft(it,"demo-1") }
    @Test fun migrationsAndRestrictedRoles() {
        db.checkRuntimeAccess()
        assertEquals(3L,db.maintenanceConnection().use { c -> c.createStatement().use { s -> s.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success").use { r -> r.next();r.getLong(1) } } })
    }
    @Test fun readerCannotSeeDraft() { val v=draft();assertEquals(0L,db.read { count("SELECT count(*) FROM catalog_version WHERE version=?",v) }) }
    @Test fun readerCannotWrite() { assertThrows(Exception::class.java) { db.read { execute("DELETE FROM skill WHERE catalog_version='demo-1'") } } }
    @Test fun publishedRowsImmutable() { assertEquals(0,db.tx { execute("UPDATE skill SET title='changed' WHERE catalog_version='demo-1'") }) }
    @Test fun duplicateDraftConflict() { val v=draft();assertEquals(FailureKind.CONFLICT,assertThrows(CatalogFailure::class.java) { service.createDraft(v,"demo-1") }.kind) }
    @Test fun referencedSkillConflict() { val v=draft();assertEquals(FailureKind.CONFLICT,assertThrows(CatalogFailure::class.java) { service.deleteSkill(v,"logistics","\"rev-1\"") }.kind) }
    @Test fun staleEtag() { val v=draft();assertEquals(FailureKind.PRECONDITIONFAILED,assertThrows(CatalogFailure::class.java) { service.deleteSkill(v,"logistics","\"rev-2\"") }.kind) }
    @Test fun sqlCheckConstraintTranslated() { val v=draft();assertEquals(FailureKind.VALIDATIONERROR,assertThrows(CatalogFailure::class.java) { db.tx { execute("UPDATE skill SET title='' WHERE catalog_version=?",v) } }.kind) }
    @Test fun failedTransactionRollsBack() { val v=draft();val before=service.detail("skills",v,"logistics",true).body;assertThrows(CatalogFailure::class.java) { db.tx { execute("UPDATE skill SET title='' WHERE catalog_version=?",v) } };assertEquals(before,service.detail("skills",v,"logistics",true).body) }
    @Test fun cacheStoresAndReusesSuccess() = runBlocking {
        var calls=0
        val provider=object: JsonProvider {
            override val provider="ONET";override val cacheKey=UUID.randomUUID().toString();override val ttlDays=7L
            override suspend fun fetch(): JsonElement { calls++;return buildJsonObject { put("test",true) } }
            override fun validate(payload: JsonElement) { require(payload is JsonObject) }
        }
        val cache=ProviderCache(db,pause={})
        assertFalse(cache.read(provider).fromCache);assertTrue(cache.read(provider).fromCache);assertEquals(1,calls)
    }
    @Test fun cacheFallsBackWithoutOverwritingSuccess() = runBlocking {
        var failing=false;var calls=0
        val provider=object: JsonProvider {
            override val provider="ONET";override val cacheKey=UUID.randomUUID().toString();override val ttlDays=7L
            override suspend fun fetch(): JsonElement { calls++;if(failing) throw ProviderException(ProviderFailure.TIMEOUT);return buildJsonObject { put("test",true) } }
            override fun validate(payload: JsonElement) { require(payload is JsonObject) }
        }
        val cache=ProviderCache(db,pause={});val first=cache.read(provider);failing=true
        val stale=cache.read(provider,forceRefresh=true)
        assertTrue(stale.stale);assertTrue(stale.fromCache);assertEquals(ProviderFailure.TIMEOUT,stale.failure)
        assertEquals(first.payload,stale.payload);assertEquals(4,calls)
        assertFalse(cache.read(provider).stale)
    }
}
