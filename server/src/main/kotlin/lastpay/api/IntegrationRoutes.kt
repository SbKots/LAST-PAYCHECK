package lastpay.api

import lastpay.domain.*
import lastpay.infrastructure.*
import io.ktor.client.HttpClient
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*

/** Public server-side proxy: credentials are never sent to Android clients. */
fun Route.integrationRoutes(
    config: Configuration,
    db: CatalogDatabase,
    client: HttpClient,
    rateCheck: (ApplicationCall) -> Unit
) {
    // Shared cache lock is retained across requests (database stores the payload).
    val cache = ProviderCache(db)

    suspend fun ApplicationCall.sendProvider(create: (HttpClient) -> JsonProvider) {
        val provider = create(client)
        attributes.put(providerNameKey, provider.provider)
        val result = try {
            withContext(Dispatchers.IO) { cache.read(provider) }
        } catch (failure: ProviderException) {
            attributes.put(providerFailureKey, failure.reason)
            throw CatalogFailure(FailureKind.UNAVAILABLE, "Внешний источник недоступен: ${failure.reason}")
        }
        result.failure?.let { attributes.put(providerFailureKey, it) }
        respond(buildJsonObject {
            put("provider", provider.provider)
            put("fromCache", result.fromCache)
            put("stale", result.stale)
            result.failure?.let { put("providerFailure", it.name) }
            put("data", result.payload)
        })
    }

    get("/integrations/worldbank/inflation") {
        rateCheck(call)
        val q = call.query(setOf("country", "yearFrom", "yearTo"), setOf("country", "yearFrom", "yearTo"))
        val country = q.getValue("country")
        if (country !in isoCountries) fail(FailureKind.VALIDATIONERROR, "Нужен ISO alpha-3 код страны", "country")
        val from = q.integer("yearFrom", min = 1900, max = 2100)
        val to = q.integer("yearTo", min = 1900, max = 2100)
        if (to < from || to - from > 9) fail(FailureKind.VALIDATIONERROR, "Допустим период до 10 лет", "yearTo")
        call.sendProvider { WorldBankProvider(it, country, from, to) }
    }
    get("/integrations/onet/occupations") {
        rateCheck(call)
        call.query(emptySet())
        val key = config.onetKey ?: fail(FailureKind.UNAVAILABLE, "O*NET не настроен на сервере")
        call.sendProvider { OnetProvider(it, key) }
    }
    get("/integrations/onet/skills/{occupationCode}") {
        rateCheck(call)
        call.query(emptySet())
        val code = call.parameters["occupationCode"] ?: fail(FailureKind.BADREQUEST, "Не указан код профессии")
        if (!Regex("^[0-9]{2}-[0-9]{4}\\.[0-9]{2}$").matches(code))
            fail(FailureKind.VALIDATIONERROR, "Неверный код профессии", "occupationCode")
        val key = config.onetKey ?: fail(FailureKind.UNAVAILABLE, "O*NET не настроен на сервере")
        call.sendProvider { OnetProvider(it, key, code) }
    }
    get("/integrations/cars/brands") {
        rateCheck(call)
        val q = call.query(setOf("page", "pageSize"))
        val page = q.integer("page", 1, 1, 1000000)
        val size = q.integer("pageSize", 1000, 1, 1000)
        val key = config.carsToken ?: fail(FailureKind.UNAVAILABLE, "CarsBase не настроен на сервере")
        call.sendProvider { CarsBaseProvider(it, key, page = page, pageSize = size) }
    }
    get("/integrations/cars/models") {
        rateCheck(call)
        val q = call.query(setOf("brandId", "page", "pageSize"))
        val id = q["brandId"]
        if (id != null && !Regex("^[A-Za-z0-9_-]{1,64}$").matches(id))
            fail(FailureKind.VALIDATIONERROR, "Неверный brandId", "brandId")
        val page = q.integer("page", 1, 1, 1000000)
        val size = q.integer("pageSize", 1000, 1, 1000)
        val key = config.carsToken ?: fail(FailureKind.UNAVAILABLE, "CarsBase не настроен на сервере")
        call.sendProvider { CarsBaseProvider(it, key, CarsResource.MODELS, id, page, size) }
    }
}
