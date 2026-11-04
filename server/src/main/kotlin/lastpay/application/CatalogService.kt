package lastpay.application

import lastpay.domain.*
import lastpay.infrastructure.*
import kotlinx.serialization.json.*
import java.time.Clock
import java.time.Duration
import java.time.Instant

class CatalogService(val database: CatalogDatabase, private val clock: Clock = Clock.systemUTC()) {
    private fun SqlSession.header(version: String, draft: Boolean = false, lock: Boolean = false): JsonObject {
        val row=rows("SELECT * FROM catalog_version WHERE version=?"+(if(lock) " FOR UPDATE" else ""), version).singleOrNull()
            // RLS hides PUBLISHED from row-locking statements. Preserve the API conflict response.
            ?: (if (lock && count("SELECT count(*) FROM catalog_version WHERE version=? AND status='PUBLISHED'",version)>0L)
                fail(FailureKind.CONFLICT,"Опубликованная версия неизменяема") else null)
            ?: fail(FailureKind.NOTFOUND,"Версия каталога не найдена")
        val state=row.getValue("status").jsonPrimitive.content
        if (draft && state!="DRAFT") fail(FailureKind.CONFLICT,"Опубликованная версия неизменяема")
        if (!draft && state!="PUBLISHED") fail(FailureKind.NOTFOUND,"Опубликованная версия не найдена")
        return row
    }
    private fun SqlSession.metadata(version: String): JsonObject = query("SELECT source_metadata::text FROM catalog_version WHERE version=?",version) { json.parseToJsonElement(it.getString(1)).jsonObject }.single()
    private fun SqlSession.resource(name: String, version: String, id: String? = null, filter: Pair<String,String>? = null): List<JsonObject> {
        val table=catalogResources[name] ?: error("Unknown internal resource")
        val conditions=buildList { add("catalog_version=?");if(id!=null)add("id=?");if(filter!=null)add(filter.first+"=?") }
        val args=buildList<Any?> { add(version);if(id!=null)add(id);if(filter!=null)add(filter.second) }
        val order=if(name=="inflation") "year ASC, id ASC" else "id ASC"
        return rows("SELECT * FROM $table WHERE ${conditions.joinToString(" AND ")} ORDER BY $order",*args.toTypedArray()).map { base ->
            val entityId=base.getValue("id").jsonPrimitive.content
            when(name) {
                "occupations" -> JsonObject(base+ ("skills" to JsonArray(rows("SELECT skill_id,importance FROM occupation_skill WHERE catalog_version=? AND occupation_id=? ORDER BY skill_id",version,entityId))))
                "car-models" -> {
                    val spec=rows("SELECT * FROM car_specification WHERE catalog_version=? AND model_id=?",version,entityId).singleOrNull()?.let { JsonObject(it-"modelId") } ?: JsonNull
                    val balance=rows("SELECT * FROM car_balance WHERE catalog_version=? AND model_id=?",version,entityId).singleOrNull()?.let { JsonObject(it-"modelId") }
                        ?: fail(FailureKind.UNAVAILABLE,"Нет обязательного игрового баланса модели")
                    JsonObject(base+mapOf("specification" to spec,"balance" to balance))
                }
                else -> base
            }
        }
    }
    private fun manifest(row: JsonObject, sources: JsonObject): JsonObject = buildJsonObject {
        put("catalogVersion",row.getValue("version"));put("schemaVersion","1");put("publishedAt",row.getValue("publishedAt"));put("contentSha256",row.getValue("contentSha256"))
        put("resources",JsonArray(catalogResources.keys.map(::JsonPrimitive)))
        put("sources",JsonArray(catalogResources.keys.map { name -> JsonObject(sources.getValue(name).jsonObject+("resource" to JsonPrimitive(name))) }))
    }
    private fun <T> catalogRead(draft: Boolean, block: SqlSession.() -> T): T =
        if (draft) database.tx(block) else database.read(block)

    fun latest(): JsonObject = database.read {
        val row=rows("SELECT * FROM catalog_version WHERE status='PUBLISHED' ORDER BY published_at DESC, version ASC LIMIT 1").singleOrNull()
            ?: fail(FailureKind.UNAVAILABLE,"Нет валидной опубликованной версии")
        manifest(row,metadata(row.getValue("version").jsonPrimitive.content))
    }
    fun list(name: String, version: String, page: Int, pageSize: Int, country: String? = null, yearFrom: Int? = null, yearTo: Int? = null, filter: Pair<String,String>? = null, draft: Boolean = false): CatalogResult = catalogRead(draft) {
        val head=header(version,draft);val meta=metadata(version)
        var all=resource(name,version,filter=filter)
        if(name=="inflation") all=all.filter { it.getValue("countryCode").jsonPrimitive.content==country && it.getValue("year").jsonPrimitive.int in yearFrom!!..yearTo!! }
        val now=clock.instant()
        val fetched=meta.values.map { Instant.parse(it.jsonObject.getValue("fetchedAt").jsonPrimitive.content) }.minOrNull() ?: now
        val stale=meta.values.any {
            val source=it.jsonObject;val provider=source.getValue("provider").jsonPrimitive.content
            val days=when(provider) { "WORLD_BANK" -> 30L;else -> 7L }
            now.isAfter(Instant.parse(source.getValue("fetchedAt").jsonPrimitive.content).plus(Duration.ofDays(days)))
        }
        val start=(page.toLong()-1)*pageSize
        val data=if(start>=all.size) emptyList() else all.drop(start.toInt()).take(pageSize)
        CatalogResult(buildJsonObject { put("data",JsonArray(data));put("meta",buildJsonObject {
            put("catalogVersion",version);put("page",page);put("pageSize",pageSize);put("total",all.size);put("stale",stale);put("fetchedAt",fetched.toString())
        }) },if(draft) CatalogRevision(version,head.getValue("revision").jsonPrimitive.long) else null)
    }
    fun detail(name: String, version: String, id: String, draft: Boolean = false): CatalogResult = catalogRead(draft) {
        val head=header(version,draft)
        val data=resource(name,version,id).singleOrNull() ?: fail(FailureKind.NOTFOUND,"Сущность не найдена")
        CatalogResult(buildJsonObject { put("data",data);put("catalogVersion",version) },if(draft) CatalogRevision(version,head.getValue("revision").jsonPrimitive.long) else null)
    }
    fun createDraft(version: String, baseVersion: String): CatalogResult = database.tx {
        header(baseVersion)
        execute("INSERT INTO catalog_version(version,status,revision,created_at,source_metadata) SELECT ?,'DRAFT',1,?,source_metadata FROM catalog_version WHERE version=?",version,clock.instant().atOffset(java.time.ZoneOffset.UTC),baseVersion)
        for(table in versionedTables) {
            val columns=query("SELECT column_name FROM information_schema.columns WHERE table_schema=current_schema() AND table_name=? ORDER BY ordinal_position",table) { it.getString(1) }
            val rest=columns.filter { it!="catalog_version" }.joinToString(",")
            execute("INSERT INTO $table(catalog_version,$rest) SELECT ?,$rest FROM $table WHERE catalog_version=?",version,baseVersion)
        }
        CatalogResult(buildJsonObject { put("catalogVersion",version);put("status","DRAFT");put("revision",1) },CatalogRevision(version,1))
    }
    private fun requireMatch(row: JsonObject, ifMatch: String?) {
        if(ifMatch==null || !Regex("^\"rev-[1-9][0-9]*\"$").matches(ifMatch)) fail(FailureKind.BADREQUEST,"Требуется корректный If-Match")
        if(ifMatch!="\"rev-${row.getValue("revision").jsonPrimitive.long}\"") fail(FailureKind.PRECONDITIONFAILED,"Черновик изменён; перечитайте ETag")
    }
    private fun SqlSession.bump(version: String): CatalogRevision {
        val meta=metadata(version).toMutableMap()
        meta["skills"]=buildJsonObject { put("provider","DEMO");put("fetchedAt",clock.instant().toString());put("sourceVersion","admin-curated");put("sha256","0".repeat(64)) }
        execute("UPDATE catalog_version SET revision=revision+1,source_metadata=?::jsonb WHERE version=?",canonicalJson(JsonObject(meta)),version)
        return CatalogRevision(version,count("SELECT revision FROM catalog_version WHERE version=?",version))
    }
    fun writeSkill(version: String, id: String?, ifMatch: String?, parse: () -> SkillInput): CatalogResult = database.tx {
        val head=header(version,draft=true,lock=true);requireMatch(head,ifMatch)
        if(id!=null && count("SELECT count(*) FROM skill WHERE catalog_version=? AND id=?",version,id)==0L) fail(FailureKind.NOTFOUND,"Навык не найден")
        val skill=parse()
        if(id==null) execute("INSERT INTO skill(catalog_version,id,code,title,description,source,external_id) VALUES(?,?,?,?,?,?,?)",version,skill.id,skill.code,skill.title,skill.description,skill.source,skill.externalId)
        else execute("UPDATE skill SET code=?,title=?,description=?,source=?,external_id=? WHERE catalog_version=? AND id=?",skill.code,skill.title,skill.description,skill.source,skill.externalId,version,id)
        val rev=bump(version)
        CatalogResult(buildJsonObject { put("data",resource("skills",version,id?:skill.id).single());put("catalogVersion",version) },rev)
    }
    fun deleteSkill(version: String, id: String, ifMatch: String?): CatalogRevision = database.tx {
        val head=header(version,draft=true,lock=true);requireMatch(head,ifMatch)
        if(count("SELECT count(*) FROM skill WHERE catalog_version=? AND id=?",version,id)==0L) fail(FailureKind.NOTFOUND,"Навык не найден")
        execute("DELETE FROM skill WHERE catalog_version=? AND id=?",version,id)
        bump(version)
    }
    fun publish(version: String, ifMatch: String?): JsonObject = database.tx {
        val head=header(version,draft=true,lock=true);requireMatch(head,ifMatch)
        if(count("SELECT count(*) FROM car_model m LEFT JOIN car_balance b ON b.catalog_version=m.catalog_version AND b.model_id=m.id WHERE m.catalog_version=? AND b.model_id IS NULL",version)>0L)
            fail(FailureKind.VALIDATIONERROR,"Для каждой модели нужен игровой баланс","car-models")
        val snapshot=linkedMapOf<String,JsonElement>()
        for ((name,table) in catalogResources) {
            if(count("SELECT count(*) FROM $table WHERE catalog_version=?",version)==0L) fail(FailureKind.VALIDATIONERROR,"Пустой обязательный справочник",name)
            snapshot[name]=JsonArray(resource(name,version))
        }
        if(count("SELECT count(*) FROM job_definition WHERE catalog_version=? AND mechanic='WAREHOUSE'",version)<3) fail(FailureKind.VALIDATIONERROR,"Нужны три складские должности","jobs")
        if(snapshot.getValue("inflation").jsonArray.any {it.jsonObject.getValue("countryCode").jsonPrimitive.content !in isoCountries})
            fail(FailureKind.VALIDATIONERROR,"Инфляция должна относиться к стране ISO, не агрегату","inflation")
        val sources=metadata(version)
        if(sources.keys!=catalogResources.keys) fail(FailureKind.VALIDATIONERROR,"Неполные метаданные источников","sources")
        val updated=buildJsonObject {
            for(name in catalogResources.keys) {
                val source=sources.getValue(name) as? JsonObject ?: fail(FailureKind.VALIDATIONERROR,"Метаданные должны быть объектом","sources")
                if(source.keys!=setOf("provider","fetchedAt","sourceVersion","sha256")) fail(FailureKind.VALIDATIONERROR,"Неверная форма метаданных","sources")
                val provider=source["provider"] as? JsonPrimitive
                if(provider==null || !provider.isString || provider.content !in setOf("ONET","WORLD_BANK","CARS_BASE","DEMO")) fail(FailureKind.VALIDATIONERROR,"Неверный источник","sources")
                val sourceVersion=source.getValue("sourceVersion")
                if(sourceVersion!==JsonNull && (sourceVersion !is JsonPrimitive || !sourceVersion.isString || sourceVersion.content.length>120)) fail(FailureKind.VALIDATIONERROR,"Неверная версия источника","sources")
                try { val stamp=source.getValue("fetchedAt").jsonPrimitive;require(stamp.isString);Instant.parse(stamp.content) } catch(_: Exception) { fail(FailureKind.VALIDATIONERROR,"Неверная дата источника","sources") }
                put(name,JsonObject(source+("sha256" to JsonPrimitive(sha256(canonicalJson(snapshot.getValue(name)).toByteArray(Charsets.UTF_8))))))
            }
        }
        snapshot["source_metadata"]=updated
        val hash=sha256(canonicalJson(JsonObject(snapshot)).toByteArray(Charsets.UTF_8))
        execute("UPDATE catalog_version SET status='PUBLISHED',published_at=?,content_sha256=?,source_metadata=?::jsonb,revision=revision+1 WHERE version=?",clock.instant().atOffset(java.time.ZoneOffset.UTC),hash,canonicalJson(updated),version)
        manifest(rows("SELECT * FROM catalog_version WHERE version=?",version).single(),updated)
    }
}
