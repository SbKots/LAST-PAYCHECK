package lastpay.application

import lastpay.domain.*
import lastpay.infrastructure.*
import kotlinx.serialization.json.*
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.math.BigDecimal

/** Only hand-authored DEMO fixtures. No provider request is simulated as a successful real import. */
class DemoSeeder(private val db: CatalogDatabase, private val clock: Clock = Clock.systemUTC()) {
    fun seed(): Boolean {
        val exists=db.tx { rows("SELECT status FROM catalog_version WHERE version='demo-1'").singleOrNull() }
        if(exists!=null) {
            if(exists.getValue("status").jsonPrimitive.content!="PUBLISHED") fail(FailureKind.CONFLICT,"Незавершённая demo-1 уже существует")
            return false
        }
        val payload=json.parseToJsonElement(javaClass.getResource("/demo-catalog.json")!!.readText()).jsonObject
        db.tx {
            val sources=buildJsonObject { for(resource in catalogResources.keys) put(resource,buildJsonObject {
                put("provider","DEMO");put("fetchedAt",clock.instant().toString());put("sourceVersion","practice9-demo-v1");put("sha256","0".repeat(64))
            }) }
            execute("INSERT INTO catalog_version(version,status,revision,created_at,source_metadata) VALUES('demo-1','DRAFT',1,?,?::jsonb)",clock.instant().atOffset(ZoneOffset.UTC),canonicalJson(sources))
            for(table in versionedTables) {
                for(element in payload.getValue(table).jsonArray) {
                    val row=element.jsonObject
                    val columns=row.keys.toList()
                    val args: List<Any?> =columns.map { column ->
                        val v=row.getValue(column)
                        when {
                            v===JsonNull -> null
                            column=="fetched_at" -> Instant.parse(v.jsonPrimitive.content).atOffset(ZoneOffset.UTC)
                            v.jsonPrimitive.isString -> v.jsonPrimitive.content
                            else -> BigDecimal(v.jsonPrimitive.content)
                        }
                    }
                    execute("INSERT INTO $table(catalog_version,${columns.joinToString(",")}) VALUES(?,${columns.joinToString(","){"?"}})","demo-1",*args.toTypedArray())
                }
            }
            // Nested Exposed transaction joins this transaction: seed + publication commit together.
            CatalogService(db,clock).publish("demo-1","\"rev-1\"")
        }
        return true
    }
}
