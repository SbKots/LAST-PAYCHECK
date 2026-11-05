package lastpay.domain

import kotlinx.serialization.json.JsonElement

enum class ProviderFailure { TIMEOUT, UNAVAILABLE, AUTH, INVALID, NO_DATA }
class ProviderException(val reason: ProviderFailure) : RuntimeException("Provider request failed: $reason")
data class ProviderData(val payload: JsonElement, val fromCache: Boolean, val stale: Boolean, val failure: ProviderFailure? = null)
interface JsonProvider {
    val provider: String
    val cacheKey: String
    val ttlDays: Long
    suspend fun fetch(): JsonElement
    fun validate(payload: JsonElement)
}
interface CarCatalogProvider : JsonProvider
