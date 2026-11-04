package lastpay.security

import lastpay.domain.*
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.time.Clock

class AdminAccess(token: String, private val scope: String = "catalog:write") {
    private val digest=MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
    fun verify(header: String?) {
        val token=header?.takeIf { it.startsWith("Bearer ", ignoreCase=true) }?.substring(7)?.takeIf { it.isNotBlank() && it.none(Char::isWhitespace) }
            ?: fail(FailureKind.UNAUTHORIZED, "Требуется административный Bearer-токен")
        val supplied=MessageDigest.getInstance("SHA-256").digest(token.toByteArray())
        if (!MessageDigest.isEqual(digest,supplied)) fail(FailureKind.UNAUTHORIZED, "Недействительный административный токен")
        if (scope!="catalog:write") fail(FailureKind.FORBIDDEN, "Недостаточно административных прав")
    }
}

/** Bounded local fixed-window limiter; the deployment proxy must supply its own distributed limits. */
class RequestLimiter(private val clock: Clock = Clock.systemUTC()) {
    private data class Window(var minute: Long, var count: Int)
    private val windows=ConcurrentHashMap<String,Window>()
    @Synchronized fun check(key: String, limit: Int) {
        val minute=clock.millis()/60000
        if (windows.size>10000) windows.entries.removeIf { it.value.minute<minute }
        if (windows.size>10000 && !windows.containsKey(key)) fail(FailureKind.RATELIMITED,"Слишком много запросов")
        val w=windows.computeIfAbsent(key) { Window(minute,0) }
        if (w.minute!=minute) { w.minute=minute;w.count=0 }
        if (++w.count>limit) fail(FailureKind.RATELIMITED,"Слишком много запросов")
    }
}
