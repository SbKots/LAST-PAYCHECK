package lastpay.infrastructure

import java.nio.file.Files
import java.nio.file.Path

data class Configuration(
    val dbUrl: String, val dbUser: String, val dbPassword: String, val adminToken: String,
    val adminScope: String = "catalog:write", val host: String = "127.0.0.1", val port: Int = 8089,
    val demoMode: Boolean = true, val schema: String = "public",
    val onetKey: String? = null, val carsToken: String? = null,
    val readerPassword: String = "", val writerPassword: String = ""
) {
    companion object {
        fun load(): Configuration {
            val file = Path.of(".env")
            val local = if (System.getenv("ENV_ONLY") != "true" && Files.exists(file)) Files.readAllLines(file).filter { it.isNotBlank() && !it.trimStart().startsWith("#") && it.contains("=") }
                .associate { it.substringBefore("=").trim().removePrefix("\uFEFF") to it.substringAfter("=").trim() } else emptyMap()
            fun value(key: String, default: String? = null): String = System.getenv(key) ?: local[key] ?: default ?: error("Required environment variable $key is missing")
            val token = value("ADMIN_TOKEN")
            require(token.length >= 32 && !token.startsWith("replace-")) { "Set a random ADMIN_TOKEN of at least 32 characters" }
            return Configuration(value("DB_URL"), value("DB_USER", ""), value("DB_PASSWORD", ""), token,
                value("ADMIN_SCOPE", "catalog:write"), value("HOST", "127.0.0.1"), value("PORT", "8089").toInt(),
                value("DEMO_MODE", "true").toBooleanStrict(),
                onetKey=value("ONET_API_KEY", "").ifBlank { null },
                carsToken=value("CARS_API_TOKEN", "").ifBlank { null },
                readerPassword=value("DB_READER_PASSWORD", ""), writerPassword=value("DB_WRITER_PASSWORD", ""))
        }
    }
}
