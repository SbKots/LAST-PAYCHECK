package lastpay

import lastpay.api.catalogModule
import lastpay.application.CatalogService
import lastpay.domain.*
import lastpay.infrastructure.*
import lastpay.security.RequestLimiter
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.server.testing.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.junit.jupiter.params.provider.ValueSource

class RoutesTest {
    private val config=Configuration("jdbc:postgresql://127.0.0.1:1/unused","","","test-token")
    private fun ApplicationTestBuilder.setup(scope: String="catalog:write") {
        application { catalogModule(CatalogService(CatalogDatabase(config)),config.copy(adminScope=scope)) }
    }
    @ParameterizedTest
    @CsvSource(
        "GET,/occupations,400", "GET,/skills,400", "GET,/jobs,400", "GET,/inflation,400",
        "GET,/cars/brands,400", "GET,/cars/models,400", "GET,/tracks,400", "GET,/race-configurations,400",
        "GET,/occupations/bad,400", "GET,/cars/models/bad,400", "GET,/tracks/bad,400",
        "POST,/catalog,405",
        "POST,/admin/catalogs,401", "GET,/admin/catalogs/v/skills,401", "POST,/admin/catalogs/v/skills,401",
        "GET,/admin/catalogs/v/skills/s,401", "PUT,/admin/catalogs/v/skills/s,401", "DELETE,/admin/catalogs/v/skills/s,401",
        "POST,/admin/catalogs/v/publish,401",
        "GET,/integrations/worldbank/inflation,400", "GET,/integrations/onet/occupations?unexpected=1,400",
        "GET,/integrations/onet/skills/bad,422", "GET,/integrations/cars/brands?page=0,422",
        "GET,/integrations/cars/models?brandId=!,422"
    )
    fun negativeForEveryOperation(method: String,path: String,status: Int) = testApplication {
        setup()
        val response=client.request("/api/v1$path") { this.method=HttpMethod.parse(method) }
        assertEquals(status,response.status.value)
        val error=json.parseToJsonElement(response.bodyAsText()).jsonObject.getValue("error").jsonObject
        assertEquals(setOf("code","message","requestId","details"),error.keys)
        assertTrue(error.getValue("requestId").jsonPrimitive.content.isNotBlank())
        assertTrue(error.getValue("details") is JsonArray)
        if(status==401) assertTrue(response.headers[HttpHeaders.WWWAuthenticate]!!.startsWith("Bearer"))
        if(status==405) assertEquals("GET",response.headers[HttpHeaders.Allow])
    }
    @Test fun forbiddenScope() = testApplication {
        setup("catalog:read")
        assertEquals(403,client.post("/api/v1/admin/catalogs") { bearerAuth("test-token") }.status.value)
    }
    @Test fun invalidBearer() = testApplication {
        setup()
        assertEquals(401,client.post("/api/v1/admin/catalogs") { bearerAuth("wrong") }.status.value)
    }
    @ParameterizedTest @ValueSource(strings=["{", "[]", "null"])
    fun malformedJson(body: String) = testApplication {
        setup()
        assertEquals(400,client.post("/api/v1/admin/catalogs") { bearerAuth("test-token");contentType(ContentType.Application.Json);setBody(body) }.status.value)
    }
    @Test fun unsupportedContentType() = testApplication {
        setup()
        assertEquals(400,client.post("/api/v1/admin/catalogs") { bearerAuth("test-token");setBody("{}") }.status.value)
    }
    @Test fun oversizedJson() = testApplication {
        setup()
        assertEquals(400,client.post("/api/v1/admin/catalogs") { bearerAuth("test-token");contentType(ContentType.Application.Json);setBody(" ".repeat(1048577)) }.status.value)
    }
    @Test fun duplicateQuery() = testApplication {
        setup()
        assertEquals(400,client.get("/api/v1/skills?catalogVersion=v&catalogVersion=v").status.value)
    }
    @Test fun disabledProvider() = testApplication {
        setup()
        val response=client.get("/api/v1/integrations/onet/occupations")
        assertEquals(503,response.status.value);assertEquals("60",response.headers[HttpHeaders.RetryAfter])
    }
    @Test fun rateLimit() = testApplication {
        setup()
        repeat(30) { assertEquals(400,client.get("/api/v1/integrations/worldbank/inflation").status.value) }
        val response=client.get("/api/v1/integrations/worldbank/inflation")
        assertEquals(429,response.status.value);assertEquals("60",response.headers[HttpHeaders.RetryAfter])
    }
    @Test fun unknownRoute() = testApplication { setup();assertEquals(404,client.get("/api/v1/unknown").status.value) }
}

class ProviderTest {
    @ParameterizedTest
    @CsvSource("401,AUTH", "403,AUTH", "404,NO_DATA", "500,UNAVAILABLE", "429,UNAVAILABLE", "302,UNAVAILABLE")
    fun mapsUpstreamErrors(status: Int, reason: String) {
        HttpClient(MockEngine { respond("{}",HttpStatusCode.fromValue(status)) }).use { client ->
            val error=assertThrows(ProviderException::class.java) { runBlocking { OnetProvider(client,"fake-key").fetch() } }
            assertEquals(ProviderFailure.valueOf(reason),error.reason)
        }
    }
    @Test fun invalidUpstreamJson() {
        HttpClient(MockEngine { respond("{") }).use { client ->
            assertEquals(ProviderFailure.INVALID,assertThrows(ProviderException::class.java) { runBlocking { OnetProvider(client,"fake-key").fetch() } }.reason)
        }
    }
    @Test fun credentialsNeverCached() {
        HttpClient(MockEngine { respond("""{"career":[{"code":"1","title":"fake-key"}]}""") }).use { client ->
            assertEquals(ProviderFailure.INVALID,assertThrows(ProviderException::class.java) { runBlocking { OnetProvider(client,"fake-key").fetch() } }.reason)
        }
    }
    @Test fun onetValidPayloadAndHeader() {
        HttpClient(MockEngine { request ->
            assertEquals("fake-key",request.headers["X-API-Key"])
            assertFalse(request.url.toString().contains("fake-key"))
            respond("""{"career":[{"code":"53-7062.00","title":"Warehouse"}],"total":1}""")
        }).use { client -> assertTrue(runBlocking { OnetProvider(client,"fake-key").fetch() } is JsonObject) }
    }
    @Test fun rejectsTruncatedOnet() {
        HttpClient(MockEngine { respond("{}") }).use { client ->
            assertEquals(ProviderFailure.INVALID,assertThrows(ProviderException::class.java) {
                OnetProvider(client,"fake").validate(json.parseToJsonElement("""{"career":[{"code":"a","title":"b"}],"total":2}"""))
            }.reason)
        }
    }
    @Test fun rejectsEmptyCars() {
        HttpClient(MockEngine { respond("{}") }).use { client ->
            assertEquals(ProviderFailure.NO_DATA,assertThrows(ProviderException::class.java) { CarsBaseProvider(client,"fake").validate(json.parseToJsonElement("""{"data":[]}""")) }.reason)
        }
    }
}


