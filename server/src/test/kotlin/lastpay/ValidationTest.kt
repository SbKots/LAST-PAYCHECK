package lastpay

import lastpay.api.*
import lastpay.domain.*
import lastpay.security.*
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.*

class ValidationTest {
    private fun failure(kind: FailureKind, block: () -> Unit): CatalogFailure =
        assertThrows(CatalogFailure::class.java, block).also { assertEquals(kind, it.kind) }
    private fun body(extra: String = "") = json.parseToJsonElement("""{"id":"skill-1","code":"skill-1","title":"Навык","description":"","source":"DEMO","externalId":null$extra}""").jsonObject

    @Test fun identifierBoundary() { assertEquals("a".repeat(64), validId("a".repeat(64))) }
    @Test fun versionBoundary() { assertEquals("a".repeat(32), validId("a".repeat(32), true)) }
    @ParameterizedTest @ValueSource(strings=["", "UPPER", "../etc", "a b", "-bad", "навык", "a\n"])
    fun invalidIdentifiers(value: String) { failure(FailureKind.BADREQUEST) { validId(value) } }
    @Test fun tooLongId() { failure(FailureKind.BADREQUEST) { validId("a".repeat(65)) } }
    @Test fun tooLongVersion() { failure(FailureKind.BADREQUEST) { validId("a".repeat(33), true) } }
    @Test fun integerDefaults() { assertEquals(20, emptyMap<String,String>().integer("pageSize",20,1,100)) }
    @Test fun missingInteger() { failure(FailureKind.BADREQUEST) { emptyMap<String,String>().integer("page",min=1,max=100) } }
    @ParameterizedTest @ValueSource(strings=["1.0", "+1", " 1", "1e2", "", "abc"])
    fun malformedInteger(value: String) { failure(FailureKind.BADREQUEST) { mapOf("page" to value).integer("page",min=1,max=100) } }
    @ParameterizedTest @ValueSource(strings=["0", "101", "9999999999999999999999999999999999"])
    fun integerRange(value: String) { failure(FailureKind.VALIDATIONERROR) { mapOf("page" to value).integer("page",min=1,max=100) } }
    @Test fun integerEdges() { for(n in listOf(1,100)) assertEquals(n,mapOf("page" to "$n").integer("page",min=1,max=100)) }
    @Test fun validSkill() { assertEquals(SkillInput("skill-1","skill-1","Навык","","DEMO",null),skillInput(body(),null)) }
    @Test fun extraJsonField() { failure(FailureKind.VALIDATIONERROR) { skillInput(body(",\"extra\":1"),null) } }
    @Test fun missingJsonField() { failure(FailureKind.VALIDATIONERROR) { skillInput(JsonObject(body()-"title"),null) } }
    @Test fun updateUsesPathId() { assertEquals("path-id",skillInput(JsonObject(body()-"id"),"path-id").id) }
    @Test fun onetRequiresExternalId() { failure(FailureKind.VALIDATIONERROR) { skillInput(JsonObject(body()+("source" to JsonPrimitive("ONET"))),null) } }
    @Test fun rejectsUnknownSource() { failure(FailureKind.VALIDATIONERROR) { skillInput(JsonObject(body()+("source" to JsonPrimitive("OTHER"))),null) } }
    @Test fun rejectsNumericText() { failure(FailureKind.BADREQUEST) { buildJsonObject { put("title",42) }.text("title",160) } }
    @Test fun rejectsNullRequiredText() { failure(FailureKind.BADREQUEST) { buildJsonObject { put("title",JsonNull) }.text("title",160) } }
    @Test fun allowsNullableText() { assertNull(buildJsonObject { put("x",JsonNull) }.text("x",1,nullable=true)) }
    @Test fun rejectsWhitespace() { failure(FailureKind.VALIDATIONERROR) { buildJsonObject { put("x"," \t") }.text("x",10) } }
    @Test fun rejectsNullCharacter() { failure(FailureKind.VALIDATIONERROR) { buildJsonObject { put("x","a\u0000") }.text("x",10) } }
    @Test fun unicodeCountsCodePoints() { assertEquals("😀😀",buildJsonObject { put("x","😀😀") }.text("x",2)) }
    @Test fun stringLengthLimit() { failure(FailureKind.VALIDATIONERROR) { buildJsonObject { put("x","😀😀😀") }.text("x",2) } }
    @Test fun errorRetainsField() { val e=failure(FailureKind.VALIDATIONERROR) { fail(FailureKind.VALIDATIONERROR,"bad","title") };assertEquals(listOf(FieldIssue("title","bad")),e.issues) }
    @Test fun globalErrorHasNoFields() { assertTrue(failure(FailureKind.NOTFOUND) { fail(FailureKind.NOTFOUND,"missing") }.issues.isEmpty()) }
    @Test fun canonicalSortAndDecimal() { assertEquals("""{"a":1,"b":[true,null,0.01]}""",canonicalJson(json.parseToJsonElement("""{"b":[true,null,1e-2],"a":1.00}"""))) }
    @Test fun canonicalPreservesArrayOrder() { assertNotEquals(canonicalJson(json.parseToJsonElement("[1,2]")),canonicalJson(json.parseToJsonElement("[2,1]"))) }
    @Test fun hashKnownVector() { assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",sha256("abc".toByteArray())) }
    @Test fun revisionEtag() { assertEquals("\"rev-42\"",CatalogRevision("v1",42).etag) }
    @Test fun countrySet() { assertTrue("RUS" in isoCountries);assertFalse("WLD" in isoCountries) }
}

class SecurityTest {
    private val access=AdminAccess("isolated-test-token")
    @Test fun acceptsBearerCaseInsensitive() { assertDoesNotThrow { access.verify("bEaReR isolated-test-token") } }
    @ParameterizedTest @ValueSource(strings=["", "Basic isolated-test-token", "Bearer wrong", "Bearer isolated-test-token ", "Bearer a b"])
    fun rejectsInvalidToken(header: String) { assertEquals(FailureKind.UNAUTHORIZED,assertThrows(CatalogFailure::class.java) { access.verify(header) }.kind) }
    @Test fun missingBearer() { assertEquals(FailureKind.UNAUTHORIZED,assertThrows(CatalogFailure::class.java) { access.verify(null) }.kind) }
    @Test fun insufficientScope() { assertEquals(FailureKind.FORBIDDEN,assertThrows(CatalogFailure::class.java) { AdminAccess("t","catalog:read").verify("Bearer t") }.kind) }
    @Test fun limitBoundaryAndIsolation() {
        val limiter=RequestLimiter(Clock.fixed(Instant.EPOCH,ZoneOffset.UTC))
        repeat(2) { limiter.check("a",2) }
        assertEquals(FailureKind.RATELIMITED,assertThrows(CatalogFailure::class.java) { limiter.check("a",2) }.kind)
        assertDoesNotThrow { limiter.check("b",2) }
    }
    @Test fun resetsAtNextMinute() {
        var now=Instant.EPOCH
        val clock=object: Clock() {
            override fun getZone()=ZoneOffset.UTC
            override fun withZone(zone: ZoneId): Clock=this
            override fun instant()=now
        }
        val limiter=RequestLimiter(clock);limiter.check("a",1)
        now=Instant.ofEpochSecond(60)
        assertDoesNotThrow { limiter.check("a",1) }
    }
}
