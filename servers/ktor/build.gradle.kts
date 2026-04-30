plugins {
    kotlin("jvm") version "2.1.21"
    kotlin("plugin.serialization") version "2.1.21"
    id("io.ktor.plugin") version "3.1.3"
    id("org.flywaydb.flyway") version "11.8.0"
    application
}

group = "com.quietmetrix"
version = "0.1.0"

repositories {
    mavenCentral()
}

val ktorVersion = "3.1.3"
val exposedVersion = "0.61.0"
val hikariVersion = "6.3.0"
val postgresDriverVersion = "42.7.5"
val flywayVersion = "11.8.0"
val koinVersion = "4.1.0"
val logbackVersion = "1.5.18"
val kotestVersion = "5.9.1"
val testcontainersVersion = "1.20.6"
val bcryptVersion = "0.10.2"
val jwtVersion = "4.5.0"

dependencies {
    implementation("io.ktor:ktor-server-core:$ktorVersion")
    implementation("io.ktor:ktor-server-content-negotiation:$ktorVersion")
    implementation("io.ktor:ktor-server-auth:$ktorVersion")
    implementation("io.ktor:ktor-server-auth-jwt:$ktorVersion")
    implementation("io.ktor:ktor-server-cors:$ktorVersion")
    implementation("io.ktor:ktor-server-status-pages:$ktorVersion")
    implementation("io.ktor:ktor-server-call-logging:$ktorVersion")
    implementation("io.ktor:ktor-server-double-receive:$ktorVersion")
    implementation("io.ktor:ktor-server-rate-limit:$ktorVersion")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktorVersion")

    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")

    implementation("com.zaxxer:HikariCP:$hikariVersion")
    implementation("org.postgresql:postgresql:$postgresDriverVersion")
    implementation("org.flywaydb:flyway-core:$flywayVersion")
    implementation("org.flywaydb:flyway-database-postgresql:$flywayVersion")

    implementation("io.insert-koin:koin-ktor:$koinVersion")
    implementation("io.insert-koin:koin-logger-slf4j:$koinVersion")

    implementation("ch.qos.logback:logback-classic:$logbackVersion")
    implementation("at.favre.lib:bcrypt:$bcryptVersion")
    implementation("com.auth0:java-jwt:$jwtVersion")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.6.2")

    testImplementation("io.ktor:ktor-server-test-host:$ktorVersion")
    testImplementation("io.kotest:kotest-runner-junit5:$kotestVersion")
    testImplementation("io.kotest:kotest-assertions-json:$kotestVersion")
    testImplementation("io.kotest:kotest-property:$kotestVersion")
    testImplementation("org.testcontainers:postgresql:$testcontainersVersion")
    testImplementation("org.testcontainers:junit-jupiter:$testcontainersVersion")
    testImplementation(kotlin("test"))
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.quietmetrix.server.ApplicationKt")
}

flyway {
    url = System.getenv("QM_DB_URL") ?: "jdbc:postgresql://localhost:5432/quietmetrix"
    user = System.getenv("QM_DB_USER") ?: "quietmetrix"
    password = System.getenv("QM_DB_PASSWORD") ?: "quietmetrix"
    locations = arrayOf("filesystem:migrations")
}

tasks.test {
    useJUnitPlatform()
}

ktor {
    docker {
        jreVersion.set(21)
        localImageName.set("quietmetrix-ktor")
        imageTag.set(version.toString())
        portMappings.set(listOf(io.ktor.plugin.features.DockerPortMapping(8080, 8080)))
    }
}