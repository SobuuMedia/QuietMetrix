plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.androidKotlinMultiplatformLibrary) apply false
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.ktorPlugin) apply false
    alias(libs.plugins.flywayPlugin) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
}

// ── Development tasks ───────────────────────────────────────────────────────

tasks.register<Exec>("devStart") {
    group = "development"
    description = "Builds the dashboard, installs it into the Ktor server, starts the Ktor server in the background, and opens the browser."
    commandLine("bash", "scripts/dev-start.sh")
}

tasks.register<Exec>("devStop") {
    group = "development"
    description = "Stops the background Ktor server started by devStart."
    commandLine("bash", "scripts/dev-stop.sh")
}

tasks.register("dev") {
    group = "development"
    description = "Shows development task usage."
    doLast {
        println("""
            |QuietMetrix development tasks
            |==============================
            |
            |  ./gradlew devStart   — build dashboard + start Ktor server + open browser
            |  ./gradlew devStop    — stop the background Ktor server
            |
            |The server runs on http://localhost:8080/dashboard/
            |Requires: Postgres on localhost:5432, env vars in servers/ktor/.env
            |""".trimMargin())
    }
}