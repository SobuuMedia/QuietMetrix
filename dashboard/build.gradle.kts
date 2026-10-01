import org.jetbrains.compose.ExperimentalComposeLibrary
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

    // JVM target exists to run unit tests: pure, Compose-free logic
    // (formatters, breakpoints, chart math, sort/filter/pagination) plus
    // Compose UI tests via runComposeUiTest()/captureToImage() (see jvmTest
    // deps below) for screenshot-regression coverage. The production
    // dashboard still ships as wasmJs only.
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
                // Backs runComposeUiTest()/captureToImage(): the JUnit4 test
                // runner that drives JVM compose tests, Compose's own
                // multiplatform UI-testing API, and Skia rendering (there is
                // no Android target in this module to get that from).
                implementation(kotlin("test-junit"))
                @OptIn(ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
                implementation(compose.desktop.currentOs)
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

// Forwards -Pgolden.record=true into the forked jvmTest JVM's system
// properties, so ScreenshotGolden.kt's writeAsGolden() can read it as
// System.getProperty("golden.record"). Without this, the Gradle project
// property and the JVM system property are two unrelated things and
// writeAsGolden()'s record-mode gate would be unreachable from the
// command line.
tasks.withType<Test>().configureEach {
    systemProperty("golden.record", providers.gradleProperty("golden.record").getOrElse("false"))
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
