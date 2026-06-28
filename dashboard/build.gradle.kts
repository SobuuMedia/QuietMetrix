import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

repositories { mavenCentral(); google() }

kotlin {
    jvmToolchain(21)

    // JVM target exists solely to run unit tests against pure, Compose-free
    // logic (formatters, breakpoints, chart math, sort/filter/pagination).
    // The production dashboard still ships as wasmJs only.
    jvm()

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig { outputFileName = "dashboard.js" }
        }
        binaries.executable()
    }

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)

            implementation(libs.kotlinx.coroutines.core)
            implementation(libs.kotlinx.serialization.json)

            implementation(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
                implementation(libs.kotlinx.browser)
            }
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "com.quietmetrix.dashboard.resources"
    generateResClass = always
}

// ---------------------------------------------------------------------------
// Distribution into the PHP and Ktor servers
// ---------------------------------------------------------------------------
// `./gradlew :dashboard:installDashboard` builds the production wasmJs bundle
// and copies it into both servers so they can serve the same dashboard:
//   - php-hosting/dashboard/                        (uploaded with the PHP files)
//   - servers/ktor/src/main/resources/dashboard/    (bundled into the JAR)
// Both server-side static handlers are configured to fall back to index.html
// for unknown paths under /dashboard so the SPA's client-side state survives a
// hard refresh.

val dashboardBundle = layout.buildDirectory.dir("dist/wasmJs/productionExecutable")

val copyDashboardToPhp by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Sync the production wasmJs dashboard into php-hosting/dashboard/."
    dependsOn("wasmJsBrowserDistribution")
    from(dashboardBundle)
    into(rootProject.file("php-hosting/dashboard"))
}

val copyDashboardToKtor by tasks.registering(Sync::class) {
    group = "distribution"
    description = "Sync the production wasmJs dashboard into the Ktor server's JAR resources."
    dependsOn("wasmJsBrowserDistribution")
    from(dashboardBundle)
    into(rootProject.file("servers/ktor/src/main/resources/dashboard"))
}

tasks.register("installDashboard") {
    group = "distribution"
    description = "Builds the production wasmJs dashboard and installs it into both servers."
    dependsOn(copyDashboardToPhp, copyDashboardToKtor)
}
