package com.lastpaycheck.data.remote.error

import com.lastpaycheck.data.remote.interceptor.RequestRejected
import com.lastpaycheck.domain.model.DomainResult
import com.lastpaycheck.domain.model.GameError
import com.lastpaycheck.domain.model.RemoteFailure
import java.io.EOFException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.ResponseBody
import retrofit2.Response

internal class RemoteCalls(
    private val json: Json,
    private val hasConnection: () -> Boolean
) {
    suspend fun <T : Any> execute(request: suspend () -> Response<T>): DomainResult<T> {
        return try {
            val response = request()
            if (response.isSuccessful) {
                val body = response.body() ?: return failure(RemoteFailure.INVALID_RESPONSE)
                DomainResult.Success(body)
            } else {
                val status = response.code()
                val code = response.errorBody()?.use(::errorCode)
                DomainResult.Failure(GameError.Remote(httpFailure(status), status, code))
            }
        } catch (error: RequestRejected) {
            failure(error.failure)
        } catch (_: SerializationException) {
            failure(RemoteFailure.INVALID_RESPONSE)
        } catch (error: IOException) {
            currentCoroutineContext().ensureActive()
            failure(
                when (error) {
                    is SSLException -> RemoteFailure.TLS
                    is EOFException -> RemoteFailure.INVALID_RESPONSE
                    else -> when {
                        !hasConnection() -> RemoteFailure.OFFLINE
                        error is InterruptedIOException -> RemoteFailure.TIMEOUT
                        error is UnknownHostException -> RemoteFailure.DNS
                        else -> RemoteFailure.CONNECTION
                    }
                }
            )
        }
    }

    private fun errorCode(body: ResponseBody): String? {
        return try {
            val source = body.source()
            if (source.request(8193L)) return null
            val payload = json.parseToJsonElement(source.readUtf8()) as? JsonObject ?: return null
            val error = (payload["error"] as? JsonObject) ?: payload
            (error["code"] as? JsonPrimitive)?.takeIf { it.isString }?.content
                ?.takeIf { codePattern.matches(it) }
        } catch (_: SerializationException) {
            null
        } catch (_: IOException) {
            null
        }
    }

    private fun failure(reason: RemoteFailure) = DomainResult.Failure(GameError.Remote(reason))
}

private val codePattern = Regex("[A-Z0-9_]{1,64}")

private fun httpFailure(status: Int): RemoteFailure = when (status) {
    400, 422 -> RemoteFailure.BAD_REQUEST
    401 -> RemoteFailure.AUTH_REQUIRED
    403 -> RemoteFailure.FORBIDDEN
    404 -> RemoteFailure.NOT_FOUND
    409 -> RemoteFailure.CONFLICT
    412 -> RemoteFailure.STALE_REVISION
    429 -> RemoteFailure.RATE_LIMITED
    in 500..599 -> RemoteFailure.SERVER
    else -> RemoteFailure.HTTP
}
