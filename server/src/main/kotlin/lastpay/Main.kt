package lastpay

import lastpay.api.catalogModule
import lastpay.application.*
import lastpay.infrastructure.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*

fun main(args: Array<String>) {
    val config=Configuration.load()
    val db=CatalogDatabase(config)
    when (args.firstOrNull()) {
        "migrate" -> {
            println("Database ready; migrations applied: ${db.migrate()}")
            return
        }
        "provision-roles" -> {
            db.provisionRoles()
            println("Database runtime credentials configured")
            return
        }
    }
    db.checkRuntimeAccess()
    if(config.demoMode || args.firstOrNull()=="seed") println("DEMO seed inserted: ${DemoSeeder(db).seed()}")
    if(args.firstOrNull()=="seed") return
    val service=CatalogService(db)
    println("LAST PAYCHECK API listening on ${config.host}:${config.port}; DEMO=${config.demoMode}")
    embeddedServer(Netty,port=config.port,host=config.host) {catalogModule(service,config)}.start(wait=true)
}
