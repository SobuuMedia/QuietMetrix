plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.ktorPlugin)
    alias(libs.plugins.flywayPlugin)
    application
}

group = "com.quietmetrix"
version = "0.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.auth)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.status.pages)
    implementation(libs.ktor.server.call.logging)
    implementation(libs.ktor.server.double.receive)
    implementation(libs.ktor.server.rate.limit)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.exposed.java.time)

    implementation(libs.hikari)
    implementation(libs.postgres.driver)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    implementation(libs.koin.ktor)

    implementation(libs.logback)
    implementation(libs.kotlin.logging)
    implementation(libs.bcrypt)
    implementation(libs.java.jwt)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions.json)
    testImplementation(libs.kotest.property)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.h2)
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
        jreVersion.set(JavaVersion.VERSION_21)
        localImageName.set("quietmetrix-ktor")
        imageTag.set(version.toString())
    }
}