plugins {
    kotlin("jvm") version "2.4.21"
    kotlin("plugin.serialization") version "2.4.21"
    application
}
repositories { mavenCentral() }
val ktor = "3.6.1"
dependencies {
    implementation("io.ktor:ktor-server-netty-jvm:$ktor")
    implementation("io.ktor:ktor-server-content-negotiation-jvm:$ktor")
    implementation("io.ktor:ktor-server-status-pages-jvm:$ktor")
    implementation("io.ktor:ktor-serialization-kotlinx-json-jvm:$ktor")
    implementation("io.ktor:ktor-client-cio-jvm:$ktor")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.12.0")
    implementation("org.jetbrains.exposed:exposed-jdbc:1.6.0")
    implementation("org.flywaydb:flyway-core:13.12.0")
    implementation("org.flywaydb:flyway-database-postgresql:13.12.0")
    implementation("org.postgresql:postgresql:42.7.14")
    runtimeOnly("ch.qos.logback:logback-classic:1.6.6")
    testImplementation("org.junit.jupiter:junit-jupiter:5.14.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.ktor:ktor-server-test-host-jvm:$ktor")
    testImplementation("io.ktor:ktor-client-mock-jvm:$ktor")
}
tasks.test { useJUnitPlatform() }
kotlin { jvmToolchain(21) }
application { mainClass.set("lastpay.MainKt") }
tasks.register<JavaExec>("migrate") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("lastpay.MainKt"); args("migrate")
}
tasks.register<JavaExec>("seedDemo") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("lastpay.MainKt"); args("seed")
}

tasks.register<JavaExec>("provisionRoles") {
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("lastpay.MainKt"); args("provision-roles")
}
