import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.compose") version "1.7.3"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
}

repositories { mavenCentral(); google() }

kotlin {
    jvmToolchain(21)

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

            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.9.0")
            implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

            implementation("io.ktor:ktor-client-core:3.0.3")
            implementation("io.ktor:ktor-client-content-negotiation:3.0.3")
            implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.3")
        }
        val wasmJsMain by getting {
            dependencies {
                implementation("io.ktor:ktor-client-js:3.0.3")
                implementation("org.jetbrains.kotlinx:kotlinx-browser:0.3")
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
