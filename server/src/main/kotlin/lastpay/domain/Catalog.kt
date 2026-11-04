package lastpay.domain

import kotlinx.serialization.json.*
import java.math.BigDecimal
import java.security.MessageDigest

enum class FailureKind { BADREQUEST, UNAUTHORIZED, FORBIDDEN, NOTFOUND, CONFLICT, PRECONDITIONFAILED, VALIDATIONERROR, RATELIMITED, INTERNALERROR, UNAVAILABLE }
data class FieldIssue(val field: String, val message: String)
class CatalogFailure(val kind: FailureKind, override val message: String, val issues: List<FieldIssue> = emptyList()) : RuntimeException(message)
fun fail(kind: FailureKind, message: String, field: String? = null): Nothing =
    throw CatalogFailure(kind, message, field?.let { listOf(FieldIssue(it, message)) } ?: emptyList())

val catalogResources = linkedMapOf(
    "occupations" to "occupation", "skills" to "skill", "jobs" to "job_definition",
    "inflation" to "inflation_observation", "car-brands" to "car_brand", "car-models" to "car_model",
    "tracks" to "race_track", "race-configurations" to "race_configuration"
)
val versionedTables = listOf("occupation", "skill", "occupation_skill", "job_definition", "inflation_observation", "car_brand", "car_model", "car_specification", "car_balance", "race_track", "race_configuration")
val json = Json { explicitNulls = true; ignoreUnknownKeys = false; isLenient = false }
val isoCountries: Set<String> = java.util.Locale.getISOCountries().map { java.util.Locale.Builder().setRegion(it).build().isO3Country }.toSet()
fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

/** Canonical JSON v1: sorted object keys; preserve semantic array order; exact decimal plain notation.
 * Resource arrays are sorted before this function is called. No locale, exponent or floating arithmetic.
 */
fun canonicalJson(value: JsonElement): String = when (value) {
    is JsonObject -> value.entries.sortedBy { it.key }.joinToString(",", "{", "}") { json.encodeToString(JsonPrimitive(it.key)) + ":" + canonicalJson(it.value) }
    is JsonArray -> value.joinToString(",", "[", "]") { canonicalJson(it) }
    JsonNull -> "null"
    is JsonPrimitive -> if (value.isString || value.booleanOrNull != null) value.toString()
        else BigDecimal(value.content).stripTrailingZeros().toPlainString()
}

data class SkillInput(val id: String, val code: String, val title: String, val description: String, val source: String, val externalId: String?)
data class CatalogRevision(val version: String, val revision: Long) { val etag: String get() = "\"rev-$revision\"" }
data class CatalogResult(val body: JsonObject, val revision: CatalogRevision? = null)

fun validId(value: String, version: Boolean = false): String {
    val pattern = if (version) "^[a-z0-9][a-z0-9._-]{0,31}$" else "^[a-z0-9][a-z0-9._-]{0,63}$"
    if (!Regex(pattern).matches(value)) fail(FailureKind.BADREQUEST, "Некорректный идентификатор")
    return value
}
