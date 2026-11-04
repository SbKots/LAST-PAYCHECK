package lastpay.api

import lastpay.application.CatalogService
import lastpay.domain.*
import lastpay.infrastructure.Configuration
import lastpay.security.*
import kotlinx.serialization.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CancellationException
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.application.hooks.ResponseSent
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.plugins.statuspages.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.util.AttributeKey
import java.util.UUID
import org.slf4j.LoggerFactory

private val audit = LoggerFactory.getLogger("CatalogAudit")
private val requestIdKey = AttributeKey<String>("catalog-request-id")
private val errorCodeKey = AttributeKey<String>("catalog-error-code")
private fun ApplicationCall.requestId() = attributes.computeIfAbsent(requestIdKey) { UUID.randomUUID().toString() }

fun Application.catalogModule(service: CatalogService, config: Configuration, limiter: RequestLimiter = RequestLimiter()) {
    val access=AdminAccess(config.adminToken,config.adminScope)
    install(createApplicationPlugin("HttpAudit") {
        on(ResponseSent) { call ->
            val status = call.response.status()?.value ?: 200
            val event = buildJsonObject {
                put("event", "http_response")
                put("requestId", call.requestId())
                put("method", call.request.httpMethod.value)
                put("status", status)
                put("area", if (call.request.path().startsWith("/api/v1/admin/")) "admin" else "public")
                call.attributes.getOrNull(errorCodeKey)?.let { put("errorCode", it) }
            }
            audit.info(event.toString())
        }
    })
    install(ContentNegotiation) { json(json) }
    install(StatusPages) {
        exception<Throwable> { call,error ->
            if(error is CancellationException) throw error
            val failure=error as? CatalogFailure ?: CatalogFailure(FailureKind.INTERNALERROR,"Внутренняя ошибка сервера")
            call.problem(failure)
        }
        status(HttpStatusCode.NotFound) { call,_ -> call.problem(CatalogFailure(FailureKind.NOTFOUND,"Маршрут не найден")) }
    }
    fun ApplicationCall.admin() {
        limiter.check("admin-peer:" + request.local.remoteHost, 120)
        access.verify(request.headers[HttpHeaders.Authorization])
        limiter.check("admin", 30)
    }
    fun ApplicationCall.public() { limiter.check("public:"+request.local.remoteHost,120) }
    routing {
        route("/api/v1") {
            get("/catalog") { call.public();call.respond(withContext(Dispatchers.IO){service.latest()}) }
            val routes=linkedMapOf("occupations" to "occupations","skills" to "skills","jobs" to "jobs","inflation" to "inflation","cars/brands" to "car-brands","cars/models" to "car-models","tracks" to "tracks","race-configurations" to "race-configurations")
            for((path,name) in routes) {
                get("/$path") {
                    call.public()
                    val extra=when(name) { "inflation" -> setOf("country","yearFrom","yearTo");"car-models" -> setOf("brandId");"race-configurations" -> setOf("trackId");else -> emptySet() }
                    val q=call.query(setOf("catalogVersion","page","pageSize")+extra,setOf("catalogVersion")+(if(name=="inflation")extra else emptySet()))
                    val version=validId(q.getValue("catalogVersion"),true)
                    val page=q.integer("page",1,1,1000000);val size=q.integer("pageSize",20,1,100)
                    val from=if(name=="inflation")q.integer("yearFrom",min=1900,max=2100)else null
                    val to=if(name=="inflation")q.integer("yearTo",min=1900,max=2100)else null
                    if(from!=null && from>to!!) fail(FailureKind.VALIDATIONERROR,"yearTo должен быть не меньше yearFrom","yearTo")
                    val country=q["country"]
                    if(country!=null && !Regex("^[A-Z]{3}$").matches(country)) fail(FailureKind.BADREQUEST,"Некорректный код страны","country")
                    if(country!=null && country !in isoCountries) fail(FailureKind.VALIDATIONERROR,"Нужна страна ISO alpha-3, не агрегат","country")
                    val filter=when { q["brandId"]!=null -> "brand_id" to validId(q.getValue("brandId"));q["trackId"]!=null -> "track_id" to validId(q.getValue("trackId"));else -> null }
                    call.respond((withContext(Dispatchers.IO){service.list(name,version,page,size,country,from,to,filter)}).body)
                }
            }
            for((path,name) in mapOf("occupations" to "occupations","cars/models" to "car-models","tracks" to "tracks")) {
                get("/$path/{id}") {
                    call.public();val q=call.query(setOf("catalogVersion"),setOf("catalogVersion"))
                    call.result(withContext(Dispatchers.IO){service.detail(name,validId(q.getValue("catalogVersion"),true),validId(call.parameters["id"]!!))})
                }
            }
            post("/admin/catalogs") {
                call.admin();call.query(emptySet());val body=call.jsonBody();body.shape(setOf("version","baseVersion"))
                val version=body.text("version",32)!!;val base=body.text("baseVersion",32)!!
                try {validId(version,true);validId(base,true)} catch(_:CatalogFailure) { fail(FailureKind.VALIDATIONERROR,"Некорректная версия") }
                val result=withContext(Dispatchers.IO){service.createDraft(version,base)}
                call.response.header(HttpHeaders.Location,"/api/v1/admin/catalogs/$version/skills");call.result(result,HttpStatusCode.Created)
            }
            get("/admin/catalogs/{version}/skills") {
                call.admin();val q=call.query(setOf("page","pageSize"));val v=validId(call.parameters["version"]!!,true)
                call.result(withContext(Dispatchers.IO){service.list("skills",v,q.integer("page",1,1,1000000),q.integer("pageSize",20,1,100),draft=true)})
            }
            get("/admin/catalogs/{version}/skills/{id}") {
                call.admin();call.query(emptySet());val v=validId(call.parameters["version"]!!,true);val id=validId(call.parameters["id"]!!)
                call.result(withContext(Dispatchers.IO){service.detail("skills",v,id,draft=true)})
            }
            post("/admin/catalogs/{version}/skills") {
                call.admin();call.query(emptySet());val v=validId(call.parameters["version"]!!,true);val body=call.jsonBody()
                val result=withContext(Dispatchers.IO){service.writeSkill(v,null,call.request.headers[HttpHeaders.IfMatch]) {skillInput(body,null)}}
                val id=result.body.getValue("data").jsonObject.getValue("id").jsonPrimitive.content
                call.response.header(HttpHeaders.Location,"/api/v1/admin/catalogs/$v/skills/$id");call.result(result,HttpStatusCode.Created)
            }
            put("/admin/catalogs/{version}/skills/{id}") {
                call.admin();call.query(emptySet());val v=validId(call.parameters["version"]!!,true);val id=validId(call.parameters["id"]!!);val body=call.jsonBody()
                call.result(withContext(Dispatchers.IO){service.writeSkill(v,id,call.request.headers[HttpHeaders.IfMatch]) {skillInput(body,id)}})
            }
            delete("/admin/catalogs/{version}/skills/{id}") {
                call.admin();call.query(emptySet());val v=validId(call.parameters["version"]!!,true);val id=validId(call.parameters["id"]!!)
                val revision=withContext(Dispatchers.IO){service.deleteSkill(v,id,call.request.headers[HttpHeaders.IfMatch])}
                call.response.header(HttpHeaders.ETag,revision.etag);call.respond(HttpStatusCode.NoContent)
            }
            post("/admin/catalogs/{version}/publish") {
                call.admin();call.query(emptySet());val v=validId(call.parameters["version"]!!,true)
                call.respond(withContext(Dispatchers.IO){service.publish(v,call.request.headers[HttpHeaders.IfMatch])})
            }
        }
    }
}
private suspend fun ApplicationCall.result(result: CatalogResult, status: HttpStatusCode = HttpStatusCode.OK) {
    result.revision?.let {response.header(HttpHeaders.ETag,it.etag)};respond(status,result.body)
}
private suspend fun ApplicationCall.problem(f: CatalogFailure) {
    val status=when(f.kind) { FailureKind.BADREQUEST -> 400;FailureKind.UNAUTHORIZED -> 401;FailureKind.FORBIDDEN -> 403;FailureKind.NOTFOUND -> 404;FailureKind.CONFLICT -> 409;FailureKind.PRECONDITIONFAILED -> 412;FailureKind.VALIDATIONERROR -> 422;FailureKind.RATELIMITED -> 429;FailureKind.INTERNALERROR -> 500;FailureKind.UNAVAILABLE -> 503 }
    val requestId=requestId()
    attributes.put(errorCodeKey, f.kind.name)
    if(status==401) response.header(HttpHeaders.WWWAuthenticate,"Bearer realm=\"catalog-admin\"")
    if(status==429 || status==503) response.header(HttpHeaders.RetryAfter,"60")
    respond(HttpStatusCode.fromValue(status),buildJsonObject {put("error",buildJsonObject {
        put("code",f.kind.name);put("message",f.message.take(300));put("requestId",requestId)
        put("details",JsonArray(f.issues.map {buildJsonObject {put("field",it.field);put("message",it.message)}}))
    })})
}
