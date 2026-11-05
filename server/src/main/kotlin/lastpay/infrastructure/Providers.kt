package lastpay.infrastructure

import lastpay.domain.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.utils.io.readAvailable
import java.io.ByteArrayOutputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.Duration
import java.util.UUID

fun providerClient(): HttpClient = HttpClient(CIO) {
    expectSuccess=false
    followRedirects=false
    install(HttpTimeout) { connectTimeoutMillis=5000;socketTimeoutMillis=10000;requestTimeoutMillis=15000 }
}

abstract class HttpJsonProvider(protected val client: HttpClient, private val credential: String? = null) : JsonProvider {
    private val maxResponseBytes = 2 * 1024 * 1024
    protected abstract val endpoint: String
    protected open fun HttpRequestBuilder.prepare() {}
    override suspend fun fetch(): JsonElement {
        try {
            val body = client.prepareGet(endpoint) { prepare() }.execute { response ->
                when(response.status.value) {
                    401,403 -> throw ProviderException(ProviderFailure.AUTH)
                    404 -> throw ProviderException(ProviderFailure.NO_DATA)
                    in 200..299 -> Unit
                    else -> throw ProviderException(ProviderFailure.UNAVAILABLE)
                }
                if ((response.headers["Content-Length"]?.toLongOrNull() ?: 0) > maxResponseBytes)
                    throw ProviderException(ProviderFailure.INVALID)
                val channel = response.bodyAsChannel()
                val bytes = ByteArrayOutputStream()
                val chunk = ByteArray(8192)
                while (true) {
                    val size = channel.readAvailable(chunk, 0, chunk.size)
                    if (size < 0) break
                    if (bytes.size() + size > maxResponseBytes) throw ProviderException(ProviderFailure.INVALID)
                    bytes.write(chunk, 0, size)
                }
                bytes.toString(Charsets.UTF_8)
            }
            val payload=try {json.parseToJsonElement(body)} catch(_:Exception) {throw ProviderException(ProviderFailure.INVALID)}
            // Reject an upstream echo before the raw payload can enter the persistent cache.
            if (!credential.isNullOrBlank()) {
                val encodedCredential = JsonPrimitive(credential).toString().removeSurrounding("\"")
                if (payload.toString().contains(encodedCredential)) throw ProviderException(ProviderFailure.INVALID)
            }
            try {validate(payload)} catch(e:ProviderException) {throw e} catch(_:Exception) {throw ProviderException(ProviderFailure.INVALID)}
            return payload
        } catch(e:ProviderException) {throw e}
          catch(e:HttpRequestTimeoutException) {throw ProviderException(ProviderFailure.TIMEOUT)}
          catch(e:java.net.SocketTimeoutException) {throw ProviderException(ProviderFailure.TIMEOUT)}
          catch(e:Exception) {
              if(e is kotlinx.coroutines.CancellationException) throw e
              if(e.javaClass.simpleName.contains("Timeout")) throw ProviderException(ProviderFailure.TIMEOUT)
              throw ProviderException(ProviderFailure.UNAVAILABLE)
          }
    }
}

/** v2 credentials only in X-API-Key; search and skills use independent validated cache keys. */
class OnetProvider(client: HttpClient, private val apiKey: String?, private val occupationCode: String? = null) : HttpJsonProvider(client, apiKey) {
    override val provider="ONET"
    override val cacheKey=if(occupationCode==null)"occupations:warehouse:start1:end2000" else "skills:$occupationCode:start1:end2000"
    override val ttlDays=7L
    override val endpoint=if(occupationCode==null)"https://api-v2.onetcenter.org/mnm/search" else {
        require(Regex("^[0-9]{2}-[0-9]{4}\\.[0-9]{2}$").matches(occupationCode))
        "https://api-v2.onetcenter.org/online/occupations/$occupationCode/details/skills"
    }
    override fun HttpRequestBuilder.prepare() {
        if(apiKey.isNullOrBlank()) throw ProviderException(ProviderFailure.AUTH)
        header("X-API-Key",apiKey)
        if(occupationCode==null) parameter("keyword","warehouse")
        parameter("start",1);parameter("end",2000)
    }
    override fun validate(payload: JsonElement) {
        val root=payload as? JsonObject ?: throw ProviderException(ProviderFailure.INVALID)
        val array=root[if(occupationCode==null)"career" else "element"] as? JsonArray ?: throw ProviderException(ProviderFailure.INVALID)
        if(array.isEmpty()) throw ProviderException(ProviderFailure.NO_DATA)
        val a=if(occupationCode==null)"code" else "id";val b=if(occupationCode==null)"title" else "name"
        if(array.any {r -> r !is JsonObject || r[a]?.jsonPrimitive?.contentOrNull.isNullOrBlank() || r[b]?.jsonPrimitive?.contentOrNull.isNullOrBlank()}) throw ProviderException(ProviderFailure.INVALID)
        // Do not cache a truncated response as a complete result.
        val total=root["total"]?.jsonPrimitive?.intOrNull
        if(total!=null && total>array.size) throw ProviderException(ProviderFailure.INVALID)
    }
}

class WorldBankProvider(client: HttpClient, private val country: String = "RUS", private val yearFrom: Int = 2020, private val yearTo: Int = 2022) : HttpJsonProvider(client) {
    init {require(Regex("^[A-Z]{3}$").matches(country));require(yearFrom in 1900..2100 && yearTo in yearFrom..2100)}
    override val provider="WORLD_BANK"
    override val cacheKey="$country:FP.CPI.TOTL.ZG:$yearFrom:$yearTo"
    override val ttlDays=30L
    override val endpoint="https://api.worldbank.org/v2/country/$country/indicator/FP.CPI.TOTL.ZG"
    override fun HttpRequestBuilder.prepare() {parameter("format","json");parameter("date","$yearFrom:$yearTo");parameter("per_page",1000)}
    override fun validate(payload: JsonElement) {
        val array=payload as? JsonArray ?: throw ProviderException(ProviderFailure.INVALID)
        if(array.size!=2 || array[0] !is JsonObject) throw ProviderException(ProviderFailure.INVALID)
        if(array[0].jsonObject["pages"]?.jsonPrimitive?.intOrNull?.let {it>1}==true) throw ProviderException(ProviderFailure.INVALID)
        if(array[1]===JsonNull) throw ProviderException(ProviderFailure.NO_DATA)
        val observations=array[1] as? JsonArray ?: throw ProviderException(ProviderFailure.INVALID)
        if(observations.isEmpty()) throw ProviderException(ProviderFailure.NO_DATA)
        var valid=0
        for(e in observations) {
            val r=e as? JsonObject ?: throw ProviderException(ProviderFailure.INVALID)
            if(r["countryiso3code"]?.jsonPrimitive?.content!=country || r["indicator"]?.jsonObject?.get("id")?.jsonPrimitive?.content!="FP.CPI.TOTL.ZG") throw ProviderException(ProviderFailure.INVALID)
            val year=r["date"]?.jsonPrimitive?.intOrNull ?: throw ProviderException(ProviderFailure.INVALID)
            if(year !in yearFrom..yearTo) throw ProviderException(ProviderFailure.INVALID)
            val v=r["value"] ?: throw ProviderException(ProviderFailure.INVALID)
            if(v!==JsonNull) {
                val primitive=v as? JsonPrimitive ?: throw ProviderException(ProviderFailure.INVALID)
                if(primitive.isString) throw ProviderException(ProviderFailure.INVALID)
                val number=primitive.content.toBigDecimalOrNull() ?: throw ProviderException(ProviderFailure.INVALID)
                if(number>java.math.BigDecimal("-100")) valid++
            }
        }
        if(valid==0) throw ProviderException(ProviderFailure.NO_DATA)
    }
}

/** Optional provider, never called in DEMO startup. Replacing it does not change internal models. */
enum class CarsResource(val path: String) { MARKS("marks"), MODELS("models") }
class CarsBaseProvider(client: HttpClient, private val token: String?, private val resource: CarsResource = CarsResource.MARKS, private val markId: String? = null,
    private val page: Int = 1, private val pageSize: Int = 1000) : HttpJsonProvider(client, token), CarCatalogProvider {
    init {require(markId==null || Regex("^[A-Za-z0-9_-]{1,64}$").matches(markId))}
    override val provider="CARS_BASE"
    private val mode = if (token == "test") "demo" else "live"
    override val cacheKey="$mode:${resource.path}:${markId.orEmpty()}:page$page:size$pageSize"
    override val ttlDays=7L
    override val endpoint="https://api.cars-base.ru/${resource.path}"
    override fun HttpRequestBuilder.prepare() {
        if(token.isNullOrBlank()) throw ProviderException(ProviderFailure.AUTH)
        parameter("token",token);parameter("pageSize",pageSize);parameter("page",page);parameter("sort_by","id")
        markId?.let {parameter("mark_id",it)}
    }
    override fun validate(payload: JsonElement) {
        val root=payload as? JsonObject ?: throw ProviderException(ProviderFailure.INVALID)
        val array=root["data"] as? JsonArray ?: throw ProviderException(ProviderFailure.INVALID)
        if(array.isEmpty()) throw ProviderException(ProviderFailure.NO_DATA)
        if(array.any {it !is JsonObject || it["id"]?.jsonPrimitive?.contentOrNull.isNullOrBlank() || it["name"]?.jsonPrimitive?.contentOrNull.isNullOrBlank()}) throw ProviderException(ProviderFailure.INVALID)
        val meta = root["meta"] as? JsonObject ?: throw ProviderException(ProviderFailure.INVALID)
        if (meta["count"]?.jsonPrimitive?.intOrNull != array.size || meta["page"]?.jsonPrimitive?.intOrNull != page)
            throw ProviderException(ProviderFailure.INVALID)
    }
}

/** Persistent last-known-good raw cache. Failed/empty responses never overwrite valid payloads. */
class ProviderCache(private val db: CatalogDatabase, private val clock: Clock = Clock.systemUTC(), private val pause: suspend (Long)->Unit = {delay(it)}) {
    // Keep single-flight per key without allocating a permanent lock per query.
    private val locks = Array(256) { Mutex() }
    suspend fun read(provider: JsonProvider, forceRefresh: Boolean = false): ProviderData {
        val hash=sha256((provider.provider+":"+provider.cacheKey).toByteArray())
        return locks[hash.take(2).toInt(16)].withLock {
            val cached=db.tx { query("SELECT payload::text, expires_at FROM provider_cache WHERE provider=? AND request_hash=?",provider.provider,hash) {rs ->
                json.parseToJsonElement(rs.getString(1)) to rs.getObject(2,java.time.OffsetDateTime::class.java).toInstant()
            }.singleOrNull() }
            if(cached!=null && cached.second.isAfter(clock.instant()) && !forceRefresh) return@withLock ProviderData(cached.first,true,false)
            var last=ProviderFailure.UNAVAILABLE
            for(attempt in 0..2) {
                try {
                    val payload=provider.fetch();provider.validate(payload)
                    val now=clock.instant()
                    db.tx {execute("INSERT INTO provider_cache(id,provider,request_hash,payload,fetched_at,expires_at) VALUES(?,?,?,?::jsonb,?,?) ON CONFLICT(provider,request_hash) DO UPDATE SET payload=EXCLUDED.payload,fetched_at=EXCLUDED.fetched_at,expires_at=EXCLUDED.expires_at,etag=NULL,catalog_version=NULL",
                        UUID.randomUUID(),provider.provider,hash,canonicalJson(payload),now.atOffset(ZoneOffset.UTC),now.plus(Duration.ofDays(provider.ttlDays)).atOffset(ZoneOffset.UTC))}
                    return@withLock ProviderData(payload,false,false)
                } catch(e:ProviderException) {
                    last=e.reason
                    if(last !in setOf(ProviderFailure.TIMEOUT,ProviderFailure.UNAVAILABLE) || attempt==2) break
                    pause((attempt+1)*1000L)
                }
            }
            if(cached!=null) ProviderData(cached.first,true,true,last) else throw ProviderException(last)
        }
    }
}
