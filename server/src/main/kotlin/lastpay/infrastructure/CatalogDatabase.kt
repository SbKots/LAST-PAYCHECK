package lastpay.infrastructure

import lastpay.domain.*
import kotlinx.serialization.json.*
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.v1.jdbc.Database
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.postgresql.ds.PGSimpleDataSource
import org.postgresql.util.PasswordUtil
import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.math.BigDecimal
import java.time.OffsetDateTime

class CatalogDatabase(val configuration: Configuration) {
    private fun source(login: String, secret: String) = PGSimpleDataSource().apply {
        setURL(configuration.dbUrl)
        user=login; password=secret
        currentSchema=configuration.schema; connectTimeout=5; socketTimeout=10
    }

    private fun maintenanceSource(): PGSimpleDataSource {
        require(configuration.dbUser.isNotBlank() && configuration.dbPassword.isNotBlank()) {
            "Set DB_USER and DB_PASSWORD for database maintenance"
        }
        return source(configuration.dbUser, configuration.dbPassword)
    }

    private fun runtimeSource(login: String, secret: String): PGSimpleDataSource {
        require(secret.length >= 32 && !secret.startsWith("replace-") &&
            configuration.readerPassword != configuration.writerPassword) {
            "Configure distinct DB_READER_PASSWORD and DB_WRITER_PASSWORD (at least 32 characters)"
        }
        return source(login, secret)
    }

    internal val readerSource by lazy { runtimeSource("lastpay_reader", configuration.readerPassword) }
    internal val writerSource by lazy { runtimeSource("lastpay_writer", configuration.writerPassword) }
    private val reader by lazy { Database.connect(readerSource) }
    private val writer by lazy { Database.connect(writerSource) }

    fun migrate(): Int = Flyway.configure().dataSource(maintenanceSource()).schemas(configuration.schema).defaultSchema(configuration.schema)
        .locations("classpath:db/migration").cleanDisabled(true).load().migrate().migrationsExecuted

    fun provisionRoles() {
        val accounts = listOf(readerSource, writerSource)
        maintenanceSource().connection.use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { statement ->
                for (account in accounts) {
                    val passwordSql = PasswordUtil.genAlterUserPasswordSQL(account.user, requireNotNull(account.password).toCharArray(), "scram-sha-256")
                    statement.execute(passwordSql)
                }
            }
            connection.commit()
        }
    }

    internal fun maintenanceConnection(): Connection = maintenanceSource().connection

    fun checkRuntimeAccess() {
        for (account in listOf(readerSource, writerSource)) {
            account.connection.use { connection ->
                val session = SqlSession(connection)
                check(session.count("""
                    SELECT count(*) FROM pg_roles r WHERE rolname = current_user AND rolname = ?
                      AND NOT (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)
                      AND NOT EXISTS (SELECT FROM pg_auth_members m WHERE m.member = r.oid)
                      AND row_security_active('catalog_version'::regclass)
                """.trimIndent(), account.user) == 1L) { "Unsafe runtime database role or missing RLS" }
            }
        }
    }

    fun <T> read(block: SqlSession.() -> T): T = transact(reader, true, block)
    fun <T> tx(block: SqlSession.() -> T): T = transact(writer, false, block)

    private fun <T> transact(database: Database, readOnly: Boolean, block: SqlSession.() -> T): T = try {
        transaction(db=database, transactionIsolation=Connection.TRANSACTION_READ_COMMITTED, readOnly=readOnly) {
            maxAttempts=1
            SqlSession(connection.connection as Connection).block()
        }
    } catch (failure: CatalogFailure) { throw failure }
      catch (error: Exception) {
        val sql = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<SQLException>().firstOrNull { it.sqlState!=null }
        when {
            sql?.sqlState in listOf("23505", "23503", "23001") -> fail(FailureKind.CONFLICT, "Конфликт уникальности или связанных данных")
            sql?.sqlState == "23514" || sql?.sqlState == "22003" -> fail(FailureKind.VALIDATIONERROR, "Нарушено ограничение данных")
            sql?.sqlState?.startsWith("08") == true || sql?.sqlState in listOf("57P01", "57014") -> fail(FailureKind.UNAVAILABLE, "База данных временно недоступна")
            else -> {
                org.slf4j.LoggerFactory.getLogger("CatalogDatabase").warn("Database failure type={} sqlState={}",error.javaClass.simpleName,sql?.sqlState)
                throw error
            }
        }
    }
}

class SqlSession(val connection: Connection) {
    fun execute(sql: String, vararg args: Any?): Int = connection.prepareStatement(sql).use { st ->
        st.queryTimeout=10; args.forEachIndexed { i, v -> st.setObject(i+1, v) }; st.executeUpdate()
    }
    fun <T> query(sql: String, vararg args: Any?, map: (ResultSet) -> T): List<T> = connection.prepareStatement(sql).use { st ->
        st.queryTimeout=10; args.forEachIndexed { i,v -> st.setObject(i+1,v) }
        st.executeQuery().use { rs -> buildList { while (rs.next()) add(map(rs)) } }
    }
    fun count(sql: String, vararg args: Any?): Long = query(sql,*args) { it.getLong(1) }.single()
    fun rows(sql: String, vararg args: Any?): List<JsonObject> = query(sql,*args) { rs ->
        buildJsonObject {
            for (i in 1..rs.metaData.columnCount) {
                val name=rs.metaData.getColumnLabel(i)
                if (name=="catalog_version") continue
                val camel=name.replace(Regex("_([a-z0-9])")) { it.groupValues[1].uppercase() }
                val value=rs.getObject(i)
                put(camel, when (value) {
                    null -> JsonNull
                    is BigDecimal -> JsonPrimitive(value)
                    is Number -> JsonPrimitive(value)
                    is Boolean -> JsonPrimitive(value)
                    is OffsetDateTime -> JsonPrimitive(value.toInstant().toString())
                    is java.sql.Timestamp -> JsonPrimitive(value.toInstant().toString())
                    else -> JsonPrimitive(value.toString())
                })
            }
        }
    }
}
